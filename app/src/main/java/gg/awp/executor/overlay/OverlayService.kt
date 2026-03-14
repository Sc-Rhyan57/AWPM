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
import android.graphics.PixelFormat
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
import java.io.File
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
    }

    private lateinit var wm: WindowManager
    private lateinit var overlayView: View
    private lateinit var wv: WebView
    private lateinit var model: OverlayModel

    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun getLocalIp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (iface in ifaces) {
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true)
                        return addr.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    private fun buildLoadstring(): String {
        val ip = getLocalIp()
        return "loadstring(game:HttpGet(\"http://$ip:$HTTP_PORT/session/script\"))()"
    }

    private fun writeAutoexecSession() {
        try {
            val dir = File(Environment.getExternalStorageDirectory(), "Delta/Autoexecute")
            if (!dir.exists()) dir.mkdirs()
            File(dir, AUTOEXEC_FILE).writeText(buildLoadstring())
        } catch (_: Exception) {}
    }

    private fun copyLoadstringToClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AWP", buildLoadstring()))
        } catch (_: Exception) {}
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

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            overlayW, overlayH, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW - overlayW) / 2
            y = (screenH - overlayH) / 6
        }

        overlayView = FrameLayout(this)
        wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        setupWebView()
        (overlayView as FrameLayout).addView(wv)
        setupDrag()
        wm.addView(overlayView, params)
        startForeground(NOTIF_ID, buildNotification())
        observeModel()

        writeAutoexecSession()
        copyLoadstringToClipboard()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = WebViewClient()
        wv.addJavascriptInterface(Bridge(), "AwpNative")
        wv.loadUrl("file:///android_asset/ui/index.html")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        wv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        params!!.x = initialX + dx
                        params!!.y = initialY + dy
                        wm.updateViewLayout(overlayView, params)
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    private fun observeModel() {
        model.isConnected.observeForever { c ->
            val s = if (c) "connected" else "disconnected"
            wv.post {
                wv.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('awp:status',{detail:'$s'}))", null
                )
            }
        }
        model.outputLog.observeForever { logs ->
            if (logs.isNotEmpty()) {
                val raw = logs.last()
                val colonIdx = raw.indexOf(":")
                val type: String
                val content: String
                if (colonIdx in 1..9) {
                    type = raw.substring(0, colonIdx)
                    content = raw.substring(colonIdx + 1)
                } else {
                    type = "print"
                    content = raw
                }
                val safe = escJs(content)
                wv.post {
                    wv.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('awp:output',{detail:{type:'$type',msg:'$safe'}}))",
                        null
                    )
                }
            }
        }
    }

    private fun escJs(s: String) = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "")

    private fun getDeltaDir(): File =
        File(Environment.getExternalStorageDirectory(), "Delta")

    private fun readDeltaFolder(sub: String): String {
        val dir = File(getDeltaDir(), sub)
        if (!dir.exists() || !dir.isDirectory) return "[]"
        val arr = JSONArray()
        dir.listFiles()
            ?.filter { it.isFile && it.extension in listOf("lua", "luau", "txt") }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                arr.put(JSONObject().apply {
                    put("name", f.name)
                    put("content", f.readText())
                })
            }
        return arr.toString()
    }

    inner class Bridge {
        @JavascriptInterface fun executeScript(s: String) = model.executeScript(s)
        @JavascriptInterface fun clearOutput() = model.clearOutput()
        @JavascriptInterface fun isConnected(): Boolean = model.isConnected.value == true

        @JavascriptInterface fun minimize() {
            wv.post { overlayView.visibility = View.GONE }
        }

        @JavascriptInterface fun maximize() {
            wv.post { overlayView.visibility = View.VISIBLE }
        }

        @JavascriptInterface fun closeApp() {
            wv.post { stopSelf() }
        }

        @JavascriptInterface fun reattach() {
            writeAutoexecSession()
            copyLoadstringToClipboard()
            model.executeScript("")
        }

        @JavascriptInterface fun setTopmost(on: Boolean) {
            wv.post {
                val p = params ?: return@post
                p.flags = if (on)
                    p.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                else
                    p.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
                try { wm.updateViewLayout(overlayView, p) } catch (_: Exception) {}
            }
        }

        @JavascriptInterface fun setAutoexec(@Suppress("UNUSED_PARAMETER") on: Boolean) {}
        @JavascriptInterface fun clearAppState() {}

        @JavascriptInterface fun getLoadstring(): String = buildLoadstring()

        @JavascriptInterface fun getWorkspaceFiles(): String = readDeltaFolder("Scripts")
        @JavascriptInterface fun getAutoexecFiles(): String = readDeltaFolder("Autoexecute")

        @JavascriptInterface fun saveWorkspaceFile(name: String, content: String) {
            try {
                val ws = File(getDeltaDir(), "workspace/AWPM").also {
                    if (!it.exists()) it.mkdirs()
                }
                File(ws, name).writeText(content)
            } catch (_: Exception) {}
        }

        @JavascriptInterface fun openFilePicker() {}
        @JavascriptInterface fun openFileAndExecute() {}

        @JavascriptInterface fun openFolder(path: String) {
            try {
                File(path).mkdirs()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("file://$path")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        NOTIF_CHANNEL, "AWP Overlay", NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "AWP floating window" }
                )
        }
        val showIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction("SHOW_OVERLAY"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL)
                .setContentTitle("AWP.GG")
                .setContentText("Toque para mostrar a janela")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(showIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AWP.GG")
                .setContentText("Toque para mostrar a janela")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SHOW_OVERLAY") {
            overlayView.visibility = View.VISIBLE
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
        try { wm.removeView(overlayView) } catch (_: Exception) {}
        model.stop()
    }
}
