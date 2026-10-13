# mobicon System — Ultra-Low-Latency Virtual Gamepad & 900° Steering Hub

An open-source, sub-8ms low-latency mobile simulator controller and Windows virtual Xbox 360 gamepad receiver mimicking **mobicon.io**.

![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android%20%7C%20Web-06B6D4)
![Framework](https://img.shields.io/badge/.NET%208-WPF%20%2B%20ASP.NET%20%2B%20ViGEmBus%20%2B%20Keyboard%20Input-10B981)
![Mobile](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-EF4444)
![Protocol](https://img.shields.io/badge/Protocol-Dual%20Binary%20(33B)%20%2B%20JSON%20WebSocket-F59E0B)

---

## 🏎️ Features Overview

| Feature | Windows Desktop Receiver (`C# / WPF & CLI / .NET 8`) | Android Mobile Client (`Kotlin / Jetpack Compose`) | Mobile Web Client (`HTML5 / WebSockets / JS`) |
| :--- | :--- | :--- | :--- |
| **Input Emulation** | `ViGEmBus` for Xbox/PlayStation-style controllers and steering controls, plus Windows keyboard simulation for truck boxes | Multi-touch analog pedals & virtual dual thumbsticks | Touch D-Pad, Thumbsticks, Sliding Triggers & Gyro |
| **Sensor Engine** | EMA smoothing filter & dynamic deadzone calculations | Low-latency Gyroscope & Accelerometer sensor fusion | DeviceOrientation API browser sensor support |
| **Steering Range** | 180°, 360°, 540°, 900°, 1080° lock with S-Curve/Gamma curves | 900° visual rotating canvas wheel with RPM shift LEDs | PS2 DualShock visual chassis |
| **Simulator Modes** | Racing Cockpit, Heavy Trucking Box, Arcade, Standard Gamepad | Dedicated Cockpit, Truck Button Box & Gamepad tabs | DualStick Gamepad & Cockpit mode |
| **Networking** | Asynchronous UDP (7777) + Kestrel Web (8080) + Auto-Discovery | Zero-allocation UDP transmitter (60Hz to 120Hz) | Real-time WebSocket connection to `/ws` |
| **Zero-Install** | Scannable QR code generated in Terminal & WPF GUI | Instant Auto-Scan & Connect | Point mobile camera at monitor QR code to play! |

---

## 📡 Protocol Specification

* **UDP Port**: `7777` (Low-Latency Native Datagrams & Auto-Discovery)
* **Web / WebSocket Port**: `8080` (Mobile Web Browser & WebSocket `/ws`)
* **Frequency**: `60 Hz` to `120 Hz` (Configurable)
* **Binary Payload Length**: `33 Bytes` for 128-bit buttons, with backward-compatible `21 Byte` legacy receive support.
* **JSON Payload**: `{"v":1,"ct":int,"lx":float,"ly":float,"rx":float,"ry":float,"lt":float,"rt":float,"b":uint,"b1":uint,"b2":uint,"b3":uint}`

```
Offset  | Type    | Field           | Description
--------|---------|-----------------|---------------------------------------------------------
0x00    | byte[2] | Header          | 0x52, 0x4F ("RO")
0x02    | uint32  | SequenceNumber  | Monotonically increasing sequence ID
0x06    | byte    | SlotAndType     | upper nibble slot, lower nibble: 0x01 Xbox, 0x02 PlayStation, 0x03 Wheel, 0x04 Button Box
0x07    | uint32  | ButtonsBitmask0 | 128-bit digital button states, bits 0..31
0x0B    | uint32  | ButtonsBitmask1 | bits 32..63
0x0F    | uint32  | ButtonsBitmask2 | bits 64..95
0x13    | uint32  | ButtonsBitmask3 | bits 96..127
0x17    | short   | AxisLeftX       | Steering angle / Left Stick X (-32768 to +32767)
0x19    | short   | AxisLeftY       | Left Stick Y (-32768 to +32767)
0x1B    | short   | AxisRightX      | Right Stick X (-32768 to +32767)
0x1D    | short   | AxisRightY      | Right Stick Y (-32768 to +32767)
0x1F    | byte    | TriggerLeft     | Analog Brake / Clutch (0 to 255)
0x20    | byte    | TriggerRight    | Analog Throttle / Gas (0 to 255)
```

### Digital Buttons Bitmask Mapping
- `Bit 0`: **A** | `Bit 1`: **B** | `Bit 2`: **X** | `Bit 3`: **Y**
- `Bit 4`: **LB (Left Bumper / Shift -)** | `Bit 5`: **RB (Right Bumper / Shift +)**
- `Bit 6`: **Back / View** | `Bit 7`: **Start / Menu** | `Bit 8`: **LS (L3)** | `Bit 9`: **RS (R3)**
- `Bit 10-13`: **D-Pad (Up, Down, Left, Right)** | `Bit 14`: **Xbox Guide**
- `Bit 15`: **Ignition (Engine Start/Stop)** | `Bit 16`: **Headlights (Low Beam)**
- `Bit 17`: **Hazard Lights** | `Bit 18`: **Horn** | `Bit 19`: **Handbrake (P)** (Mapped to A)
- `Bit 20`: **Shift Up** (Mapped to RB) | `Bit 21`: **Shift Down** (Mapped to LB) | `Bit 22`: **Wipers**
- `Bit 23`: **Lift Axle** | `Bit 24`: **Differential Lock** | `Bit 25`: **Cruise Control**
- `Bit 26`: **Beacon / Strobe** | `Bit 27`: **High Beam** | `Bit 28`: **Signal Left** | `Bit 29`: **Signal Right**
- `Bit 30`: **Camera View** | `Bit 31`: **Nitro / Boost**

---

## 🖥️ Part 1: Windows Receiver Setup

### Prerequisites
1. **.NET 8 SDK** or higher installed.
2. **Nefarius ViGEmBus Driver** for Xbox/PlayStation-style gamepads and wheel/controller axes. Included installer `ViGEmBus_1.22.0_x64_x86_arm64.exe` or download from official release:
   - 👉 [ViGEmBus Latest Releases](https://github.com/nefarius/ViGEmBus/releases)
The receiver checks ViGEmBus at startup. If it is missing, it downloads the latest GitHub release installer and opens it with elevation; pass `--no-driver-install` to disable that automatic setup flow. Truck boxes and custom button decks use Windows keyboard simulation and do not need a separate DirectInput driver.

### Option A: Launch GUI Dashboard (WPF Receiver)
```powershell
cd WindowsReceiver
dotnet run
```

Backend selection:
```powershell
dotnet run -- --backend auto   # default: ViGEm for controllers/wheels, keyboard simulation for truck boxes
dotnet run -- --backend vigem  # force ViGEm only
dotnet run -- --dry            # no virtual devices
dotnet run -- --no-driver-install  # check drivers but do not launch installers
```

Truckbox buttons are emitted as keyboard key down/up events from the receiver, while the same phone/player can drive an Xbox-style ViGEm pad for steering, pedals, shifters, gears, and handbrake.

### Option B: Launch Standalone Console / Web Receiver
```powershell
cd receiver
dotnet run
```

### Windows Firewall Rule (Run once in Admin PowerShell)
```powershell
New-NetFirewallRule -DisplayName "mobicon Receiver (UDP 7777 + HTTP 8080)" -Direction Inbound -LocalPort 7777,8080 -Protocol UDP -Action Allow; New-NetFirewallRule -DisplayName "mobicon Web (TCP 8080)" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

---

## 📱 Part 2: Mobile Client Connection

### Method 1: Zero-Install Mobile Browser (Easiest)
1. Ensure phone is on the **same Wi-Fi** as PC.
2. Point your phone camera at the **QR Code** displayed in the Receiver (WPF GUI or Terminal) or visit `http://<PC_IP>:8080`.
3. Play immediately inside Chrome or Safari!

### Method 2: Native Android App (io.mobicon.client)
1. Open `android/` in Android Studio or build APK with `.\gradlew.bat assembleDebug`.
2. Tap **Auto-Scan PC** (or enter PC IP and Port `7777`).
3. Tap **Center Wheel** in landscape position and race!
