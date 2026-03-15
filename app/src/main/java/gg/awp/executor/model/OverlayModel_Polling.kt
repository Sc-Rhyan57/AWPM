package gg.awp.executor.model

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import gg.awp.executor.server.PollingHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayModel(private val app: Application) {

    val isConnected = MutableLiveData(false)
    val outputLog = MutableLiveData<List<String>>(emptyList())

    private var httpServer: PollingHttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        scope.launch {
            try {
                httpServer = PollingHttpServer(
                    context = app,
                    onScriptOutput = { msg -> appendOutput(msg) },
                    onClientConnected = {
                        mainHandler.post {
                            isConnected.value = true
                        }
                    },
                    onClientDisconnected = {
                        mainHandler.post {
                            isConnected.value = false
                        }
                    }
                ).also { it.start() }
                Log.d("OverlayModel", "HTTP Polling server started on :8080")
            } catch (e: Exception) {
                Log.e("OverlayModel", "Failed to start server", e)
            }
        }
    }

    fun executeScript(script: String) {
        if (script.isBlank()) return
        scope.launch {
            if (httpServer?.isClientConnected() == true) {
                httpServer?.executeScript(script)
            } else {
                appendOutput("sys:Nenhum executor conectado")
            }
        }
    }

    fun clearOutput() { outputLog.postValue(emptyList()) }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        try { httpServer?.stop() } catch (_: Exception) {}
    }

    private fun appendOutput(line: String) {
        val cur = outputLog.value?.toMutableList() ?: mutableListOf()
        cur.add(line)
        if (cur.size > 500) cur.removeAt(0)
        outputLog.postValue(cur)
    }
}
