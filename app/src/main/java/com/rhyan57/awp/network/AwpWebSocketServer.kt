package com.rhyan57.awp.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.rhyan57.awp.data.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class AwpWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val gson = Gson()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state

    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput

    private var activeClient: WebSocket? = null
    private var clientName: String = "unknown"

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        activeClient = conn
        appendConsole("[AWP] Client connected: ${conn.remoteSocketAddress}")
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        if (conn == activeClient) {
            activeClient = null
            clientName   = "unknown"
            _state.value = ConnectionState.WaitingForClient
            appendConsole("[AWP] Client disconnected")
        }
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val json   = JsonParser.parseString(message).asJsonObject
            val method = json.get("Method")?.asString ?: return

            when (method) {
                "Authorization" -> {
                    clientName   = json.get("Name")?.asString ?: "unknown"
                    _state.value = ConnectionState.Connected(clientName)
                    appendConsole("[AWP] Authorized as: $clientName")
                }
                "Error" -> {
                    val msg = json.get("Message")?.asString ?: "unknown error"
                    appendConsole("[ERR] $msg")
                }
                else -> appendConsole("[MSG] $message")
            }
        } catch (_: Exception) {
            appendConsole("[RAW] $message")
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        _state.value = ConnectionState.Error(ex.message ?: "unknown error")
        appendConsole("[ERR] ${ex.message}")
    }

    override fun onStart() {
        _state.value = ConnectionState.WaitingForClient
        appendConsole("[AWP] WebSocket server started on port ${address.port}")
    }

    fun executeScript(script: String): Boolean {
        val client = activeClient ?: return false
        if (!client.isOpen) return false
        client.send(gson.toJson(mapOf("Method" to "Execute", "Data" to script)))
        appendConsole("[EXEC] Sent ${script.length} chars to $clientName")
        return true
    }

    fun isClientConnected(): Boolean = activeClient?.isOpen == true

    private fun appendConsole(line: String) {
        val current = _consoleOutput.value.toMutableList()
        current.add(line)
        if (current.size > 200) current.removeAt(0)
        _consoleOutput.value = current
    }
}
