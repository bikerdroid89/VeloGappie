[🇳🇱 Nederlands](README.md) | [🇩🇪 Deutsch](README.de.md) | [🇬🇧 English](README.en.md)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Platform: Android 8+](https://img.shields.io/badge/Android-8.0%2B-green.svg)]()
[![Wear OS 3+](https://img.shields.io/badge/Wear%20OS-3%2B-teal.svg)]()

# VeloGappie

Independent Android app for Veloretti V2 e-bikes.
No account needed — connects directly to your bike over Bluetooth.

## Download

Pre-built APKs are available on the [Releases page](../../releases/latest).
No Play Store account needed — just download and install.

## What it is

VeloGappie talks directly to your bike over Bluetooth — no Veloretti account, no cloud
dependency. Your ride data stays on your phone. Optional features like weather on the
bike display and Google Drive backup use the network, but nothing goes to Veloretti's
servers.

## What it is not

VeloGappie is not an official Veloretti product and not a replacement for the official app.
There is no guarantee that everything works correctly. Use at your own risk.

## Screenshots

| Dashboard | Ride history | Settings | Information |
|-----------|--------------|----------|-------------|
| ![Dashboard](docs/assets/screenshot-dashboard.png) | ![Ride history](docs/assets/screenshot-ridehistory.png) | ![Settings](docs/assets/screenshot-settings.png) | ![Information](docs/assets/screenshot-info.png) |

| Wear OS |
|---------|
| ![Wear OS](docs/assets/wearos-screenshot.png) |

## Why VeloGappie?

| | Official Veloretti app | VeloGappie |
|---|---|---|
| Account required | Yes | No |
| Veloretti cloud required | Yes | No |
| Ride history with GPS | No | Yes |
| Wear OS support | No | Yes |
| Health Connect | No | Yes |
| Navigation to bike display | Limited | Google Maps, Komoot, Waze |
| Open source | No | Yes (GPL-3.0) |

## Features

### Controls
- **Motor assist** level 0–5 (including Superhero mode)
- **Lights** — manual on/off, or automatic at sunset (computed on-device from GPS)
- **Digital bell** (default or ping sound)
- **Cadence calibration** — exact 1 rpm steps via slider

### Enviolo hub
- Eco / Comfort / Sport ride modes
- Set start multiplier (0.00–2.55x)
- Hub diagnostics — serial number, article number, cadence range, hub odometer

### Bike display
- **Clock** — time on the display in normal or large two-field format
- **Heart rate** — live BPM from the watch, or alternating with clock
- **Live weather** — temperature, precipitation and icon sent to the bike display
- **Navigation arrows** with distance in metres sent to the bike's display
- **Free text** — up to 10 characters on the display (e.g. street name)

### Data
- Battery percentage, health, charging status
- Speed, max speed, cadence
- Odometer and trip distance
- Firmware versions (display + experience module)

### Ride history
- Local storage of rides (distance, duration, average/max speed, heart rate, cadence)
- **GPS routes** — each ride's route is logged and displayed as a map in ride details
- **Elevation and battery** — elevation gain and battery usage (start → end) per ride
- **Ride groups** — bundle rides together (e.g. bike holiday, commute)
- Merge, delete, add/remove rides from groups
- **Health Connect** — automatically writes cycling sessions with direct link from ride details
- **Google Drive backup** — optional sync of settings and ride history

### Navigation bridge
- Forwards turn-by-turn directions from Google Maps, Komoot, Waze, OsmAnd, and more to the bike's display
- Automatic direction detection (left/right/straight/u-turn) and distance
- Multilingual support (NL/EN/DE)

### Launch control
- Long-press the handlebar button to max out motor assist and Enviolo ratio
- Automatically reverts once you reach top speed

### Wear OS companion
- Live speed, distance, duration, heart rate on your wrist
- Cadence control via the watch crown
- Double-tap to toggle lights
- Ambient always-on mode
- On-watch heart rate sensor

## Building

This is a standard Android Studio project (Kotlin + Jetpack Compose).

1. Open the `app/` folder in Android Studio
2. Let Gradle sync
3. Build & run on a phone with Android 8.0+ (API 26)
4. For the watch app: build the `:wear` module and install on a Wear OS 3+ device

Both modules share a pinned `debug.keystore` so the Wear OS Data Layer can pair them.
This is a standard Android debug key (password: `android`), not a secret.

**Requirements:** Android SDK 34, JDK 17, Gradle 8.7+

## Usage

1. Open the app and grant Bluetooth and location permissions
2. Tap **Scan for bikes** — your bike shows up as `V-<serial>`
3. Tap your bike to connect
4. The dashboard shows battery level, speed and controls immediately
5. Swipe to the other tabs for ride history, settings and technical info

Location permission is only used for the automatic lights feature (sunset calculation).
Your location is never sent anywhere.

## Compatibility

Intended for Veloretti V2 e-bikes that advertise over BLE as `V-<serial>`.
V1 bikes use a different protocol and are not supported.

## Contributing

Issues and pull requests are welcome. If you find a bug or want to add a feature,
please open an issue first so we can discuss it.

## License

GPL-3.0 — see [LICENSE](LICENSE).

## Disclaimer

This app is **not affiliated with or endorsed by Veloretti B.V.** Use is entirely at your
own risk. The developer is not liable for any damage to the bike, loss of warranty, or
software errors caused by Bluetooth commands.

"Veloretti" and "Ivy" are trademarks of Veloretti B.V. "Enviolo" is a trademark of
Enviolo. All brand names are the property of their respective owners.

## Font

Uses [Hanken Grotesk](https://fonts.google.com/specimen/Hanken+Grotesk)
(SIL Open Font License).
