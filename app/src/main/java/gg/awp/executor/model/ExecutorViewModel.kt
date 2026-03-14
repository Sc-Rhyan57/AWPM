package gg.awp.executor.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import gg.awp.executor.server.AwpWebSocketServer
import gg.awp.executor.server.SessionHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExecutorViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ExecutorViewModel"

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _outputLog = MutableLiveData<List<String>>(emptyList())
    val outputLog: LiveData<List<String>> = _outputLog

    private var wsServer: AwpWebSocketServer? = null
    private var httpServer: SessionHttpServer? = null

    init {
        startServers()
    }

    private fun startServers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>().applicationContext

                httpServer = SessionHttpServer(ctx).also { it.start() }
                Log.d(tag, "HTTP server started on :8080")

                wsServer = AwpWebSocketServer(
                    context = ctx,
                    onScriptOutput = { msg -> appendOutput(msg) },
                    onClientConnected = {
                        _isConnected.postValue(true)
                        Log.d(tag, "Roblox connected")
                    },
                    onClientDisconnected = {
                        _isConnected.postValue(false)
                        Log.d(tag, "Roblox disconnected")
                    }
                ).also { it.start() }

                Log.d(tag, "WebSocket server started on :9999")
            } catch (e: Exception) {
                Log.e(tag, "Failed to start servers", e)
            }
        }
    }

    fun executeScript(script: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = wsServer
            if (server != null && server.isClientConnected()) {
                server.executeScript(script)
            } else {
                appendOutput("[AWP] No executor connected")
            }
        }
    }

    private fun appendOutput(line: String) {
        val current = _outputLog.value?.toMutableList() ?: mutableListOf()
        current.add(line)
        if (current.size > 500) {
            current.removeAt(0)
        }
        _outputLog.postValue(current)
    }

    fun clearOutput() {
        _outputLog.postValue(emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        try {
            wsServer?.stop()
            httpServer?.stop()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping servers", e)
        }
    }
}
