if _G.__AWP_POLLING_ACTIVE then
    warn("[AWP] Already active - stopping previous instance")
    _G.__AWP_POLLING_ACTIVE = false
    task.wait(1)
end
_G.__AWP_POLLING_ACTIVE = true

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local serverUrl = "http://REPLACE_WITH_LOCAL_IP:8080"
local sessionId = HttpService:GenerateGUID(false)
local connected = false

warn("[AWP] =============================")
warn("[AWP] Starting AWP HTTP Polling")
warn("[AWP] Server: " .. serverUrl)
warn("[AWP] Session: " .. sessionId)
warn("[AWP] =============================")

local execName = "Unknown"
pcall(function()
    execName = (identifyexecutor and identifyexecutor()) or
               (getexecutorname and getexecutorname()) or "Unknown"
end)
warn("[AWP] Executor: " .. execName)

local userId = 0
pcall(function()
    userId = Players.LocalPlayer.UserId
end)
warn("[AWP] UserId: " .. tostring(userId))

-- Usa request() do executor igual ReChat faz, nao HttpService
-- request() nao tem as restricoes do HttpService para IPs locais na rede
local function httpRequest(url, method, body)
    local ok, result = pcall(function()
        return request({
            Url = url,
            Method = method or "GET",
            Headers = { ["Content-Type"] = "application/json" },
            Body = body and HttpService:JSONEncode(body) or nil
        })
    end)
    if ok and result and (result.StatusCode == 200 or result.Success) then
        local decOk, decoded = pcall(function()
            return HttpService:JSONDecode(result.Body)
        end)
        if decOk then return true, decoded end
        return true, result.Body
    end
    return false, nil
end

local function httpGet(endpoint)
    return httpRequest(serverUrl .. endpoint, "GET", nil)
end

local function httpPost(endpoint, body)
    return httpRequest(serverUrl .. endpoint, "POST", body)
end

warn("[AWP] Testing server connection...")
local pingOk = false
for i = 1, 5 do
    local ok, _ = httpGet("/ping")
    if ok then
        pingOk = true
        warn("[AWP] Server reachable!")
        break
    end
    warn("[AWP] Ping attempt " .. i .. " failed, retrying...")
    task.wait(1)
end

if not pingOk then
    warn("[AWP] ERROR: Cannot reach server at " .. serverUrl)
    warn("[AWP] Make sure AWP app is running and on same Wi-Fi network")
    _G.__AWP_POLLING_ACTIVE = false
    return
end

local function sendReady()
    warn("[AWP] Sending ready signal...")
    local ok, _ = httpPost("/session/ready", {
        sessionId = sessionId,
        executor = execName,
        userId = userId
    })
    warn(ok and "[AWP] Ready signal sent OK" or "[AWP] Ready signal failed")
end

local function poll()
    local currentDelay = 0.5
    while _G.__AWP_POLLING_ACTIVE do
        local ok, data = httpGet("/session/poll?id=" .. sessionId)

        if ok and data then
            currentDelay = 0.5
            if not connected then
                connected = true
                warn("[AWP] Connected to AWP server!")
                sendReady()
            end
            if data.scripts and #data.scripts > 0 then
                warn("[AWP] Received " .. #data.scripts .. " script(s)")
                for i, scriptData in ipairs(data.scripts) do
                    warn("[AWP] Executing script " .. i .. ": " .. tostring(scriptData):sub(1, 60))
                    local fn, err = loadstring(scriptData)
                    if fn then
                        task.spawn(function()
                            local execOk, execErr = pcall(fn)
                            if not execOk then
                                warn("[AWP] Script error: " .. tostring(execErr))
                            end
                        end)
                    else
                        warn("[AWP] Syntax error: " .. tostring(err))
                    end
                end
            end
        else
            if connected then
                warn("[AWP] Lost connection, retrying...")
                connected = false
            end
            currentDelay = math.min(currentDelay * 1.5, 5)
        end

        task.wait(currentDelay)
    end
    warn("[AWP] Polling stopped")
end

task.spawn(poll)
warn("[AWP] Polling loop started - session: " .. sessionId)
