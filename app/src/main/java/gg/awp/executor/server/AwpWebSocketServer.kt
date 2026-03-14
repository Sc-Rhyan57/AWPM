package gg.awp.executor.server

import android.content.Context
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class AwpWebSocketServer(
    private val context: Context,
    private val onScriptOutput: (String) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit
) : WebSocketServer(InetSocketAddress(9999)) {

    private var activeClient: WebSocket? = null
    private val tag = "AwpWebSocketServer"

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        activeClient = conn
        onClientConnected()
        Log.d(tag, "Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        if (activeClient == conn) {
            activeClient = null
        }
        onClientDisconnected()
        Log.d(tag, "Client disconnected: code=$code reason=$reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        when {
            message == "ping" -> conn.send("pong")
            message == "__ready" -> Log.d(tag, "Roblox executor ready")
            message.startsWith("__console:") -> {
                val payload = message.removePrefix("__console:")
                onScriptOutput(payload)
            }
            message.startsWith("__error:") -> {
                val error = message.removePrefix("__error:")
                Log.e(tag, "Script error: $error")
                onScriptOutput("error:$error")
            }
            else -> onScriptOutput(message)
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {}

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(tag, "WebSocket error", ex)
    }

    override fun onStart() {
        Log.d(tag, "WebSocket server started on port 9999")
        connectionLostTimeout = 30
    }

    fun executeScript(script: String) {
        val client = activeClient
        if (client != null && client.isOpen) {
            client.send(script)
        } else {
            Log.w(tag, "No active client to execute script")
        }
    }

    fun isClientConnected(): Boolean = activeClient != null && activeClient!!.isOpen
}
