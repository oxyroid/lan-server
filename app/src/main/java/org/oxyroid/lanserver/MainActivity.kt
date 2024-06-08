package org.oxyroid.lanserver

import android.content.Context
import android.net.nsd.NsdManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.WebSocket
import org.oxyroid.lanserver.ui.theme.LanserverTheme

class MainActivity : ComponentActivity() {
    private val lanService by lazy {
        LanService.default(
            getSystemService(Context.NSD_SERVICE) as NsdManager,
            listOf(
                Endpoint {
                    it.webSocket("/say_hello") {
                        for (frame in incoming) {
                            log("frame: $frame")
                            when (frame) {
                                is Frame.Close, is Frame.Text -> received = frame
                                else -> {}
                            }
                        }
                    }
                }
            )
        )
    }
    private var isClient by mutableStateOf(false)
    private var received: Frame? by mutableStateOf(null)
    private var msg: String by mutableStateOf("")
    private var ws: WebSocket? by mutableStateOf(null)
    private var logs: String by mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val deployed by lanService.deployed.collectAsState()
            LanserverTheme {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = isClient,
                            onClick = { isClient = true },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 0,
                                count = 2,
                                baseShape = RoundedCornerShape(8.dp)
                            ),
                            label = { Text("Client") }
                        )
                        SegmentedButton(
                            selected = !isClient,
                            onClick = { isClient = false },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = 1,
                                count = 2,
                                baseShape = RoundedCornerShape(8.dp)
                            ),
                            label = { Text("Server") }
                        )
                    }

                    if (isClient) {
                        Text(
                            text = "",
                            modifier = Modifier.fillMaxWidth()
                        )
                        var input by remember { mutableStateOf("") }
                        Row {
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { sendToServer(input) }) {
                                Text(text = "send")
                            }
                            if (ws == null) {
                                Button(onClick = { connect() }) {
                                    Text(text = "connect")
                                }
                            } else {
                                Button(
                                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                                    onClick = { disconnect() }
                                ) {
                                    Text(text = "disconnect")
                                }
                            }
                        }
                    } else {
                        val currentDeployed = deployed
                        if (currentDeployed != null) {
                            Text(
                                text = when (val r = received) {
                                    is Frame.Text -> r.readText()
                                    is Frame.Close -> "closed"
                                    else -> ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("IP: ${currentDeployed.host}:${currentDeployed.port}")
                        }

                        if (deployed == null) {
                            Button(onClick = { deploy() }) {
                                Text(text = "deploy")
                            }
                        } else {
                            Button(
                                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                                onClick = { shutdown() }
                            ) {
                                Text(text = "shutdown")
                            }
                        }
                    }

                    Text(
                        text = logs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(
                                rememberScrollState()
                            )
                    )
                }
            }
        }
    }

    private fun deploy() {
        deployJob = lanService
            .deploy()
            .launchIn(lifecycleScope)
    }

    private fun shutdown() {
        deployJob?.cancel()
        deployJob = null
    }

    private var deployJob: Job? = null

    private fun connect() {
        disconnect()
        val callback = object : LanService.RealtimeCallback {
            override fun onMessage(text: String) {
                msg = text
            }

            override fun onClosed() {
                log("onClosed")
                ws = null
            }

            override fun onClosing() {
                log("onClosing")
                ws = null
            }

            override fun onFailure(t: Throwable) {
                log(t.message)
                ws = null
            }
        }
        lanService
            .connect(
                callback = callback,
                clazz = SayHelloApi::class.java
            )
            .onEach { state ->
                log("state: $state")
                when (state) {
                    is LanService.State.Connected -> {
                        ws = state.websocket
                    }

                    is LanService.State.Error,
                    LanService.State.Idle,
                    LanService.State.Timeout -> {
                        ws = null
                    }

                    else -> {}
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun disconnect() {
        ws?.close(1000, null)
        ws = null
    }

    private fun sendToServer(input: String) {
        val currentWs = ws
        if (currentWs == null) {
            log("Client is offline")
            return
        }
        currentWs.send(input)
    }

    private fun log(any: Any?) {
        logs += "\n" + any?.toString()
    }
}

private interface SayHelloApi {}