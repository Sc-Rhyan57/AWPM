if _G.__AWP_WS_ACTIVE then
    print("[AWP] Already active, skipping")
    return
end
_G.__AWP_WS_ACTIVE = true

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")

local serverIp = "REPLACE_WITH_LOCAL_IP"
local ws = nil
local connected = false

print("[AWP] Starting AWP client...")
print("[AWP] Server IP: " .. serverIp)

local execName = "Unknown"
pcall(function()
    execName = (identifyexecutor and identifyexecutor()) or
               (getexecutorname and getexecutorname()) or "Unknown"
    print("[AWP] Executor: " .. execName)
end)

local userId = 0
pcall(function()
    userId = Players.LocalPlayer.UserId
    print("[AWP] User ID: " .. userId)
end)

local function connectToServer()
    if connected then 
        print("[AWP] Already connected")
        return 
    end
    
    connected = false
    local wsUrl = "ws://" .. serverIp .. ":9999"
    
    print("[AWP] Connecting to " .. wsUrl)
    
    local ok, err = pcall(function()
        ws = WebSocket.connect(wsUrl)
    end)
    
    if not ok then
        print("[AWP] Connect failed: " .. tostring(err))
        return
    end
    
    if not ws then
        print("[AWP] WebSocket is nil")
        return
    end
    
    print("[AWP] WebSocket object created")
    
    ws.OnClose:Connect(function()
        print("[AWP] Connection closed by server")
        connected = false
        ws = nil
    end)
    
    ws.OnMessage:Connect(function(msg)
        print("[AWP] Received: " .. tostring(msg):sub(1, 50))
        
        if msg == "__ping" or msg == "ping" then
            pcall(function() ws:Send("__pong") end)
            if not connected then
                connected = true
                print("[AWP] Connection established!")
                task.wait(0.1)
                pcall(function() ws:Send("__ready") end)
                task.wait(0.1)
                pcall(function() 
                    ws:Send("__meta:" .. HttpService:JSONEncode({
                        executor = execName,
                        userId = userId
                    }))
                end)
            end
            return
        end
        
        if msg:sub(1, 1) ~= "_" then
            local fn, loadErr = loadstring(msg)
            if fn then
                task.spawn(function()
                    local ok, execErr = pcall(fn)
                    if not ok then 
                        print("[AWP] Exec error: " .. tostring(execErr))
                    end
                end)
            else
                print("[AWP] Syntax error: " .. tostring(loadErr))
            end
        end
    end)
    
    print("[AWP] Waiting for server ping...")
end

task.wait(1)
connectToServer()
