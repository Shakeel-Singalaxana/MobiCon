# 🎮 mobicon: Turn Your Phone Into a PC Game Controller

A free, open-source virtual gamepad for Windows. Run the receiver on your PC, scan a QR code with your phone, and play. No app install needed.

Inspired by [ROJX](https://rojx.io). I didn't own a controller, and the best phone-controller app I found was paid, so I built a free version.

[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Mobile%20Web-06B6D4)](#)
[![Framework](https://img.shields.io/badge/.NET%208-ASP.NET%20%2B%20ViGEmBus-10B981)](#)
[![Status](https://img.shields.io/badge/Status-Early%20Development-F59E0B)](#project-status)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 🚦 Project Status

| Component | Status |
| --------- | ------ |
| Windows receiver, console + web (`receiver/`) | ✅ Working, partially complete |
| Mobile web client (touch controls, QR connect) | ✅ Working |
| Gamepad, Racing Cockpit and Truck Button Box layouts | ✅ Working (see [Known Limitations](#-known-limitations)) |
| Gyro / tilt steering | 🚧 UI included, **not working reliably yet** |
| Native Android app (`android/`, Kotlin + Jetpack Compose) | 🚧 Work in progress, source included but **not released** |

> This is an early-stage hobby project. Expect rough edges, and bug reports are welcome.

---

## ✨ Features

- **Virtual controller**: games see a real Xbox-style gamepad via the [Nefarius ViGEmBus](https://github.com/nefarius/ViGEmBus) driver, with a PlayStation-style controller type and wheel axes also supported by the protocol.
- **Keyboard simulation for truck boxes**: truck button-box buttons are sent as Windows key presses, so no extra driver is needed for them.
- **Multiple players**: the protocol carries a player slot.
- **Zero-install**: the receiver shows a QR code, you scan it, and the controller opens in your phone's browser (Chrome or Safari).
- **Multiple layouts**: standard gamepad, racing cockpit, truck button box and arcade.
- **Configurable steering**: adjustable lock range (180° to 1080°), smoothing and response curves.
- **Open protocol**: a compact 21-byte binary format over UDP, plus JSON over WebSocket for the web client (see [Protocol](#-protocol-specification)).

---

## ⚡ Latency: What to Expect

Latency depends heavily on your network. Typical numbers over a home Wi-Fi network:

| Client | Typical end-to-end latency |
| ------ | -------------------------- |
| Mobile web client (WebSocket) | roughly 15 to 40 ms |
| Native Android app (UDP), planned | roughly 8 to 20 ms (target, not yet measured) |

These are estimates, not benchmarks. For best results:

- Use **5 GHz Wi-Fi** and put your PC on **Ethernet** if possible.
- Keep your phone and PC on the same network, close to the router.
- Avoid congested 2.4 GHz networks.

**Good for:** racing and truck sims, platformers, adventure, casual and party games, most emulators.
**Not ideal for:** competitive shooters, fighting games or rhythm games that need frame-precise timing. A touchscreen also has no tactile feedback.

---

## 🖥️ Setup (Windows Receiver)

### Prerequisites

1. **Windows 10 or 11**
2. **ViGEmBus driver**: get it from the [official releases](https://github.com/nefarius/ViGEmBus/releases) if you want to install it yourself. The receiver also checks for ViGEmBus at startup and, if it is missing, downloads the latest installer and opens it with elevation. Pass `--no-driver-install` to turn that off. Restart your PC if the installer asks.

### Option A: Download the release (easiest)

1. Go to the [**Releases**](../../releases) page and download the latest Windows build (currently the **v0.5 pre-release**).
2. Extract it and run the receiver.
3. A QR code will appear in the receiver's terminal window.

### Option B: Build from source

Requires the **[.NET 8 SDK](https://dotnet.microsoft.com/download)** or higher.

```
cd receiver
dotnet run
```

Useful options:

```
dotnet run -- --backend auto       # default: ViGEm for controllers/wheels, keyboard simulation for truck boxes
dotnet run -- --backend vigem      # force ViGEm only
dotnet run -- --dry                # no virtual devices (for testing)
dotnet run -- --no-driver-install  # check drivers but don't launch installers
```

> The receiver is partially complete. A WPF GUI dashboard is planned but is not in the repo yet.

### Allow it through Windows Firewall (run once in Admin PowerShell)

```powershell
New-NetFirewallRule -DisplayName "mobicon Receiver (UDP 7777)" -Direction Inbound -LocalPort 7777 -Protocol UDP -Action Allow
New-NetFirewallRule -DisplayName "mobicon Web (TCP 8080)" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
```

---

## 📱 Connecting Your Phone

1. Make sure your phone and PC are on the **same Wi-Fi network**.
2. Scan the **QR code** shown in the receiver's terminal window, or open `http://<PC_IP>:8080` in your phone's browser.
3. Pick a layout and start playing.

**Tip:** if you can't connect, check the firewall rules above and make sure your router doesn't have "client isolation" (AP isolation) turned on.

### Native Android app 🚧

A native Android client (Kotlin + Jetpack Compose) is partially built and its source is in the `android/` folder, aiming for UDP transport and auto-discovery. It is **not finished and no APK is released yet**, so use the mobile web client for now. You can open `android/` in Android Studio if you want to follow along.

---

## ⚠️ Known Limitations

- **Gyro / tilt steering does not work reliably yet.** The UI is present but treat it as unfinished.
- The native Android app is unfinished and has no release yet.
- Windows only (it depends on the ViGEmBus driver).
- Touchscreen controls have no tactile feedback.
- Wi-Fi jitter can cause occasional input spikes, which a wired connection to the PC helps with.

---

## 🗺️ Roadmap

- [ ] Fix and stabilize gyro steering
- [ ] Add a WPF GUI dashboard
- [ ] Finish and publish the native Android client (UDP, auto-discovery)
- [ ] Measure and publish real latency benchmarks
- [ ] Custom layout editor
- [ ] Haptic feedback
- [ ] Demo video and screenshots

---

## 📡 Protocol Specification

- **UDP port:** `7777` (native datagrams and auto-discovery)
- **Web / WebSocket port:** `8080` (mobile web client, WebSocket at `/ws`)
- **Update rate:** 60 to 120 Hz (configurable)
- **Binary payload:** 33 bytes (128-bit button mask). The receiver still accepts the legacy 21-byte packet.
- **JSON payload (web client):**
  `{"v":1,"ct":int,"lx":float,"ly":float,"rx":float,"ry":float,"lt":float,"rt":float,"b":uint,"b1":uint,"b2":uint,"b3":uint}`

```
Offset  | Type    | Field           | Description
--------|---------|-----------------|---------------------------------------------------------
0x00    | byte[2] | Header          | 0x52, 0x4F ("RO")
0x02    | uint32  | SequenceNumber  | Monotonically increasing sequence ID
0x06    | byte    | SlotAndType     | Upper nibble: player slot. Lower nibble: 0x01 Xbox, 0x02 PlayStation, 0x03 Wheel, 0x04 Button Box
0x07    | uint32  | ButtonsBitmask0 | Digital buttons, bits 0..31
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

### Button bitmask (bits 0 to 31)

- `Bit 0`: **A** | `Bit 1`: **B** | `Bit 2`: **X** | `Bit 3`: **Y**
- `Bit 4`: **LB (Shift -)** | `Bit 5`: **RB (Shift +)**
- `Bit 6`: **Back / View** | `Bit 7`: **Start / Menu** | `Bit 8`: **LS (L3)** | `Bit 9`: **RS (R3)**
- `Bit 10-13`: **D-Pad (Up, Down, Left, Right)** | `Bit 14`: **Xbox Guide**
- `Bit 15`: **Ignition** | `Bit 16`: **Headlights (Low Beam)**
- `Bit 17`: **Hazard Lights** | `Bit 18`: **Horn** | `Bit 19`: **Handbrake** (mapped to A)
- `Bit 20`: **Shift Up** (mapped to RB) | `Bit 21`: **Shift Down** (mapped to LB) | `Bit 22`: **Wipers**
- `Bit 23`: **Lift Axle** | `Bit 24`: **Differential Lock** | `Bit 25`: **Cruise Control**
- `Bit 26`: **Beacon / Strobe** | `Bit 27`: **High Beam** | `Bit 28`: **Signal Left** | `Bit 29`: **Signal Right**
- `Bit 30`: **Camera View** | `Bit 31`: **Nitro / Boost**

---

## 🤝 Contributing

Issues and pull requests are welcome, especially for gyro support, the Android client and latency testing. If you try it with a game, please report whether it works.

## 🙏 Credits

- Inspired by [ROJX](https://rojx.io)
- [Nefarius ViGEmBus](https://github.com/nefarius/ViGEmBus) for virtual gamepad emulation

## 📄 License

[MIT](LICENSE)
