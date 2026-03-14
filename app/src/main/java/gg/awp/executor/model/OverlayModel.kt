package gg.awp.executor.model

import android.app.Application
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private var disconnectRunnable: Runnable? = null
    private val disconnectDebounceMs = 1500L

    fun start() {
        scope.launch {
            try {
                httpServer = SessionHttpServer(app).also { it.start() }
                wsServer = AwpWebSocketServer(
                    context = app,
                    onScriptOutput = { msg -> appendOutput(msg) },
                    onClientConnected = {
                        mainHandler.post {
                            disconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                            disconnectRunnable = null
                            isConnected.value = true
                        }
                    },
                    onClientDisconnected = {
                        val r = Runnable {
                            if (wsServer?.isClientConnected() != true) {
                                isConnected.value = false
                            }
                        }
                        disconnectRunnable = r
                        mainHandler.postDelayed(r, disconnectDebounceMs)
                    }
                ).also { it.start() }
            } catch (e: Exception) {
                Log.e("OverlayModel", "Failed to start servers", e)
            }
        }
    }

    fun executeScript(script: String) {
        if (script.isBlank()) return
        scope.launch {
            val server = wsServer
            if (server != null && server.isClientConnected()) {
                server.executeScript(script)
            } else {
                appendOutput("sys:Nenhum executor conectado")
            }
        }
    }

    fun clearOutput() { outputLog.postValue(emptyList()) }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        try { wsServer?.stop(); httpServer?.stop() } catch (_: Exception) {}
    }

    private fun appendOutput(line: String) {
        val cur = outputLog.value?.toMutableList() ?: mutableListOf()
        cur.add(line)
        if (cur.size > 500) cur.removeAt(0)
        outputLog.postValue(cur)
    }
}
