# v2: In-app class playback with metrics overlay + Peloton-style redesign

Date: 2026-07-19
Status: approved for implementation (autonomous session; decisions recorded here in lieu of
interactive review)

## Goal

Two coupled improvements to the v1 app:

1. **In-app video playback (Video Ride).** Tapping a class no longer hands off to the
   YouTube app via `ACTION_VIEW`. Instead the video plays inside OpenRide in an embedded
   YouTube IFrame player, with live sensor metrics overlaid — the pattern used by the
   open-source Peloton-alternative projects (grupetto et al.) and the way the official app
   feels: one cohesive screen, video + metrics together.
2. **Peloton-look redesign.** Restyle the app so it reads like the official Bike app:
   bottom tab bar, dark near-black chrome, tracked-uppercase metric labels, big bold
   numerals, a persistent bottom in-ride metrics bar, power-zone colors. Constraint
   unchanged from the PRD (Non-Goal #1): *layout and interaction patterns only* — no
   Peloton assets, fonts, logos, or brand colors are copied.

## Why this is ToS-safe (supersedes the T10 decision)

`docs/DECISIONS.md` (T10) chose the intent handoff for v1 simplicity, explicitly noting the
embedded IFrame player is "*also* compliant when done correctly" and marking it the revisit
path. The PRD (Non-Goal #5) likewise allows "intent **or embedded IFrame player**". Rules we
follow to stay compliant:

- Playback uses YouTube's official IFrame Player API embed (`youtube-nocookie.com` host
  page), streamed by YouTube's own player. Nothing is downloaded, cached, scraped, or
  rehosted.
- YouTube's native player controls stay enabled (`controls=1`); the metrics overlay is
  dismissible (tap the video area) so it never permanently obscures the player UI.
- No ad-stripping, no background/audio-only tricks.

## Workstream A — Video Ride

### Flow

Classes tab → tap a class card → `ClassesViewModel.startRideForVideo()` starts the ride
session for the active profile (same semantics as Home's Quick Start; returns false and
navigates nowhere if no active profile) → outer nav navigates to `video_ride/{videoId}` →
full-screen player with overlay → End Ride → existing Ride Summary (same back-stack shape as
the plain in-ride flow).

Quick Start (no video) keeps the existing `InRide` destination.

### Components

- `ui/classes/YouTubeEmbed.kt` — pure function building the IFrame-API host HTML for a
  video id (JS-escaped), `playsinline`, autoplay, `controls=1`, `rel=0`,
  `modestbranding=1`. Unit-tested (id embedding, escaping, required params).
- `ui/ride/VideoRideScreen.kt` — `AndroidView`-hosted `WebView` (JS on,
  `mediaPlaybackRequiresUserGesture = false`, `loadDataWithBaseURL` against
  `https://www.youtube-nocookie.com`) filling the screen, plus the overlay:
  - Top scrim row: elapsed timer, auto-pause / sensor banners, Pause and End buttons.
  - Bottom: `RideMetricsBar` (shared component, translucent variant).
  - Tapping the video area toggles overlay visibility (so YouTube controls are reachable).
  - Reuses `InRideViewModel` unchanged — same state, pause/resume/end semantics.
- `ui/ride/RideMetricsBar.kt` — the signature element (see design language): one strip of
  metric cells (Cadence / Output / Resistance / Speed / Distance / HR-when-paired), current
  value large, tracked-caps label above, avg/max small below, thin vertical dividers.
  Solid-surface variant for the plain in-ride screen, translucent-over-video variant for
  Video Ride.
- Navigation: `Destinations.VideoRide = "video_ride/{videoId}"` on the outer graph;
  `MainScaffold` passes an `onStartVideoRide` callback into `ClassesScreen`.
- `ClassesViewModel` gains `RideSessionManager` + `ActiveProfileHolder` dependencies and
  `startRideForVideo(): Boolean` (mirrors `HomeViewModel.startQuickRide`).
- `VideoPlayback.kt` (intent launcher) is deleted; `DECISIONS.md` gets a superseding entry.

### Out of scope (recorded for later)

- Syncing ride pause ↔ video pause via a JS bridge (v2.1 candidate; needs player-state
  plumbing).
- Resume-where-you-left-off, playlists, offline anything (never — Non-Goal #5).

## Workstream B — Peloton-look redesign

### Design language (tokens)

- **Palette** (`ui/theme/Color.kt`, existing hexes kept — they are already "Peloton-style,
  not Peloton-copied"): near-black `#0C0C0E` background, `#1C1C1F`/`#29292D` surfaces, red
  accent `#E8442D`, plus new: `ScrimOverlay` (black 55%) for video overlays, and a
   7-step power-zone ramp (slate → blue → teal → green → yellow → orange → red) used for
  zone chips and the in-ride zone accent.
- **Type** (`ui/theme/Type.kt`): system sans only (no bundled fonts, per constraints).
  Peloton feel comes from scale + tracking: `MetricValue` 56sp Bold, `MetricLabel` 12sp
  SemiBold with 1.5sp letter-spacing UPPERCASE, `TimerDisplay` 72sp Bold tabular-feel,
  `SectionEyebrow` 13sp SemiBold 1.5sp tracking for channel/section headers.
- **Signature element:** the bottom ride metrics bar — the one thing every screen of the
  official bike experience is remembered by — shared between plain rides and video rides.

### Screen-by-screen

- **MainScaffold:** side `NavigationRail` + emoji → bottom `NavigationBar` with Material
  icons (core icon set only; no new dependency), red selected tint, hairline top divider.
- **Home:** greeting header (avatar chip right, like the bike app's top bar), a large
  hero "Quick Start" card (surface card, red CTA), goal row as a quiet chip beneath.
- **Classes:** channel names become tracked-caps eyebrows; cards grow to 16:9 ≈ 280dp
  with duration badge; row/height rhythm matched to the bike app's shelf layout.
- **In-Ride (no video):** timer top-center with goal progress hairline under it; large
  central current-output block with zone-colored accent; `RideMetricsBar` pinned to the
  bottom; Pause/End as quiet outlined controls top-right.
- **Video Ride:** as Workstream A.
- **Profile select / History / Summary:** token-level restyle only (type/eyebrows/spacing)
  — same structure.

## Testing

- New: `YouTubeEmbedTest`; `ClassesViewModelTest` extended for `startRideForVideo`
  (active/no-active profile).
- All existing unit tests must keep passing (`./gradlew test`), plus `assembleDebug`.
- On-hardware verification of WebView playback on the bike tablet is a follow-up (same
  standing as other `-real` build items).
