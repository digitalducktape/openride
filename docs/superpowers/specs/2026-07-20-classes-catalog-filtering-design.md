# Classes Catalog Filtering — Design

**Date:** 2026-07-20
**Status:** Approved direction (design confirmed section-by-section with the rider)
**Builds on:** branch `fix/members-only-content-leak` (the members-only page-parser fix is
already committed there; this work continues on the same branch as one "catalog quality" change).

Three related improvements to what reaches the Classes catalog, all in the content layer plus a
small amount of Classes-tab UI:

- **A. Spin-class relevance filter** — stop surfacing a creator's non-cycling content (sculpt,
  strength, vlogs) in the catalog and in their playlist list.
- **B. Feed-fallback made safe** — when the page fetch fails and the app degrades to the RSS
  feed (which carries no members-only marker), show those videos but make them non-startable
  until a page fetch can verify them.
- **C. "Hide completed" filter** — a toggle on the Classes tab that hides classes the active
  profile has already finished.

## Background / why now

The [content catalog expansion](2026-07-19-content-catalog-expansion-design.md) deliberately
listed title keyword filtering as a **non-goal** ("a keyword filter would drop valid classes;
the duration + visibility filters do the real work"). Real-world use reversed that call: a
fitness creator like Kristina Girod posts mostly *non*-cycling content (SCULPT CLASSES, LOWER
BODY WORKOUTS, WEEKLY VLOGS, personal videos), so duration + visibility filters alone leave the
catalog full of things that aren't spin classes. The rider has now seen the over-inclusion and
accepts the false-negative risk of keyword filtering, mitigated by the hybrid approach below.

Separately, fixing the members-only leak at the page parser closed the primary path, but the
feed-fallback path (used when a page fetch fails) still has no members-only signal at all.

## Non-goals

- YouTube Data API. The app stays key-free (existing decision).
- Per-rider or per-source tuning of the keyword lists — the heuristic is a hardcoded default
  (YAGNI; revisit if a creator is chronically mis-filtered).
- Retroactive re-filtering of already-cached content beyond the normal refresh cycle. The next
  successful fetch rewrites each cache entry through the new filters.

---

## A. Spin-class relevance filter (hybrid)

### Rule

A new stateless helper `ClassRelevance` in `core/content` exposes one predicate:

```
isCyclingTitle(title): Boolean  =  contains ≥1 CYCLING term  AND  contains 0 VETO terms
```

Matched **case-insensitively on word boundaries** (`\b`) so `ride` doesn't match `bride`/`pride`.

- **CYCLING terms (allow):** ride, rides, spin, cycle, cycling, cycled, bike, biking, saddle,
  cadence, peloton, rpm, watt, watts.
- **VETO terms:** vlog, haul, grwm, unboxing, "try-on"/"try on", "q&a", podcast, recap,
  "day in my life", story, storytime.

**Why this shape:** other *workout* types (sculpt, strength, yoga, pilates, "full body") are
dropped automatically by having no cycling term — the veto list is not a catalog of every other
exercise. Veto only handles items that *do* contain a cycling word but aren't a class
("Sunday **ride** vlog", "my **cycling** story"). As a side effect the allow-list also drops
junk-titled entries seen in the wild ("April 9, 2026", a bare "https://my.playbookapp.io/…" link).

The term lists are the tunable knobs; start with the above and adjust from real misses.

### Where it runs

- **Videos:** in `YouTubeContentRepository`'s post-merge filter, alongside the existing
  duration filter (currently `rideable`). It operates on `Video.title`, so it covers **both**
  page-sourced and feed-sourced videos with one check.
  - **Deliberately NOT in `YouTubePageParser.parseVideos`**, which throws `ContentParseException`
    on an empty result and would mistake a low-cycling channel for a broken page (triggering a
    needless feed fallback). Relevance filtering must be able to yield few/zero videos without
    looking like a fetch failure.
- **Playlists:** filter `PlaylistSummary` by `isCyclingTitle(title)` in
  `YouTubeContentRepository.fetchPlaylists`, so a creator page shows "SPIN CLASSES /
  SPIN CLASSES: THEME RIDES" and not "SCULPT CLASSES / WEEKLY VLOGS / FULL BODY WORKOUTS".

Members-only detection stays where the earlier fix put it (parser, `BADGE_MEMBERS_ONLY`).

### Edge cases

- A channel that filters down to zero relevant videos falls through the repository's existing
  empty-handling (keep last-known cache / degraded row), same as any other empty result.

---

## B. Feed-fallback made safe (non-startable)

### Data model

- `Video` gains `val startable: Boolean = true`.
- In `YouTubeContentRepository.fetchVideos`, the feed-only fallback branch
  (`feedVideos != null -> feedVideos`) maps its videos to `copy(startable = false)`. The
  page-authoritative branch leaves `startable = true`.
- `ContentCache` write/read persists `startable`; a missing key reads as `true` (old cache
  files and page entries stay startable). A later successful page fetch rewrites the cache
  entry as verified.

### Enforcement

- **Start gate:** `ClassesViewModel.startRideForVideo(videoId)` already returns `Boolean`. It
  looks the video up in the loaded sections and returns `false` when the video is missing or
  `!startable`, emitting a one-shot message *"Can't verify this class right now — pull to
  refresh"* (transient `SharedFlow` surfaced as a snackbar/toast) instead of starting a ride.
- **`VideoCard`:** when `!startable`, render an "unverified" treatment — dimmed thumbnail plus a
  small label (e.g. "Unavailable — refresh") — so it's visibly distinct from a startable class.
- **Random Ride:** `randomRide` picks only from `startable` videos, so it never lands the rider
  on a class that won't start. (Non-startable videos still *display* in the grid/rows.)

### Interaction with A

Feed-fallback videos are still relevance-filtered by title (feed titles are present), so the
degraded row is both relevant and safe.

---

## C. "Hide completed" filter

### Definition of "completed"

"Completed" = a **finished** ride, i.e. one the rider ended and that was saved. This maps
exactly to the existing `takenVideos` set: a `rides` row is inserted only in
`RideSessionManager.stop()` / `RideRepository.saveRide()` (never on `start()`), and `stop()`
saves unconditionally. So the filter reuses the same data — and the same definition — as the
existing "already ridden" badge. No new query or schema change.

### Changes

- `ClassFilters` gains `val hideTaken: Boolean = false`. It does **not** feed into
  `isDefaultBrowse`, so toggling it filters within both the per-creator browse rows and the flat
  grid without forcing a mode switch.
- `ClassFiltering.rows` and `.grid` take an extra `taken: Set<String>` (video ids) and drop
  taken videos when `hideTaken` is on. `ClassesViewModel` folds `takenVideos` into the
  `combine` that builds `rows`/`grid` (and `randomRide` respects it too).
- **Filter bar:** a toggle chip at the top of the Classes filter bar, proposed label
  **"Hide completed"**. A row that empties after hiding completed classes simply doesn't show
  (existing non-empty filter on rows).

---

## Testing

- **A — `ClassRelevanceTest`:** keeps ride/spin/cycle/cycling/bike titles; drops SCULPT/VLOG/
  STRENGTH/YOGA (no cycling term); vetoes "ride"+"vlog", "cycling"+"story"; word-boundary
  (`bride` ≠ ride). `YouTubeContentRepositoryTest`: page+feed videos filtered by relevance;
  playlists filtered by title.
- **B — `YouTubeContentRepositoryTest`:** page-failed fetch yields feed videos with
  `startable = false`; page success yields `startable = true`. `ContentCacheTest`: `startable`
  round-trips; a legacy cache file with no `startable` key reads as `true`.
  `ClassesViewModelTest`: `startRideForVideo` returns `false` and emits the message for a
  non-startable video; `randomRide` never returns a non-startable video.
- **C — `ClassFilteringTest`:** `hideTaken` removes taken ids from `grid` and from each row's
  videos; leaves them when off; `randomRide` skips taken when on. `ClassFilters`: `hideTaken`
  does not change `isDefaultBrowse`.

## Files touched (anticipated)

- `core/content/ClassRelevance.kt` (new), `Video.kt`, `YouTubeContentRepository.kt`,
  `ContentCache.kt`
- `ui/classes/ClassFilters.kt`, `ClassesViewModel.kt`, `ClassesScreen.kt`, `VideoCard.kt`
- Tests: `ClassRelevanceTest.kt` (new), `YouTubeContentRepositoryTest.kt`, `ContentCacheTest.kt`,
  `ClassesViewModelTest.kt`, `ClassFilteringTest.kt`
