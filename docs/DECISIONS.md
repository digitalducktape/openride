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
controls stay enabled (`"controls": 1`) and the metrics overlay is dismissible (tap the
video) so the player UI is never permanently obscured; no ad-stripping or background-playback
tricks.

## Classes playback v2.1: ride state and video playback are synced

**Decision (2026-07-20):** the follow-up the entry above reserved is now done — `YouTubeEmbed`
builds its page on YouTube's official **IFrame Player API** rather than a bare `<iframe>`, and
`VideoRideScreen` drives it both ways:

- Ride paused (Pause button *or* freewheel auto-pause) → `openridePause()` via
  `evaluateJavascript`; resuming plays again. Pausing the pedals now pauses the class, which
  is what riders expect and what the previous "deliberately independent" behavior got wrong.
- Video reaches `ENDED` → the page calls back through an injected `@JavascriptInterface`
  (`OpenRideBridge.onVideoEnded()`) and the ride ends and saves automatically, landing on the
  normal summary instead of running on after the class is over.

Playback control goes exclusively through the official player API, so the ToS stance above is
unchanged: no direct media manipulation, no chrome stripping. The page keeps a `wantPaused`
flag because the ride can pause before the player finishes bootstrapping, and the JS-interface
callback hops to the UI thread (`View.post`) since it arrives on a WebView-internal thread.

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

## Classes catalog: page scraping alongside RSS

**Decision (2026-07-20):** the Classes catalog fetches each source's public YouTube page in
addition to its RSS feed.

**Why:** the feed carries neither video duration nor any visibility flag and stops at 15
videos, so "no classes under 10 minutes" and "no members-only classes" are both impossible
from it. The page's embedded `ytInitialData` has durations, is public-only, and covers ~30
videos.

The alternative was the YouTube Data API, which would mean an API key and a quota — this app
is deliberately key-free. The cost of scraping is fragility: YouTube can change the page
shape whenever it likes. That's contained by treating a parse failure exactly like a network
failure, so the catalog falls back to RSS (working, but unfiltered by length) and then to the
on-disk cache, and by keeping the parser's expected structure pinned in fixtures.

Every content fetch also retries once: YouTube's feed and page endpoints intermittently
return 404/500 for valid URLs — observed alternating between 200 and 404 seconds apart.

## The Classes catalog lives in the database

**Decision (2026-07-20):** `ChannelConfig` is now only a seed list — the live catalog is the
`content_sources` table.

**Why:** it lets riders add their own channels and playlists and hide built-ins they don't
ride. Built-ins are hideable but not deletable, so the original catalog is always recoverable.

## Automatic local backup lives in shared Downloads, not app storage

**Decision (2026-07-20):** alongside the manual Back Up button (P1-8), `AutoBackupManager`
keeps a rolling backup written to `Download/OpenRide/openride-autobackup.json` via
`MediaStore.Downloads`, and silently restores it at launch when the database is empty.

**Why there:** the whole point is surviving an app update or reinstall, and everything under
the app's own `filesDir`/`cacheDir` is deleted on uninstall — a backup that dies with the app
isn't a backup. Shared Downloads survives, and MediaStore is the scoped-storage-sanctioned
way to write it on the tablet's Android 11 without a storage permission. Two recovery paths
follow from that: an in-place update or cleared app data restores silently through MediaStore,
while a full uninstall/reinstall severs MediaStore ownership of the row — but the file is
still sitting in Downloads for the existing "Restore from file" (SAF) picker to open.

**One file per package** (`openride-autobackup-<applicationId>.json`), because MediaStore
scopes Downloads queries to the owning app. Verified on the bike: with a single shared name,
the `.real` sensor build wrote the file first, and the mock build could neither see nor
overwrite it — it silently got `openride-autobackup (1).json`, leaving two same-looking
backups whose contents belong to different installs. Namespacing keeps each build's history
its own while staying stable across that package's own reinstalls.

**The one safety rule:** an empty database never overwrites the stored backup. The dangerous
launch is one where local data is missing *and* the restore didn't take (unreadable file,
storage hiccup); writing then would replace the last good backup with nothing. This rule
replaced an earlier "skip the flow's first emission" approach, which had the same intent but
raced — a conflating source folds changes that arrive before collection starts into that
first emission, so dropping it dropped real data.

**Avatar photos travel as bytes:** `ProfileBackup.avatarPhotoBase64` carries the photo itself
rather than `Profile.avatarPhotoPath`, which is an install-specific path that would dangle
anywhere else; restore writes a fresh file and stores its new path.

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
