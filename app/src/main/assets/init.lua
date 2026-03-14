local hgui = gethui()

local function destroyDeltaGui(inst)
    for _, child in ipairs(inst:GetChildren()) do
        if child.Name == "Executor" then
            if child.Parent == hgui then child:Destroy()
            else child.Parent:Destroy() end
            return true
        end
        if destroyDeltaGui(child) then return true end
    end
    return false
end

destroyDeltaGui(hgui)

for _, v in ipairs(hgui:GetChildren()) do
    local n = v.Name:lower()
    if n:find("delta") or n:find("executor") or n:find("exploit") or n:find("injector") then
        v:Destroy()
    end
end

local ws = WebSocket.connect("ws://127.0.0.1:9999")

local function send(msg)
    pcall(function() ws:Send(msg) end)
end

local execName = "Unknown"
pcall(function()
    execName = identifyexecutor and identifyexecutor() or
               getexecutorname and getexecutorname() or
               "Unknown"
end)

local userId = 0
local displayName = ""
local username = ""
pcall(function()
    local lp = game:GetService("Players").LocalPlayer
    userId = lp.UserId
    displayName = lp.DisplayName
    username = lp.Name
end)

local metaJson = string.format(
    '__meta:{"executor":"%s","userId":%d,"displayName":"%s","username":"%s"}',
    execName, userId, displayName, username
)

local msgTypes = {
    [Enum.MessageType.MessageOutput]  = "print",
    [Enum.MessageType.MessageInfo]    = "info",
    [Enum.MessageType.MessageWarning] = "warn",
    [Enum.MessageType.MessageError]   = "error",
}

local ok, ls = pcall(function() return game:GetService("LogService") end)
if ok and ls then
    ls.MessageOut:Connect(function(message, msgType)
        local t = msgTypes[msgType] or "print"
        send("__console:" .. t .. ":" .. tostring(message))
    end)
end

local rawPrint = print
local rawWarn  = warn
local rawError = error

print = function(...)
    local parts = {}
    for i = 1, select('#', ...) do parts[i] = tostring(select(i, ...)) end
    local out = table.concat(parts, "\t")
    rawPrint(...)
    send("__console:print:" .. out)
end

warn = function(...)
    local parts = {}
    for i = 1, select('#', ...) do parts[i] = tostring(select(i, ...)) end
    local out = table.concat(parts, "\t")
    rawWarn(...)
    send("__console:warn:" .. out)
end

error = function(msg, level)
    send("__console:error:" .. tostring(msg))
    rawError(msg, level or 1)
end

ws.OnMessage:Connect(function(msg)
    if msg == "ping" then
        send("pong")
        return
    end
    local fn, err = loadstring(msg)
    if fn then
        local ok2, execErr = pcall(fn)
        if not ok2 then send("__error:" .. tostring(execErr)) end
    else
        send("__error:syntax:" .. tostring(err))
    end
end)

ws.OnClose:Connect(function()
    ws = nil
end)

send("__ready")
send(metaJson)
