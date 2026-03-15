package gg.awp.executor.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import gg.awp.executor.model.OverlayModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface

class OverlayService : Service() {

    companion object {
        var running = false
        const val NOTIF_CHANNEL = "awp_overlay"
        const val NOTIF_ID = 42
        const val OVERLAY_W_DP = 640
        const val OVERLAY_H_DP = 480
        const val HTTP_PORT = 8080
        const val AUTOEXEC_FILE = ".AWP_SESSION.lua"
        const val CORNER_RADIUS_DP = 14

        val KNOWN_EXECUTOR_PATHS = listOf(
            "Delta"    to listOf("Delta/Scripts",    "Delta/Autoexecute"),
            "Hydrogen" to listOf("Hydrogen/scripts", "Hydrogen/autoexec"),
            "Fluxus"   to listOf("Fluxus/scripts",   "Fluxus/autoexec"),
            "Codex"    to listOf("Codex/scripts",    "Codex/autoexec"),
            "Arceus"   to listOf("AceusX/scripts",   "AceusX/autoexec"),
            "Solara"   to listOf("Solara/scripts",   "Solara/autoexec"),
            "Wave"     to listOf("Wave/scripts",     "Wave/autoexec"),
        )
    }

    private lateinit var wm: WindowManager
    private lateinit var overlayView: FrameLayout
    private lateinit var wv: WebView
    private lateinit var model: OverlayModel

    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var isLocked = false
    private var initialW = 0; private var initialH = 0
    private var resizeTouchX = 0f; private var resizeTouchY = 0f
    private var isResizing = false

    private var detectedExecutorName = "Unknown"
    private var detectedScriptsPath = ""
    private var detectedAutoexecPath = ""

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun getScreenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds; Pair(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }

    private fun getLocalIp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (iface in ifaces)
                for (addr in iface.inetAddresses)
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true)
                        return addr.hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun buildLoadstring() =
        "loadstring(game:HttpGet(\"http://${getLocalIp()}:$HTTP_PORT/session/script\"))()"

    private fun detectExecutor() {
        val base = Environment.getExternalStorageDirectory()
        for ((name, paths) in KNOWN_EXECUTOR_PATHS) {
            val scripts = paths.firstOrNull { it.contains("script", ignoreCase = true) } ?: continue
            if (File(base, scripts).exists()) {
                detectedExecutorName = name
                detectedScriptsPath = "/sdcard/$scripts"
                val auto = paths.firstOrNull { it.contains("auto", ignoreCase = true) } ?: ""
                detectedAutoexecPath = if (auto.isNotEmpty()) "/sdcard/$auto" else ""
                return
            }
        }
        detectedExecutorName = "Unknown"
        detectedScriptsPath = "/sdcard/Delta/Scripts"
        detectedAutoexecPath = "/sdcard/Delta/Autoexecute"
    }

    private fun writeAutoexecSession() {
        try {
            val dir = File(detectedAutoexecPath.ifEmpty { "/sdcard/Delta/Autoexecute" })
            if (!dir.exists()) dir.mkdirs()
            File(dir, AUTOEXEC_FILE).writeText(buildLoadstring())
        } catch (_: Exception) {}
    }

    private fun copyToClipboard() {
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("AWP", buildLoadstring()))
        } catch (_: Exception) {}
    }

    private fun getAwpWorkspaceDir(): File =
        File(Environment.getExternalStorageDirectory(), "Delta/workspace/AWPM")
            .also { if (!it.exists()) it.mkdirs() }

    private fun readLogcat(): String {
        return try {
            val pid = android.os.Process.myPid().toString()
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "--pid=$pid", "-t", "300"))
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(proc.inputStream)).use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) sb.append(line).append('\n')
            }
            sb.toString()
        } catch (e: Exception) { "Erro ao ler logcat: ${e.message}" }
    }

    private fun buildRoundedBackground(): GradientDrawable {
        val cornerPx = dpToPx(CORNER_RADIUS_DP).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#0d0d0d"))
            cornerRadii = floatArrayOf(
                cornerPx, cornerPx,
                cornerPx, cornerPx,
                cornerPx, cornerPx,
                cornerPx, cornerPx
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate() {
        super.onCreate()
        running = true
        model = OverlayModel(application)
        model.start()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val (screenW, screenH) = getScreenSize()
        val overlayW = minOf(dpToPx(OVERLAY_W_DP), screenW)
        val overlayH = minOf(dpToPx(OVERLAY_H_DP), screenH)
        initialW = overlayW; initialH = overlayH

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            overlayW, overlayH, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW - overlayW) / 2
            y = (screenH - overlayH) / 6
        }

        overlayView = FrameLayout(this).apply {
            background = buildRoundedBackground()
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            keepScreenOn = true
        }

        setupWebView()
        overlayView.addView(wv)
        setupDrag()
        wm.addView(overlayView, params)
        startForeground(NOTIF_ID, buildNotification())
        observeModel()
        detectExecutor()
        writeAutoexecSession()
        copyToClipboard()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(false); builtInZoomControls = false; displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                wv.resumeTimers()
            }
        }
        wv.addJavascriptInterface(Bridge(), "AwpNative")
        wv.resumeTimers()
        wv.loadUrl("file:///android_asset/ui/index.html")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        wv.setOnTouchListener { _, event ->
            if (isLocked) return@setOnTouchListener false
            val p = params ?: return@setOnTouchListener false
            val edge = dpToPx(24)
            val inResize = event.x > p.width - edge && event.y > p.height - edge
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x; initialY = p.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    isResizing = inResize
                    if (isResizing) { initialW = p.width; initialH = p.height; resizeTouchX = event.rawX; resizeTouchY = event.rawY }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) {
                        val (sw, sh) = getScreenSize()
                        p.width = (initialW + (event.rawX - resizeTouchX).toInt()).coerceIn(dpToPx(280), sw)
                        p.height = (initialH + (event.rawY - resizeTouchY).toInt()).coerceIn(dpToPx(220), sh)
                        try { wm.updateViewLayout(overlayView, p) } catch (_: Exception) {}
                        true
                    } else {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            p.x = initialX + dx; p.y = initialY + dy
                            try { wm.updateViewLayout(overlayView, p) } catch (_: Exception) {}
                            true
                        } else false
                    }
                }
                MotionEvent.ACTION_UP -> { isResizing = false; false }
                else -> false
            }
        }
    }

    private fun observeModel() {
        model.isConnected.observeForever { c ->
            val s = if (c) "connected" else "disconnected"
            wv.post { wv.evaluateJavascript("window.dispatchEvent(new CustomEvent('awp:status',{detail:'$s'}))", null) }
        }
        model.outputLog.observeForever { logs ->
            if (logs.isNotEmpty()) {
                val raw = logs.last()
                if (raw.startsWith("__meta:")) {
                    val json = escJs(raw.removePrefix("__meta:"))
                    wv.post { wv.evaluateJavascript("window.dispatchEvent(new CustomEvent('awp:meta',{detail:$json}))", null) }
                    return@observeForever
                }
                val ci = raw.indexOf(":")
                val type = if (ci in 1..9) raw.substring(0, ci) else "print"
                val content = if (ci in 1..9) raw.substring(ci + 1) else raw
                val safe = escJs(content)
                wv.post { wv.evaluateJavascript("window.dispatchEvent(new CustomEvent('awp:output',{detail:{type:'$type',msg:'$safe'}}))", null) }
            }
        }
    }

    private fun escJs(s: String) = s
        .replace("\\", "\\\\").replace("'", "\\'")
        .replace("\n", "\\n").replace("\r", "")

    private fun readFolder(path: String): String {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return "[]"
        val arr = JSONArray()
        dir.listFiles()
            ?.filter { it.isFile && it.extension in listOf("lua", "luau", "txt") }
            ?.sortedBy { it.name }
            ?.forEach { f -> arr.put(JSONObject().apply { put("name", f.name); put("content", f.readText()) }) }
        return arr.toString()
    }

    inner class Bridge {
        @JavascriptInterface fun executeScript(s: String) = model.executeScript(s)
        @JavascriptInterface fun clearOutput() = model.clearOutput()
        @JavascriptInterface fun isConnected(): Boolean = model.isConnected.value == true
        @JavascriptInterface fun minimize() { wv.post { overlayView.visibility = View.GONE } }
        @JavascriptInterface fun maximize() { wv.post { overlayView.visibility = View.VISIBLE } }
        @JavascriptInterface fun closeApp() { wv.post { stopSelf() } }
        @JavascriptInterface fun setLocked(on: Boolean) { isLocked = on }
        @JavascriptInterface fun isLocked(): Boolean = isLocked

        @JavascriptInterface fun reattach() {
            detectExecutor(); writeAutoexecSession(); copyToClipboard()
        }

        @JavascriptInterface fun setTopmost(on: Boolean) {
            wv.post {
                val p = params ?: return@post
                p.flags = if (on) p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                          else p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
                try { wm.updateViewLayout(overlayView, p) } catch (_: Exception) {}
            }
        }

        @JavascriptInterface fun setAutoexec(@Suppress("UNUSED_PARAMETER") on: Boolean) {}
        @JavascriptInterface fun clearAppState() {}

        @JavascriptInterface fun getLoadstring(): String = buildLoadstring()
        @JavascriptInterface fun getDetectedExecutor(): String = detectedExecutorName
        @JavascriptInterface fun getScriptsPath(): String = detectedScriptsPath
        @JavascriptInterface fun getAutoexecPath(): String = detectedAutoexecPath

        @JavascriptInterface fun getWorkspaceFiles(): String =
            readFolder(detectedScriptsPath.ifEmpty { "/sdcard/Delta/Scripts" })

        @JavascriptInterface fun getAutoexecFiles(): String =
            readFolder(detectedAutoexecPath.ifEmpty { "/sdcard/Delta/Autoexecute" })

        @JavascriptInterface fun saveWorkspaceFile(name: String, content: String) {
            try { File(getAwpWorkspaceDir(), name).writeText(content) } catch (_: Exception) {}
        }

        @JavascriptInterface fun readWorkspaceFile(name: String): String? =
            try { File(getAwpWorkspaceDir(), name).takeIf { it.exists() }?.readText() } catch (_: Exception) { null }

        @JavascriptInterface fun getLogs(): String = readLogcat()

        @JavascriptInterface fun openFilePicker() {}
        @JavascriptInterface fun openFileAndExecute() {}

        @JavascriptInterface fun openFolder(path: String) {
            try {
                File(path).mkdirs()
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("file://$path"); flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(NOTIF_CHANNEL, "AWP Overlay", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "AWP floating window" }
                )
        val showIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction("SHOW_OVERLAY"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, NOTIF_CHANNEL)
                .setContentTitle("AWP.GG").setContentText("Toque para mostrar")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(showIntent).setOngoing(true).build()
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AWP.GG").setContentText("Toque para mostrar")
                .setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SHOW_OVERLAY") overlayView.visibility = View.VISIBLE
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
        wv.pauseTimers()
        try { wm.removeView(overlayView) } catch (_: Exception) {}
        model.stop()
    }
}
