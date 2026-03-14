local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local ws = nil
local connected = false
local connecting = false

local execName = "Unknown"
pcall(function()
    execName = (identifyexecutor and identifyexecutor()) or
               (getexecutorname and getexecutorname()) or "Unknown"
end)

local userId, displayName, username = 0, "", ""
pcall(function()
    local lp = Players.LocalPlayer
    userId = lp.UserId
    displayName = lp.DisplayName
    username = lp.Name
end)

local metaPayload = HttpService:JSONEncode({
    executor    = execName,
    userId      = userId,
    displayName = displayName,
    username    = username
})

pcall(function()
    local hgui = gethui()
    local function destroyExec(inst)
        for _, c in ipairs(inst:GetChildren()) do
            if c.Name == "Executor" then
                pcall(function()
                    if c.Parent == hgui then c:Destroy() else c.Parent:Destroy() end
                end)
                return
            end
            destroyExec(c)
        end
    end
    destroyExec(hgui)
    for _, v in ipairs(hgui:GetChildren()) do
        local n = v.Name:lower()
        if n:find("delta") or n:find("executor") or n:find("exploit") or n:find("injector") then
            pcall(function() v:Destroy() end)
        end
    end
end)

local rawPrint = print
local rawWarn  = warn
local rawError = error

local function send(msg)
    if ws and connected then
        pcall(function() ws:Send(msg) end)
    end
end

print = function(...)
    local parts = {}
    for i = 1, select('#', ...) do parts[i] = tostring(select(i, ...)) end
    pcall(rawPrint, ...)
    send("__console:print:" .. table.concat(parts, "\t"))
end

warn = function(...)
    local parts = {}
    for i = 1, select('#', ...) do parts[i] = tostring(select(i, ...)) end
    pcall(rawWarn, ...)
    send("__console:warn:" .. table.concat(parts, "\t"))
end

error = function(msg, level)
    send("__console:error:" .. tostring(msg))
    pcall(rawError, msg, level or 1)
end

pcall(function()
    local ls = game:GetService("LogService")
    local types = {
        [Enum.MessageType.MessageOutput]  = "print",
        [Enum.MessageType.MessageInfo]    = "info",
        [Enum.MessageType.MessageWarning] = "warn",
        [Enum.MessageType.MessageError]   = "error",
    }
    ls.MessageOut:Connect(function(message, msgType)
        send("__console:" .. (types[msgType] or "print") .. ":" .. tostring(message))
    end)
end)

local function connect()
    if connecting or connected then return end
    connecting = true
    ws = nil

    local ok, err = pcall(function()
        ws = WebSocket.connect("ws://127.0.0.1:9999")
    end)

    if not ok or not ws then
        connecting = false
        connected = false
        ws = nil
        return
    end

    ws.OnClose:Connect(function()
        connected = false
        connecting = false
        ws = nil
    end)

    ws.OnMessage:Connect(function(msg)
        if msg == "ping" then
            send("pong")
            return
        end
        local fn, loadErr = loadstring(msg)
        if fn then
            local ok2, execErr = pcall(fn)
            if not ok2 then send("__error:" .. tostring(execErr)) end
        else
            send("__error:syntax:" .. tostring(loadErr))
        end
    end)

    task.wait(0.3)

    connected = true
    connecting = false

    send("__ready")

    task.wait(0.1)

    send("__meta:" .. metaPayload)
end

task.spawn(function()
    local retryDelay = 3
    while true do
        if not connected and not connecting then
            connect()
            if connected then
                retryDelay = 3
            else
                task.wait(retryDelay)
                retryDelay = math.min(retryDelay + 2, 15)
            end
        end
        task.wait(1)
    end
end)
