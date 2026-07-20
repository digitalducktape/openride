# Content Catalog Expansion — Design

**Date:** 2026-07-19
**Status:** Approved direction (all four approach questions answered "recommended" option)

Expands the Classes tab from 4 hard-coded channels to a managed catalog: 8 new creators,
duration/members-only filtering, sort + length filters, a Random Ride button, a per-creator
click-through page with playlists, and a settings screen for adding custom channels/playlists.

## Goals

1. Add 8 new creator channels (below) to the built-in catalog.
2. Filter out videos under 10 minutes and non-public (members-only) videos.
3. Classes page: category chips (All / Workout / Scenic), sort (Newest default / Oldest /
   Random), length filter, and a **Random Ride** button.
4. Creator click-through page: a creator's latest videos plus their curated playlists.
5. User-managed content sources: add a channel or playlist by URL/@handle, hide built-ins.

## Non-goals

- Title keyword filtering (titles "typically but not always" contain Cycling/Spin/Bike/Ride —
  a keyword filter would drop valid classes; the duration + visibility filters do the real work).
- YouTube Data API integration. The app stays key-free (existing design decision).
- Import/export of the source list (possible later addition to the Content Sources screen).
- Full video back-catalog. The catalog shows a channel's recent ~30 videos and up to ~100 per
  playlist; playlists are the channels' own mechanism for deep curated catalogs.

## New built-in channels (IDs resolved and verified 2026-07-19)

| Channel | Handle | Channel ID | Category |
|---|---|---|---|
| Kaleigh Cohen Cycling | @kaleigh | UChY_9WJx0saa0St48lSdytQ | Workout |
| Kirsten Allen | @KirstenAllen | UCyEw-nPmOgo18LPK7wkkdtQ | Workout |
| GCN Training | @GCNTraining | UCMEiuHFc7nHTDq2_KzLu0bw | Workout |
| The Spin Junkie | @TheSpinJunkie | UC0d6o9_OVUZdC4g3ci1_UXA | Workout |
| Kristina Girod | @KristinaGirod | UCDomKTwMIX0U_cMQzUweggQ | Workout |
| Joe Alvarado | @JoeAlvarado | UCH9KY-e1kLOMQ2NcDvXMO3w | Workout |
| TaG Cycling | @TaGCycling1 | UCU8VLagwhLbab5RQhjlzY9A | Workout |
| Bike the World | c/BiketheWorld | UCWXNvnztTNz99SYgTnyltwg | Scenic |

Existing channels stay: Virtual Cycling Workouts + Indoor Cycling Videos (Scenic),
Ride With Alina + Gabriella Lynn (move from "Rides" to **Workout** — the `Rides` category is
renamed `Workout` to match the new top-level grouping [Just Ride, Scenic, Workout, Random];
"Just Ride" remains the Home tab's quick-start and "Random" is a button, not a category).

## Data sources & fetch pipeline

The YouTube RSS feed (today's only source) has no duration, no visibility flag, and only the
latest 15 videos — the required filters are impossible with RSS alone. Verified live against
real channels:

- The public, logged-out channel `/videos` page embeds JSON (`ytInitialData`) listing the
  latest ~30 **public** videos as `lockupViewModel` objects with video id, title, thumbnail,
  and duration text (`"thumbnailBadgeViewModel":{"text":"18:23"}`). Shorts and live-tab
  content are excluded. Members-only videos do not appear in the logged-out listing (an RSS
  entry absent from a successfully-scraped page is treated as non-public and skipped).
- The `/playlists` tab page embeds each playlist's id (`PL...`), title, and thumbnail the
  same way; a playlist's own page embeds its videos with durations (up to ~100).
- Playlist RSS feeds (`/feeds/videos.xml?playlist_id=PL...`) work and serve as fallback.
- The feed/page endpoints are flaky (intermittent 404/500 that succeed on retry), so every
  fetch gets **one retry** before falling back to cache.

**Merge rule per channel:** scrape the `/videos` page (primary; provides durations and the
public-only listing) and fetch the RSS feed (provides exact publish timestamps for the newest
15). Page entries win; RSS timestamps overwrite the page's approximate ones where ids match.
Page entries beyond RSS's 15 parse the page's relative published time ("3 weeks ago") into an
approximate epoch — good enough for ordering. If the page scrape fails but RSS succeeds,
show RSS entries with `durationSec = null` (degraded but working). If both fail, fall back
to the on-disk cache exactly as today (`refreshFailed` banner preserved).

**Filter rule:** drop videos with known duration < 600 s. Unknown duration (RSS-fallback
path) is kept — hiding everything when scraping breaks is worse than showing a few short
videos. Non-public videos are dropped whenever the page scrape succeeded.

### New/changed components (core/content)

- `ChannelPageParser` — extracts videos (id, title, thumbnail, duration, approx published)
  from a channel `/videos` or playlist page's embedded JSON; extracts playlist summaries from
  a `/playlists` page. Pure function of the HTML string → easily unit-tested with fixtures.
  Parsing is defensive: any structural surprise degrades to "scrape failed", never a crash.
- `ChannelHandleResolver` — resolves a pasted URL/@handle to `(channelId | playlistId,
  displayName)` by fetching the page and reading `"externalId":"UC..."` / playlist metadata
  (same technique used to build the table above).
- `YouTubeContentRepository` — gains the scrape+merge+filter pipeline, one-retry fetches, and
  reads its source list from `ContentSourceRepository` instead of `ChannelConfig`. Exposes
  `channelSections()` (as today), `creatorContent(sourceId)` (latest + playlists), and
  `playlistVideos(playlistId)`.
- `ContentCache` — unchanged format, plus a second file per creator for playlist metadata.
- `ChannelConfig` — becomes only the seed list for the database (below).

## Data model (Room)

New table `content_sources`, migration 4→5, seeded from `ChannelConfig.ALL` + the 8 new
channels on first access:

```
ContentSource(
  id: Long (PK, autoincrement),
  sourceType: CHANNEL | PLAYLIST,
  youtubeId: String,        // UC... or PL..., unique
  displayName: String,
  category: WORKOUT | SCENIC,
  builtIn: Boolean,         // built-ins can be hidden but not deleted
  hidden: Boolean,
  position: Int,            // row order on the Classes page
)
```

`ContentSourceDao` + `ContentSourceRepository` (observe list, add, hide/unhide, delete
custom). `ContentCategory.Rides` is renamed `Workout`. A user-added playlist behaves like a
channel row on the Classes page (its videos, same filters).

## UI

### Classes page (`ui/classes`)

Header row: **Random Ride** button (right-aligned) + filter bar beneath the title:

- Category chips: `All` / `Workout` / `Scenic` (default All).
- Sort menu: `Newest` (default) / `Oldest` / `Random`.
- Length chips: `Any` / `<20` / `20–30` / `30–45` / `45+` min (unknown duration only matches `Any`).

Layout: with default sort + `Any` length, today's one-row-per-source layout (rows ordered by
`position`, newest-first within a row; channel name is now tappable → creator page). Choosing
a non-default sort or length filter switches to a flat grid of all matching videos across
sources, ordered per the sort (Random = shuffled once per selection; re-selecting reshuffles).
Category chips apply in both layouts. Taken badges, refresh banner, and tap-to-ride behavior
are unchanged in both.

**Random Ride button:** picks a uniformly random video from the currently filtered set
(category + length + all standing filters) and starts it exactly like tapping its card
(`startRideForVideo` → `VideoRide`). Disabled while loading or when the filtered set is empty.

### Creator page (new, `ui/creator`)

Route `creator/{sourceId}` in the outer NavHost (like `HrPairing`), pushed from a tapped
channel name. Content: creator name header, a `LATEST` row (the merged ~30-video catalog,
same cards/taken badges/tap-to-ride), then one row per playlist (title + first videos;
playlist videos fetched on first display, cached). Playlist rows honor the same
duration/visibility filters. A playlist row that fails to load shows the standard
refresh-failed treatment.

### Content Sources screen (new, `ui/sources`)

Route `content_sources`, reached from the Profile tab (alongside HR pairing / updates).

- List of all sources grouped by category; each row: name, type (channel/playlist),
  built-in marker, hide/show toggle; custom sources also get delete.
- **Add source:** text field accepting a channel URL, `@handle`, or playlist URL →
  `ChannelHandleResolver` fetches and previews the resolved name → user picks Workout or
  Scenic → saved, appears on Classes immediately. Errors surface inline ("Couldn't find that
  channel", "No connection — try again") and never save a half-resolved source.

## Error handling summary

- Every network fetch: one retry, then cache fallback, then `refreshFailed` presentation.
- Page-scrape parse failure ⇒ same path as network failure for that channel (RSS-only,
  durations null) — a YouTube markup change degrades filtering, never blanks the catalog.
- Resolver failures never write to the database.
- Random Ride with an empty filtered set: button disabled, nothing to handle.

## Testing

- `ChannelPageParser`: fixture HTML (saved real pages, trimmed) → videos with durations;
  playlist page → playlist summaries; malformed/unexpected JSON → parse-failure result.
- `YouTubeContentRepository`: merge rule (page+RSS timestamps), 10-min filter, members-only
  drop (RSS id absent from page), RSS-only degraded path, retry-then-cache.
- `ClassesViewModel`: category/length/sort filtering, random pick comes from filtered set,
  flat-vs-rows mode selection.
- `ChannelHandleResolver`: URL/@handle parsing variants, not-found, network error.
- Room: migration 4→5 test + seed, `ContentSourceDao` CRUD.
- Existing tests updated for the `Rides`→`Workout` rename and repository constructor change.

## Risks

- **Scrape fragility:** YouTube can change page internals at any time. Mitigated by the RSS
  fallback (app keeps working, filters degrade), defensive parsing, and parser fixtures that
  make the contract explicit.
- **Approximate dates** for videos older than RSS's window: ordering could be slightly off
  between channels; acceptable for a browse list.
- **Endpoint flakiness** (observed live): mitigated by retry + existing cache fallback.
