using System.Runtime.InteropServices;
using System.Text.Json.Serialization;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

namespace PadLink;

/// <summary>
/// Bit definitions matching Web Controller, Android App, and Receiver bitmasks.
/// mobicon-by shakbrotech (https://github.com/shakeel-singalaxana)
/// </summary>
public static class ButtonBits
{
    // Standard Gamepad (Bits 0..14)
    public const uint A = 1u << 0;             // Cross (PS)
    public const uint B = 1u << 1;             // Circle (PS)
    public const uint X = 1u << 2;             // Square (PS)
    public const uint Y = 1u << 3;             // Triangle (PS)
    public const uint LeftShoulder = 1u << 4;  // LB / L1
    public const uint RightShoulder = 1u << 5; // RB / R1
    public const uint Back = 1u << 6;          // View / Share / Select
    public const uint Start = 1u << 7;         // Menu / Options
    public const uint LeftThumb = 1u << 8;     // L3 / Left Stick Click
    public const uint RightThumb = 1u << 9;    // R3 / Right Stick Click
    public const uint DpadUp = 1u << 10;
    public const uint DpadDown = 1u << 11;
    public const uint DpadLeft = 1u << 12;
    public const uint DpadRight = 1u << 13;
    public const uint Guide = 1u << 14;        // Xbox Button / PS Button

    // Simulation / Cockpit / Logitech Wheels & Shifter (Bits 15..31)
    public const uint Horn = 1u << 15;
    public const uint Wipers = 1u << 16;
    public const uint HighBeams = 1u << 17;
    public const uint Hazard = 1u << 18;
    public const uint Handbrake = 1u << 19;
    public const uint ShiftUp = 1u << 20;      // Paddle Shifter Right
    public const uint ShiftDown = 1u << 21;    // Paddle Shifter Left
    public const uint Gear1 = 1u << 22;        // H-Pattern 1st Gear
    public const uint Gear2 = 1u << 23;        // H-Pattern 2nd Gear
    public const uint Gear3 = 1u << 24;        // H-Pattern 3rd Gear
    public const uint Gear4 = 1u << 25;        // H-Pattern 4th Gear
    public const uint Gear5 = 1u << 26;        // H-Pattern 5th Gear
    public const uint Gear6 = 1u << 27;        // H-Pattern 6th Gear
    public const uint GearReverse = 1u << 28;  // H-Pattern Reverse Gear
    public const uint DiffLock = 1u << 29;
    public const uint TrailerAttach = 1u << 30;
    public const uint CruiseControl = 1u << 31;
}

public enum ControllerKind
{
    Gamepad = 0x01,
    PlayStation = 0x02,
    SteeringWheel = 0x03,
    ButtonBox = 0x04
}

public sealed record DriverInfo(string Name, bool Installed, string DownloadUrl, string? Detail = null);
public sealed record DriverStatus(DriverInfo vigem);
public sealed record DriverSetupResult(bool Success, string Message, string? Path = null);

public static class DriverProbe
{
    public const string ViGEmDownloadUrl = "https://github.com/nefarius/ViGEmBus/releases";
    public static DriverInfo ViGEm() => new(
        "ViGEmBus",
        CanCreateViGEmClient(),
        ViGEmDownloadUrl);

    private static bool CanCreateViGEmClient()
    {
        try
        {
            using var client = new ViGEmClient();
            return true;
        }
        catch
        {
            return false;
        }
    }

}

/// <summary>Packet sent by phone or web client (~60-120 Hz).</summary>
public sealed class Packet
{
    [JsonPropertyName("v")]          public int V { get; set; } = 1;
    [JsonPropertyName("p")]          public int? P { get; set; } // Player slot 0..3 (null = auto)
    [JsonPropertyName("deviceId")]   public string? DeviceId { get; set; } // Unique client device identifier for exclusive slot locking
    [JsonPropertyName("deviceName")] public string? DeviceName { get; set; } // Friendly client device name (e.g., "iPhone 15", "Galaxy Tab")
    [JsonPropertyName("ct")]         public ControllerKind ControllerType { get; set; } = ControllerKind.Gamepad;
    [JsonPropertyName("lx")]         public float Lx { get; set; } // Left stick X / Steering (-1..1)
    [JsonPropertyName("ly")]         public float Ly { get; set; } // Left stick Y (-1..1)
    [JsonPropertyName("rx")]         public float Rx { get; set; } // Right stick X / Look X (-1..1)
    [JsonPropertyName("ry")]         public float Ry { get; set; } // Right stick Y / Look Y (-1..1)
    [JsonPropertyName("lt")]         public float Lt { get; set; } // Left Trigger / Brake (0..1)
    [JsonPropertyName("rt")]         public float Rt { get; set; } // Right Trigger / Gas (0..1)
    [JsonPropertyName("clutch")]     public float Clutch { get; set; } // Clutch pedal (0..1)
    [JsonPropertyName("gear")]       public int Gear { get; set; } // 0=Neutral, 1..12=Gears, -1=Reverse
    [JsonPropertyName("b")]          public uint B { get; set; } // 128-bit Button Bitmask word 0 (bits 0..31)
    [JsonPropertyName("b1")]         public uint B1 { get; set; } // 128-bit Button Bitmask word 1 (bits 32..63)
    [JsonPropertyName("b2")]         public uint B2 { get; set; } // 128-bit Button Bitmask word 2 (bits 64..95)
    [JsonPropertyName("b3")]         public uint B3 { get; set; } // 128-bit Button Bitmask word 3 (bits 96..127)
    [JsonPropertyName("ext")]        public uint Ext { get => B1; set => B1 = value; } // Backward-compatible alias for b1
    [JsonPropertyName("cmd")]        public string? Cmd { get; set; } // Optional semantic command string (e.g., "ign_start", "retarder_up")

    public bool IsPressed(int bit)
    {
        if (bit < 0 || bit > 127) return false;
        uint mask = 1u << (bit & 31);
        return (bit >> 5) switch
        {
            0 => (B & mask) != 0,
            1 => (B1 & mask) != 0,
            2 => (B2 & mask) != 0,
            3 => (B3 & mask) != 0,
            _ => false
        };
    }

    /// <summary>
    /// Parses high-speed compact binary UDP packets.
    /// v2 packets are 33 bytes and carry b as a 128-bit bitmask (4 x uint32, network order).
    /// Legacy v1 packets are 21 bytes and carry only the low 32 bits.
    /// </summary>
    public static bool TryParseBinary(byte[] buffer, int length, out Packet packet)
    {
        packet = new Packet();
        if (buffer == null || length < 21) return false;
        // Support both "MB" (Mobicon) and legacy "RO"
        if (!((buffer[0] == 0x52 && buffer[1] == 0x4F) || (buffer[0] == 0x4D && buffer[1] == 0x42))) return false;

        try
        {
            using var ms = new MemoryStream(buffer, 0, length, false);
            using var br = new BinaryReader(ms);
            br.ReadByte(); // 'M' or 'R'
            br.ReadByte(); // 'B' or 'O'
            uint seq = ReadUInt32BigEndian(br);
            byte ctype = br.ReadByte();
            uint b0 = ReadUInt32BigEndian(br);
            uint b1 = 0;
            uint b2 = 0;
            uint b3 = 0;
            if (length >= 33)
            {
                b1 = ReadUInt32BigEndian(br);
                b2 = ReadUInt32BigEndian(br);
                b3 = ReadUInt32BigEndian(br);
            }
            short lx = ReadInt16BigEndian(br);
            short ly = ReadInt16BigEndian(br);
            short rx = ReadInt16BigEndian(br);
            short ry = ReadInt16BigEndian(br);
            byte lt = br.ReadByte();
            byte rt = br.ReadByte();

            int slot = (ctype >> 4) & 0x0F;
            int kind = ctype & 0x0F;
            packet.V = 1;
            if (slot < 4) packet.P = slot;
            packet.ControllerType = Enum.IsDefined(typeof(ControllerKind), kind) ? (ControllerKind)kind : ControllerKind.Gamepad;
            packet.Lx = Math.Clamp(lx / 32767f, -1f, 1f);
            packet.Ly = Math.Clamp(ly / 32767f, -1f, 1f);
            packet.Rx = Math.Clamp(rx / 32767f, -1f, 1f);
            packet.Ry = Math.Clamp(ry / 32767f, -1f, 1f);
            packet.Lt = Math.Clamp(lt / 255f, 0f, 1f);
            packet.Rt = Math.Clamp(rt / 255f, 0f, 1f);
            packet.B = b0;
            packet.B1 = b1;
            packet.B2 = b2;
            packet.B3 = b3;
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static uint ReadUInt32BigEndian(BinaryReader br)
    {
        var bytes = br.ReadBytes(4);
        if (bytes.Length != 4) throw new EndOfStreamException();
        return ((uint)bytes[0] << 24) | ((uint)bytes[1] << 16) | ((uint)bytes[2] << 8) | bytes[3];
    }

    private static short ReadInt16BigEndian(BinaryReader br)
    {
        var bytes = br.ReadBytes(2);
        if (bytes.Length != 2) throw new EndOfStreamException();
        return unchecked((short)((bytes[0] << 8) | bytes[1]));
    }
}

public interface IPadPool : IDisposable
{
    string Name { get; }
    int MaxSlots { get; }
    void Apply(int slot, Packet p);
    void Reset(int slot);
    void ResetAll();
    event Action<int, byte, byte, byte>? FeedbackReceived; // (slot, largeMotor, smallMotor, led)
}

/// <summary>
/// Manages up to 4 virtual Xbox 360 controllers with Force Feedback / Rumble support.
/// </summary>
public sealed class ViGEmPadPool : IPadPool
{
    private static readonly (int Bit, Xbox360Button Button)[] Map =
    {
        (0, Xbox360Button.A), (1, Xbox360Button.B), (2, Xbox360Button.X), (3, Xbox360Button.Y),
        (4, Xbox360Button.LeftShoulder), (5, Xbox360Button.RightShoulder),
        (6, Xbox360Button.Back), (7, Xbox360Button.Start),
        (8, Xbox360Button.LeftThumb), (9, Xbox360Button.RightThumb),
        (10, Xbox360Button.Up), (11, Xbox360Button.Down), (12, Xbox360Button.Left), (13, Xbox360Button.Right),
        (14, Xbox360Button.Guide),
    };

    private static readonly (uint Mask, Xbox360Button Button)[] SteeringMap =
    {
        (ButtonBits.Horn, Xbox360Button.RightThumb),
        (ButtonBits.Wipers, Xbox360Button.Back),
        (ButtonBits.HighBeams, Xbox360Button.LeftThumb),
        (ButtonBits.Hazard, Xbox360Button.Start),
        (ButtonBits.Handbrake, Xbox360Button.A),
        (ButtonBits.ShiftUp, Xbox360Button.RightShoulder),
        (ButtonBits.ShiftDown, Xbox360Button.LeftShoulder),
        (ButtonBits.Gear1, Xbox360Button.Up),
        (ButtonBits.Gear2, Xbox360Button.Right),
        (ButtonBits.Gear3, Xbox360Button.Down),
        (ButtonBits.Gear4, Xbox360Button.Left),
        (ButtonBits.Gear5, Xbox360Button.X),
        (ButtonBits.Gear6, Xbox360Button.Y),
        (ButtonBits.GearReverse, Xbox360Button.B),
        (ButtonBits.DiffLock, Xbox360Button.LeftThumb),
        (ButtonBits.TrailerAttach, Xbox360Button.RightThumb),
        (ButtonBits.CruiseControl, Xbox360Button.Start)
    };

    private readonly ViGEmClient _client;
    private readonly IXbox360Controller[] _pads = new IXbox360Controller[4];
    public string Name => "ViGEm Xbox 360 Multi-Pad Pool (4 Slots)";
    public int MaxSlots => 4;

    public event Action<int, byte, byte, byte>? FeedbackReceived;

    public ViGEmPadPool()
    {
        _client = new ViGEmClient(); // Throws if ViGEmBus driver is not installed
        for (int i = 0; i < 4; i++)
        {
            int slot = i;
            var pad = _client.CreateXbox360Controller();
            pad.AutoSubmitReport = false;
            pad.FeedbackReceived += (sender, e) =>
            {
                FeedbackReceived?.Invoke(slot, e.LargeMotor, e.SmallMotor, e.LedNumber);
            };
            pad.Connect();
            _pads[i] = pad;
        }
    }

    private static short Axis(float v) => (short)(Math.Clamp(v, -1f, 1f) * 32767);
    private static byte Trigger(float v) => (byte)(Math.Clamp(v, 0f, 1f) * 255);

    public void Apply(int slot, Packet p)
    {
        if (slot < 0 || slot >= 4) return;
        var pad = _pads[slot];
        if (pad is null) return;

        pad.SetAxisValue(Xbox360Axis.LeftThumbX, Axis(p.Lx));
        pad.SetAxisValue(Xbox360Axis.LeftThumbY, Axis(p.Ly));
        pad.SetAxisValue(Xbox360Axis.RightThumbX, Axis(p.Rx));
        float rightY = p.ControllerType == ControllerKind.SteeringWheel
            ? Math.Clamp(p.Clutch, 0f, 1f)
            : p.Ry;
        pad.SetAxisValue(Xbox360Axis.RightThumbY, Axis(rightY));
        pad.SetSliderValue(Xbox360Slider.LeftTrigger, Trigger(p.Lt));
        pad.SetSliderValue(Xbox360Slider.RightTrigger, Trigger(p.Rt));

        uint buttons = p.B;

        if (p.ControllerType == ControllerKind.SteeringWheel)
        {
            if (p.Gear == 1) buttons |= ButtonBits.Gear1;
            else if (p.Gear == 2) buttons |= ButtonBits.Gear2;
            else if (p.Gear == 3) buttons |= ButtonBits.Gear3;
            else if (p.Gear == 4) buttons |= ButtonBits.Gear4;
            else if (p.Gear == 5) buttons |= ButtonBits.Gear5;
            else if (p.Gear == 6) buttons |= ButtonBits.Gear6;
            else if (p.Gear < 0) buttons |= ButtonBits.GearReverse;
        }

        foreach (var (bit, button) in Map)
        {
            pad.SetButtonState(button, (buttons & (1u << bit)) != 0);
        }
        foreach (var (mask, button) in SteeringMap)
        {
            if ((buttons & mask) != 0) pad.SetButtonState(button, true);
        }

        pad.SubmitReport();
    }

    public void Reset(int slot)
    {
        if (slot < 0 || slot >= 4) return;
        Apply(slot, new Packet { V = 1 });
    }

    public void ResetAll()
    {
        for (int i = 0; i < 4; i++)
        {
            Reset(i);
        }
    }

    public void Dispose()
    {
        for (int i = 0; i < 4; i++)
        {
            try
            {
                _pads[i]?.Disconnect();
            }
            catch { }
        }
        _client.Dispose();
    }
}

public enum BackendMode
{
    Auto,
    ViGEm,
    Dry
}

/// <summary>
/// Routes controller interfaces through ViGEm and truck/button boxes through keyboard simulation.
/// </summary>
public sealed class CompositePadPool : IPadPool
{
    private readonly IPadPool? _vigem;
    private readonly IPadPool _keyboard;

    public string Name => $"auto ({_vigem?.Name ?? "ViGEm unavailable"} + {_keyboard.Name})";
    public int MaxSlots => 4;
    public event Action<int, byte, byte, byte>? FeedbackReceived;

    public CompositePadPool(IPadPool? vigem, IPadPool keyboard)
    {
        _vigem = vigem;
        _keyboard = keyboard;
        if (_vigem != null) _vigem.FeedbackReceived += RelayFeedback;
    }

    public void Apply(int slot, Packet p)
    {
        if (p.ControllerType == ControllerKind.ButtonBox)
        {
            _keyboard.Apply(slot, p);
            return;
        }

        _vigem?.Apply(slot, p);
    }

    public void Reset(int slot)
    {
        _vigem?.Reset(slot);
        _keyboard.Reset(slot);
    }

    public void ResetAll()
    {
        _vigem?.ResetAll();
        _keyboard.ResetAll();
    }

    public void Dispose()
    {
        _vigem?.Dispose();
        _keyboard.Dispose();
    }

    private void RelayFeedback(int slot, byte large, byte small, byte led) => FeedbackReceived?.Invoke(slot, large, small, led);
}

public sealed class KeyboardButtonBoxPool : IPadPool
{
    private readonly uint[][] _pressed = Enumerable.Range(0, 4).Select(_ => new uint[4]).ToArray();
    private static readonly KeyBinding[] Bindings = BuildBindings();

    public string Name => "Windows keyboard simulation for truck/button boxes";
    public int MaxSlots => 4;

#pragma warning disable CS0067
    public event Action<int, byte, byte, byte>? FeedbackReceived;
#pragma warning restore CS0067

    public void Apply(int slot, Packet p)
    {
        if (slot < 0 || slot >= _pressed.Length) return;

        uint[] next = { p.B, p.B1, p.B2, p.B3 };
        for (int word = 0; word < 4; word++)
        {
            uint changed = _pressed[slot][word] ^ next[word];
            if (changed == 0) continue;

            for (int bit = 0; bit < 32; bit++)
            {
                uint mask = 1u << bit;
                if ((changed & mask) == 0) continue;
                int globalBit = (word * 32) + bit;
                bool down = (next[word] & mask) != 0;
                KeyboardInput.Send(Bindings[globalBit], down);
            }

            _pressed[slot][word] = next[word];
        }
    }

    public void Reset(int slot)
    {
        if (slot < 0 || slot >= _pressed.Length) return;

        for (int word = 0; word < 4; word++)
        {
            uint pressed = _pressed[slot][word];
            if (pressed == 0) continue;

            for (int bit = 0; bit < 32; bit++)
            {
                uint mask = 1u << bit;
                if ((pressed & mask) == 0) continue;
                KeyboardInput.Send(Bindings[(word * 32) + bit], false);
            }

            _pressed[slot][word] = 0;
        }
    }

    public void ResetAll()
    {
        for (int i = 0; i < _pressed.Length; i++) Reset(i);
    }

    public void Dispose() => ResetAll();

    private static KeyBinding[] BuildBindings()
    {
        var bindings = new KeyBinding[128];

        // ETS2/ATS-friendly defaults for the built-in truckbox bit range.
        bindings[0] = KeyBinding.Plain(Vk.F4);
        bindings[1] = KeyBinding.Plain(Vk.F3);
        bindings[2] = KeyBinding.Plain(Vk.F2);
        bindings[3] = KeyBinding.Plain(Vk.R);
        bindings[4] = KeyBinding.Plain(Vk.Oem4);
        bindings[5] = KeyBinding.Plain(Vk.Oem6);
        bindings[6] = KeyBinding.Plain(Vk.I);
        bindings[7] = KeyBinding.Plain(Vk.Numpad1);
        bindings[8] = KeyBinding.Plain(Vk.X);
        bindings[9] = KeyBinding.Plain(Vk.C);
        bindings[10] = KeyBinding.Plain(Vk.OemMinus);
        bindings[11] = KeyBinding.Plain(Vk.OemPlus);
        bindings[12] = KeyBinding.Plain(Vk.OemComma);
        bindings[13] = KeyBinding.Plain(Vk.OemPeriod);
        bindings[14] = KeyBinding.Plain(Vk.E);
        bindings[15] = KeyBinding.Plain(Vk.H);
        bindings[16] = KeyBinding.Plain(Vk.P);
        bindings[17] = KeyBinding.Plain(Vk.K);
        bindings[18] = KeyBinding.Plain(Vk.F);
        bindings[19] = KeyBinding.Plain(Vk.Space);
        bindings[20] = KeyBinding.Plain(Vk.OemPeriod);
        bindings[21] = KeyBinding.Plain(Vk.OemComma);
        bindings[22] = KeyBinding.Plain(Vk.A);
        bindings[23] = KeyBinding.Plain(Vk.D);
        bindings[24] = KeyBinding.Plain(Vk.Z);
        bindings[25] = KeyBinding.Plain(Vk.LeftShift);
        bindings[26] = KeyBinding.Plain(Vk.O);
        bindings[27] = KeyBinding.Plain(Vk.B);
        bindings[28] = KeyBinding.Plain(Vk.U);
        bindings[29] = KeyBinding.Plain(Vk.V);
        bindings[30] = KeyBinding.Plain(Vk.T);
        bindings[31] = KeyBinding.Plain(Vk.C);

        ushort[] extendedKeys =
        {
            Vk.F1, Vk.F2, Vk.F3, Vk.F4, Vk.F5, Vk.F6, Vk.F7, Vk.F8,
            Vk.F9, Vk.F10, Vk.F11, Vk.F12, Vk.D1, Vk.D2, Vk.D3, Vk.D4,
            Vk.D5, Vk.D6, Vk.D7, Vk.D8, Vk.D9, Vk.D0, Vk.A, Vk.B,
            Vk.C, Vk.D, Vk.E, Vk.F, Vk.G, Vk.H, Vk.I, Vk.J
        };

        FillChordGroup(bindings, 32, KeyModifier.Control, extendedKeys);
        FillChordGroup(bindings, 64, KeyModifier.Alt, extendedKeys);
        FillChordGroup(bindings, 96, KeyModifier.Control | KeyModifier.Alt, extendedKeys);

        return bindings;
    }

    private static void FillChordGroup(KeyBinding[] bindings, int offset, KeyModifier modifier, ushort[] keys)
    {
        for (int i = 0; i < keys.Length; i++)
        {
            bindings[offset + i] = new KeyBinding(keys[i], modifier);
        }
    }
}

[Flags]
internal enum KeyModifier
{
    None = 0,
    Control = 1,
    Alt = 2
}

internal readonly record struct KeyBinding(ushort Key, KeyModifier Modifiers)
{
    public static KeyBinding Plain(ushort key) => new(key, KeyModifier.None);
}

internal static class KeyboardInput
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventFKeyUp = 0x0002;

    public static void Send(KeyBinding binding, bool down)
    {
        if (!OperatingSystem.IsWindows()) return;

        Span<Input> inputs = stackalloc Input[3];
        int count = 0;

        if (binding.Modifiers.HasFlag(KeyModifier.Control)) inputs[count++] = Key(Vk.Control, down);
        if (binding.Modifiers.HasFlag(KeyModifier.Alt)) inputs[count++] = Key(Vk.Menu, down);
        inputs[count++] = Key(binding.Key, down);

        if (!down)
        {
            inputs.Slice(0, count).Reverse();
        }

        _ = SendInput((uint)count, ref inputs[0], Marshal.SizeOf<Input>());
    }

    private static Input Key(ushort virtualKey, bool down) => new()
    {
        type = InputKeyboard,
        union = new InputUnion
        {
            ki = new KeyboardInputData
            {
                wVk = virtualKey,
                dwFlags = down ? 0u : KeyEventFKeyUp
            }
        }
    };

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, ref Input pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint type;
        public InputUnion union;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public KeyboardInputData ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInputData
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }
}

internal static class Vk
{
    public const ushort Back = 0x08;
    public const ushort Tab = 0x09;
    public const ushort Space = 0x20;
    public const ushort D0 = 0x30;
    public const ushort D1 = 0x31;
    public const ushort D2 = 0x32;
    public const ushort D3 = 0x33;
    public const ushort D4 = 0x34;
    public const ushort D5 = 0x35;
    public const ushort D6 = 0x36;
    public const ushort D7 = 0x37;
    public const ushort D8 = 0x38;
    public const ushort D9 = 0x39;
    public const ushort A = 0x41;
    public const ushort B = 0x42;
    public const ushort C = 0x43;
    public const ushort D = 0x44;
    public const ushort E = 0x45;
    public const ushort F = 0x46;
    public const ushort G = 0x47;
    public const ushort H = 0x48;
    public const ushort I = 0x49;
    public const ushort J = 0x4A;
    public const ushort K = 0x4B;
    public const ushort L = 0x4C;
    public const ushort N = 0x4E;
    public const ushort O = 0x4F;
    public const ushort P = 0x50;
    public const ushort R = 0x52;
    public const ushort T = 0x54;
    public const ushort U = 0x55;
    public const ushort V = 0x56;
    public const ushort X = 0x58;
    public const ushort Z = 0x5A;
    public const ushort Numpad1 = 0x61;
    public const ushort F1 = 0x70;
    public const ushort F2 = 0x71;
    public const ushort F3 = 0x72;
    public const ushort F4 = 0x73;
    public const ushort F5 = 0x74;
    public const ushort F6 = 0x75;
    public const ushort F7 = 0x76;
    public const ushort F8 = 0x77;
    public const ushort F9 = 0x78;
    public const ushort F10 = 0x79;
    public const ushort F11 = 0x7A;
    public const ushort F12 = 0x7B;
    public const ushort LeftShift = 0xA0;
    public const ushort Control = 0x11;
    public const ushort Menu = 0x12;
    public const ushort OemPlus = 0xBB;
    public const ushort OemComma = 0xBC;
    public const ushort OemMinus = 0xBD;
    public const ushort OemPeriod = 0xBE;
    public const ushort Oem4 = 0xDB;
    public const ushort Oem6 = 0xDD;
}

/// <summary>
/// Dry run pad pool for testing without ViGEm driver.
/// </summary>
public sealed class DryPadPool : IPadPool
{
    private readonly long[] _last = new long[4];
    public string Name => "dry-run (no virtual controllers created)";
    public int MaxSlots => 4;

#pragma warning disable CS0067
    public event Action<int, byte, byte, byte>? FeedbackReceived;
#pragma warning restore CS0067

    public void Apply(int slot, Packet p)
    {
        if (slot < 0 || slot >= 4) return;
        long now = Environment.TickCount64;
        if (now - _last[slot] < 150) return; // ~7 Hz
        _last[slot] = now;
        Console.WriteLine($"[mobicon Slot P{slot + 1}] kind={p.ControllerType} steer={p.Lx,6:0.00} brake={p.Lt,4:0.00} gas={p.Rt,4:0.00} clutch={p.Clutch,4:0.00} gear={p.Gear,2} btn=0x{p.B3:X8}{p.B2:X8}{p.B1:X8}{p.B:X8}");
    }

    public void Reset(int slot) { }
    public void ResetAll() { }
    public void Dispose() { }
}
