package gg.awp.executor.server

import android.content.Context
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask

class AwpWebSocketServer(
    private val context: Context,
    private val onScriptOutput: (String) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit
) : WebSocketServer(InetSocketAddress(9999)) {

    private var activeClient: WebSocket? = null
    private val tag = "AwpWsServer"
    private var pingTimer: Timer? = null

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        activeClient = conn
        onClientConnected()
        startPingTimer()
        Log.d(tag, "Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        if (activeClient == conn) {
            activeClient = null
            stopPingTimer()
            onClientDisconnected()
        }
        Log.d(tag, "Client disconnected: code=$code reason=$reason remote=$remote")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        when {
            message == "pong" -> {}
            message == "__ready" -> {
                Log.d(tag, "Executor ready")
                onScriptOutput("sys:Executor conectado")
            }
            message.startsWith("__console:") -> {
                onScriptOutput(message.removePrefix("__console:"))
            }
            message.startsWith("__error:") -> {
                onScriptOutput("error:${message.removePrefix("__error:")}")
            }
            message.startsWith("__meta:") -> {
                onScriptOutput(message)
            }
            else -> onScriptOutput(message)
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {}

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(tag, "WebSocket error: ${ex.message}")
    }

    override fun onStart() {
        connectionLostTimeout = 0
        Log.d(tag, "WebSocket server started on port 9999")
    }

    fun executeScript(script: String) {
        val client = activeClient
        if (client != null && client.isOpen) {
            client.send(script)
        }
    }

    fun isClientConnected(): Boolean = activeClient?.isOpen == true

    private fun startPingTimer() {
        stopPingTimer()
        pingTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val client = activeClient
                    if (client != null && client.isOpen) {
                        try { client.send("ping") } catch (_: Exception) {}
                    } else {
                        stopPingTimer()
                    }
                }
            }, 10_000L, 10_000L)
        }
    }

    private fun stopPingTimer() {
        pingTimer?.cancel()
        pingTimer = null
    }

    override fun stop() {
        stopPingTimer()
        super.stop()
    }
}
