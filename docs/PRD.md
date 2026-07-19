# PRD: Independent Workout App for Peloton Bike (Sideloaded via OpenPelo)

**Status:** Draft v5 — personal project, no deadline
**Author:** Ed Wolf
**Last updated:** 2026-07-19

---

## Problem Statement

The Peloton Bike's onboard tablet is intentionally locked to the Peloton app, which requires an active All-Access membership (~$44/mo) to unlock cadence/resistance/power metrics, workout history, and class content. Without the subscription, the bike's tablet — and its cadence/resistance sensors — are functionally inert, even though the hardware (screen, speakers, sensors, Android 11 OS) is fully capable on its own. This forces owners into an ongoing subscription just to see basic ride metrics on hardware they already own outright.

Using [OpenPelo](https://github.com/doudar/Openpelo) to sideload apps via ADB (no root/bootloader unlock required), we can install a custom Android app on the bike's tablet that restores core utility — live metrics, per-user workout logging, health-data export, and freely-sourced workout content — without any recurring membership fee. [Grupetto](https://github.com/selalipop/grupetto) demonstrates that sensor data (cadence, resistance, power, speed) is reachable via an undocumented Android system service on the tablet — no root, and reportedly independent of subscription status — which resolves the previous open question about whether metrics would even be accessible post-cancellation.

## Goals

1. Eliminate the recurring All-Access subscription while retaining live workout metrics (cadence, resistance, power, speed) during a ride, using the system-service access technique demonstrated by Grupetto.
2. Support multiple household users with distinct profiles, each with their own workout history — this is a first-class requirement, not an afterthought.
3. Provide a persistent local workout history per user, with an export path to Apple Health for users who want it there.
4. Allow browsing and playback of a **dynamically curated**, freely available cycling content list (auto-updating as the source YouTube channels post new videos) on the bike's screen during a ride.
5. Recreate the core Peloton Bike screens (home/profile select, ride/metrics, history) as closely as reasonably possible, so the experience feels native to the hardware rather than a bolted-on replacement.
6. Ship a v1 that is installable and usable end-to-end via OpenPelo sideloading on the tablet's stock Android 11, without root or bootloader unlock.
7. Keep the stock Peloton app/OS intact and reversible — the bike should remain restorable to stock at any time.

## Non-Goals

1. **Recreating Peloton's licensed class library or instructor content** — copyrighted, out of scope; the "recreate the UI" goal applies to screen layout/interaction design, not to reproducing Peloton's actual video content or brand assets.
2. **Social/leaderboard features (following riders, high-fives, global leaderboard)** — explicitly out of scope per requirements; there is no ambition to build multiplayer/social features even with multi-user support in place.
3. **Root/bootloader unlock or hardware modification** — both OpenPelo (sideloading) and Grupetto's system-service technique work without root; anything that would require root stays out of scope.
4. **iOS/other equipment support (Echelon, NordicTrack, etc.)** — scoped strictly to this one Peloton Bike.
5. **Downloading, scraping, or rehosting third-party video content** — curated content is browsed and played via YouTube's own app/player (intent or embedded IFrame player), never downloaded or mirrored, to stay within YouTube's ToS and avoid the licensing problem this project is explicitly trying to stay away from (see Non-Goal #1).
6. **A dedicated companion iOS app for automatic Apple Health sync (v1)** — genuinely useful, but a second platform's worth of engineering; v1 ships a file-export bridge instead (see P1-1). Revisit as P2 if manual export proves too tedious in practice.

## Reference Projects & Reuse Strategy

A survey of existing open-source projects. The critical distinction is **data source**: only on-device system-service projects give live, subscription-independent sensor data — which is the entire premise of this project. Everything that reads Peloton's *cloud* web API needs an account login and stops being useful once the membership lapses, so those are reference material for code patterns only, never a live data source here.

### On-device live sensors (this project's data path)

| Project | Relevance | License | How we use it |
|---|---|---|---|
| [grupetto](https://github.com/selalipop/grupetto) | **Foundation.** Binds the undocumented Bike **Gen 2** system service for live cadence/resistance/power/speed, no root, subscription-independent. Matches our exact hardware. | Confirm before copying code (see licensing note) | Primary reference / dependency for P0-2 sensor access. Build the front-end fresh on top of this technique. |
| [switchback](https://github.com/orbitalmutiny/switchback) | Similar idea but targets **Bike+** (not our Gen 2), and is an *overlay HUD* on top of Peloton's UI rather than a launcher replacement. Immature (1 star, single beta). | BSL 1.1 (non-commercial OK; → MIT in 2030) | **Not forking.** Cherry-pick *ideas* only: its GPX route-simulation + grade display is a nice P2 (see Future Considerations). |

### Peloton cloud web API (reference patterns only — NOT a data source)

| Project | Relevance | License | How we use it |
|---|---|---|---|
| [peloton-to-garmin](https://github.com/philosowaffle/peloton-to-garmin) | Mature FIT/TCX file generation + Peloton data model (C#/.NET). Pulls from Peloton cloud, not device. | GPL-3.0 | Reference for **FIT/TCX generation** feeding Apple Health export (P1-1). Reimplement from the approach, don't copy code, to avoid GPL obligations. |
| [homeassistant-peloton-sensor](https://github.com/edwork/homeassistant-peloton-sensor) | Shows the full set of fields Peloton's cloud exposes (via pylotoncycle). Historical/account data. | Apache-2.0 (permissive) | Reference for data-model field names / units. |
| [peloton-stats](https://github.com/tashapiro/peloton-stats) | Workout history stats & visualization ideas. | (check) | Inspiration for the history/summary screens (P0-5). |
| [Peloton-Data-to-Google-Sheets](https://github.com/tychaney/Peloton-Data-to-Google-Sheets) | Export-to-Sheets pattern. | (check) | Reference for CSV/export shape (P1-2). |
| [t1copilot](https://github.com/cykj40/t1copilot) | Companion/dashboard patterns. | (check) | Low-priority reference. |

### Cross-platform fitness bridge

| Project | Relevance | License | How we use it |
|---|---|---|---|
| [qdomyos-zwift](https://github.com/cagnulein/qdomyos-zwift) | Mature FTMS/BLE/ANT+ bridge (Qt/C++). Deep implementations of BLE HR pairing, power-from-HR/cadence estimation, Strava auto-upload. Not a fork base (Qt, not native Android). | GPL-3.0 | Reference for **BLE HR pairing** (P1-4), **power/output estimation** logic, and **Strava export** (P2). Reimplement, don't copy, re: GPL. |

### Utility

| Project | Relevance | How we use it |
|---|---|---|
| [Power-zone gist](https://gist.github.com/AJ-Acevedo/47d5eaf669f40811f66aaf25bf1ad57e) | Power-zone math: FTP = 95% of a 20-min avg output → 7 zones. Just a formula. | Directly usable as the power-zone calc behind ride goals / power display (P1-3). |

**Licensing note:** GPL-3.0 projects (qdomyos-zwift, peloton-to-garmin) require any project that *copies their code* to be released under GPL-3.0. For a personal project that's acceptable, but reimplementing from the documented approach rather than lifting source keeps the app unencumbered. Confirm grupetto's license before copying, since it's the closest dependency.

## User Stories

Primary persona: **a household member using the bike**, now explicitly plural (multi-user).

- As a rider, I want to select or create my profile when I approach the bike so that my metrics and history stay separate from other household members'.
- As a rider, I want to see live cadence, resistance, power, and speed while pedaling so that I know how hard I'm working without paying for All-Access.
- As a rider, I want a simple start/pause/stop workout timer so that I can track ride duration.
- As a rider, I want each completed ride saved to my profile's history (date, duration, avg/max cadence, power) so that I can see my own progress over time, separate from other riders.
- As a rider, I want to export a completed ride in a format I can bring into Apple Health so that it counts toward my broader fitness tracking, even though the bike itself is Android.
- As a rider, I want to browse a curated list of free scenic/instructor-led rides from a small set of trusted YouTube channels so that I have guided content without a subscription.
- As a rider, I want the home screen and ride screen to feel familiar if I've used a Peloton before, so that the transition away from the subscription doesn't feel like a downgrade in usability.
- As a rider, I want the app to launch automatically when the tablet powers on, straight to profile selection, so that I don't have to navigate Android's launcher.
- As a rider, if the cadence/resistance/power feed is unavailable, I want a clear on-screen message (not a silent blank dashboard) so that I know to check the connection rather than assume I'm reading a real zero.
- As a rider, I want to still reach stock Android settings (WiFi, volume) from within the app so that basic device maintenance doesn't require re-running OpenPelo.

## Requirements

### Must-Have (P0)

| # | Requirement | Acceptance Criteria |
|---|---|---|
| P0-1 | App installs via OpenPelo sideloading (ADB) on Android 11, no root/bootloader unlock | Given a stock Bike tablet with USB debugging enabled, when the app APK is pushed via OpenPelo, then it installs and launches without errors |
| P0-2 | Live cadence, resistance, power, and speed via the internal system service (Grupetto technique) | Given the app binds to the discovered system service and the flywheel is spinning, when cadence/resistance/power change, then displayed values update within 1 second |
| P0-3 | Multi-user profiles | Given the app is freshly launched, when it reaches the home screen, then the rider can select an existing profile or create a new one (name + avatar, plus optional weight and FTP — inputs for calorie estimates, power zones P1-3, and export accuracy P1-1), and all subsequent metrics/history are scoped to that profile until "switch rider" is used |
| P0-4 | Workout timer (start/pause/stop) | Given the rider taps "Start Ride," when the timer is running, then elapsed time displays and persists through pause/resume |
| P0-5 | Per-user local workout history with time-series recording | Given a ride is active, then the app records a **per-second time series** of cadence/resistance/power (not just aggregates) — an architectural requirement so FIT/TCX export (P1-1) and ride graphs are possible later without re-architecture or data loss. Given a ride is stopped, then a ride summary screen shows totals (duration, avg/max cadence, avg/max power, estimated output/calories), and on dismissal the ride is saved under the active profile and viewable in that profile's history list only |
| P0-6 | Dynamically curated free content browser + playback | Given the rider opens "Classes," when they browse, then each configured channel appears as a category row whose videos are pulled live from that channel's YouTube RSS feed (auto-updating as the channel posts), listed with thumbnail/title/duration, and selecting one plays it full-screen via YouTube's app/player with audio through the bike's speakers. Given a channel's feed is unreachable, then that row shows a cached/last-known list or a clear "couldn't refresh" state rather than disappearing |
| P0-7 | Peloton-style UI for home/profile-select, ride, and history screens | Given a rider familiar with the stock Peloton UI, when using this app's equivalent screens, then layout, navigation patterns, and information hierarchy are recognizably similar (not pixel-identical, and using this project's own visual assets, not Peloton's) |
| P0-8 | Auto-launch on boot | Given the tablet reboots, when Android finishes booting, then this app opens automatically instead of the stock Peloton launcher, landing on profile selection |
| P0-9 | Graceful sensor-failure state | Given the cadence/resistance/power data source is unreachable, when the rider opens the ride screen, then an explicit "Sensors not detected" message is shown instead of blank/zero values |
| P0-10 | Screen stays awake during a ride | Given a ride is active, when the rider doesn't touch the screen, then the display does not sleep or dim off until the ride is stopped (Android keep-screen-on flag), and normal display timeout resumes once the ride ends |

### Nice-to-Have (P1)

| # | Requirement | Acceptance Criteria |
|---|---|---|
| P1-1 | Apple Health export via file bridge | Given a completed ride, when the rider taps "Export," then a standard FIT or TCX file is generated and shareable (e.g. via Android share sheet to email/AirDrop-alternative/cloud drive) for import into Apple Health using an existing third-party bridge app on iPhone. FIT/TCX generation approach references peloton-to-garmin (reimplement, don't copy — GPL) |
| P1-2 | Export history as CSV | Given a history list, when the rider taps "Export," then a CSV file is written to shared storage |
| P1-3 | Basic ride goals + power zones (time or cadence/power target) | Given a rider sets a target, when riding, then progress toward that target is visible on-screen. Power-zone display uses the standard FTP-based 7-zone math (FTP = 95% of a 20-min avg output; per the power-zone gist) |
| P1-4 | Bluetooth heart-rate monitor pairing (per profile) | Given a BLE HR strap is in pairing mode, when selected in-app, then live BPM displays alongside cadence, and is remembered per profile. BLE pairing + power-from-HR/cadence estimation reference qdomyos-zwift (reimplement — GPL) |
| P1-5 | Quick-access to stock Android settings from within the app | Given the rider is in the app, when they tap a settings icon, then Android's WiFi/volume/display settings open without needing the stock launcher |
| P1-6 | Simple PIN or tap-to-confirm profile switching | Given multiple profiles exist, when switching riders mid-session, then a lightweight confirmation (not a full re-onboarding) prevents accidental logging under the wrong profile |
| P1-7 | One-time historical import from Peloton cloud | Given the All-Access subscription is **still active**, when the rider runs the import (in-app or a companion script) with their Peloton credentials, then past workouts (date, duration, avg/max cadence, output) are fetched via the Peloton web API and merged into the matching local profile's history. **Time-sensitive: only possible before cancellation** — the one legitimate live use of the cloud-API reference projects (pylotoncycle data model, peloton-to-garmin) |
| P1-8 | Local history backup & restore | Given ride history exists, when the rider taps "Back up," then the full database (all profiles, all rides incl. time series) exports to a single file shareable off-device; given a fresh install, when that file is imported, then all profiles and rides are restored. Protects against tablet failure/factory reset — the tablet is otherwise a single point of failure for years of data |
| P1-9 | Bluetooth headphone audio routing | Given BT headphones are paired via Android settings, when class content plays, then audio routes to the headphones instead of the bike's speakers |

### Future Considerations (P2)

- Companion iOS app for fully automatic Apple Health sync (removes the manual export step in P1-1).
- Sync history to Strava/Garmin Connect via their public APIs (Strava auto-upload pattern references qdomyos-zwift).
- GPX route simulation: import a GPX route and show position/grade/progress as you ride (concept borrowed from switchback; note switchback's *auto-resistance* control is Bike+-only and does not apply to our Gen 2).
- Serverless-cached curation (scheduled function pulls each channel via the YouTube Data API into a single hosted `content.json`): unlocks whole-catalog browse beyond RSS's ~15-video cap, editorial control (pin/hide/reorder), and keeps the API key off-device. Only worth it if RSS proves too limiting.
- Expand curated content beyond the initial 4 channels, possibly with per-profile favorites/history of watched videos.
- Companion phone app (Android) for reviewing history away from the bike.
- Auto-pause/auto-resume when pedaling stops and restarts (freewheel detection from the cadence stream).
- In-app self-updater (fetch a signed APK from a personal host) so app updates don't require re-running ADB.

## Risks & Mitigations

| Risk | Likelihood / Impact | Mitigation |
|---|---|---|
| Peloton OTA update removes/locks the system service the app relies on, or reinstates the stock launcher | Medium / **High — kills P0-2, the project's premise** | Block/disable OTA updates during OpenPelo setup and never accept an update prompt; record the exact firmware build the app is verified against; keep the bike off auto-update indefinitely |
| The undocumented service's interface differs on this tablet's specific firmware build vs. what grupetto targets | Low–Medium / High | Run the sensor-binding spike **first**, before any UI work; keep sensor access behind an abstraction layer so a fallback source (e.g. a clip-on BLE cadence sensor) could be swapped in as a last resort |
| Tablet hardware failure or factory reset wipes all local history | Low / Medium | P1-8 backup/restore; keep periodic backups off-device |
| YouTube changes or retires per-channel RSS feeds | Low / Medium | Feed-fetching isolated in the content layer; fall back to the P2 serverless cache or a static list |
| Every app update requires re-sideloading over ADB | Certain / Low (friction only) | Use wireless ADB for iteration; P2 in-app self-updater if it becomes tedious |
| Development requires physical access to the bike | Certain / Low | The sensor abstraction layer ships with a mock implementation (simulated cadence/resistance), so all UI and logic development happens in a standard Android 11 emulator; only sensor-spike and integration testing need the bike |

## Success Metrics

**Leading indicators (days–weeks post-launch):**
- App successfully installed and auto-launching on the bike: pass/fail, verified once.
- Cadence/resistance/power readings accurate within an acceptable margin of the last known Peloton-app readings (self-verified by riding once with both, before cancelling membership): target ±5% on cadence, power noted as best-effort since it's not Peloton-calibrated.
- Ride completed end-to-end (profile select → metrics visible → stop → saved to that profile's history) without a crash: target 10 consecutive successful rides across at least 2 profiles.
- At least one successful Apple Health import via the P1-1 file bridge, verified manually once.

**Lagging indicators (months post-launch):**
- All-Access membership cancelled and not renewed: binary, this is the actual point of the project.
- Ride frequency maintained at pre-cancellation levels across all household riders combined — track via the app's own per-profile history, compare monthly ride counts before/after cancellation.
- Curated content browser actually gets used (vs. riders defaulting to no content) — track via a simple "class started" count per profile.

## Open Questions

- **(Engineering — RESOLVED)** Bike generation is **Gen 2** — matches the hardware Grupetto's system-service technique is documented against, so P0-2 can proceed on the assumption the technique applies directly (still verify against the specific tablet firmware during the Phase 1 spike, but the premise is sound).
- **(Engineering — RESOLVED, approach chosen)** Dynamic curation will use **per-channel YouTube RSS feeds** (`https://www.youtube.com/feeds/videos.xml?channel_id=<ID>`) fetched on-device: no API key, no quota, no backend, and auto-updating as channels post. Missing metadata (video duration) is fetched lazily per-video (oEmbed or a single Data API `videos.list` call) and cached locally. First implementation step is resolving the 4 channels' handles to their stable `channel_id` values. A serverless cache with the Data API (editorial control, whole-catalog browse, key kept off-device) is deferred to P2 (see Future Considerations) and only pursued if the RSS approach proves too limited (~15 most-recent-videos cap per channel).
- **(Engineering — non-blocking)** Playback mechanism for P0-6: launch the official YouTube app via an intent (simplest, guaranteed ToS-compliant, but hands control to another app's UI/back-button behavior), or embed YouTube's IFrame Player API in a WebView (keeps the rider inside this app's UI, more implementation work)? Affects how seamless the "recreate Peloton UI" goal (P0-7) can feel during content playback.
- **(Engineering — non-blocking)** Does disabling/uninstalling the stock Peloton launcher and "Device Management" app (per OpenPelo's setup notes) risk being reverted by an unexpected OTA update? Worth confirming update behavior so setup doesn't need to be redone unexpectedly.
- **(Product — non-blocking)** For P0-3/P1-6, is a lightweight tap-to-select profile switch sufficient, or does household use actually need a PIN (e.g. to stop a kid from logging rides under an adult's profile)? Default to tap-to-select for v1; add PIN only if it turns out to matter in practice.
- **(Product — non-blocking)** For Apple Health (P1-1), is FIT or TCX the better export format? FIT is Garmin's format but widely supported by import bridges (e.g. HealthFit); TCX is more human-readable/XML-based. Recommend FIT given broader bridge-app support, but worth a quick check against whichever specific bridge app you plan to use.

## Timeline Considerations

- No hard deadline — personal project, paced around evenings/weekends.
- **Suggested phasing**, updated for the expanded scope:
  - **Phase 1 (P0 list):** installable app on Android 11, live metrics via the Grupetto system-service technique, multi-user profiles, timer, per-user history, curated content browser/playback, Peloton-style core screens, auto-launch, sensor-failure handling. This alone is enough to cancel the membership with full household use.
  - **Phase 2 (P1 list):** Apple Health file-bridge export, CSV export, ride goals/power zones, per-profile HR pairing, in-app settings shortcut, profile-switch confirmation, historical import, backup/restore, BT headphone routing.
  - **Phase 3 (P2 list):** automatic Apple Health sync via a companion iOS app, Strava/Garmin sync, expanded curated content, companion Android phone app.
- **Dependency:** Bike generation is confirmed **Gen 2**, so the "no root needed" premise (Grupetto's system-service technique) is on solid ground. The residual Phase 1 risk is narrower: verifying the exact service binds against your tablet's specific firmware build. Do that spike first, before building UI on top of P0-2.
- **Pre-cancellation checklist** — these are only possible while the subscription is active, so cancellation is explicitly sequenced *after* them:
  1. **Side-by-side accuracy ride**: one ride with both the stock Peloton app and this app's readings, to validate the ±5% cadence target (see Success Metrics).
  2. **Historical import (P1-7)**: pull all past workouts from Peloton's cloud into local profiles — this data becomes permanently inaccessible after cancellation.
  3. **UI reference capture**: pull pixel-perfect screenshots of the stock app via ADB (`adb shell screencap`) — in-ride metrics screen (highest priority), home, profile select, ride summary, history, and class browse. These are the design reference for P0-7 and become unreachable after cancellation. Layout/hierarchy reference only — no Peloton assets, fonts, or imagery get copied into the app (Non-Goal #1).
  4. **Block OTA updates** per OpenPelo setup, so a firmware push can't later close the system-service access (see Risks).
