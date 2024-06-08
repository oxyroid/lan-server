package org.oxyroid.lanserver

import android.util.Log
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration

internal class NettyHttpServer(
    private val endpoints: List<Endpoint>
) : HttpServer {
    private var server: EmbeddedServer<*, *>? = null
    private var loggingCoroutineDispatcher = Dispatchers.IO

    override fun start(port: Int) {
        server = embeddedServer(Netty, port) {
            configureSerialization()
            configureSockets()
            configureCors()
            routing {
                endpoints.forEach { it.apply(this) }
            }
        }.apply {
            CoroutineScope(loggingCoroutineDispatcher).launch {
                engine.resolvedConnectors().forEach {
                    val host = escapeHostname(it.host)
                    Log.e(
                        "NettyHttpServer",
                        "Responding at ${it.type.name.lowercase()}://$host:${it.port}"
                    )
                }
            }
            start(false)
        }
    }

    override fun stop() {
        server?.stop()
        server = null
    }

    private fun Application.configureSerialization() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                }
            )
        }
    }

    private fun Application.configureSockets() {
        install(WebSockets) {
            val json = Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }
            contentConverter = KotlinxWebsocketSerializationConverter(json)
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
        }
    }

    private fun Application.configureCors() {
        install(CORS) {
            anyHost()
            allowSameOrigin = true
        }
    }

    private val OS_NAME = System
        .getProperty("os.name", "")!!
        .lowercase()

    private fun escapeHostname(value: String): String {
        if (!OS_NAME.contains("windows")) return value
        if (value != "0.0.0.0") return value

        return "127.0.0.1"
    }
}