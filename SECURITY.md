# Security Policy

## What VeloGappie does

VeloGappie communicates **directly with your bike over Bluetooth Low Energy (BLE)**.
All data stays on your phone. There is no account, no login, no cloud dependency,
and no telemetry.

## What VeloGappie does NOT do

- **No internet traffic** — except the optional Google Drive backup (user-initiated)
  and the optional update checker (checks GitHub Releases)
- **No tracking** — your location is used only for sunset calculation (auto-lights)
  and GPS ride logging, both on-device only
- **No data collection** — ride history, settings, and preferences are stored locally
  in the app's private storage and never transmitted
- **No third-party SDKs** — no analytics, no crash reporting, no ads

## Network traffic summary

| Feature | Destination | When |
|---------|------------|------|
| Weather on display | Open-Meteo API | Every 15 min while enabled |
| Google Drive backup | Google Drive API | When user taps "Sync" |
| Update checker | GitHub Releases API | On app open (if enabled) |
| Map tiles | CARTO (OpenStreetMap) | When viewing ride routes |

All other functionality is local BLE communication with no network access.

## Reporting a vulnerability

If you find a security issue, please report it responsibly:

1. **Email:** Open a [security advisory](../../security/advisories/new) on this repository
2. **Do not** open a public issue for security vulnerabilities
3. Allow reasonable time for a fix before public disclosure

## Supported versions

Only the latest release is supported. Update to the newest version from the
[Releases page](../../releases/latest).
