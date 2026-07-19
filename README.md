# OpenRide

An independent, subscription-free workout app for the Peloton Bike (Gen 2), sideloaded onto the bike's stock Android 11 tablet via [OpenPelo](https://github.com/doudar/Openpelo) — no root, no bootloader unlock.

OpenRide replaces the stock launcher with a Peloton-style front-end providing:

- **Live ride metrics** — cadence, resistance, power, speed — read from the tablet's internal system service (technique per [grupetto](https://github.com/selalipop/grupetto)), independent of any Peloton subscription
- **Multi-user profiles** with per-rider workout history (per-second time-series recording)
- **Dynamically curated free classes** — auto-updating rows from configured YouTube channels via their RSS feeds
- **Apple Health export path** via FIT/TCX file bridge (Phase 2)

See [docs/PRD.md](docs/PRD.md) for the full product spec, and the repo issues for the Phase 1 engineering breakdown.

## Status

Phase 1 (v1 / P0 scope) — in development.

## Architecture notes

- Kotlin + Jetpack Compose, single-activity, `minSdk 30` (the tablet's Android 11), package `dev.digitalducktape.openride`
- Sensor access sits behind a `BikeDataSource` abstraction with a `MockBikeDataSource` (simulated ride) so all UI/logic development runs in a standard emulator; only the real system-service binding needs the physical bike
- Room database: `Profile` / `Ride` / `RideSample` (per-second samples — required for later FIT/TCX export)
- Content browser fetches per-channel YouTube RSS (`/feeds/videos.xml?channel_id=…`) on-device: no API key, no quota, no backend

## Personal project

Built for one household's bike. Not affiliated with Peloton. No Peloton assets, content, or branding are used; UI similarity is limited to layout and interaction patterns.
