package org.oxyroid.lanserver

import io.ktor.http.URLBuilder
import io.ktor.http.set
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

internal object Utils {
    fun findPortOrThrow(): Int = ServerSocket(0).use { it.localPort }
    fun getLocalHostAddress(): InetAddress? = NetworkInterface
        .getNetworkInterfaces()
        .iterator()
        .asSequence()
        .flatMap { networkInterface ->
            networkInterface.inetAddresses
                .asSequence()
                .filter { !it.isLoopbackAddress }
        }
        .toList()
        .firstOrNull { it.isLocalAddress() }

    private fun InetAddress.isLocalAddress(): Boolean {
        try {
            return isSiteLocalAddress
                    && !hostAddress!!.contains(":")
                    && hostAddress != "127.0.0.1"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun <API> createHttpApi(
        serverInfo: LanService.ServerInfo,
        clazz: Class<API>
    ): API {
        val baseUrl = URLBuilder().apply {
            set("http", serverInfo.host, serverInfo.port)
        }
            .buildString()
        val json = Json { ignoreUnknownKeys = true }
        val okHttpClient = createOkhttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
        return retrofit.create(clazz)
    }

    fun createWebsocket(
        serverInfo: LanService.ServerInfo,
        listener: WebSocketListener,
        path: String?
    ): WebSocket {
        val okHttpClient = createOkhttpClient()
        val baseUrl = URLBuilder().apply {
            set("http", serverInfo.host, serverInfo.port, path)
        }
            .buildString()
        return okHttpClient.newWebSocket(
            Request.Builder()
                .url(baseUrl)
                .build(),
            listener
        )
    }

    private fun createOkhttpClient(): OkHttpClient = OkHttpClient.Builder()
        .build()
}