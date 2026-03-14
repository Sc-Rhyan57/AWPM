package gg.awp.executor.server

import android.content.Context
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.IExtension
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.protocols.IProtocol
import org.java_websocket.protocols.Protocol
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections
import java.util.Timer
import java.util.TimerTask

class AwpWebSocketServer(
    private val context: Context,
    private val onScriptOutput: (String) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit
) : WebSocketServer(
    InetSocketAddress("0.0.0.0", 9999),
    listOf<Draft>(
        Draft_6455(
            emptyList<IExtension>(),
            listOf<IProtocol>(Protocol(""))
        )
    )
) {

    private val tag = "AwpWsServer"

    @Volatile private var activeClient: WebSocket? = null
    @Volatile private var heartbeatTimer: Timer? = null

    init {
        isReuseAddr = true
        isTcpNoDelay = true
        connectionLostTimeout = 0
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        activeClient = conn
        startHeartbeat()
        onClientConnected()
        Log.d(tag, "onOpen: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d(tag, "onClose code=$code reason='$reason' remote=$remote addr=${conn.remoteSocketAddress}")
        if (activeClient === conn) {
            activeClient = null
            stopHeartbeat()
            onClientDisconnected()
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        when {
            message == "pong" -> {}
            message == "__ready" -> {
                Log.d(tag, "executor ready")
                onScriptOutput("sys:Executor conectado")
            }
            message.startsWith("__console:") -> onScriptOutput(message.removePrefix("__console:"))
            message.startsWith("__error:") -> onScriptOutput("error:${message.removePrefix("__error:")}")
            message.startsWith("__meta:") -> onScriptOutput(message)
            else -> onScriptOutput(message)
        }
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {}

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(tag, "onError addr=${conn?.remoteSocketAddress}: ${ex.message}")
    }

    override fun onStart() {
        Log.d(tag, "WebSocket server started on :9999")
    }

    fun executeScript(script: String) {
        val client = activeClient ?: return
        if (client.isOpen) {
            try { client.send(script) } catch (e: Exception) {
                Log.e(tag, "send failed: ${e.message}")
            }
        }
    }

    fun isClientConnected(): Boolean = activeClient?.isOpen == true

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = Timer("awp-hb", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val c = activeClient
                    if (c != null && c.isOpen) {
                        try { c.send("ping") } catch (_: Exception) { stopHeartbeat() }
                    } else {
                        stopHeartbeat()
                    }
                }
            }, 15_000L, 15_000L)
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    override fun stop(timeout: Int) {
        stopHeartbeat()
        super.stop(timeout)
    }
}
