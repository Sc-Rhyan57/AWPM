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
local retryDelay = 0.5
local maxRetryDelay = 5

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

local function safeHttpGet(url)
    local ok, result = pcall(function()
        return HttpService:GetAsync(url, true)
    end)
    if ok then
        local decOk, decoded = pcall(function()
            return HttpService:JSONDecode(result)
        end)
        if decOk then
            return true, decoded
        end
        return false, nil
    end
    return false, nil
end

local function safeHttpPost(url, body)
    local ok, result = pcall(function()
        return HttpService:PostAsync(
            url,
            HttpService:JSONEncode(body),
            Enum.HttpContentType.ApplicationJson,
            true
        )
    end)
    if ok then
        return true, result
    end
    return false, nil
end

local function sendReady()
    warn("[AWP] Sending ready signal...")
    local ok, _ = safeHttpPost(serverUrl .. "/session/ready", {
        sessionId = sessionId,
        executor = execName,
        userId = userId
    })
    if ok then
        warn("[AWP] Ready signal sent OK")
    else
        warn("[AWP] Ready signal failed")
    end
end

local function ping()
    local ok, _ = safeHttpGet(serverUrl .. "/ping")
    return ok
end

warn("[AWP] Testing server connection...")
local pingOk = false
for i = 1, 5 do
    if ping() then
        pingOk = true
        warn("[AWP] Server reachable!")
        break
    end
    warn("[AWP] Ping attempt " .. i .. " failed, retrying...")
    task.wait(1)
end

if not pingOk then
    warn("[AWP] ERROR: Cannot reach server at " .. serverUrl)
    warn("[AWP] Make sure AWP app is running and on same network")
    _G.__AWP_POLLING_ACTIVE = false
    return
end

local function poll()
    local currentDelay = retryDelay
    while _G.__AWP_POLLING_ACTIVE do
        local ok, data = safeHttpGet(serverUrl .. "/session/poll?id=" .. sessionId)

        if ok and data then
            currentDelay = retryDelay

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
            currentDelay = math.min(currentDelay * 1.5, maxRetryDelay)
        end

        task.wait(currentDelay)
    end
    warn("[AWP] Polling stopped")
end

task.spawn(poll)
warn("[AWP] Polling loop started - session: " .. sessionId)
