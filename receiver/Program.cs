using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using PadLink;
using QRCoder;

const int UdpPort = 7777;   // Android & UDP clients
const int HttpPort = 8080;  // Web client + WebSocket + Config UI
const string GithubUrl = "https://github.com/shakeel-singalaxana";
const string BrandName = "mobicon-by shakbrotech";

var backendMode = ParseBackendMode(args);
var driverStatus = GetDriverStatus();
var host = new PadHost(CreatePadPool(backendMode));
bool autoDriverInstall = backendMode != BackendMode.Dry && !args.Contains("--no-driver-install");

var builder = WebApplication.CreateBuilder(Array.Empty<string>());
builder.WebHost.UseUrls($"http://0.0.0.0:{HttpPort}");
builder.Logging.SetMinimumLevel(LogLevel.Warning);
var app = builder.Build();

app.UseWebSockets();
app.UseDefaultFiles();   // serves wwwroot/index.html at "/"
app.UseStaticFiles();

// Active WebSocket connections mapped to their requested player slot (or -1 for all/auto)
var activeWebSockets = new ConcurrentDictionary<WebSocket, string>();
// Active UDP client endpoints mapped to their assigned player slot
var activeUdpClients = new ConcurrentDictionary<IPEndPoint, int>();

// Configuration & Custom Decks Persistence
string settingsFilePath = Path.Combine(AppContext.BaseDirectory, "mobicon_settings.json");
object settingsLock = new();

string defaultConfigJson = """
{
  "maxSteeringDegrees": 1080,
  "steeringSensitivity": 1.0,
  "degreeRatio": 1.0,
  "steeringLinearity": 1.0,
  "steeringDeadzone": 0,
  "returnSpeed": "realistic",
  "gyroEnabled": true,
  "gyroAngleRange": 35,
  "gyroSmoothing": 0.40,
  "vibrateEnabled": true,
  "vibrateStrength": 100,
  "gasSensitivity": 1.0,
  "brakeSensitivity": 1.0,
  "clutchSensitivity": 1.0,
  "handbrakeMode": "momentary",
  "shifterMode": "h_pattern",
  "truckboxCategories": {
    "engine": true,
    "lights": true,
    "aux": true,
    "cruise": true,
    "cabin": true,
    "hud": true,
    "camera": true
  }
}
""";

JsonElement currentConfig = JsonDocument.Parse(defaultConfigJson).RootElement.Clone();
JsonElement currentDecks = JsonDocument.Parse("[]").RootElement.Clone();

try
{
    if (File.Exists(settingsFilePath))
    {
        using var stream = File.OpenRead(settingsFilePath);
        using var doc = JsonDocument.Parse(stream);
        if (doc.RootElement.TryGetProperty("config", out var cfg)) currentConfig = cfg.Clone();
        if (doc.RootElement.TryGetProperty("decks", out var dks)) currentDecks = dks.Clone();
    }
}
catch { }

void SaveSettings()
{
    try
    {
        lock (settingsLock)
        {
            var data = new { config = currentConfig, decks = currentDecks };
            File.WriteAllText(settingsFilePath, JsonSerializer.Serialize(data, new JsonSerializerOptions { WriteIndented = true }));
        }
    }
    catch { }
}

// Broadcast helper for WebSockets
async Task BroadcastToWebSocketsAsync(object data)
{
    byte[] jsonBytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(data));
    var segment = new ArraySegment<byte>(jsonBytes);
    foreach (var (ws, _) in activeWebSockets)
    {
        if (ws.State == WebSocketState.Open)
        {
            try
            {
                await ws.SendAsync(segment, WebSocketMessageType.Text, true, CancellationToken.None);
            }
            catch { }
        }
    }
}

// Broadcast slot changes to all connected web clients in real-time
host.OnSlotsChanged += async (slotsState) =>
{
    await BroadcastToWebSocketsAsync(slotsState);
};

// Forward real-time controller inputs & Logitech steering telemetry to monitoring clients
long lastTelemetryBroadcastTime = 0;
host.OnTelemetryReceived += async (telem) =>
{
    long now = Environment.TickCount64;
    // Broadcast at up to 60 FPS for responsive live visualizer
    if (now - lastTelemetryBroadcastTime >= 16)
    {
        lastTelemetryBroadcastTime = now;
        await BroadcastToWebSocketsAsync(new
        {
            type = "telemetry",
            slot = telem.Slot,
            lx = telem.Lx,
            ly = telem.Ly,
            rx = telem.Rx,
            ry = telem.Ry,
            lt = telem.Lt,
            rt = telem.Rt,
            clutch = telem.Clutch,
            gear = telem.Gear,
            b = telem.B,
            b1 = telem.B1,
            b2 = telem.B2,
            b3 = telem.B3,
            ext = telem.Ext,
            deviceName = telem.DeviceName,
            deviceId = telem.DeviceId
        });
    }
};

// Attach Force Feedback / Haptic Rumble Dispatcher
UdpClient? udpServerInstance = null;
host.OnFeedbackReceived += async (slot, large, small, led) =>
{
    // 1. Dispatch to WebSockets
    byte[] wsJson = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new
    {
        type = "rumble",
        slot,
        large,
        small,
        led
    }));

    foreach (var (ws, _) in activeWebSockets)
    {
        if (ws.State == WebSocketState.Open)
        {
            try
            {
                await ws.SendAsync(new ArraySegment<byte>(wsJson), WebSocketMessageType.Text, true, CancellationToken.None);
            }
            catch { }
        }
    }

    // 2. Dispatch to UDP clients (6-byte binary datagram: 0x4D 0x42 [slot] [large] [small] [led])
    if (udpServerInstance != null)
    {
        byte[] udpPacket = new byte[] { 0x4D, 0x42, (byte)slot, large, small, led };
        foreach (var (ep, epSlot) in activeUdpClients)
        {
            if (epSlot == -1 || epSlot == slot)
            {
                try
                {
                    await udpServerInstance.SendAsync(udpPacket, udpPacket.Length, ep);
                }
                catch { }
            }
        }
    }
};

// Gather all active network IPv4 addresses
var localIps = new List<IPAddress>();
foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
{
    if (ni.OperationalStatus != OperationalStatus.Up || ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
    foreach (var ua in ni.GetIPProperties().UnicastAddresses)
    {
        if (ua.Address.AddressFamily == AddressFamily.InterNetwork)
        {
            localIps.Add(ua.Address);
        }
    }
}

// Determine best primary LAN IP
var primaryIp = localIps.FirstOrDefault(ip =>
{
    var b = ip.GetAddressBytes();
    if (b[0] == 192 && b[1] == 168 && !b.SequenceEqual(new byte[] { 192, 168, 56, 1 })) return true;
    if (b[0] == 10) return true;
    if (b[0] == 172 && b[1] >= 16 && b[1] <= 31) return true;
    return false;
}) ?? localIps.FirstOrDefault(ip => !ip.ToString().StartsWith("169.254"))
   ?? localIps.FirstOrDefault()
   ?? IPAddress.Loopback;

string primaryUrl = $"http://{primaryIp}:{HttpPort}";
string configUrl = $"http://localhost:{HttpPort}/config.html";

// Pre-generate QR Code PNG
using var qrGenerator = new QRCodeGenerator();
using var qrCodeData = qrGenerator.CreateQrCode(primaryUrl, QRCodeGenerator.ECCLevel.M);
var pngQrCode = new PngByteQRCode(qrCodeData);
byte[] qrPngBytes = pngQrCode.GetGraphic(15);

// REST API Endpoints for Configuration Interface & Monitoring
app.MapGet("/config", () => Results.Redirect("/config.html"));

app.MapGet("/api/qr", () => Results.File(qrPngBytes, "image/png"));

app.MapGet("/api/status", () =>
{
    var allEndpoints = new List<string> { primaryUrl };
    foreach (var ip in localIps.Where(i => !i.Equals(primaryIp)))
    {
        allEndpoints.Add($"http://{ip}:{HttpPort}");
    }
    allEndpoints.Add($"http://localhost:{HttpPort}");

    return Results.Ok(new
    {
        brand = BrandName,
        github = GithubUrl,
        backend = host.BackendName,
        drivers = GetDriverStatus(),
        maxSlots = host.MaxSlots,
        httpPort = HttpPort,
        udpPort = UdpPort,
        primaryUrl = primaryUrl,
        configUrl = configUrl,
        qrUrl = "/api/qr",
        endpoints = allEndpoints,
        slots = host.GetSlotsState()
    });
});

app.MapGet("/api/drivers", () => Results.Ok(new
{
    status = GetDriverStatus()
}));

app.MapPost("/api/drivers/install_missing", async (HttpContext ctx) =>
{
    var results = await DriverSetup.InstallMissingAsync(GetDriverStatus(), ctx.RequestAborted);
    return Results.Ok(new { success = results.Any(r => r.Success), results });
});

app.MapPost("/api/drivers/install/{driver}", async (string driver, HttpContext ctx) =>
{
    var result = await DriverSetup.InstallDriverAsync(driver, ctx.RequestAborted);
    return Results.Ok(new { success = result.Success, result });
});

app.MapGet("/api/slots", () => Results.Ok(host.GetSlotsState()));

app.MapGet("/api/telemetry", () => Results.Ok(host.GetTelemetryState()));

app.MapGet("/api/config", () => Results.Ok(new
{
    success = true,
    config = currentConfig,
    decks = currentDecks
}));

app.MapPost("/api/config", async (HttpContext ctx) =>
{
    using var reader = new StreamReader(ctx.Request.Body);
    var body = await reader.ReadToEndAsync();
    try
    {
        using var doc = JsonDocument.Parse(body);
        currentConfig = doc.RootElement.Clone();
        SaveSettings();
        await BroadcastToWebSocketsAsync(new { type = "config_update", config = currentConfig });
        return Results.Ok(new { success = true, config = currentConfig });
    }
    catch (Exception ex)
    {
        return Results.BadRequest(new { error = ex.Message });
    }
});

app.MapPost("/api/decks", async (HttpContext ctx) =>
{
    using var reader = new StreamReader(ctx.Request.Body);
    var body = await reader.ReadToEndAsync();
    try
    {
        using var doc = JsonDocument.Parse(body);
        currentDecks = doc.RootElement.Clone();
        SaveSettings();
        await BroadcastToWebSocketsAsync(new { type = "decks_update", decks = currentDecks });
        return Results.Ok(new { success = true, decks = currentDecks });
    }
    catch (Exception ex)
    {
        return Results.BadRequest(new { error = ex.Message });
    }
});

app.MapPost("/api/claim_slot", async (HttpContext ctx) =>
{
    using var reader = new StreamReader(ctx.Request.Body);
    var body = await reader.ReadToEndAsync();
    try
    {
        using var doc = JsonDocument.Parse(body);
        string deviceId = doc.RootElement.TryGetProperty("deviceId", out var dProp) ? dProp.GetString() ?? Guid.NewGuid().ToString("N") : Guid.NewGuid().ToString("N");
        string deviceName = doc.RootElement.TryGetProperty("deviceName", out var nProp) ? nProp.GetString() ?? "Unknown Device" : "Unknown Device";
        int requestedSlot = doc.RootElement.TryGetProperty("slot", out var sProp) ? sProp.GetInt32() : 0;

        bool granted = host.TryClaimSlot(deviceId, deviceName, requestedSlot, out int assignedSlot);
        return Results.Ok(new { success = true, granted, assignedSlot, deviceId });
    }
    catch (Exception ex)
    {
        return Results.BadRequest(new { error = ex.Message });
    }
});

app.MapPost("/api/release_slot", async (HttpContext ctx) =>
{
    using var reader = new StreamReader(ctx.Request.Body);
    var body = await reader.ReadToEndAsync();
    try
    {
        using var doc = JsonDocument.Parse(body);
        string deviceId = doc.RootElement.GetProperty("deviceId").GetString() ?? "";
        host.ReleaseSlot(deviceId);
        return Results.Ok(new { success = true });
    }
    catch (Exception ex)
    {
        return Results.BadRequest(new { error = ex.Message });
    }
});

app.MapPost("/api/kick_slot", async (HttpContext ctx) =>
{
    using var reader = new StreamReader(ctx.Request.Body);
    var body = await reader.ReadToEndAsync();
    try
    {
        using var doc = JsonDocument.Parse(body);
        int slot = doc.RootElement.GetProperty("slot").GetInt32();
        host.ForceKickSlot(slot);
        return Results.Ok(new { success = true, slot });
    }
    catch (Exception ex)
    {
        return Results.BadRequest(new { error = ex.Message });
    }
});

app.MapPost("/api/shutdown", () =>
{
    _ = Task.Run(async () =>
    {
        await Task.Delay(400);
        Console.WriteLine("[mobicon] Shutdown requested from Configuration UI. Closing server...");
        app.Lifetime.StopApplication();
        await Task.Delay(200);
        Environment.Exit(0);
    });
    return Results.Ok(new { success = true, message = "Shutting down mobicon receiver..." });
});

app.MapPost("/api/exit", () => Results.Redirect("/api/shutdown"));

app.Map("/ws", async ctx =>
{
    if (!ctx.WebSockets.IsWebSocketRequest) { ctx.Response.StatusCode = 400; return; }
    using var ws = await ctx.WebSockets.AcceptWebSocketAsync();
    string clientKey = Guid.NewGuid().ToString("N");
    activeWebSockets[ws] = clientKey;
    string source = "web " + ctx.Connection.RemoteIpAddress;

    // Send initial slots state, config, and telemetry to the new client
    try
    {
        var initSlotsJson = JsonSerializer.Serialize(host.GetSlotsState());
        await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(initSlotsJson)), WebSocketMessageType.Text, true, CancellationToken.None);

        var initConfigJson = JsonSerializer.Serialize(new
        {
            type = "config_update",
            config = currentConfig,
            decks = currentDecks
        });
        await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(initConfigJson)), WebSocketMessageType.Text, true, CancellationToken.None);

        var initTelemJson = JsonSerializer.Serialize(host.GetTelemetryState());
        await ws.SendAsync(new ArraySegment<byte>(Encoding.UTF8.GetBytes(initTelemJson)), WebSocketMessageType.Text, true, CancellationToken.None);
    }
    catch { }

    var buf = new byte[4096];
    try
    {
        while (ws.State == WebSocketState.Open)
        {
            var r = await ws.ReceiveAsync(buf, ctx.RequestAborted);
            if (r.MessageType == WebSocketMessageType.Close) break;
            string text = Encoding.UTF8.GetString(buf, 0, r.Count);
            
            try
            {
                using var doc = JsonDocument.Parse(text);
                if (doc.RootElement.TryGetProperty("type", out var typeProp))
                {
                    string msgType = typeProp.GetString() ?? "";
                    if (msgType == "claim_slot")
                    {
                        string dId = doc.RootElement.TryGetProperty("deviceId", out var dP) ? dP.GetString() ?? clientKey : clientKey;
                        string dName = doc.RootElement.TryGetProperty("deviceName", out var nP) ? nP.GetString() ?? "Web Client" : "Web Client";
                        int reqSlot = doc.RootElement.TryGetProperty("slot", out var sP) ? sP.GetInt32() : 0;
                        bool granted = host.TryClaimSlot(dId, dName, reqSlot, out int assigned);
                        
                        var reply = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new
                        {
                            type = "slot_assigned",
                            granted,
                            slot = assigned,
                            requested = reqSlot
                        }));
                        await ws.SendAsync(new ArraySegment<byte>(reply), WebSocketMessageType.Text, true, CancellationToken.None);
                        continue;
                    }
                    else if (msgType == "release_slot")
                    {
                        string dId = doc.RootElement.TryGetProperty("deviceId", out var dP) ? dP.GetString() ?? clientKey : clientKey;
                        host.ReleaseSlot(dId);
                        continue;
                    }
                    else if (msgType == "get_slots")
                    {
                        var reply = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(host.GetSlotsState()));
                        await ws.SendAsync(new ArraySegment<byte>(reply), WebSocketMessageType.Text, true, CancellationToken.None);
                        continue;
                    }
                    else if (msgType == "get_config")
                    {
                        var reply = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new
                        {
                            type = "config_update",
                            config = currentConfig,
                            decks = currentDecks
                        }));
                        await ws.SendAsync(new ArraySegment<byte>(reply), WebSocketMessageType.Text, true, CancellationToken.None);
                        continue;
                    }
                    else if (msgType == "set_config" && doc.RootElement.TryGetProperty("config", out var newCfg))
                    {
                        currentConfig = newCfg.Clone();
                        SaveSettings();
                        await BroadcastToWebSocketsAsync(new { type = "config_update", config = currentConfig });
                        continue;
                    }
                    else if (msgType == "set_decks" && doc.RootElement.TryGetProperty("decks", out var newDks))
                    {
                        currentDecks = newDks.Clone();
                        SaveSettings();
                        await BroadcastToWebSocketsAsync(new { type = "decks_update", decks = currentDecks });
                        continue;
                    }
                    else if (msgType == "get_telemetry")
                    {
                        var reply = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(host.GetTelemetryState()));
                        await ws.SendAsync(new ArraySegment<byte>(reply), WebSocketMessageType.Text, true, CancellationToken.None);
                        continue;
                    }
                }
            }
            catch { }

            host.HandleJson(text, source);
        }
    }
    catch (Exception) { /* client disconnected */ }
    finally
    {
        activeWebSockets.TryRemove(ws, out var removedId);
        if (removedId != null) host.ReleaseSlot(removedId);
    }
});

var stopping = app.Lifetime.ApplicationStopping;
host.StartWatchdog(stopping);
_ = Task.Run(() => UdpLoop(host, UdpPort, activeUdpClients, u => udpServerInstance = u, stopping));

// Generate ASCII QR Code for direct mobile scanning
var qrCode = new AsciiQRCode(qrCodeData);
string qrAscii = qrCode.GetGraphic(1, "  ", "██");

Console.WriteLine();
Console.WriteLine("==================================================================");
Console.WriteLine($"      {BrandName.ToUpperInvariant()} (MULTI-CONTROLLER HUB)");
Console.WriteLine($"      Author Profile : {GithubUrl}");
Console.WriteLine("==================================================================");
Console.WriteLine($"Backend        : {host.BackendName}");
Console.WriteLine($"Driver Check   : ViGEmBus={(driverStatus.vigem.Installed ? "Installed" : "Missing")} | Truckbox=Keyboard simulation");
if (!driverStatus.vigem.Installed)
{
    Console.WriteLine("Driver Setup   : Install ViGEmBus for virtual Xbox/PlayStation and steering controllers:");
    Console.WriteLine($"  * ViGEmBus: {DriverProbe.ViGEmDownloadUrl}");
}
Console.WriteLine($"Multiplayer    : Up to 4 Players (Controllers plus truck boxes/button pads per player)");
Console.WriteLine($"Web Controller : {primaryUrl}");
Console.WriteLine($"Config Hub (PC): {configUrl}");
Console.WriteLine($"Android App    : UDP Port {UdpPort} (Auto-Discovery Supported)");
Console.WriteLine($"Haptics/Rumble : Bidirectional Active (Game -> Phone Vibration)");
Console.WriteLine();
Console.WriteLine("Scan this QR code with mobile camera to launch Web Controller:");
Console.WriteLine();
Console.WriteLine(qrAscii);
Console.WriteLine("Available Network Endpoints:");
Console.WriteLine($"  * {primaryUrl} (Recommended Mobile)");
foreach (var ip in localIps.Where(i => !i.Equals(primaryIp)))
{
    Console.WriteLine($"  * http://{ip}:{HttpPort}");
}
Console.WriteLine($"  * http://localhost:{HttpPort}");
Console.WriteLine($"  * {configUrl} (PC Configuration Interface)");
Console.WriteLine();
Console.WriteLine("Supported Layouts:");
Console.WriteLine("  1. Logitech G29 Racing Wheel (1080°/900° Wheel, F1 Paddles, Gas/Brake/Clutch, 6-Speed H-Shifter)");
Console.WriteLine("  2. Heavy Truck Cockpit / Super Button Box (All 7 ETS2/ATS Engine, Transmission, Lights, Brakes, HUD, Views)");
Console.WriteLine("  3. PlayStation DualSense / DualShock (Symmetric Sticks, Shapes, Touchpad)");
Console.WriteLine("  4. Xbox Series X|S / One (Asymmetric Sticks, ABXY, Triggers, D-Pad)");
Console.WriteLine();
Console.WriteLine("Press Ctrl+C to quit.");
Console.WriteLine("==================================================================");
Console.WriteLine();

if (autoDriverInstall && !driverStatus.vigem.Installed)
{
    _ = Task.Run(async () =>
    {
        await Task.Delay(1200);
        Console.WriteLine("[mobicon] Missing driver(s) detected. Downloading installer(s) and opening setup...");
        var results = await DriverSetup.InstallMissingAsync(driverStatus, CancellationToken.None);
        foreach (var result in results)
        {
            Console.WriteLine($"[mobicon] Driver setup: {result.Message}");
        }
    });
}

// Auto-open PC Configuration Interface in default browser
if (!args.Contains("--no-browser") && !args.Contains("--headless"))
{
    _ = Task.Run(async () =>
    {
        await Task.Delay(800);
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = configUrl,
                UseShellExecute = true
            });
        }
        catch { }
    });
}

await app.RunAsync();
host.Dispose();

static async Task UdpLoop(PadHost host, int port, ConcurrentDictionary<IPEndPoint, int> activeUdpClients, Action<UdpClient> setInstance, CancellationToken ct)
{
    using var udp = new UdpClient();
    setInstance(udp);
    try
    {
        udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        udp.Client.Bind(new IPEndPoint(IPAddress.Any, port));
        udp.Client.ReceiveBufferSize = 65536;

        while (!ct.IsCancellationRequested)
        {
            var r = await udp.ReceiveAsync(ct);
            byte[] buf = r.Buffer;
            if (buf.Length == 0) continue;

            // 1. Discovery request ("MOBICON_DISCOVER", "ROJX_DISCOVER", "PADLINK_DISCOVER")
            if (buf.Length >= 4 && (buf[0] == 'M' || buf[0] == 'R' || buf[0] == 'P'))
            {
                string text = Encoding.UTF8.GetString(buf);
                if (text.StartsWith("MOBICON_DISCOVER") || text.StartsWith("ROJX_DISCOVER") || text.StartsWith("PADLINK_DISCOVER"))
                {
                    byte[] reply = Encoding.UTF8.GetBytes($"MOBICON_SERVER|{Environment.MachineName}|{port}|{HttpPort}|READY");
                    await udp.SendAsync(reply, reply.Length, r.RemoteEndPoint);
                    continue;
                }
            }

            // 2. Binary Control Packet (0x4D, 0x42 "MB" or 0x52, 0x4F "RO", 21-byte legacy or 33-byte 128-bit button mask)
            if (buf.Length >= 21 && ((buf[0] == 0x4D && buf[1] == 0x42) || (buf[0] == 0x52 && buf[1] == 0x4F)))
            {
                if (Packet.TryParseBinary(buf, buf.Length, out var packet))
                {
                    string endpointId = r.RemoteEndPoint.ToString();
                    packet.DeviceId ??= endpointId;
                    packet.DeviceName ??= "Android (" + r.RemoteEndPoint.Address + ")";
                    int? assigned = host.HandlePacket(packet, endpointId);
                    if (assigned.HasValue)
                    {
                        activeUdpClients[r.RemoteEndPoint] = assigned.Value;
                    }
                }
                continue;
            }

            // 3. JSON Control Packet
            try
            {
                string json = Encoding.UTF8.GetString(buf);
                using var doc = JsonDocument.Parse(json);
                string endpointId = r.RemoteEndPoint.ToString();
                int slot = 0;
                if (doc.RootElement.TryGetProperty("p", out var pProp) && pProp.TryGetInt32(out int pVal))
                {
                    slot = pVal;
                }
                activeUdpClients[r.RemoteEndPoint] = slot;
                host.HandleJson(json, endpointId);
            }
            catch { }
        }
    }
    catch (OperationCanceledException) { }
    catch (Exception ex)
    {
        Console.WriteLine($"UDP server error: {ex.Message}");
    }
}

static BackendMode ParseBackendMode(string[] args)
{
    if (args.Contains("--dry")) return BackendMode.Dry;

    string? backend = null;
    string? backendArg = args.FirstOrDefault(a => a.StartsWith("--backend=", StringComparison.OrdinalIgnoreCase));
    if (backendArg != null) backend = backendArg.Split('=', 2)[1];

    int backendFlagIndex = Array.FindIndex(args, a => a.Equals("--backend", StringComparison.OrdinalIgnoreCase));
    if (backendFlagIndex >= 0 && backendFlagIndex + 1 < args.Length) backend = args[backendFlagIndex + 1];

    return backend?.ToLowerInvariant() switch
    {
        "vigem" or "vgembus" => BackendMode.ViGEm,
        "dry" => BackendMode.Dry,
        _ => BackendMode.Auto
    };
}

static DriverStatus GetDriverStatus() => new(DriverProbe.ViGEm());

static IPadPool CreatePadPool(BackendMode mode)
{
    if (mode == BackendMode.Dry)
    {
        return new DryPadPool();
    }

    IPadPool? TryCreate(string label, Func<IPadPool> factory)
    {
        try { return factory(); }
        catch (Exception e)
        {
            Console.WriteLine($"Could not initialize {label} backend: {e.Message}");
            return null;
        }
    }

    if (mode == BackendMode.ViGEm)
    {
        return new CompositePadPool(TryCreate("ViGEm", () => new ViGEmPadPool()), new KeyboardButtonBoxPool());
    }

    var vigem = TryCreate("ViGEm", () => new ViGEmPadPool());
    return new CompositePadPool(vigem, new KeyboardButtonBoxPool());

}

