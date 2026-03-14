# AWP Mobile

Android port of AWP-UI executor frontend for Roblox + Delta.

**By Rhyan57**

## How it works
1. App starts a WebSocket server (port 8765) and HTTP server (port 8080)
2. User clicks Run → Roblox opens
3. Paste bootstrap loadstring in Delta → it connects back via WebSocket
4. App sends scripts, Delta executes them

## Build
Via GitHub Actions → workflow_dispatch
