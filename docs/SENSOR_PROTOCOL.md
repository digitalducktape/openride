# Gen 2 Sensor Protocol (T3 / #3)

How OpenRide reads live cadence / resistance / power off the Peloton Bike Gen 2 tablet, and
how that was determined. This is an **interoperability reconstruction** for the owner's own
hardware: OpenRide binds an exported, unguarded on-device service and reconstructs its binder
interface as AIDL. No Peloton code is copied or redistributed; no DRM is circumvented; no class
content is touched (see the PRD Non-Goals).

## TL;DR

| | |
|---|---|
| **Service package** | `com.onepeloton.affernetservice` (the "affernet" system service) |
| **Component** | `com.onepeloton.affernetservice.AffernetService` — `exported=true`, **no** `android:permission` |
| **Bind Intent** | action `com.onepeloton.affernetservice.IV1Interface`, package `com.onepeloton.affernetservice` |
| **Bound interface** | `IV1Interface` (descriptor `com.onepeloton.affernetservice.IV1Interface`) |
| **Model** | callback / push (register a callback, service pushes frames) — **not** polling |
| **Callback interface** | `IV1Callback` (descriptor `com.onepeloton.affernetservice.IV1Callback`), `oneway` |
| **Frame payload** | `BikeData` Parcelable, delivered to `onSensorDataChange` (callback txn 1) |

## Why this path (A vs B, IV1 vs IBike)

The recon found two exported unguarded services: `com.peloton.service.SensorData` (legacy,
`Messenger`-based) and `com.onepeloton.affernetservice` (this one). The affernet service exposes
several binder interfaces — `IAffernetService`, `IV1Interface`, `IBikeInterface`,
`IAuroraInterface`, `ITreadInterface`, `ICaesarInterface`, `IAccessoryService`.

`IBikeInterface` looks tempting (it has `getRPM`, `getPower`, `getCurrentResistance`,
`getBikeData`, `registerCallback`) but it is a lower-level, bike-board-specific interface.
The interface that actually streams the compact `BikeData` sensor frame via a simple
register-a-callback model — and the one **grupetto has proven on Gen 2** — is **`IV1Interface`
+ `IV1Callback`**. Both interfaces share the same `BikeData` Parcelable, so the payload decode
is identical either way; OpenRide binds the `IV1Interface` path because it matches grupetto's
field-tested approach exactly.

Speed is intentionally absent from the service. The stock Peloton bike itself synthesises speed
from power; OpenRide reproduces that (see "Speed" below).

## Transaction codes

AIDL assigns binder transaction codes 1..N in method-declaration order, so the reconstructed
`.aidl` files declare methods in the exact order below to match the live service.

### `IV1Interface` (bound binder)

| Code | Method | Used by OpenRide |
|---|---|---|
| 1 | `registerCallback(IV1Callback, String)` | yes — subscribe to frames |
| 2 | `unregisterCallback(IV1Callback, String)` | yes — on stop |
| 3 | `setFakeDataMode(boolean): boolean` | verification only (no-rider frames) |
| 4 | `setCallbackReportRate(int): int` | yes — request ~1 Hz |

(The real interface has codes 5..15 too — calibration/Lxx/power-source calls OpenRide never
uses. Only 1..4 are reconstructed, since declaring more only risks signature drift.)

### `IV1Callback` (our callback, `oneway`)

| Code | Method | Handled |
|---|---|---|
| 1 | `onSensorDataChange(in BikeData)` | yes — decode -> `BikeMetrics` |
| 2 | `onSensorError(long)` | yes — mark Disconnected |
| 3 | `onCalibrationStatus(int, boolean, long)` | no-op (kept so codes 1/2 line up) |

`onSensorDataChange`'s wire form is `[int hasData][BikeData if hasData != 0]`; AIDL's standard
nullable-parcelable marshalling (`writeInt(1)` + `writeToParcel`, or `writeInt(0)` for null)
reproduces that framing exactly, so the generated stub decodes the service's frames verbatim.

## `BikeData` payload layout

The Parcelable is large; only the first fields matter to OpenRide and they sit at the very front
(before any variable-length field), so they are captured before anything could desync. Full
read order (matching the service's `writeToParcel`) is implemented and commented in
`app/src/main/java/com/onepeloton/affernetservice/BikeData.kt`. The consumed fields:

| Offset | Field | Type | Meaning |
|---|---|---|---|
| 1 | `mRPM` | `long` | crank cadence, **already RPM** |
| 2 | `mPower` | `long` | output, **centi-watts** (watts x 100) |
| 5 | `mCurrentResistance` | `int` | resistance, **already 0..100** |
| 6 | `mTargetResistance` | `int` | commanded resistance, 0..100 |

Field 57 is a nested `V3BikeData` Parcelable (`readParcelable`), reconstructed in
`V3BikeData.kt` so the outer parcel stays in sync when a V3 sensor-board frame is present. The
error-map arrays are length-prefixed (`BIKE_ERROR_COUNT = 15`).

## Scaling (raw frame -> `BikeMetrics`)

All confirmed against grupetto's Gen 2 decode (`sensor/v1new/`):

| `BikeMetrics` field | Formula | Note |
|---|---|---|
| `cadenceRpm` | `mRPM` | no scaling |
| `powerWatts` | `mPower / 100` | centi-watts -> watts (grupetto: `power / 100f`) |
| `resistancePercent` | `mCurrentResistance` (clamped 0..100) | no scaling |
| `speedMph` | `pelotonSpeedMphFromPower(watts)` | derived; service has no speed field |

### Speed

`PelotonSpeed.kt` implements the piecewise-cubic power->speed fit the PeloMon project
reverse-engineered from the stock bike's own curve
(<https://ihaque.org/posts/2020/12/25/pelomon-part-ib-computing-speed/>), the same one grupetto
uses. Input watts, output mph; clamped at 0.

## Connection state

- `Connected` — set only when the **first real frame** arrives (a successful bind that never
  delivers data does not masquerade as live).
- `Disconnected` — service disconnect or `onSensorError`.
- `Unavailable` — any bind failure, or before `start()`. On any non-bike device the service is
  absent and the source stays `Unavailable`; `start()` never throws (PRD P0-9).

## Reconstructed files

```
app/src/main/aidl/com/onepeloton/affernetservice/
    IV1Interface.aidl      IV1Callback.aidl      BikeData.aidl      V3BikeData.aidl
app/src/main/java/com/onepeloton/affernetservice/
    BikeData.kt            V3BikeData.kt          (Parcelable wire layouts)
app/src/main/java/dev/digitalducktape/openride/core/sensor/
    PelotonBikeDataSource.kt   PelotonSpeed.kt
```

Building the real-sensor APK: the `debugReal` variant sets `USE_REAL_BIKE_SENSOR=true` (so
`AppContainer` wires `PelotonBikeDataSource`) and installs alongside the mock build via a
`.real` applicationId suffix — `./gradlew :app:installDebugReal`.

## On-device verification status

Target: `PLTN-RB1VQ`, Android 11, build `RQ.250113.A`.

**Confirmed on the physical bike (no rider):**

- The `AffernetService` is `exported=true` with no permission (verified via the APK manifest and
  `dumpsys package`), so binding needs no signature permission.
- _(filled in by the on-device run — see the "on-device bind verified" commit)_ bind succeeds,
  `registerCallback` returns without exception, `setFakeDataMode(true)` is accepted, and
  `onSensorDataChange` frames arrive and decode into in-range `BikeMetrics`. This exercises the
  full reconstructed pipeline (transaction codes + `BikeData` wire layout) end-to-end using the
  service's own synthetic-data mode, so it does not require pedaling.

**Still requires a rider (manual, cannot be automated here):**

- Confirming that live `cadenceRpm` tracks actual pedaling and `powerWatts` rises with effort.
  Fake-data mode proves the transport and decode are correct; only a person on the bike can
  confirm the real sensor values map as expected. Turning the resistance knob (which changes
  resistance without pedaling) is a good partial live-signal check during that session.

The automated portion is `app/src/androidTest/.../SensorBindingInstrumentedTest.kt`
(`./gradlew :app:connectedDebugAndroidTest`), which is portable: it self-skips the live
assertions on any device where the affernet service is absent.
