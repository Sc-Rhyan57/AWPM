package gg.awp.executor.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
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

class OverlayService : Service() {

    companion object {
        var running = false
        const val NOTIF_CHANNEL = "awp_overlay"
        const val NOTIF_ID = 42
    }

    private lateinit var wm: WindowManager
    private lateinit var overlayView: View
    private lateinit var wv: WebView
    private lateinit var model: OverlayModel

    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    private var overlayW = 0
    private var overlayH = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "InflateParams")
    override fun onCreate() {
        super.onCreate()
        running = true

        model = OverlayModel(application)
        model.start()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val display = wm.defaultDisplay
        val screenW = display.width
        val screenH = display.height

        overlayW = (screenW * 0.92f).toInt()
        overlayH = (screenH * 0.78f).toInt()

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
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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
        wv.loadUrl("file:///android_asset/ui/index.html")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        wv.setOnTouchListener { v, event ->
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
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
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
            wv.post { wv.evaluateJavascript("window.dispatchEvent(new CustomEvent('awp:status',{detail:'$s'}))", null) }
        }
        model.outputLog.observeForever { logs ->
            if (logs.isNotEmpty()) {
                val raw = logs.last()
                val colonIdx = raw.indexOf(":")
                val type: String; val content: String
                if (colonIdx in 1..9) {
                    type = raw.substring(0, colonIdx)
                    content = raw.substring(colonIdx + 1)
                } else { type = "print"; content = raw }
                val safe = escJs(content)
                wv.post {
                    wv.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('awp:output',{detail:{type:'$type',msg:'$safe'}}))", null
                    )
                }
            }
        }
    }

    private fun escJs(s: String) = s
        .replace("\\", "\\\\").replace("'", "\\'")
        .replace("\n", "\\n").replace("\r", "")

    private fun readUri(uri: Uri) = try {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) { null }

    private fun getFileName(uri: android.net.Uri): String {
        var name = "script.lua"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && col >= 0) name = c.getString(col)
        }
        return name
    }

    private fun getDeltaDir(): File = File(
        android.os.Environment.getExternalStorageDirectory(), "Delta"
    )

    private fun readDeltaFolder(sub: String): String {
        val dir = File(getDeltaDir(), sub)
        if (!dir.exists() || !dir.isDirectory) return "[]"
        val arr = JSONArray()
        dir.listFiles()
            ?.filter { it.isFile && it.extension in listOf("lua", "luau", "txt") }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                arr.put(JSONObject().apply { put("name", f.name); put("content", f.readText()) })
            }
        return arr.toString()
    }

    inner class Bridge {
        @JavascriptInterface fun executeScript(s: String) = model.executeScript(s)
        @JavascriptInterface fun clearOutput() = model.clearOutput()
        @JavascriptInterface fun isConnected(): Boolean = model.isConnected.value == true

        @JavascriptInterface fun minimize() { wv.post { overlayView.visibility = View.GONE } }
        @JavascriptInterface fun maximize() { wv.post { overlayView.visibility = View.VISIBLE } }
        @JavascriptInterface fun closeApp() {
            wv.post {
                stopSelf()
            }
        }

        @JavascriptInterface fun reattach() {}
        @JavascriptInterface fun setTopmost(on: Boolean) {}
        @JavascriptInterface fun setAutoexec(on: Boolean) {}
        @JavascriptInterface fun clearAppState() {}

        @JavascriptInterface fun getWorkspaceFiles(): String = readDeltaFolder("Scripts")
        @JavascriptInterface fun getAutoexecFiles(): String = readDeltaFolder("Autoexecute")

        @JavascriptInterface fun saveWorkspaceFile(name: String, content: String) {
            try {
                val ws = File(getDeltaDir(), "workspace/AWPM").also { if (!it.exists()) it.mkdirs() }
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
                .createNotificationChannel(NotificationChannel(
                    NOTIF_CHANNEL, "AWP Overlay",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "AWP floating window" })
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ShimActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL)
                .setContentTitle("AWP.GG")
                .setContentText("Floating executor active — tap to show")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("AWP.GG")
                .setContentText("Floating executor active")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
        try { wm.removeView(overlayView) } catch (_: Exception) {}
        model.stop()
    }
}
