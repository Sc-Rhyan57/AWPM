if _G.__AWP_POLLING_ACTIVE then
    print("[AWP] Already active")
    return
end
_G.__AWP_POLLING_ACTIVE = true

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local serverUrl = "http://REPLACE_WITH_LOCAL_IP:8080"
local sessionId = HttpService:GenerateGUID(false)
local connected = false

print("[AWP] Starting HTTP Polling client...")
print("[AWP] Server: " .. serverUrl)
print("[AWP] Session: " .. sessionId)

local execName = "Unknown"
pcall(function()
    execName = (identifyexecutor and identifyexecutor()) or
               (getexecutorname and getexecutorname()) or "Unknown"
end)

local userId = 0
pcall(function()
    userId = Players.LocalPlayer.UserId
end)

local function httpPost(endpoint, data)
    local success, result = pcall(function()
        return HttpService:PostAsync(
            serverUrl .. endpoint,
            HttpService:JSONEncode(data),
            Enum.HttpContentType.ApplicationJson
        )
    end)
    if success then
        return pcall(function() return HttpService:JSONDecode(result) end)
    end
    return false, nil
end

local function httpGet(endpoint)
    local success, result = pcall(function()
        return HttpService:GetAsync(serverUrl .. endpoint)
    end)
    if success then
        return pcall(function() return HttpService:JSONDecode(result) end)
    end
    return false, nil
end

local function sendReady()
    httpPost("/session/ready", {
        sessionId = sessionId,
        executor = execName,
        userId = userId
    })
end

local function poll()
    while _G.__AWP_POLLING_ACTIVE do
        local success, data = httpGet("/session/poll?id=" .. sessionId)
        
        if success and data and data.scripts then
            if not connected then
                connected = true
                print("[AWP] Connected!")
                sendReady()
            end
            
            for _, scriptData in ipairs(data.scripts) do
                print("[AWP] Executing: " .. scriptData:sub(1, 50))
                local fn, err = loadstring(scriptData)
                if fn then
                    task.spawn(function()
                        local ok, execErr = pcall(fn)
                        if not ok then
                            print("[AWP] Error: " .. tostring(execErr))
                        end
                    end)
                else
                    print("[AWP] Syntax error: " .. tostring(err))
                end
            end
        end
        
        task.wait(0.5)
    end
end

task.spawn(poll)
print("[AWP] Polling started")
