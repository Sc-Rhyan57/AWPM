local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local ws = nil
local connected = false

local function send(msg)
    if ws and connected then
        pcall(function() ws:Send(msg) end)
    end
end

local function destroyDeltaGui(inst)
    pcall(function()
        for _, child in ipairs(inst:GetChildren()) do
            if child.Name == "Executor" then
                pcall(function()
                    if child.Parent == gethui() then child:Destroy()
                    else child.Parent:Destroy() end
                end)
                return
            end
            destroyDeltaGui(child)
        end
    end)
end

pcall(function()
    local hgui = gethui()
    destroyDeltaGui(hgui)
    for _, v in ipairs(hgui:GetChildren()) do
        local n = v.Name:lower()
        if n:find("delta") or n:find("executor") or n:find("exploit") or n:find("injector") then
            pcall(function() v:Destroy() end)
        end
    end
end)

local execName = "Unknown"
pcall(function()
    execName = (identifyexecutor and identifyexecutor()) or
               (getexecutorname and getexecutorname()) or
               "Unknown"
end)

local userId = 0
local displayName = ""
local username = ""
pcall(function()
    local lp = Players.LocalPlayer
    userId = lp.UserId
    displayName = lp.DisplayName
    username = lp.Name
end)

local metaPayload = HttpService:JSONEncode({
    executor = execName,
    userId = userId,
    displayName = displayName,
    username = username
})

local rawPrint = print
local rawWarn  = warn
local rawError = error

print = function(...)
    local parts = {}
    for i = 1, select('#', ...) do parts[i] = tostring(select(i, ...)) end
    local out = table.concat(parts, "\t")
    pcall(rawPrint, ...)
    send("__console:print:" .. out)
end

warn = function(...)
    local parts = {}
    for i = 1, select('#', ...) do parts[i] = tostring(select(i, ...)) end
    local out = table.concat(parts, "\t")
    pcall(rawWarn, ...)
    send("__console:warn:" .. out)
end

error = function(msg, level)
    send("__console:error:" .. tostring(msg))
    pcall(rawError, msg, level or 1)
end

local ok, ls = pcall(function() return game:GetService("LogService") end)
if ok and ls then
    pcall(function()
        ls.MessageOut:Connect(function(message, msgType)
            local types = {
                [Enum.MessageType.MessageOutput]  = "print",
                [Enum.MessageType.MessageInfo]    = "info",
                [Enum.MessageType.MessageWarning] = "warn",
                [Enum.MessageType.MessageError]   = "error",
            }
            local t = types[msgType] or "print"
            send("__console:" .. t .. ":" .. tostring(message))
        end)
    end)
end

local function connect()
    pcall(function()
        ws = WebSocket.connect("ws://127.0.0.1:9999")
        connected = true

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
            connected = false
            ws = nil
        end)

        send("__ready")
        send("__meta:" .. metaPayload)

        task.spawn(function()
            while connected do
                task.wait(4)
                send("pong")
            end
        end)
    end)
end

task.spawn(function()
    while true do
        if not connected then
            connect()
        end
        task.wait(3)
    end
end)
