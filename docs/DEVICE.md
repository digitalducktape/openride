# Target device

The physical Bike this project is verified against.

| Property | Value |
|---|---|
| Model (`ro.product.model`) | PLTN-RB1VQ |
| Device codename | RB1VQ |
| Android release | 11 |
| Firmware build (`ro.build.display.id`) | `RQ.250113.A` |

## Why this matters

Ticket #3 (real sensor binding via the internal system service) is only valid
against a known firmware. `RQ.250113.A` is the build the app is installed and
tested on. If an OTA changes this build string, the system-service interface may
change and #3 must be re-verified — which is why blocking OTA updates is part of
the pre-cancellation checklist (see PRD "Risks" and `docs/INSTALL.md`).

## Install verified

- `adb install -r` of the debug APK: **Success** on build `RQ.250113.A`
- App launches to the profile-select screen, renders correctly at 1920×1080 landscape
- Launcher alias (`.HomeLauncherAlias`) remains **disabled** — installing did not alter stock HOME behavior
