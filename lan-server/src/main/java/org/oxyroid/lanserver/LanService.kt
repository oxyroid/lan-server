package org.oxyroid.lanserver

import android.net.nsd.NsdManager
import io.ktor.server.websocket.WebSocketServerSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.WebSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface LanService {
    // for server side
    val deployed: StateFlow<ServerInfo?>
    fun deploy(): Flow<Unit>

    // for client side
    fun <API> connect(
        timeout: Duration = 8.seconds,
        clazz: Class<API>,
        callback: RealtimeCallback
    ): Flow<State<API>>

    sealed class State<out API> {
        data object Idle : State<Nothing>()
        data object Searching : State<Nothing>()
        data object Timeout : State<Nothing>()

        data class Connecting(val info: ServerInfo) : State<Nothing>()
        data class Connected<API>(
            val api: API,
            val websocket: WebSocket
        ) : State<API>()

        data class Error(val cause: Throwable) : State<Nothing>()
    }

    data class ServerInfo(
        val host: String,
        val port: Int,
        val deviceInfo: DeviceInfo? = null
    )

    data class DeviceInfo(
        val version: String,
        val debug: Boolean
    )

    fun interface RealtimeCallback {
        fun onMessage(text: String)
        fun onClosing() {}
        fun onClosed() {}
        fun onFailure(t: Throwable) {}
    }

    companion object
}

fun LanService.Companion.default(
    nsdManager: NsdManager,
    endpoints: List<Endpoint>
): LanService {
    return NsdLanService(
        nsdDeviceManager = NsdDeviceManagerImpl(
            nsdManager = nsdManager
        ),
        httpServer = NettyHttpServer(endpoints)
    )
}