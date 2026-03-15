if _G.__AWP_WS_ACTIVE then
    return
end
_G.__AWP_WS_ACTIVE = true

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local ws = nil
local connected = false
local connecting = false
local reconnectAttempts = 0
local maxReconnectDelay = 30
local serverIp = "REPLACE_WITH_LOCAL_IP"

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
        local ok = pcall(function() ws:Send(msg) end)
        if not ok then
            connected = false
            ws = nil
        end
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

local function cleanup()
    if ws then
        pcall(function() ws:Close() end)
        ws = nil
    end
    connected = false
    connecting = false
end

local function connect()
    if connecting or connected then return end
    connecting = true
    
    cleanup()
    
    task.wait(1.0)

    local wsUrl = "ws://" .. serverIp .. ":9999"
    local ok, err = pcall(function()
        ws = WebSocket.connect(wsUrl)
    end)

    if not ok or not ws then
        rawPrint("[AWP] Failed to connect: " .. tostring(err))
        connecting = false
        connected = false
        ws = nil
        reconnectAttempts = reconnectAttempts + 1
        return
    end

    local closeHandled = false
    local openConfirmed = false

    ws.OnClose:Connect(function()
        if closeHandled then return end
        closeHandled = true
        rawPrint("[AWP] Connection closed")
        connected = false
        connecting = false
        ws = nil
        reconnectAttempts = reconnectAttempts + 1
    end)

    ws.OnMessage:Connect(function(msg)
        if not openConfirmed then
            openConfirmed = true
            rawPrint("[AWP] First message received, connection stable")
        end
        
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

    task.wait(2.0)

    if ws and not closeHandled then
        connected = true
        connecting = false
        reconnectAttempts = 0
        rawPrint("[AWP] Connected to " .. wsUrl)

        send("__ready")
        task.wait(0.3)
        send("__meta:" .. metaPayload)
    else
        rawPrint("[AWP] Connection failed during handshake")
        cleanup()
    end
end

task.spawn(function()
    while _G.__AWP_WS_ACTIVE do
        if not connected and not connecting then
            local delay = math.min(3 + (reconnectAttempts * 2), maxReconnectDelay)
            rawPrint("[AWP] Attempting connection (attempt " .. (reconnectAttempts + 1) .. ")")
            connect()
            if not connected then
                task.wait(delay)
            end
        end
        task.wait(3)
    end
end)
