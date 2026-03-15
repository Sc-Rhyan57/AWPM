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
) : WebSocketServer(InetSocketAddress("0.0.0.0", 9999)) {

    private val tag = "AwpWsServer"

    @Volatile private var activeClient: WebSocket? = null

    init {
        isReuseAddr = true
        isTcpNoDelay = false
        connectionLostTimeout = 0
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        activeClient?.let { old ->
            if (old !== conn && old.isOpen) {
                try { old.close() } catch (_: Exception) {}
            }
        }
        
        activeClient = conn
        onClientConnected()
        Log.d(tag, "onOpen: ${conn.remoteSocketAddress}")
        
        try {
            Thread.sleep(100)
            conn.send("__ping")
        } catch (_: Exception) {}
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d(tag, "onClose code=$code reason='$reason' remote=$remote")
        if (activeClient === conn) {
            activeClient = null
            onClientDisconnected()
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d(tag, "onMessage: ${message.take(50)}")
        when {
            message == "__pong" || message == "pong" -> {}
            message == "__ready" -> onScriptOutput("sys:Executor conectado")
            message.startsWith("__console:") -> onScriptOutput(message.removePrefix("__console:"))
            message.startsWith("__error:") -> onScriptOutput("error:${message.removePrefix("__error:")}")
            message.startsWith("__meta:") -> onScriptOutput(message)
            else -> onScriptOutput(message)
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {}

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(tag, "onError: ${ex.message}", ex)
    }

    override fun onStart() {
        Log.d(tag, "WebSocket server started on :9999 (SIMPLE MODE)")
    }

    fun executeScript(script: String) {
        val client = activeClient ?: return
        if (client.isOpen) {
            try { 
                client.send(script)
                Log.d(tag, "Sent ${script.length} chars")
            } catch (e: Exception) {
                Log.e(tag, "send failed: ${e.message}")
            }
        }
    }

    fun isClientConnected(): Boolean = activeClient?.isOpen == true
}
