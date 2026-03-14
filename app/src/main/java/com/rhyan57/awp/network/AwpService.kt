package com.rhyan57.awp.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class AwpService : Service() {
    inner class AwpBinder : Binder() {
        fun getService() = this@AwpService
    }

    private val binder = AwpBinder()
    lateinit var wsServer: AwpWebSocketServer
    lateinit var httpServer: AwpHttpServer

    companion object {
        const val WS_PORT   = 8765
        const val HTTP_PORT = 8080
        const val NOTIF_CHANNEL = "awp_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        wsServer   = AwpWebSocketServer(WS_PORT)
        httpServer = AwpHttpServer(HTTP_PORT, WS_PORT)

        wsServer.start()
        httpServer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { wsServer.stop(1000) } catch (_: Exception) {}
        httpServer.stop()
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL, "AWP Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "AWP WebSocket & HTTP servers" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("AWP")
            .setContentText("Servers running — WS :$WS_PORT  HTTP :$HTTP_PORT")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
}
