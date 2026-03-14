package gg.awp.executor.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class SessionHttpServer(
    private val context: Context
) : NanoHTTPD(8080) {

    private val tag = "SessionHttpServer"

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

            val response = newFixedLengthResponse(
                Response.Status.OK,
                "text/plain; charset=utf-8",
                script
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
