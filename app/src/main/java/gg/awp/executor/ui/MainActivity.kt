package gg.awp.executor.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import gg.awp.executor.R
import gg.awp.executor.model.ExecutorViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private val vm: ExecutorViewModel by viewModels()
    private lateinit var wv: WebView
    private var pickMode = 0

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.data ?: return@registerForActivityResult
            val content = readUri(uri) ?: return@registerForActivityResult
            val name = getFileName(uri)
            when (pickMode) {
                1 -> {
                    val safe = escapeForJs(content)
                    val safeName = name.replace("'", "\\'")
                    wv.post { wv.evaluateJavascript("window.dispatchEvent(new CustomEvent('awp:file',{detail:{name:'$safeName',content:'$safe'}}))", null) }
                }
                2 -> vm.executeScript(content)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wv = findViewById(R.id.webView)
        setupWebView()
        setupObservers()
        wv.loadUrl("file:///android_asset/ui/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = WebViewClient()
        wv.addJavascriptInterface(Bridge(), "AwpNative")
    }

    private fun setupObservers() {
        vm.isConnected.observe(this) { c ->
            val s = if (c) "connected" else "disconnected"
            wv.post { wv.evaluateJavascript("window.dispatchEvent(new CustomEvent('awp:status',{detail:'$s'}))", null) }
        }
        vm.outputLog.observe(this) { logs ->
            if (logs.isNotEmpty()) {
                val raw = logs.last()
                val colonIdx = raw.indexOf(":")
                val type: String
                val content: String
                if (colonIdx > 0 && colonIdx < 10) {
                    type = raw.substring(0, colonIdx)
                    content = raw.substring(colonIdx + 1)
                } else {
                    type = "print"
                    content = raw
                }
                val safeContent = escapeForJs(content)
                wv.post {
                    wv.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('awp:output',{detail:{type:'$type',msg:'$safeContent'}}))",
                        null
                    )
                }
            }
        }
    }

    private fun escapeForJs(s: String) = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")

    private fun readUri(uri: Uri) = try {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) { null }

    private fun getFileName(uri: Uri): String {
        var name = "script.lua"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && col >= 0) name = c.getString(col)
        }
        return name
    }

    private fun launchPicker(mode: Int) {
        pickMode = mode
        val i = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/x-lua", "application/octet-stream"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        picker.launch(Intent.createChooser(i, "Open Script"))
    }

    private fun getDeltaDir(): File =
        File(Environment.getExternalStorageDirectory(), "Delta")

    private fun readDeltaFolder(sub: String): String {
        val dir = File(getDeltaDir(), sub)
        if (!dir.exists() || !dir.isDirectory) return "[]"
        val arr = JSONArray()
        dir.listFiles()
            ?.filter { it.isFile && (it.extension == "lua" || it.extension == "luau" || it.extension == "txt") }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                val obj = JSONObject()
                obj.put("name", f.name)
                obj.put("content", f.readText())
                arr.put(obj)
            }
        return arr.toString()
    }

    inner class Bridge {

        @JavascriptInterface
        fun executeScript(s: String) = vm.executeScript(s)

        @JavascriptInterface
        fun clearOutput() = vm.clearOutput()

        @JavascriptInterface
        fun isConnected(): Boolean = vm.isConnected.value == true

        @JavascriptInterface
        fun openFilePicker() { wv.post { launchPicker(1) } }

        @JavascriptInterface
        fun openFileAndExecute() { wv.post { launchPicker(2) } }

        @JavascriptInterface
        fun minimize() = wv.post { moveTaskToBack(true) }

        @JavascriptInterface
        fun maximize() {}

        @JavascriptInterface
        fun closeApp() = wv.post { finish() }

        @JavascriptInterface
        fun reattach() {}

        @JavascriptInterface
        fun setTopmost(on: Boolean) {}

        @JavascriptInterface
        fun setAutoexec(on: Boolean) {}

        @JavascriptInterface
        fun clearAppState() {}

        @JavascriptInterface
        fun getWorkspaceFiles(): String = readDeltaFolder("Scripts")

        @JavascriptInterface
        fun getAutoexecFiles(): String = readDeltaFolder("Autoexecute")

        @JavascriptInterface
        fun saveWorkspaceFile(name: String, content: String) {
            try {
                val ws = File(getDeltaDir(), "workspace/AWPM")
                if (!ws.exists()) ws.mkdirs()
                File(ws, name).writeText(content)
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun openFolder(path: String) {
            wv.post {
                try {
                    val dir = File(path)
                    if (!dir.exists()) dir.mkdirs()
                    val uri = Uri.parse("content://com.android.externalstorage.documents/root/primary")
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "resource/folder")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        val fallback = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("file://$path")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { startActivity(fallback) } catch (e2: Exception) {}
                    }
                } catch (e: Exception) {}
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); wv.destroy() }
}
