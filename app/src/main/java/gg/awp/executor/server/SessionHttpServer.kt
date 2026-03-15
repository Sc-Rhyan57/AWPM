package gg.awp.executor.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.NetworkInterface

class SessionHttpServer(
    private val context: Context
) : NanoHTTPD(8080) {

    private val tag = "SessionHttpServer"

    private fun getLocalIp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (iface in ifaces) {
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(tag, "$method $uri")

        return when {
            uri == "/session/script" && method == Method.GET -> serveInitScript()
            uri == "/ping" && method == Method.GET -> newFixedLengthResponse(
                Response.Status.OK,
                MIME_PLAINTEXT,
                "pong"
            )
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not Found"
            )
        }
    }

    private fun serveInitScript(): Response {
        return try {
            val script = context.assets.open("init.lua")
                .bufferedReader()
                .use { it.readText() }

            val localIp = getLocalIp()
            val modifiedScript = script.replace("REPLACE_WITH_LOCAL_IP", localIp)

            Log.d(tag, "Serving init.lua with IP: $localIp")

            val response = newFixedLengthResponse(
                Response.Status.OK,
                "text/plain; charset=utf-8",
                modifiedScript
            )
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            response
        } catch (e: IOException) {
            Log.e(tag, "Failed to read init.lua", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Failed to load init script"
            )
        }
    }
}
