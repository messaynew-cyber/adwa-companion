# ADWA Companion 👁️

A floating Android overlay — the ADWA eye lives on your home screen, wanders around, and shows live trading data.

## Features
- **Floating eye** overlay (like Messenger chat heads)
- **Animated** — breathes, blinks, pupil darts, wanders randomly
- **Market-aware** — glows green (SOL ▲), red (▼), gold (—)
- **Tap** for mini dashboard with SOL, equity, battery
- **Double-tap** to open Telegram
- **Drag** to reposition
- **Settings** to toggle overlay on/off

## Data Source
Pulls live status from: `http://129.80.112.9/adwa-status.json`
- Markets: SOL price & 24h change
- Portfolio: Equity & cash
- System: Battery & temperature
- Swarm: Online status

## Build
```bash
flutter pub get
flutter build apk --release --split-per-abi
```

## GitHub Actions
Push to `main` → auto-builds APK → downloadable from Actions artifacts.
