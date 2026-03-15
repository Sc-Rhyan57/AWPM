package gg.awp.executor.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class PollingHttpServer(
    private val context: Context,
    private val onScriptOutput: (String) -> Unit,
    private val onClientConnected: () -> Unit,
    private val onClientDisconnected: () -> Unit
) : NanoHTTPD(8080) {

    private val tag = "PollingHttpServer"
    private val ACK_TIMEOUT_MS = 8_000L
    private val SESSION_TIMEOUT_MS = 20_000L

    data class Session(
        val id: String,
        val scripts: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(),
        @Volatile var lastSeen: Long = System.currentTimeMillis(),
        @Volatile var executor: String = "Unknown",
        @Volatile var userId: Long = 0,
        @Volatile var readyReceived: Boolean = false,
        @Volatile var pendingAck: Boolean = false,
        @Volatile var lastScriptDelivery: Long = 0L
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    @Volatile private var activeSessionId: String? = null
    private val hasActiveClient = AtomicBoolean(false)

    init {
        Thread {
            while (true) {
                Thread.sleep(3000)
                val now = System.currentTimeMillis()
                val toRemove = mutableListOf<String>()

                sessions.forEach { (id, session) ->
                    val inactive = now - session.lastSeen > SESSION_TIMEOUT_MS
                    val ackTimedOut = session.pendingAck &&
                            session.lastScriptDelivery > 0 &&
                            (now - session.lastScriptDelivery) > ACK_TIMEOUT_MS

                    if (inactive) {
                        toRemove.add(id)
                    } else if (ackTimedOut) {
                        toRemove.add(id)
                        onScriptOutput("err:Executor não respondeu (ACK timeout)")
                    }
                }

                toRemove.forEach { id ->
                    sessions.remove(id)
                    if (id == activeSessionId) {
                        activeSessionId = null
                        if (hasActiveClient.compareAndSet(true, false)) {
                            onClientDisconnected()
                        }
                    }
                }
            }
        }.apply { isDaemon = true; name = "AWP-SessionReaper" }.start()
    }

    private fun getLocalIp(): String {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses)
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress && addr.hostAddress?.contains('.') == true)
                        return addr.hostAddress ?: continue
            }
            for (iface in ifaces)
                for (addr in iface.inetAddresses)
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true)
                        return addr.hostAddress ?: continue
        } catch (e: Exception) {
            Log.e(tag, "getLocalIp error", e)
        }
        return "127.0.0.1"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (method == Method.OPTIONS)
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").also { addCors(it) }

        return when {
            uri == "/session/script"        && method == Method.GET  -> serveInitScript()
            uri.startsWith("/session/poll") && method == Method.GET  -> {
                val sid = session.parameters["id"]?.firstOrNull()
                    ?: return corsError(Response.Status.BAD_REQUEST, "Missing id parameter")
                handlePoll(sid)
            }
            uri == "/session/ready"         && method == Method.POST -> handleReady(readBody(session))
            uri == "/session/ack"           && method == Method.POST -> handleAck(readBody(session))
            uri == "/session/execute"       && method == Method.POST -> handleExecute(readBody(session))
            uri == "/ping"                  && method == Method.GET  -> corsOk("\"pong\"")
            else -> corsError(Response.Status.NOT_FOUND, "Not Found")
        }
    }

    private fun addCors(r: Response) {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        r.addHeader("Access-Control-Allow-Headers", "Content-Type")
        r.addHeader("Cache-Control", "no-cache, no-store")
    }

    private fun corsOk(content: String) =
        newFixedLengthResponse(Response.Status.OK, "application/json", content).also { addCors(it) }

    private fun corsError(status: Response.Status, msg: String) =
        newFixedLengthResponse(status, MIME_PLAINTEXT, msg).also { addCors(it) }

    private fun serveInitScript(): Response {
        return try {
            val script = context.assets.open("init.lua").bufferedReader().use { it.readText() }
            val modified = script.replace("REPLACE_WITH_LOCAL_IP", getLocalIp())
            newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", modified).also { addCors(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to serve init script", e)
            corsError(Response.Status.INTERNAL_ERROR, "Failed to load init script: ${e.message}")
        }
    }

    private fun handlePoll(sessionId: String): Response {
        val session = sessions.getOrPut(sessionId) { Session(sessionId) }
        session.lastSeen = System.currentTimeMillis()

        if (activeSessionId == null || activeSessionId == sessionId) {
            if (activeSessionId == null) {
                activeSessionId = sessionId
                if (hasActiveClient.compareAndSet(false, true)) onClientConnected()
            }
        }

        val scriptsToSend = session.scripts.toList()
        if (scriptsToSend.isNotEmpty()) {
            session.scripts.clear()
            session.pendingAck = true
            session.lastScriptDelivery = System.currentTimeMillis()
        }

        val json = JSONObject().apply {
            put("status", "ok")
            put("sessionId", sessionId)
            put("scripts", JSONArray(scriptsToSend))
            put("timestamp", System.currentTimeMillis())
        }
        return corsOk(json.toString())
    }

    private fun handleAck(body: String): Response {
        return try {
            if (body.isBlank()) return corsError(Response.Status.BAD_REQUEST, "Empty body")
            val json = JSONObject(body)
            val sessionId = json.getString("sessionId")
            val session = sessions[sessionId]
            if (session != null) {
                session.pendingAck = false
                session.lastSeen = System.currentTimeMillis()
            }
            corsOk("""{"status":"ok"}""")
        } catch (e: Exception) {
            Log.e(tag, "handleAck error: $body", e)
            corsError(Response.Status.BAD_REQUEST, "Invalid JSON: ${e.message}")
        }
    }

    private fun handleReady(body: String): Response {
        return try {
            if (body.isBlank()) return corsError(Response.Status.BAD_REQUEST, "Empty body")
            val json = JSONObject(body)
            val sessionId = json.getString("sessionId")
            val executor = json.optString("executor", "Unknown")
            val userId = json.optLong("userId", 0)

            val session = sessions.getOrPut(sessionId) { Session(sessionId) }
            session.executor = executor
            session.userId = userId
            session.readyReceived = true

            if (activeSessionId == null) {
                activeSessionId = sessionId
                if (hasActiveClient.compareAndSet(false, true)) onClientConnected()
            }

            onScriptOutput("sys:Executor conectado ($executor)")
            val metaJson = JSONObject().apply { put("executor", executor); put("userId", userId) }
            onScriptOutput("__meta:$metaJson")

            corsOk("""{"status":"ok","sessionId":"$sessionId"}""")
        } catch (e: Exception) {
            Log.e(tag, "handleReady error: body=$body", e)
            corsError(Response.Status.BAD_REQUEST, "Invalid JSON: ${e.message}")
        }
    }

    private fun handleExecute(body: String): Response {
        return try {
            if (body.isBlank()) return corsError(Response.Status.BAD_REQUEST, "Empty body")
            val json = JSONObject(body)
            val script = json.getString("script")
            val sid = activeSessionId
            return if (sid != null && sessions.containsKey(sid)) {
                sessions[sid]!!.scripts.add(script)
                corsOk("""{"status":"ok"}""")
            } else {
                corsError(Response.Status.SERVICE_UNAVAILABLE, "No active session")
            }
        } catch (e: Exception) {
            Log.e(tag, "handleExecute error", e)
            corsError(Response.Status.BAD_REQUEST, "Invalid request: ${e.message}")
        }
    }

    private fun readBody(session: IHTTPSession): String {
        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            map["postData"] ?: ""
        } catch (e: Exception) {
            Log.e(tag, "readBody error", e)
            ""
        }
    }

    fun executeScript(script: String) {
        val sid = activeSessionId
        if (sid != null) {
            sessions[sid]?.scripts?.add(script)
        } else {
            onScriptOutput("err:Nenhum executor conectado")
        }
    }

    fun isClientConnected(): Boolean = hasActiveClient.get()
}
