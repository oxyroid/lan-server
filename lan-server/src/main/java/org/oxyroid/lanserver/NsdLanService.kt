package org.oxyroid.lanserver

import io.ktor.server.websocket.WebSocketServerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.time.Duration

internal class NsdLanService(
    private val nsdDeviceManager: NsdDeviceManager,
    private val httpServer: HttpServer
) : LanService {
    override val deployed = MutableStateFlow<LanService.ServerInfo?>(null)
    override fun deploy(): Flow<Unit> = channelFlow<Unit> {
        val serverPort = Utils.findPortOrThrow()
        httpServer.start(serverPort)
        while (isActive) {
            val nsdPort = Utils.findPortOrThrow()
            val host = Utils.getLocalHostAddress()?.hostAddress ?: continue

            nsdDeviceManager
                .broadcast(
                    port = nsdPort,
                    metadata = mapOf(
                        NsdDeviceManager.META_DATA_PORT to serverPort,
                        NsdDeviceManager.META_DATA_HOST to host
                    )
                )
                .onStart {
//                    logger.log("start-server: opening...")
                }
                .onCompletion {
                    deployed.value = null
//                    logger.log("start-server: nsd completed")
                }
                .onEach { registered ->
//                    logger.log("start-server: registered: $registered")
                    deployed.value = registered
                }
                .collect()
        }
    }
        .onCompletion { httpServer.stop() }
        .flowOn(Dispatchers.IO)

    override fun <API> connect(
        timeout: Duration,
        clazz: Class<API>,
        callback: LanService.RealtimeCallback
    ): Flow<LanService.State<API>> = channelFlow<LanService.State<API>> {
        val websocket: WebSocket
        val info = nsdDeviceManager
            .search()
            .onStart { send(LanService.State.Searching) }
            .timeout(timeout) {
                trySend(LanService.State.Timeout)
                cancel()
            }
            .firstOrNull()
            ?: return@channelFlow

        send(LanService.State.Connecting(info))

        val api = Utils.createHttpApi(info, clazz)
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                callback.onMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                callback.onClosing()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                callback.onClosed()
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                callback.onFailure(t)
            }
        }

        websocket = Utils.createWebsocket(
            info,
            listener,
            "say_hello"
        )

        send(
            LanService.State.Connected(api, websocket)
        )

        awaitClose {
            websocket.close(1000, null)
        }
    }
        .catch { emit(LanService.State.Error(it)) }

    @OptIn(FlowPreview::class)
    private fun <T> Flow<T>.timeout(duration: Duration, block: FlowCollector<T>.() -> Unit) =
        this@timeout.timeout(duration).catch {
            if (it is TimeoutCancellationException) {
                block()
            }
        }
}
