using System.Collections.Concurrent;
using System.Text.Json;

namespace PadLink;

public sealed class SlotState
{
    public int Slot { get; init; }
    public string? OwnerId { get; set; }
    public string? DeviceName { get; set; }
    public string? SourceKey { get; set; }
    public long LastActivity { get; set; }
    public bool IsActive => OwnerId != null && (Environment.TickCount64 - LastActivity < 3000);
}

public sealed class TelemetryState
{
    public int Slot { get; set; }
    public float Lx { get; set; }
    public float Ly { get; set; }
    public float Rx { get; set; }
    public float Ry { get; set; }
    public float Lt { get; set; }
    public float Rt { get; set; }
    public float Clutch { get; set; }
    public int Gear { get; set; }
    public uint B { get; set; }
    public uint B1 { get; set; }
    public uint B2 { get; set; }
    public uint B3 { get; set; }
    public uint Ext { get; set; }
    public string? DeviceName { get; set; }
    public string? DeviceId { get; set; }
    public long Timestamp { get; set; }
}

/// <summary>
/// Manages multi-device routing for up to 4 virtual controllers (P1..P4),
/// strict exclusive 1-device-to-1-player slot locking, watchdog timeouts, and haptic feedback dispatch.
/// mobicon-by shakbrotech (https://github.com/shakeel-singalaxana)
/// </summary>
public sealed class PadHost : IDisposable
{
    private readonly IPadPool _pool;
    private readonly object _gate = new();
    private readonly SlotState[] _slots = new SlotState[4];
    private readonly TelemetryState[] _latestTelemetry = new TelemetryState[4];
    private readonly ConcurrentDictionary<string, int> _assignedSlots = new();

    public event Action<int, byte, byte, byte>? OnFeedbackReceived; // (slot, largeMotor, smallMotor, led)
    public event Action<object>? OnSlotsChanged;
    public event Action<TelemetryState>? OnTelemetryReceived;

    public PadHost(IPadPool pool)
    {
        _pool = pool;
        for (int i = 0; i < 4; i++)
        {
            _slots[i] = new SlotState { Slot = i };
            _latestTelemetry[i] = new TelemetryState { Slot = i };
        }
        _pool.FeedbackReceived += (slot, large, small, led) =>
        {
            OnFeedbackReceived?.Invoke(slot, large, small, led);
        };
    }

    public string BackendName => _pool.Name;
    public int MaxSlots => _pool.MaxSlots;

    public object GetSlotsState(string? queryDeviceId = null)
    {
        lock (_gate)
        {
            long now = Environment.TickCount64;
            var list = new List<object>();
            for (int i = 0; i < 4; i++)
            {
                var s = _slots[i];
                bool active = s.OwnerId != null && (now - s.LastActivity < 3000);
                list.Add(new
                {
                    slot = i,
                    locked = active,
                    ownerId = active ? s.OwnerId : null,
                    deviceName = active ? s.DeviceName : null,
                    isYou = active && queryDeviceId != null && s.OwnerId == queryDeviceId
                });
            }
            return new { type = "slots_status", slots = list };
        }
    }

    public object GetTelemetryState()
    {
        lock (_gate)
        {
            return new { type = "telemetry_all", telemetry = _latestTelemetry };
        }
    }

    private void ResetSlotInternal(int i)
    {
        _slots[i].OwnerId = null;
        _slots[i].DeviceName = null;
        _slots[i].SourceKey = null;
        _pool.Reset(i);
        
        var telem = _latestTelemetry[i];
        telem.Lx = 0;
        telem.Ly = 0;
        telem.Rx = 0;
        telem.Ry = 0;
        telem.Lt = 0;
        telem.Rt = 0;
        telem.Clutch = 0;
        telem.Gear = 0;
        telem.B = 0;
        telem.B1 = 0;
        telem.B2 = 0;
        telem.B3 = 0;
        telem.Ext = 0;
        telem.DeviceName = null;
        telem.DeviceId = null;
        telem.Timestamp = Environment.TickCount64;
        OnTelemetryReceived?.Invoke(telem);
    }

    public bool TryClaimSlot(string deviceId, string deviceName, int requestedSlot, out int assignedSlot)
    {
        lock (_gate)
        {
            long now = Environment.TickCount64;
            // 1. If requestedSlot is valid (0..3) and free/self
            if (requestedSlot >= 0 && requestedSlot < 4)
            {
                var target = _slots[requestedSlot];
                bool isFreeOrSelf = target.OwnerId == null || (now - target.LastActivity > 3000) || target.OwnerId == deviceId;
                if (isFreeOrSelf)
                {
                    // Release any previous slot held by this device
                    for (int i = 0; i < 4; i++)
                    {
                        if (i != requestedSlot && _slots[i].OwnerId == deviceId)
                        {
                            ResetSlotInternal(i);
                        }
                    }

                    target.OwnerId = deviceId;
                    target.DeviceName = deviceName;
                    target.LastActivity = now;
                    _assignedSlots[deviceId] = requestedSlot;
                    assignedSlot = requestedSlot;
                    NotifySlotsChanged();
                    return true;
                }
            }

            // 2. Requested slot is taken by another device! Find first available free slot
            for (int i = 0; i < 4; i++)
            {
                var s = _slots[i];
                if (s.OwnerId == null || (now - s.LastActivity > 3000) || s.OwnerId == deviceId)
                {
                    for (int j = 0; j < 4; j++)
                    {
                        if (j != i && _slots[j].OwnerId == deviceId)
                        {
                            ResetSlotInternal(j);
                        }
                    }

                    s.OwnerId = deviceId;
                    s.DeviceName = deviceName;
                    s.LastActivity = now;
                    _assignedSlots[deviceId] = i;
                    assignedSlot = i;
                    NotifySlotsChanged();
                    return false; // Assigned fallback slot
                }
            }

            // All slots locked
            assignedSlot = -1;
            return false;
        }
    }

    public void ReleaseSlot(string deviceId)
    {
        lock (_gate)
        {
            bool changed = false;
            for (int i = 0; i < 4; i++)
            {
                if (_slots[i].OwnerId == deviceId)
                {
                    ResetSlotInternal(i);
                    changed = true;
                }
            }
            RemoveDeviceRoutes(deviceId);
            if (changed) NotifySlotsChanged();
        }
    }

    public void ForceKickSlot(int slot)
    {
        lock (_gate)
        {
            if (slot >= 0 && slot < 4)
            {
                string? owner = _slots[slot].OwnerId;
                if (owner != null) RemoveDeviceRoutes(owner);
                ResetSlotInternal(slot);
                NotifySlotsChanged();
            }
        }
    }

    public void HandleJson(string text, string sourceKey)
    {
        Packet? p;
        try { p = JsonSerializer.Deserialize<Packet>(text); } catch { return; }
        if (p is null || p.V != 1) return;
        HandlePacket(p, sourceKey);
    }

    public int? HandlePacket(Packet p, string sourceKey)
    {
        if (p is null) return null;
        lock (_gate)
        {
            long now = Environment.TickCount64;
            string deviceId = !string.IsNullOrEmpty(p.DeviceId) ? p.DeviceId : sourceKey;
            string deviceName = !string.IsNullOrEmpty(p.DeviceName) ? p.DeviceName : sourceKey;
            string routeKey = GetRouteKey(deviceId, p.ControllerType);

            int slot = ResolveSlotStrict(p.P, routeKey, p.ControllerType, deviceId, deviceName, sourceKey, now);
            if (slot < 0 || slot >= 4) return null;

            var state = _slots[slot];
            state.SourceKey = sourceKey;
            state.LastActivity = now;
            _pool.Apply(slot, p);

            var telem = _latestTelemetry[slot];
            telem.Slot = slot;
            telem.Lx = p.Lx;
            telem.Ly = p.Ly;
            telem.Rx = p.Rx;
            telem.Ry = p.Ry;
            telem.Lt = p.Lt;
            telem.Rt = p.Rt;
            telem.Clutch = p.Clutch;
            telem.Gear = p.Gear;
            telem.B = p.B;
            telem.B1 = p.B1;
            telem.B2 = p.B2;
            telem.B3 = p.B3;
            telem.Ext = p.Ext;
            telem.DeviceName = deviceName;
            telem.DeviceId = deviceId;
            telem.Timestamp = now;

            OnTelemetryReceived?.Invoke(telem);
            return slot;
        }
    }

    private static string GetRouteKey(string deviceId, ControllerKind kind)
    {
        return kind is ControllerKind.ButtonBox or ControllerKind.SteeringWheel
            ? $"{deviceId}:{kind}"
            : $"{deviceId}:controller";
    }

    private void RemoveDeviceRoutes(string deviceId)
    {
        string prefix = deviceId + ":";
        foreach (var key in _assignedSlots.Keys)
        {
            if (key == deviceId || key.StartsWith(prefix, StringComparison.Ordinal))
            {
                _assignedSlots.TryRemove(key, out _);
            }
        }
    }

    private int ResolveSlotStrict(int? requestedSlot, string routeKey, ControllerKind kind, string deviceId, string deviceName, string sourceKey, long now)
    {
        // 1. If this device/control-surface route already owns a slot
        if (_assignedSlots.TryGetValue(routeKey, out int curSlot) && curSlot >= 0 && curSlot < 4)
        {
            var s = _slots[curSlot];
            if (s.OwnerId == deviceId)
            {
                // If device requests a different valid free slot, switch to it
                if (requestedSlot.HasValue && requestedSlot.Value != curSlot && requestedSlot.Value >= 0 && requestedSlot.Value < 4)
                {
                    var target = _slots[requestedSlot.Value];
                    if (target.OwnerId == null || (now - target.LastActivity > 3000))
                    {
                        s.OwnerId = null;
                        s.DeviceName = null;
                        s.SourceKey = null;
                        _pool.Reset(curSlot);

                        target.OwnerId = deviceId;
                        target.DeviceName = deviceName;
                        target.SourceKey = sourceKey;
                        target.LastActivity = now;
                        _assignedSlots[routeKey] = requestedSlot.Value;
                        NotifySlotsChanged();
                        return requestedSlot.Value;
                    }
                }
                return curSlot;
            }
        }

        // 2. New device trying to claim requested slot
        if (requestedSlot.HasValue && requestedSlot.Value >= 0 && requestedSlot.Value < 4)
        {
            int req = requestedSlot.Value;
            var target = _slots[req];
            if (kind == ControllerKind.ButtonBox && target.OwnerId != null && now - target.LastActivity <= 3000)
            {
                _assignedSlots[routeKey] = req;
                return req;
            }

            if (target.OwnerId == null || (now - target.LastActivity > 3000) || target.OwnerId == deviceId)
            {
                target.OwnerId = deviceId;
                target.DeviceName = deviceName;
                target.SourceKey = sourceKey;
                target.LastActivity = now;
                _assignedSlots[routeKey] = req;
                Console.WriteLine($"[mobicon] Slot P{req + 1} locked exclusively to device: {deviceName} ({deviceId})");
                NotifySlotsChanged();
                return req;
            }
        }

        // 3. Requested slot is locked by another device! Assign first free slot
        for (int i = 0; i < 4; i++)
        {
            var s = _slots[i];
            if (s.OwnerId == null || (now - s.LastActivity > 3000))
            {
                s.OwnerId = deviceId;
                s.DeviceName = deviceName;
                s.SourceKey = sourceKey;
                s.LastActivity = now;
                _assignedSlots[routeKey] = i;
                Console.WriteLine($"[mobicon] Requested slot occupied. Assigned free Slot P{i + 1} to device: {deviceName} ({deviceId})");
                NotifySlotsChanged();
                return i;
            }
        }

        // All slots locked! Prevent hijacking other players' controls
        return -1;
    }

    private void NotifySlotsChanged()
    {
        try
        {
            OnSlotsChanged?.Invoke(GetSlotsState());
        }
        catch { }
    }

    /// <summary>
    /// Watchdog loop: releases any slot that stops sending packets for 3000 ms.
    /// </summary>
    public void StartWatchdog(CancellationToken ct) => Task.Run(async () =>
    {
        while (!ct.IsCancellationRequested)
        {
            try { await Task.Delay(500, ct); } catch (OperationCanceledException) { break; }
            lock (_gate)
            {
                long now = Environment.TickCount64;
                bool changed = false;
                for (int i = 0; i < 4; i++)
                {
                    var s = _slots[i];
                    if (s.OwnerId != null && now - s.LastActivity > 3000)
                    {
                        Console.WriteLine($"[mobicon] Slot P{i + 1} released (idle timeout): {s.DeviceName} ({s.OwnerId})");
                        RemoveDeviceRoutes(s.OwnerId);
                        ResetSlotInternal(i);
                        changed = true;
                    }
                }
                if (changed) NotifySlotsChanged();
            }
        }
    });

    public void Dispose()
    {
        lock (_gate)
        {
            _pool.ResetAll();
            _pool.Dispose();
        }
    }
}
