package com.rhyan57.awp.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rhyan57.awp.data.db.AppDatabase
import com.rhyan57.awp.data.model.*
import com.rhyan57.awp.data.repository.ScriptBloxRepository
import com.rhyan57.awp.network.AwpService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db        = AppDatabase.getInstance(application)
    private val dao       = db.savedScriptDao()
    private val sbRepo    = ScriptBloxRepository()
    private val prefs: SharedPreferences = application.getSharedPreferences("awp_prefs", Context.MODE_PRIVATE)

    private var awpService: AwpService? = null

    val savedScripts: StateFlow<List<SavedScript>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _consoleOutput = MutableStateFlow<List<String>>(emptyList())
    val consoleOutput: StateFlow<List<String>> = _consoleOutput

    private val _sbScripts = MutableStateFlow<List<ScriptBloxScript>>(emptyList())
    val sbScripts: StateFlow<List<ScriptBloxScript>> = _sbScripts

    private val _sbLoading = MutableStateFlow(false)
    val sbLoading: StateFlow<Boolean> = _sbLoading

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _editorContent = MutableStateFlow("")
    val editorContent: StateFlow<String> = _editorContent

    private val _executorVisible = MutableStateFlow(false)
    val executorVisible: StateFlow<Boolean> = _executorVisible

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val b = binder as AwpService.AwpBinder
            awpService = b.getService()
            viewModelScope.launch {
                b.getService().wsServer.state.collect { _connectionState.value = it }
            }
            viewModelScope.launch {
                b.getService().wsServer.consoleOutput.collect { _consoleOutput.value = it }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { awpService = null }
    }

    init {
        val intent = Intent(application, AwpService::class.java)
        application.startForegroundService(intent)
        application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        loadScriptBlox()
    }

    fun generateSessionToken(): String = UUID.randomUUID().toString().replace("-", "").take(62)

    fun executeScript(script: String) {
        viewModelScope.launch {
            val sent = awpService?.wsServer?.executeScript(script) ?: false
            if (!sent) appendConsole("[AWP] Not connected — no client")
        }
    }

    fun setEditorContent(content: String) { _editorContent.value = content }

    fun toggleExecutor() { _executorVisible.value = !_executorVisible.value }

    fun openExecutor() { _executorVisible.value = true }

    fun saveScript(title: String, content: String, autoExec: Boolean = false) {
        viewModelScope.launch {
            dao.insert(SavedScript(title = title, content = content, isAutoExec = autoExec))
        }
    }

    fun deleteScript(script: SavedScript) {
        viewModelScope.launch { dao.delete(script) }
    }

    fun toggleAutoExec(script: SavedScript) {
        viewModelScope.launch { dao.update(script.copy(isAutoExec = !script.isAutoExec)) }
    }

    fun loadScriptBlox(query: String = "", page: Int = 1) {
        viewModelScope.launch {
            _sbLoading.value = true
            _sbScripts.value = sbRepo.fetchScripts(query, page)
            _sbLoading.value = false
        }
    }

    fun updateSetting(update: AppSettings.() -> AppSettings) {
        val updated = _settings.value.update()
        _settings.value = updated
        saveSettings(updated)
    }

    fun isClientConnected() = awpService?.wsServer?.isClientConnected() ?: false

    fun getLocalIp(): String {
        return try {
            val socket = java.net.DatagramSocket()
            socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 80)
            val ip = socket.localAddress.hostAddress ?: "127.0.0.1"
            socket.close()
            ip
        } catch (_: Exception) { "127.0.0.1" }
    }

    private fun appendConsole(line: String) {
        val list = _consoleOutput.value.toMutableList()
        list.add(line)
        _consoleOutput.value = list
    }

    private fun loadSettings(): AppSettings = AppSettings(
        deleteOriginalGuiOnExec = prefs.getBoolean("delete_orig_gui", true),
        autoExecEnabled         = prefs.getBoolean("auto_exec", false),
        wsPort                  = prefs.getInt("ws_port", 8765),
        httpPort                = prefs.getInt("http_port", 8080),
        uiVisible               = prefs.getBoolean("ui_visible", true),
        uiLocked                = prefs.getBoolean("ui_locked", false)
    )

    private fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("delete_orig_gui", s.deleteOriginalGuiOnExec)
            .putBoolean("auto_exec", s.autoExecEnabled)
            .putInt("ws_port", s.wsPort)
            .putInt("http_port", s.httpPort)
            .putBoolean("ui_visible", s.uiVisible)
            .putBoolean("ui_locked", s.uiLocked)
            .apply()
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(serviceConnection)
    }
}
