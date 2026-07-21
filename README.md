# OpenRide

OpenRide is a free, independent workout app for the Peloton Bike (Gen 2). It replaces the screen you normally see when you turn on the bike with a similar-feeling experience — live stats while you ride, a library of free cycling videos, and a history of all your past rides — without needing a Peloton subscription.

It's installed using a free tool called [OpenPelo](https://github.com/doudar/Openpelo), which lets you add apps to the bike's tablet. This doesn't require rooting the device or unlocking anything permanently.

<p align="center">
  <img src="docs/screenshots/home.png" width="32%" alt="Home screen with Quick Start" />
  <img src="docs/screenshots/live_ride.png" width="32%" alt="Live ride metrics" />
  <img src="docs/screenshots/classes.png" width="32%" alt="Classes catalog" />
</p>

## What it does

- **Live stats while you ride** — cadence, resistance, power, and speed, pulled straight from the bike itself, with no subscription required
- **A profile for everyone in the house** — each rider gets their own name, picture, and workout history
- **A library of free classes** — a constantly-updating selection of cycling videos pulled in automatically, so there's always something new to ride to
- **Ride history and personal bests** — a calendar of every past ride, plus your all-time best output, cadence, and duration
- **Export your ride data** — download any ride as a file (FIT, TCX, or CSV) to keep or use elsewhere, so your data is always yours
- **Heart-rate strap and Bluetooth headphone support** — pair a heart-rate monitor, and headphone audio just works once paired

<p align="center">
  <img src="docs/screenshots/ride_summary.png" width="32%" alt="Ride summary with export" />
  <img src="docs/screenshots/history.png" width="32%" alt="Ride history and personal records" />
  <img src="docs/screenshots/profile.png" width="32%" alt="Profile and settings" />
</p>

## Getting the app

OpenRide isn't available in an app store — you build it from this repo and install it onto the bike's tablet yourself. This does take a little technical setup, but the steps below walk through everything.

### What you'll need

- A Peloton Bike (Gen 2), with [OpenPelo](https://github.com/doudar/Openpelo) already set up on it. OpenPelo is what gives your computer the ability to talk to the bike's tablet — set that up first, following its own instructions.
- A computer with `adb` installed (this is Android's device-connection tool, part of the free "Android SDK platform-tools" download) and Java 21 installed.
- A copy of this repo on your computer (`git clone`, or download it as a ZIP from GitHub and unzip it).

### Step 1: Build the app

Open a terminal in the folder where you downloaded this repo, and run:

```sh
./gradlew assembleDebug
```

This compiles OpenRide into an installable file, which will show up at `app/build/outputs/apk/debug/app-debug.apk`.

### Step 2: Install it on the bike

Connect to the bike's tablet with `adb` (per OpenPelo's instructions), then run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.digitalducktape.openride/.MainActivity
```

The app should open to a profile selection screen — you're in.

From here, making OpenRide the screen that greets you when the bike turns on, and making sure a software update doesn't undo any of this, are covered step-by-step in **[docs/INSTALL.md](docs/INSTALL.md)**. Please read it in full before going further — the order of steps there matters.

## For developers

The rest of this section is technical detail for anyone contributing to the code — feel free to skip it otherwise.

- Kotlin + Jetpack Compose, single-activity, `minSdk 30` (the tablet's Android 11), package `dev.digitalducktape.openride`
- Sensor access sits behind a `BikeDataSource` abstraction with a `MockBikeDataSource` (simulated ride) so all UI/logic development runs in a standard emulator; only the real system-service binding needs the physical bike
- Room database: `Profile` / `Ride` / `RideSample` (per-second samples — required for FIT/TCX export)
- Content browser fetches per-channel YouTube RSS (`/feeds/videos.xml?channel_id=…`) on-device: no API key, no quota, no backend

## License

OpenRide is released under the [Apache License 2.0](LICENSE). Attributions for
prior community work and bundled dependencies are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Independent project — not affiliated with Peloton

OpenRide is an independent, non-commercial project. It is **not affiliated with,
authorized, sponsored, or endorsed by Peloton Interactive, Inc.** "Peloton" and
"Peloton Bike" are trademarks of their owner, used here only to describe the
hardware OpenRide runs on.

OpenRide ships **no Peloton source code, artwork, branding, audio, video, or
class content**. It reads the bike's own sensor values through an existing,
unprotected on-device service (no root, no firmware modification) purely for
interoperability, and streams third-party workout videos through YouTube's
official player. Any UI resemblance is limited to common layout and interaction
patterns. For the reverse-engineering and trademark basis, see
[docs/INTEROP.md](docs/INTEROP.md).
