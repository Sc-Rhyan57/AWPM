package gg.awp.executor.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class PollingHttpServer(
    private val context: Context,
    private val onScriptOutput: (String) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit
) : NanoHTTPD(8080) {

    private val tag = "PollingHttpServer"
    
    data class Session(
        val id: String,
        val scripts: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        var lastSeen: Long = System.currentTimeMillis(),
        var executor: String = "Unknown",
        var userId: Long = 0
    )
    
    private val sessions = ConcurrentHashMap<String, Session>()
    private var activeSessionId: String? = null

    init {
        Thread {
            while (true) {
                Thread.sleep(10000)
                val now = System.currentTimeMillis()
                sessions.entries.removeIf { (id, session) ->
                    val timeout = now - session.lastSeen > 15000
                    if (timeout) {
                        Log.d(tag, "Session $id timed out")
                        if (id == activeSessionId) {
                            activeSessionId = null
                            onClientDisconnected()
                        }
                    }
                    timeout
                }
            }
        }.apply { isDaemon = true }.start()
    }

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
            
            uri.startsWith("/session/poll") && method == Method.GET -> {
                val params = session.parms
                val sessionId = params["id"] ?: return newNotFound()
                handlePoll(sessionId)
            }
            
            uri == "/session/ready" && method == Method.POST -> {
                val body = readBody(session)
                handleReady(body)
            }
            
            uri == "/session/execute" && method == Method.POST -> {
                val body = readBody(session)
                handleExecute(body)
            }
            
            uri == "/ping" && method == Method.GET -> newOk("pong")
            
            else -> newNotFound()
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

            newResponse(Response.Status.OK, "text/plain; charset=utf-8", modifiedScript)
                .apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Cache-Control", "no-cache")
                }
        } catch (e: Exception) {
            Log.e(tag, "Failed to read init.lua", e)
            newError(Response.Status.INTERNAL_ERROR, "Failed to load init script")
        }
    }

    private fun handlePoll(sessionId: String): Response {
        val session = sessions.getOrPut(sessionId) { Session(sessionId) }
        session.lastSeen = System.currentTimeMillis()

        if (activeSessionId == null) {
            activeSessionId = sessionId
            onClientConnected()
            Log.d(tag, "New session connected: $sessionId")
        }

        val scriptsToSend = session.scripts.toList()
        session.scripts.clear()

        val json = JSONObject().apply {
            put("status", "ok")
            put("scripts", JSONArray(scriptsToSend))
        }

        return newOk(json.toString())
    }

    private fun handleReady(body: String): Response {
        return try {
            val json = JSONObject(body)
            val sessionId = json.getString("sessionId")
            val executor = json.optString("executor", "Unknown")
            val userId = json.optLong("userId", 0)

            sessions[sessionId]?.apply {
                this.executor = executor
                this.userId = userId
            }

            Log.d(tag, "Session ready: $sessionId (Executor: $executor, User: $userId)")
            onScriptOutput("sys:Executor conectado ($executor)")
            
            val metaJson = JSONObject().apply {
                put("executor", executor)
                put("userId", userId)
            }
            onScriptOutput("__meta:$metaJson")

            newOk("""{"status":"ok"}""")
        } catch (e: Exception) {
            Log.e(tag, "Error in handleReady", e)
            newError(Response.Status.BAD_REQUEST, e.message ?: "Invalid request")
        }
    }

    private fun handleExecute(body: String): Response {
        return try {
            val json = JSONObject(body)
            val script = json.getString("script")
            
            activeSessionId?.let { sessionId ->
                sessions[sessionId]?.scripts?.add(script)
                Log.d(tag, "Queued script for session $sessionId (${script.length} chars)")
            }

            newOk("""{"status":"ok"}""")
        } catch (e: Exception) {
            Log.e(tag, "Error in handleExecute", e)
            newError(Response.Status.BAD_REQUEST, e.message ?: "Invalid request")
        }
    }

    private fun readBody(session: IHTTPSession): String {
        val map = HashMap<String, String>()
        session.parseBody(map)
        return map["postData"] ?: ""
    }

    private fun newOk(content: String): Response {
        return newResponse(Response.Status.OK, "application/json", content)
            .apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "no-cache")
            }
    }

    private fun newNotFound(): Response {
        return newError(Response.Status.NOT_FOUND, "Not Found")
    }

    private fun newError(status: Response.Status, message: String): Response {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, message)
    }

    private fun newResponse(status: Response.Status, mimeType: String, content: String): Response {
        return newFixedLengthResponse(status, mimeType, content)
    }

    fun executeScript(script: String) {
        activeSessionId?.let { sessionId ->
            sessions[sessionId]?.scripts?.add(script)
            Log.d(tag, "Added script to queue: ${script.length} chars")
        }
    }

    fun isClientConnected(): Boolean = activeSessionId != null
}
