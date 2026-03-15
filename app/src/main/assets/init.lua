if _G.__AWP_POLLING_ACTIVE then
    _G.__AWP_POLLING_ACTIVE = false
    task.wait(1)
end
_G.__AWP_POLLING_ACTIVE = true

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local serverUrl = "http://REPLACE_WITH_LOCAL_IP:8080"
local sessionId = HttpService:GenerateGUID(false)
local connected = false

local execName = "Unknown"
pcall(function()
    execName = (identifyexecutor and identifyexecutor()) or
               (getexecutorname and getexecutorname()) or "Unknown"
end)

local userId = 0
pcall(function()
    userId = Players.LocalPlayer.UserId
end)

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

local pingOk = false
for i = 1, 5 do
    local ok, _ = httpGet("/ping")
    if ok then
        pingOk = true
        break
    end
    task.wait(1)
end

if not pingOk then
    warn("[AWP] Cannot reach server at " .. serverUrl)
    _G.__AWP_POLLING_ACTIVE = false
    return
end

local function sendReady()
    httpPost("/session/ready", {
        sessionId = sessionId,
        executor = execName,
        userId = userId
    })
end

local function sendAck(count)
    httpPost("/session/ack", {
        sessionId = sessionId,
        count = count
    })
end

local function poll()
    local currentDelay = 0.5
    while _G.__AWP_POLLING_ACTIVE do
        local ok, data = httpGet("/session/poll?id=" .. sessionId)

        if ok and data then
            currentDelay = 0.5
            if not connected then
                connected = true
                sendReady()
            end
            if data.scripts and #data.scripts > 0 then
                local count = #data.scripts
                for i, scriptData in ipairs(data.scripts) do
                    local fn, err = loadstring(scriptData)
                    if fn then
                        task.spawn(function()
                            pcall(fn)
                        end)
                    end
                end
                sendAck(count)
            end
        else
            if connected then
                connected = false
            end
            currentDelay = math.min(currentDelay * 1.5, 5)
        end

        task.wait(currentDelay)
    end
end

task.spawn(poll)
