package com.rhyan57.awp.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class AwpHttpServer(private val port: Int, private val wsPort: Int) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val scope   = CoroutineScope(Dispatchers.IO)

    fun start() {
        running.set(true)
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        scope.launch { handleClient(client) }
                    } catch (_: Exception) { if (!running.get()) break }
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1) ?: "/"

            val luaScript = buildBootstrapLua()

            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: text/plain\r\n")
            writer.print("Content-Length: ${luaScript.toByteArray().size}\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.print(luaScript)
            writer.flush()
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun buildBootstrapLua(): String {
        val localIp = getLocalIp()
        val wsUrl   = "ws://$localIp:$wsPort/"

        return """
local Services = setmetatable({}, {
    __index = function(self, key)
        return game:GetService(key)
    end
})

local function destroyOriginalGui()
    pcall(function()
        local cg = Services.CoreGui
        for _, child in ipairs(cg:GetChildren()) do
            pcall(function()
                local exec = child:FindFirstChild("Executor", true)
                if exec then
                    exec.Parent:Destroy()
                end
            end)
        end
    end)
end

local SMethod = WebSocket and WebSocket.connect
if not SMethod then
    warn("[AWP] WebSocket not available in this executor.")
    return
end

destroyOriginalGui()

local function main()
    local ok, ws = pcall(SMethod, "$wsUrl")
    if not ok then return end

    local closed = false

    ws:Send(Services.HttpService:JSONEncode({
        Method = "Authorization",
        Name   = Services.Players.LocalPlayer.Name
    }))

    ws.OnMessage:Connect(function(raw)
        local parsed = Services.HttpService:JSONDecode(raw)

        if parsed.Method == "Execute" then
            local fn, err = loadstring(parsed.Data)
            if err then
                ws:Send(Services.HttpService:JSONEncode({
                    Method  = "Error",
                    Message = tostring(err)
                }))
                return
            end
            local s, e = pcall(fn)
            if not s then
                ws:Send(Services.HttpService:JSONEncode({
                    Method  = "Error",
                    Message = tostring(e)
                }))
            end
        end
    end)

    ws.OnClose:Connect(function()
        closed = true
    end)

    repeat task.wait() until closed
end

while task.wait(1) do
    pcall(main)
end
""".trimIndent()
    }

    private fun getLocalIp(): String {
        return try {
            val socket = java.net.DatagramSocket()
            socket.connect(java.net.InetAddress.getByName("8.8.8.8"), 80)
            val ip = socket.localAddress.hostAddress ?: "127.0.0.1"
            socket.close()
            ip
        } catch (_: Exception) { "127.0.0.1" }
    }
}
