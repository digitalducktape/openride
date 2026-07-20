# Engineering decisions

Short-form log of decisions that aren't obvious from the code alone. Add to this as Phase 1/2
work raises other judgment calls worth recording.

## Classes playback v2: in-app embedded player with metrics overlay (supersedes T10 below)

**Decision (2026-07-19):** classes now play *inside* OpenRide — `VideoRideScreen` hosts a
WebView streaming the class through YouTube's own embedded player
(`youtube-nocookie.com/embed/<id>`, built by `ui/classes/YouTubeEmbed`), with live ride
metrics overlaid (shared `RideMetricsBar` + timer/controls scrim). Tapping a class starts a
ride session and navigates to `video_ride/{videoId}`; ending it lands on the normal summary.
This is exactly the "revisit as P2" path the original T10 entry reserved, pulled forward
because the context-switch to the YouTube app was the roughest seam in v1.

**ToS stance (PRD Non-Goal #5, unchanged):** the embed is YouTube's official player on the
privacy-enhanced domain; nothing is downloaded, cached, or rehosted; YouTube's native player
controls stay enabled (`controls=1`) and the metrics overlay is dismissible (tap the video)
so the player UI is never permanently obscured; no ad-stripping or background-playback
tricks. Ride pause and video pause are deliberately independent in v2 — syncing them needs
a JS bridge to the IFrame Player API and is recorded in the v2 spec as a follow-up.

## Classes playback: YouTube app intent vs. in-app WebView IFrame player (T10 — superseded)

**Decision:** v1 launches the video via an `ACTION_VIEW` intent to `https://www.youtube.com/watch?v=<id>`
(package-hinted at the YouTube app, falling back to a browser if it isn't installed — see
`ui/classes/VideoPlayback.kt`). It does **not** embed YouTube's IFrame Player API in a WebView.

**Why:**

- **Simplicity.** An intent is a few lines; an embedded IFrame player means a `WebView`,
  JS bridge, lifecycle/orientation handling, and its own error states — a meaningfully bigger
  surface for a v1.
- **ToS safety.** The PRD (Non-Goal #5) is explicit that curated content must never be
  downloaded, scraped, or rehosted — content stays inside YouTube's own app/player at all
  times. An intent handoff to the real YouTube app is the least ambiguous way to guarantee
  that; an embedded player is *also* compliant when done correctly, but it's more surface
  area to get wrong (e.g. accidentally caching video data, stripping YouTube's own UI/ads in
  a way their ToS disallows).
- **PRD P0-6 acceptance criteria** only requires "plays it full-screen via YouTube's
  app/player" — it doesn't require staying inside our UI chrome, so the intent approach
  already satisfies the requirement as written.

**Cost / what we're giving up:** control passes to the YouTube app's own UI and back button
for the duration of playback, which is a rougher seam than an embedded player would be
against the "feels like one cohesive app" goal (PRD P0-7). On the bike's tablet specifically,
this also means whatever's installed as "the YouTube app" (likely YouTube or YouTube Kids,
depending on what OpenPelo's setup leaves in place) becomes part of the experience.

**Revisit as:** a P2 item if that context-switch turns out to bother riders in practice (see
PRD's Future Considerations). At that point, an embedded WebView IFrame player would keep
riders inside OpenRide's own chrome — worth prototyping once there's real usage to justify
the added complexity.

## Bike Gen 2 sensor access: primary mechanism assumption (T3, #3)

**Decision:** `PelotonBikeDataSource` targets the callback/binder-based `AffernetService`
mechanism (package `com.onepeloton.affernetservice`) as primary, based on grupetto's public
reverse-engineering, rather than grupetto's older Messenger/`Command`-enum mechanism
(package `com.peloton.service.SensorData`) used for the original Gen 1 bike.

**Why:** grupetto's codebase has two distinct sensor-access code paths tied to different
Peloton service package names, which reads as "older bike generation" vs. "newer bike
generation." Since our hardware is confirmed Gen 2, the newer `affernetservice` path is the
more plausible match — but this is an inference from public reverse-engineering notes, not
something verified against our specific tablet's firmware. See the TODO comments in
`PelotonBikeDataSource` (referencing issue #3) for exactly what needs on-device confirmation
before this can be trusted, and the fallback behavior (`ConnectionState.Unavailable`) if the
assumption is wrong.
