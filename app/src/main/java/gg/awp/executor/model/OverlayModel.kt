package gg.awp.executor.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import gg.awp.executor.server.AwpWebSocketServer
import gg.awp.executor.server.SessionHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayModel(private val app: Application) {

    val isConnected = MutableLiveData(false)
    val outputLog = MutableLiveData<List<String>>(emptyList())

    private var wsServer: AwpWebSocketServer? = null
    private var httpServer: SessionHttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        scope.launch {
            try {
                httpServer = SessionHttpServer(app).also { it.start() }
                wsServer = AwpWebSocketServer(
                    context = app,
                    onScriptOutput = { msg -> appendOutput(msg) },
                    onClientConnected = { isConnected.postValue(true) },
                    onClientDisconnected = { isConnected.postValue(false) }
                ).also { it.start() }
            } catch (e: Exception) {
                Log.e("OverlayModel", "Failed to start servers", e)
            }
        }
    }

    fun executeScript(script: String) {
        scope.launch {
            val server = wsServer
            if (server != null && server.isClientConnected()) {
                server.executeScript(script)
            } else {
                appendOutput("sys:No executor connected")
            }
        }
    }

    fun clearOutput() { outputLog.postValue(emptyList()) }

    fun stop() {
        try { wsServer?.stop(); httpServer?.stop() } catch (_: Exception) {}
    }

    private fun appendOutput(line: String) {
        val cur = outputLog.value?.toMutableList() ?: mutableListOf()
        cur.add(line)
        if (cur.size > 500) cur.removeAt(0)
        outputLog.postValue(cur)
    }
}
