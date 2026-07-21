# Content Catalog Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the OpenRide Classes tab from 4 hard-coded YouTube channels to a user-manageable catalog with 12 creators, duration/visibility filtering, sort + length filters, a Random Ride button, and a per-creator page showing their playlists.

**Architecture:** The existing RSS-only content layer gains a second, richer source: YouTube's public channel/playlist pages embed a `ytInitialData` JSON blob containing video durations and a public-only listing. `YouTubePageParser` extracts that; `YouTubeContentRepository` merges page data (durations, ~30 videos) with RSS data (exact publish timestamps) and applies the filters, degrading to RSS-only and then to the on-disk cache when fetches fail. The channel list itself moves from the `ChannelConfig` object into a new Room table so the user can add and hide sources at runtime.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, kotlinx.coroutines, `org.json` (bundled with Android — no new dependencies), Robolectric + JUnit4 for unit tests.

## Global Constraints

- **No new Gradle dependencies.** JSON parsing uses `org.json` (already used by `ContentCache`); HTTP uses `HttpURLConnection` via the existing `FeedFetcher` interface.
- **No YouTube API key.** All content comes from public RSS feeds and public page HTML.
- **Build JDK:** Homebrew `openjdk@21`. Run gradle with `JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdkhome` — see the exact command in Task 1 Step 3. The system JDK (26) breaks Kotlin compilation.
- **No emulator is installed.** Only `src/test` (Robolectric) tests can be run. Do not add `src/androidTest` tests; do not attempt to run them.
- **Minimum class length:** 600 seconds. Videos with a *known* duration below that are dropped; videos with `durationSec == null` are always kept.
- **Category names:** the enum `ContentCategory` has exactly two values after this work: `Workout` and `Scenic`. The old value `Rides` is renamed (not added alongside).
- **Every network fetch retries exactly once** before being treated as failed — YouTube's endpoints return intermittent 404/500 responses that succeed on retry (verified live).
- **Parser resilience:** any unexpected page structure must degrade to "page fetch failed", never throw out of the repository. Existing behavior — cached content plus the `refreshFailed` banner instead of an empty screen — is preserved.
- Follow existing file conventions: KDoc on every new public class explaining *why*, package layout mirroring `core/…` and `ui/…`, tests as `@RunWith(AndroidJUnit4::class)` in `app/src/test/java/…`.

## Deviation from the spec

The spec proposed caching playlist *metadata* to a second cache file per creator. This plan
doesn't: the creator page fetches a creator's playlist list live and shows the page without
playlist shelves if that fetch fails, which is a strictly simpler failure mode than a stale
playlist list that no longer matches the creator's page. Video content for each source is
still cached exactly as before. If offline playlist browsing turns out to matter, it's an
additive change to `ContentCache` later.

## Verified YouTube page structure (do not re-derive)

All of this was confirmed live against real channels on 2026-07-19. The page HTML contains:

```
var ytInitialData = {...};</script>
```

Inside that JSON, every video or playlist tile is a `lockupViewModel` object (search the tree recursively — the surrounding structure differs between page types, the tile shape does not):

| Field | JSON path within `lockupViewModel` |
|---|---|
| id | `contentId` (video id, or `PL…` playlist id) |
| kind | `contentType` = `LOCKUP_CONTENT_TYPE_VIDEO` or `LOCKUP_CONTENT_TYPE_PLAYLIST` |
| title | `metadata.lockupMetadataViewModel.title.content` |
| video thumbnail | `contentImage.thumbnailViewModel.image.sources[0].url` |
| playlist thumbnail | `contentImage.collectionThumbnailViewModel.primaryThumbnail.thumbnailViewModel.image.sources[0].url` |
| duration (`"18:23"`) | `contentImage.thumbnailViewModel.overlays[].thumbnailBottomOverlayViewModel.badges[].thumbnailBadgeViewModel.text` |
| playlist size (`"4 videos"`) | `contentImage.collectionThumbnailViewModel.primaryThumbnail.thumbnailViewModel.overlays[].thumbnailOverlayBadgeViewModel.thumbnailBadges[].thumbnailBadgeViewModel.text` |
| relative publish date (`"4 months ago"`) | `metadata.lockupMetadataViewModel.metadata.contentMetadataViewModel.metadataRows[].metadataParts[].text.content` — the part matching `/^\d+ \w+ ago$/` |

URLs used:

- Channel videos: `https://www.youtube.com/channel/<UC…>/videos`
- Channel playlists: `https://www.youtube.com/channel/<UC…>/playlists`
- Playlist contents: `https://www.youtube.com/playlist?list=<PL…>`
- Channel RSS: `https://www.youtube.com/feeds/videos.xml?channel_id=<UC…>`
- Playlist RSS: `https://www.youtube.com/feeds/videos.xml?playlist_id=<PL…>`
- Handle resolution: fetch `https://www.youtube.com/@handle` (or any channel URL) and read `"externalId":"UC…"`; display name comes from `<title>Name - YouTube</title>`.

Fetching works without a custom User-Agent, but send a desktop UA anyway to avoid being served a mobile/consent variant.

## File Structure

**Created:**

| File | Responsibility |
|---|---|
| `core/content/ContentSource.kt` | Room entity + `ContentSourceType` enum for a configured channel/playlist |
| `core/content/ContentSourceDao.kt` | Room DAO for `content_sources` |
| `core/content/ContentSourceRepository.kt` | Seeding, observing, adding, hiding, deleting sources |
| `core/content/YouTubePageParser.kt` | `ytInitialData` → videos / playlist summaries |
| `core/content/RelativeTime.kt` | `"4 months ago"` → approximate epoch millis |
| `core/content/DurationText.kt` | `"18:23"` → seconds |
| `core/content/PlaylistSummary.kt` | One playlist tile (id, title, thumbnail, video count) |
| `core/content/ChannelHandleResolver.kt` | Pasted URL/@handle → resolved source |
| `core/content/CreatorContent.kt` | Creator page payload (latest videos + playlist rows) |
| `ui/classes/ClassFilters.kt` | Filter/sort enums + pure filtering functions |
| `ui/creator/CreatorUiState.kt`, `ui/creator/CreatorViewModel.kt`, `ui/creator/CreatorScreen.kt` | Creator click-through page |
| `ui/sources/ContentSourcesViewModel.kt`, `ui/sources/ContentSourcesScreen.kt` | Content Sources settings screen |
| `ui/classes/VideoCard.kt` | `VideoCard` + badges extracted from `ClassesScreen` for reuse by the creator page |

**Modified:** `core/content/ChannelConfig.kt` (becomes the seed list), `core/content/ChannelSection.kt` (`Rides`→`Workout`, plus `sourceId`), `core/content/FeedFetcher.kt` (UA + retry helper), `core/content/YouTubeContentRepository.kt` (merge pipeline), `core/data/OpenRideDatabase.kt` (v5 + migration), `AppContainer.kt`, `ui/navigation/Destinations.kt`, `ui/navigation/OpenRideNavHost.kt`, `ui/main/MainScaffold.kt`, `ui/classes/ClassesUiState.kt`, `ui/classes/ClassesViewModel.kt`, `ui/classes/ClassesScreen.kt`, `ui/profile/ProfileTabScreen.kt`.

---

## Task 1: Category rename and the expanded seed catalog

**Files:**
- Modify: `app/src/main/java/dev/digitalducktape/openride/core/content/ChannelSection.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/core/content/ChannelConfig.kt`
- Modify: `app/src/test/java/dev/digitalducktape/openride/core/content/YouTubeContentRepositoryTest.kt`
- Modify: `app/src/test/java/dev/digitalducktape/openride/ui/classes/ClassesViewModelTest.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/ChannelConfigTest.kt` (create)

**Interfaces:**
- Produces: `ContentCategory.Workout`, `ContentCategory.Scenic`; `ChannelConfig.Channel(id, displayName, handle, category)` unchanged in shape; `ChannelConfig.ALL: List<Channel>` with 12 entries.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dev/digitalducktape/openride/core/content/ChannelConfigTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelConfigTest {

    @Test
    fun `seed catalog contains all twelve curated channels`() {
        assertEquals(12, ChannelConfig.ALL.size)
    }

    @Test
    fun `every seed channel has a resolved UC channel id and a handle`() {
        ChannelConfig.ALL.forEach { channel ->
            assertTrue("${channel.displayName} id", channel.id.startsWith("UC"))
            assertTrue("${channel.displayName} handle", channel.handle.isNotBlank())
        }
    }

    @Test
    fun `channel ids are unique`() {
        assertEquals(ChannelConfig.ALL.size, ChannelConfig.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `scenic and workout are the only categories in use`() {
        assertEquals(3, ChannelConfig.ALL.count { it.category == ContentCategory.Scenic })
        assertEquals(9, ChannelConfig.ALL.count { it.category == ContentCategory.Workout })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /Users/ed/sources/openride
JAVA_HOME=$(/opt/homebrew/bin/brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:testDebugUnitTest --tests '*ChannelConfigTest*'
```

Expected: FAIL — `ContentCategory.Workout` unresolved reference, and `ALL.size` is 4.

(If that `JAVA_HOME` path does not exist, run `/opt/homebrew/bin/brew --prefix openjdk@21` and use `<prefix>/libexec/openjdk.jdk/Contents/Home`. Use this same command form for every test run in this plan; it is abbreviated as `./gradlew :app:testDebugUnitTest …` below.)

- [ ] **Step 3: Rename the category**

In `core/content/ChannelSection.kt`, replace the enum:

```kotlin
/**
 * Which curated grouping a source belongs to. These are the two browsable class categories
 * on the Classes tab — "Just Ride" is the Home tab's quick start and "Random" is a button,
 * so neither is a category here.
 */
enum class ContentCategory {
    Scenic,
    Workout,
}
```

Then fix every reference to `ContentCategory.Rides` across the codebase:

```bash
grep -rln "ContentCategory.Rides" app/src | xargs sed -i '' 's/ContentCategory\.Rides/ContentCategory.Workout/g'
```

- [ ] **Step 4: Replace the seed catalog**

Replace the body of `core/content/ChannelConfig.kt` (keep the file's existing KDoc about how ids were resolved, updating the "all four" wording to "all twelve"):

```kotlin
object ChannelConfig {
    data class Channel(
        val id: String,
        val displayName: String,
        val handle: String,
        val category: ContentCategory,
    )

    val SCENIC = listOf(
        Channel("UCbQiCVLFtWLOjVO0YuUqo7Q", "Virtual Cycling Workouts", "@VirtualCyclingWorkouts", ContentCategory.Scenic),
        Channel("UCVbBtdw-_SCqGDs6-_awaDg", "Indoor Cycling Videos", "@IndoorCyclingVideos", ContentCategory.Scenic),
        Channel("UCWXNvnztTNz99SYgTnyltwg", "Bike the World", "@biketheworld", ContentCategory.Scenic),
    )

    val WORKOUT = listOf(
        Channel("UCbSfwHIgMnaINnkmDaFzqDw", "Ride With Alina", "@ridewithalina", ContentCategory.Workout),
        Channel("UCo0Pbk8bCutBN-Yf_404Kvw", "Gabriella Lynn", "@GabriellaLynnn", ContentCategory.Workout),
        Channel("UChY_9WJx0saa0St48lSdytQ", "Kaleigh Cohen Cycling", "@kaleigh", ContentCategory.Workout),
        Channel("UCyEw-nPmOgo18LPK7wkkdtQ", "Kirsten Allen", "@KirstenAllen", ContentCategory.Workout),
        Channel("UCMEiuHFc7nHTDq2_KzLu0bw", "GCN Training", "@GCNTraining", ContentCategory.Workout),
        Channel("UC0d6o9_OVUZdC4g3ci1_UXA", "The Spin Junkie", "@TheSpinJunkie", ContentCategory.Workout),
        Channel("UCDomKTwMIX0U_cMQzUweggQ", "Kristina Girod", "@KristinaGirod", ContentCategory.Workout),
        Channel("UCH9KY-e1kLOMQ2NcDvXMO3w", "Joe Alvarado", "@JoeAlvarado", ContentCategory.Workout),
        Channel("UCU8VLagwhLbab5RQhjlzY9A", "TaG Cycling", "@TaGCycling1", ContentCategory.Workout),
    )

    /** Seed order for the `content_sources` table: scenic rows first, then workouts. */
    val ALL = SCENIC + WORKOUT
}
```

- [ ] **Step 5: Run the full unit-test suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS, including the 4 new `ChannelConfigTest` cases. Fix any test that asserted on the old `Rides` name.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/core/content app/src/test/java/dev/digitalducktape/openride
git commit -m "Rename Rides category to Workout and seed eight new creator channels"
```

---

## Task 2: Duration and relative-date text parsing

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/DurationText.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/RelativeTime.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/DurationTextTest.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/RelativeTimeTest.kt`

**Interfaces:**
- Produces: `DurationText.toSeconds(text: String): Int?`; `RelativeTime.toEpochMs(text: String, nowEpochMs: Long): Long?`. Both used by `YouTubePageParser` in Task 3.

- [ ] **Step 1: Write the failing tests**

`DurationTextTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationTextTest {

    @Test
    fun `parses minutes and seconds`() {
        assertEquals(1103, DurationText.toSeconds("18:23"))
    }

    @Test
    fun `parses hours minutes and seconds`() {
        assertEquals(3723, DurationText.toSeconds("1:02:03"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(600, DurationText.toSeconds(" 10:00 "))
    }

    @Test
    fun `returns null for non-duration badges`() {
        assertNull(DurationText.toSeconds("4 videos"))
        assertNull(DurationText.toSeconds("LIVE"))
        assertNull(DurationText.toSeconds(""))
        assertNull(DurationText.toSeconds("1:2:3:4"))
    }
}
```

`RelativeTimeTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `parses days ago`() {
        assertEquals(now - 3L * 86_400_000L, RelativeTime.toEpochMs("3 days ago", now))
    }

    @Test
    fun `parses singular units`() {
        assertEquals(now - 86_400_000L, RelativeTime.toEpochMs("1 day ago", now))
    }

    @Test
    fun `parses weeks months and years`() {
        assertEquals(now - 2L * 7 * 86_400_000L, RelativeTime.toEpochMs("2 weeks ago", now))
        assertEquals(now - 4L * 30 * 86_400_000L, RelativeTime.toEpochMs("4 months ago", now))
        assertEquals(now - 5L * 365 * 86_400_000L, RelativeTime.toEpochMs("5 years ago", now))
    }

    @Test
    fun `orders newer before older`() {
        val newer = RelativeTime.toEpochMs("2 days ago", now)!!
        val older = RelativeTime.toEpochMs("2 months ago", now)!!
        assertTrue(newer > older)
    }

    @Test
    fun `returns null for text that is not a relative time`() {
        assertNull(RelativeTime.toEpochMs("16K views", now))
        assertNull(RelativeTime.toEpochMs("The Spin Junkie", now))
        assertNull(RelativeTime.toEpochMs("Streamed live", now))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests '*DurationTextTest*' --tests '*RelativeTimeTest*'
```

Expected: FAIL — `Unresolved reference: DurationText` / `RelativeTime`.

- [ ] **Step 3: Implement both parsers**

`core/content/DurationText.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

/**
 * Parses YouTube's thumbnail duration badge text (`"18:23"`, `"1:02:03"`) into seconds.
 *
 * The same badge slot also carries non-duration text on some tiles (`"4 videos"` on a
 * playlist, `"LIVE"` on a stream), so anything that isn't a colon-separated clock returns
 * `null` — the caller treats that as "duration unknown", never as zero.
 */
object DurationText {
    fun toSeconds(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        return when (numbers.size) {
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        }
    }
}
```

`core/content/RelativeTime.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

/**
 * Turns YouTube's relative publish label (`"4 months ago"`) into an approximate epoch
 * timestamp.
 *
 * The channel page only ever states age this coarsely. Exact timestamps come from the RSS
 * feed, but that feed only covers a channel's most recent 15 videos — everything older is
 * ordered by this approximation instead. Months and years use flat 30/365-day lengths: the
 * result is only ever used for sorting a browse list, so calendar accuracy would buy nothing.
 */
object RelativeTime {
    private val PATTERN = Regex("""^(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago$""")

    fun toEpochMs(text: String, nowEpochMs: Long): Long? {
        val match = PATTERN.find(text.trim().lowercase()) ?: return null
        val count = match.groupValues[1].toLongOrNull() ?: return null
        val unitMs = when (match.groupValues[2]) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            "week" -> 7L * 86_400_000L
            "month" -> 30L * 86_400_000L
            else -> 365L * 86_400_000L
        }
        return nowEpochMs - count * unitMs
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests '*DurationTextTest*' --tests '*RelativeTimeTest*'
```

Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/core/content/DurationText.kt \
        app/src/main/java/dev/digitalducktape/openride/core/content/RelativeTime.kt \
        app/src/test/java/dev/digitalducktape/openride/core/content/DurationTextTest.kt \
        app/src/test/java/dev/digitalducktape/openride/core/content/RelativeTimeTest.kt
git commit -m "Parse YouTube duration badges and relative publish dates"
```

---

## Task 3: YouTubePageParser

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/PlaylistSummary.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/YouTubePageParser.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/YouTubePageParserTest.kt`
- Test fixtures: `app/src/test/resources/fixtures/channel_videos_page.html`, `app/src/test/resources/fixtures/channel_playlists_page.html`

**Interfaces:**
- Consumes: `DurationText.toSeconds`, `RelativeTime.toEpochMs` (Task 2); `Video` (existing).
- Produces:
  - `data class PlaylistSummary(val id: String, val title: String, val thumbnailUrl: String, val videoCount: Int?)`
  - `class ContentParseException(message: String) : Exception(message)`
  - `class YouTubePageParser(private val nowEpochMs: () -> Long = System::currentTimeMillis)` with
    `fun parseVideos(html: String, channelName: String): List<Video>` and
    `fun parsePlaylists(html: String): List<PlaylistSummary>`, both throwing `ContentParseException` when `ytInitialData` is absent or unreadable.

- [ ] **Step 1: Create the test fixtures**

The fixtures mirror the real page structure documented in "Verified YouTube page structure" above, trimmed to the fields the parser reads.

`app/src/test/resources/fixtures/channel_videos_page.html`:

```html
<!DOCTYPE html><html><body>
<script nonce="x">var ytInitialData = {"contents":{"tabs":[{"tabRenderer":{"content":{"richGridRenderer":{"contents":[
{"richItemRenderer":{"content":{"lockupViewModel":{
  "contentId":"vidLong00001","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
  "contentImage":{"thumbnailViewModel":{
    "image":{"sources":[{"url":"https://i.ytimg.com/vi/vidLong00001/hqdefault.jpg","width":168}]},
    "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"18:23"}}]}}]}},
  "metadata":{"lockupMetadataViewModel":{
    "title":{"content":"Quick HIIT Workout | Instant Inferno"},
    "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[
      {"text":{"content":"16K views"}},{"text":{"content":"4 months ago"}}]}]}}}}}}},
{"richItemRenderer":{"content":{"lockupViewModel":{
  "contentId":"vidShort0002","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
  "contentImage":{"thumbnailViewModel":{
    "image":{"sources":[{"url":"https://i.ytimg.com/vi/vidShort0002/hqdefault.jpg","width":168}]},
    "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"5:04"}}]}}]}},
  "metadata":{"lockupMetadataViewModel":{
    "title":{"content":"Five Minute Warmup"},
    "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[
      {"text":{"content":"2K views"}},{"text":{"content":"2 days ago"}}]}]}}}}}}},
{"richItemRenderer":{"content":{"lockupViewModel":{
  "contentId":"vidNoDur0003","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
  "contentImage":{"thumbnailViewModel":{
    "image":{"sources":[{"url":"https://i.ytimg.com/vi/vidNoDur0003/hqdefault.jpg","width":168}]},
    "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"LIVE"}}]}}]}},
  "metadata":{"lockupMetadataViewModel":{
    "title":{"content":"Live Ride Along"},
    "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[
      {"text":{"content":"900 watching"}}]}]}}}}}}}
]}}}}]}};</script>
</body></html>
```

`app/src/test/resources/fixtures/channel_playlists_page.html`:

```html
<!DOCTYPE html><html><body>
<script nonce="x">var ytInitialData = {"contents":{"items":[
{"lockupViewModel":{
  "contentId":"PLtheme000000001","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST",
  "contentImage":{"collectionThumbnailViewModel":{"primaryThumbnail":{"thumbnailViewModel":{
    "image":{"sources":[{"url":"https://i.ytimg.com/vi/coverA/hqdefault.jpg","width":168}]},
    "overlays":[{"thumbnailOverlayBadgeViewModel":{"thumbnailBadges":[
      {"thumbnailBadgeViewModel":{"text":"4 videos"}}]}}]}}}},
  "metadata":{"lockupMetadataViewModel":{"title":{"content":"Love & Hate Spin Classes"}}}}},
{"lockupViewModel":{
  "contentId":"PLtheme000000002","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST",
  "contentImage":{"collectionThumbnailViewModel":{"primaryThumbnail":{"thumbnailViewModel":{
    "image":{"sources":[{"url":"https://i.ytimg.com/vi/coverB/hqdefault.jpg","width":168}]},
    "overlays":[]}}}},
  "metadata":{"lockupMetadataViewModel":{"title":{"content":"Climb Series"}}}}},
{"lockupViewModel":{
  "contentId":"vidNotAList1","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
  "contentImage":{"thumbnailViewModel":{
    "image":{"sources":[{"url":"https://i.ytimg.com/vi/vidNotAList1/hqdefault.jpg","width":168}]},
    "overlays":[]}},
  "metadata":{"lockupMetadataViewModel":{"title":{"content":"A video on the playlists tab"}}}}}
]}};</script>
</body></html>
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/dev/digitalducktape/openride/core/content/YouTubePageParserTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePageParserTest {

    private val now = 1_700_000_000_000L
    private val parser = YouTubePageParser(nowEpochMs = { now })

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    @Test
    fun `parses every video tile on a channel videos page`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(listOf("vidLong00001", "vidShort0002", "vidNoDur0003"), videos.map { it.id })
        assertEquals("Quick HIIT Workout | Instant Inferno", videos[0].title)
        assertEquals("Test Channel", videos[0].channelName)
        assertTrue(videos[0].thumbnailUrl.startsWith("https://i.ytimg.com/vi/vidLong00001/"))
    }

    @Test
    fun `reads the duration badge as seconds`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(1103, videos[0].durationSec)
        assertEquals(304, videos[1].durationSec)
    }

    @Test
    fun `leaves duration null when the badge is not a clock`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertNull(videos[2].durationSec)
    }

    @Test
    fun `approximates published time from the relative label`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(now - 4L * 30 * 86_400_000L, videos[0].publishedEpochMs)
        assertEquals(now - 2L * 86_400_000L, videos[1].publishedEpochMs)
    }

    @Test
    fun `published time is zero when no relative label is present`() {
        val videos = parser.parseVideos(fixture("channel_videos_page.html"), "Test Channel")

        assertEquals(0L, videos[2].publishedEpochMs)
    }

    @Test
    fun `parses playlist tiles and ignores video tiles`() {
        val playlists = parser.parsePlaylists(fixture("channel_playlists_page.html"))

        assertEquals(listOf("PLtheme000000001", "PLtheme000000002"), playlists.map { it.id })
        assertEquals("Love & Hate Spin Classes", playlists[0].title)
        assertEquals(4, playlists[0].videoCount)
        assertTrue(playlists[0].thumbnailUrl.contains("coverA"))
    }

    @Test
    fun `playlist video count is null when the badge is missing`() {
        val playlists = parser.parsePlaylists(fixture("channel_playlists_page.html"))

        assertNull(playlists[1].videoCount)
    }

    @Test
    fun `parseVideos ignores playlist tiles`() {
        val videos = parser.parseVideos(fixture("channel_playlists_page.html"), "Test Channel")

        assertEquals(listOf("vidNotAList1"), videos.map { it.id })
    }

    @Test
    fun `throws when the page has no ytInitialData`() {
        assertThrows(ContentParseException::class.java) {
            parser.parseVideos("<html><body>signed out wall</body></html>", "Test Channel")
        }
    }

    @Test
    fun `throws when ytInitialData is not valid json`() {
        val html = """<script>var ytInitialData = {"broken":;</script>"""
        assertThrows(ContentParseException::class.java) { parser.parseVideos(html, "Test Channel") }
    }

    @Test
    fun `returns an empty list for a well-formed page with no tiles`() {
        val html = """<script>var ytInitialData = {"contents":{"items":[]}};</script>"""

        assertEquals(emptyList<Video>(), parser.parseVideos(html, "Test Channel"))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*YouTubePageParserTest*'
```

Expected: FAIL — `Unresolved reference: YouTubePageParser`.

- [ ] **Step 4: Implement PlaylistSummary**

`core/content/PlaylistSummary.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

/**
 * One of a creator's curated playlists, as shown on their creator page.
 *
 * @param id the `PL…` playlist id — also what fetches the playlist's own videos.
 * @param videoCount how many videos the playlist tile claims, when the badge is present.
 *   Display-only; the fetched list is the source of truth for what the rider can actually ride.
 */
data class PlaylistSummary(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val videoCount: Int?,
)
```

- [ ] **Step 5: Implement YouTubePageParser**

`core/content/YouTubePageParser.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import org.json.JSONArray
import org.json.JSONObject

/** A YouTube page couldn't be read as content — treated exactly like a failed fetch. */
class ContentParseException(message: String) : Exception(message)

/**
 * Extracts videos and playlists from a public YouTube page's embedded `ytInitialData` JSON.
 *
 * This is the app's only source of video *duration* and of a public-only listing: the RSS
 * feed the content layer started with carries neither, so the "no classes under 10 minutes"
 * and "no members-only classes" rules are impossible without it (and adding the YouTube Data
 * API would mean an API key, which this app deliberately doesn't use).
 *
 * The one structure this relies on is the `lockupViewModel` tile, which YouTube uses
 * identically on a channel's `/videos` tab, its `/playlists` tab, and a playlist's own page —
 * so [findLockups] walks the whole JSON tree looking for those tiles rather than following
 * the page-specific scaffolding around them, which differs per page type and changes more
 * often. Anything unexpected raises [ContentParseException], which the repository treats as
 * a failed fetch: filtering degrades, the catalog does not disappear.
 */
class YouTubePageParser(private val nowEpochMs: () -> Long = System::currentTimeMillis) {

    fun parseVideos(html: String, channelName: String): List<Video> =
        findLockups(html)
            .filter { it.optString(KEY_CONTENT_TYPE) == TYPE_VIDEO }
            .mapNotNull { lockup -> toVideo(lockup, channelName) }

    fun parsePlaylists(html: String): List<PlaylistSummary> =
        findLockups(html)
            .filter { it.optString(KEY_CONTENT_TYPE) == TYPE_PLAYLIST }
            .mapNotNull { lockup -> toPlaylist(lockup) }

    private fun toVideo(lockup: JSONObject, channelName: String): Video? {
        val id = lockup.optString(KEY_CONTENT_ID).takeIf { it.isNotEmpty() } ?: return null
        val metadata = lockup.optJSONObject(KEY_METADATA)?.optJSONObject(KEY_METADATA_VM)
        val title = metadata?.optJSONObject(KEY_TITLE)?.optString(KEY_CONTENT)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val thumbnail = lockup.optJSONObject(KEY_CONTENT_IMAGE)?.optJSONObject(KEY_THUMBNAIL_VM)

        return Video(
            id = id,
            title = title,
            thumbnailUrl = firstImageUrl(thumbnail) ?: defaultThumbnailUrl(id),
            channelName = channelName,
            durationSec = durationBadges(thumbnail).firstNotNullOfOrNull(DurationText::toSeconds),
            publishedEpochMs = publishedEpochMs(metadata),
        )
    }

    private fun toPlaylist(lockup: JSONObject): PlaylistSummary? {
        val id = lockup.optString(KEY_CONTENT_ID).takeIf { it.isNotEmpty() } ?: return null
        val title = lockup.optJSONObject(KEY_METADATA)
            ?.optJSONObject(KEY_METADATA_VM)
            ?.optJSONObject(KEY_TITLE)
            ?.optString(KEY_CONTENT)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val thumbnail = lockup.optJSONObject(KEY_CONTENT_IMAGE)
            ?.optJSONObject(KEY_COLLECTION_VM)
            ?.optJSONObject(KEY_PRIMARY_THUMBNAIL)
            ?.optJSONObject(KEY_THUMBNAIL_VM)

        return PlaylistSummary(
            id = id,
            title = title,
            thumbnailUrl = firstImageUrl(thumbnail).orEmpty(),
            videoCount = countBadges(thumbnail).firstNotNullOfOrNull(::parseVideoCount),
        )
    }

    /** `"4 videos"` → 4. */
    private fun parseVideoCount(text: String): Int? =
        Regex("""^(\d+)\s+video""").find(text.trim().lowercase())?.groupValues?.get(1)?.toIntOrNull()

    private fun publishedEpochMs(metadata: JSONObject?): Long {
        val rows = metadata?.optJSONObject(KEY_METADATA)
            ?.optJSONObject(KEY_CONTENT_METADATA_VM)
            ?.optJSONArray(KEY_METADATA_ROWS)
            ?: return 0L
        val now = nowEpochMs()
        for (row in rows.objects()) {
            for (part in row.optJSONArray(KEY_METADATA_PARTS).orEmptyArray().objects()) {
                val text = part.optJSONObject(KEY_TEXT)?.optString(KEY_CONTENT).orEmpty()
                RelativeTime.toEpochMs(text, now)?.let { return it }
            }
        }
        return 0L
    }

    private fun firstImageUrl(thumbnail: JSONObject?): String? =
        thumbnail?.optJSONObject(KEY_IMAGE)
            ?.optJSONArray(KEY_SOURCES)
            .orEmptyArray()
            .objects()
            .firstNotNullOfOrNull { it.optString(KEY_URL).takeIf { url -> url.isNotEmpty() } }

    /** Duration text lives in the bottom-overlay badges of a video thumbnail. */
    private fun durationBadges(thumbnail: JSONObject?): List<String> =
        thumbnail?.optJSONArray(KEY_OVERLAYS).orEmptyArray().objects().flatMap { overlay ->
            overlay.optJSONObject(KEY_BOTTOM_OVERLAY_VM)
                ?.optJSONArray(KEY_BADGES)
                .orEmptyArray()
                .objects()
                .mapNotNull { it.optJSONObject(KEY_BADGE_VM)?.optString(KEY_TEXT_FIELD) }
        }

    /** Video-count text lives in the badge overlay of a playlist's collection thumbnail. */
    private fun countBadges(thumbnail: JSONObject?): List<String> =
        thumbnail?.optJSONArray(KEY_OVERLAYS).orEmptyArray().objects().flatMap { overlay ->
            overlay.optJSONObject(KEY_BADGE_OVERLAY_VM)
                ?.optJSONArray(KEY_THUMBNAIL_BADGES)
                .orEmptyArray()
                .objects()
                .mapNotNull { it.optJSONObject(KEY_BADGE_VM)?.optString(KEY_TEXT_FIELD) }
        }

    private fun findLockups(html: String): List<JSONObject> {
        val json = extractInitialData(html)
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ContentParseException("ytInitialData was not readable JSON")
        }
        val lockups = mutableListOf<JSONObject>()
        collectLockups(root, lockups)
        return lockups
    }

    private fun collectLockups(node: Any?, into: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(KEY_LOCKUP_VM)?.let { into.add(it) }
                node.keys().forEach { key -> collectLockups(node.opt(key), into) }
            }
            is JSONArray -> (0 until node.length()).forEach { collectLockups(node.opt(it), into) }
        }
    }

    /**
     * Pulls the `var ytInitialData = {...};` object out of the page by brace-matching from the
     * opening `{`, rather than with a regex — the blob is ~1 MB of JSON containing arbitrary
     * `}` and `</script>` sequences inside string values, so a lazy regex match truncates it.
     */
    private fun extractInitialData(html: String): String {
        val marker = MARKERS.firstNotNullOfOrNull { marker ->
            html.indexOf(marker).takeIf { it >= 0 }?.let { it + marker.length }
        } ?: throw ContentParseException("no ytInitialData in page")

        val start = html.indexOf('{', marker)
        if (start < 0) throw ContentParseException("ytInitialData had no object body")

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, index + 1)
                }
            }
        }
        throw ContentParseException("ytInitialData object was never closed")
    }

    private fun JSONArray?.orEmptyArray(): JSONArray = this ?: JSONArray()

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun defaultThumbnailUrl(videoId: String) =
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private companion object {
        val MARKERS = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ")
        const val TYPE_VIDEO = "LOCKUP_CONTENT_TYPE_VIDEO"
        const val TYPE_PLAYLIST = "LOCKUP_CONTENT_TYPE_PLAYLIST"
        const val KEY_LOCKUP_VM = "lockupViewModel"
        const val KEY_CONTENT_ID = "contentId"
        const val KEY_CONTENT_TYPE = "contentType"
        const val KEY_CONTENT_IMAGE = "contentImage"
        const val KEY_THUMBNAIL_VM = "thumbnailViewModel"
        const val KEY_COLLECTION_VM = "collectionThumbnailViewModel"
        const val KEY_PRIMARY_THUMBNAIL = "primaryThumbnail"
        const val KEY_IMAGE = "image"
        const val KEY_SOURCES = "sources"
        const val KEY_URL = "url"
        const val KEY_OVERLAYS = "overlays"
        const val KEY_BOTTOM_OVERLAY_VM = "thumbnailBottomOverlayViewModel"
        const val KEY_BADGE_OVERLAY_VM = "thumbnailOverlayBadgeViewModel"
        const val KEY_THUMBNAIL_BADGES = "thumbnailBadges"
        const val KEY_BADGES = "badges"
        const val KEY_BADGE_VM = "thumbnailBadgeViewModel"
        const val KEY_TEXT_FIELD = "text"
        const val KEY_METADATA = "metadata"
        const val KEY_METADATA_VM = "lockupMetadataViewModel"
        const val KEY_CONTENT_METADATA_VM = "contentMetadataViewModel"
        const val KEY_METADATA_ROWS = "metadataRows"
        const val KEY_METADATA_PARTS = "metadataParts"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_CONTENT = "content"
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*YouTubePageParserTest*'
```

Expected: PASS (11 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/core/content/YouTubePageParser.kt \
        app/src/main/java/dev/digitalducktape/openride/core/content/PlaylistSummary.kt \
        app/src/test/java/dev/digitalducktape/openride/core/content/YouTubePageParserTest.kt \
        app/src/test/resources/fixtures
git commit -m "Parse videos and playlists out of YouTube page ytInitialData"
```

---

## Task 4: Fetching — text fetches, retry, and handle resolution

**Files:**
- Modify: `app/src/main/java/dev/digitalducktape/openride/core/content/FeedFetcher.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/ChannelHandleResolver.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/RetryingFetchTest.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/ChannelHandleResolverTest.kt`

**Interfaces:**
- Consumes: `FeedFetcher.fetch(url: String): InputStream` (existing interface, unchanged signature).
- Produces:
  - `fun FeedFetcher.fetchText(url: String): String` — fetches and decodes UTF-8, closing the stream.
  - `fun <T> retryingOnce(block: () -> T): T` — top-level function in `FeedFetcher.kt`.
  - `class ChannelHandleResolver(fetcher)` with `suspend fun resolve(input: String): Result<ResolvedSource>`.

> **Task ordering:** this task uses `ContentSourceType` and `ResolvedSource`, both declared in
> `core/content/ContentSource.kt` in **Task 5**. Do Task 5 before this one. (They are listed in
> this order because Task 5's *tests* read more naturally once the resolver exists, but only
> Task 5's code is a compile-time prerequisite.)

- [ ] **Step 1: Write the failing tests**

`RetryingFetchTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryingFetchTest {

    @Test
    fun `returns the first successful result without retrying`() {
        var calls = 0
        val result = retryingOnce {
            calls++
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries once after a failure and returns the second result`() {
        var calls = 0
        val result = retryingOnce {
            calls++
            if (calls == 1) throw IOException("HTTP 500") else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `rethrows when both attempts fail`() {
        var calls = 0
        assertThrows(IOException::class.java) {
            retryingOnce<String> {
                calls++
                throw IOException("HTTP 404")
            }
        }
        assertEquals(2, calls)
    }

    @Test
    fun `fetchText decodes the stream as UTF-8`() {
        val fetcher = FeedFetcher { "héllo".byteInputStream() }

        assertEquals("héllo", fetcher.fetchText("https://example.test"))
    }
}
```

`ChannelHandleResolverTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelHandleResolverTest {

    private val channelPage = """
        <html><head><title>Kaleigh Cohen Cycling - YouTube</title></head>
        <body><script>{"externalId":"UChY_9WJx0saa0St48lSdytQ","other":1}</script></body></html>
    """.trimIndent()

    private val playlistPage = """
        <html><head><title>Climb Series - YouTube</title></head><body></body></html>
    """.trimIndent()

    @Test
    fun `resolves a bare handle to its channel id and name`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        val resolved = resolver.resolve("@kaleigh").getOrThrow()

        assertEquals(ContentSourceType.CHANNEL, resolved.sourceType)
        assertEquals("UChY_9WJx0saa0St48lSdytQ", resolved.youtubeId)
        assertEquals("Kaleigh Cohen Cycling", resolved.displayName)
        assertEquals("https://www.youtube.com/@kaleigh", requested)
    }

    @Test
    fun `resolves a handle typed without the at sign`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        resolver.resolve("kaleigh").getOrThrow()

        assertEquals("https://www.youtube.com/@kaleigh", requested)
    }

    @Test
    fun `resolves a full channel URL`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { channelPage.byteInputStream() })

        val resolved = resolver.resolve("https://www.youtube.com/@kaleigh/videos").getOrThrow()

        assertEquals("UChY_9WJx0saa0St48lSdytQ", resolved.youtubeId)
    }

    @Test
    fun `resolves a legacy slash-c URL`() = runTest {
        var requested = ""
        val resolver = ChannelHandleResolver(FeedFetcher { url ->
            requested = url
            channelPage.byteInputStream()
        })

        resolver.resolve("https://www.youtube.com/c/BiketheWorld").getOrThrow()

        assertEquals("https://www.youtube.com/c/BiketheWorld", requested)
    }

    @Test
    fun `resolves a playlist URL without fetching a channel id`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { playlistPage.byteInputStream() })

        val resolved = resolver
            .resolve("https://www.youtube.com/playlist?list=PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs")
            .getOrThrow()

        assertEquals(ContentSourceType.PLAYLIST, resolved.sourceType)
        assertEquals("PLZC-drDfHcp1AvqBguYXNmTMcwYDRcuTs", resolved.youtubeId)
        assertEquals("Climb Series", resolved.displayName)
    }

    @Test
    fun `fails when the page has no channel id`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { "<html>nope</html>".byteInputStream() })

        val result = resolver.resolve("@ghost")

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when the page cannot be fetched`() = runTest {
        val resolver = ChannelHandleResolver(FeedFetcher { throw IOException("offline") })

        assertTrue(resolver.resolve("@kaleigh").isFailure)
    }

    @Test
    fun `fails on blank input without fetching`() = runTest {
        var calls = 0
        val resolver = ChannelHandleResolver(FeedFetcher {
            calls++
            channelPage.byteInputStream()
        })

        assertTrue(resolver.resolve("   ").isFailure)
        assertEquals(0, calls)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests '*RetryingFetchTest*' --tests '*ChannelHandleResolverTest*'
```

Expected: FAIL — `Unresolved reference: retryingOnce` / `fetchText` / `ChannelHandleResolver`.

- [ ] **Step 3: Add fetchText, retryingOnce, and a desktop User-Agent**

Append to `core/content/FeedFetcher.kt` (and add `import java.io.IOException` if not already imported):

```kotlin
/** Fetches [url] and decodes it as UTF-8 text, always closing the stream. */
fun FeedFetcher.fetchText(url: String): String =
    fetch(url).use { it.bufferedReader().readText() }

/**
 * Runs [block], and on any failure runs it exactly once more.
 *
 * YouTube's feed and page endpoints intermittently answer 404/500 for URLs that are
 * perfectly valid — the same feed URL was observed alternating between 200 and 404 seconds
 * apart. Without this, a healthy channel would randomly fall back to cached content.
 */
fun <T> retryingOnce(block: () -> T): T = try {
    block()
} catch (first: Exception) {
    block()
}
```

In `HttpUrlFeedFetcher.fetch`, add the User-Agent before reading `responseCode`:

```kotlin
        connection.requestMethod = "GET"
        // Without a desktop UA, YouTube may serve a mobile or consent-wall variant of the
        // page whose embedded JSON has a different shape than YouTubePageParser expects.
        connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
```

and add to its `companion object`:

```kotlin
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
```

- [ ] **Step 4: Implement ChannelHandleResolver**

`core/content/ChannelHandleResolver.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns whatever the rider pasted into the Content Sources screen — an `@handle`, a channel
 * URL, a `/c/` vanity URL, or a playlist URL — into a stable id the feeds accept.
 *
 * Channel handles can't be used in the RSS feed URL, only the underlying `UC…` channel id
 * can, and the only key-free way to learn that id is to fetch the channel page and read the
 * `"externalId"` value embedded in it. That's the same technique used to resolve the built-in
 * catalog in [ChannelConfig].
 */
class ChannelHandleResolver(private val fetcher: FeedFetcher = HttpUrlFeedFetcher()) {

    suspend fun resolve(input: String): Result<ResolvedSource> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a channel or playlist link"))
        }

        playlistId(trimmed)?.let { playlistId ->
            return@withContext runCatching {
                val html = retryingOnce { fetcher.fetchText("https://www.youtube.com/playlist?list=$playlistId") }
                ResolvedSource(
                    sourceType = ContentSourceType.PLAYLIST,
                    youtubeId = playlistId,
                    displayName = pageTitle(html) ?: "Playlist",
                )
            }
        }

        val url = channelUrl(trimmed)
        runCatching {
            val html = retryingOnce { fetcher.fetchText(url) }
            val channelId = EXTERNAL_ID.find(html)?.groupValues?.get(1)
                ?: throw ContentParseException("Couldn't find that channel")
            ResolvedSource(
                sourceType = ContentSourceType.CHANNEL,
                youtubeId = channelId,
                displayName = pageTitle(html) ?: channelId,
            )
        }
    }

    /** The `list=` id of a playlist URL, or `null` if [input] isn't one. */
    private fun playlistId(input: String): String? =
        PLAYLIST_ID.find(input)?.groupValues?.get(1)
            ?: input.takeIf { it.startsWith("PL") && !it.contains('/') && !it.contains(' ') }

    /**
     * The page to fetch for [input]. A full youtube.com URL is fetched as given (minus any
     * tab suffix); anything else is treated as a handle.
     */
    private fun channelUrl(input: String): String = when {
        input.contains("youtube.com/") -> {
            val withoutScheme = input.substringAfter("youtube.com/").trim('/')
            val path = withoutScheme.substringBefore('?')
                .split('/')
                .let { segments ->
                    if (segments.size > 1 && segments[0] in setOf("c", "channel", "user")) {
                        segments.take(2)
                    } else {
                        segments.take(1)
                    }
                }
                .joinToString("/")
            "https://www.youtube.com/$path"
        }
        input.startsWith("@") -> "https://www.youtube.com/$input"
        else -> "https://www.youtube.com/@$input"
    }

    /** `<title>Kaleigh Cohen Cycling - YouTube</title>` → `Kaleigh Cohen Cycling`. */
    private fun pageTitle(html: String): String? =
        TITLE.find(html)?.groupValues?.get(1)
            ?.removeSuffix(" - YouTube")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        val EXTERNAL_ID = Regex(""""externalId":"(UC[\w-]+)"""")
        val PLAYLIST_ID = Regex("""[?&]list=(PL[\w-]+)""")
        val TITLE = Regex("""<title>([^<]*)</title>""")
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests '*RetryingFetchTest*' --tests '*ChannelHandleResolverTest*'
```

Expected: PASS (12 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/core/content/FeedFetcher.kt \
        app/src/main/java/dev/digitalducktape/openride/core/content/ChannelHandleResolver.kt \
        app/src/test/java/dev/digitalducktape/openride/core/content/RetryingFetchTest.kt \
        app/src/test/java/dev/digitalducktape/openride/core/content/ChannelHandleResolverTest.kt
git commit -m "Add retrying text fetches and channel/playlist link resolution"
```

---

## Task 5: Content sources in Room

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/ContentSource.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/ContentSourceDao.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/ContentSourceRepository.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/core/data/OpenRideDatabase.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/content/ContentSourceRepositoryTest.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/core/data/DatabaseMigrationTest.kt` (extend)

**Interfaces:**
- Consumes: `ChannelConfig.ALL`, `ContentCategory` (Task 1).
- Produces:
  - `enum class ContentSourceType { CHANNEL, PLAYLIST }`
  - `data class ResolvedSource(val sourceType: ContentSourceType, val youtubeId: String, val displayName: String)` — consumed by `ChannelHandleResolver` (Task 4) and `ContentSourceRepository.add`
  - `data class ContentSource(id: Long = 0, sourceType: ContentSourceType, youtubeId: String, displayName: String, category: ContentCategory, builtIn: Boolean, hidden: Boolean, position: Int)`
  - `ContentSourceRepository(dao)` with `suspend fun seedIfEmpty()`, `fun observeVisible(): Flow<List<ContentSource>>`, `fun observeAll(): Flow<List<ContentSource>>`, `suspend fun visibleOnce(): List<ContentSource>`, `suspend fun getById(id: Long): ContentSource?`, `suspend fun add(resolved: ResolvedSource, category: ContentCategory): Long`, `suspend fun setHidden(id: Long, hidden: Boolean)`, `suspend fun deleteCustom(id: Long)`
  - `MIGRATION_4_5`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/dev/digitalducktape/openride/core/content/ContentSourceRepositoryTest.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentSourceRepositoryTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var repository: ContentSourceRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenRideDatabase::class.java,
        ).build()
        repository = ContentSourceRepository(db.contentSourceDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `seedIfEmpty inserts the built-in catalog in order`() = runTest {
        repository.seedIfEmpty()

        val sources = repository.observeAll().first()
        assertEquals(ChannelConfig.ALL.size, sources.size)
        assertEquals(ChannelConfig.ALL.map { it.id }, sources.map { it.youtubeId })
        assertTrue(sources.all { it.builtIn })
        assertTrue(sources.none { it.hidden })
        assertEquals(List(sources.size) { it }, sources.map { it.position })
    }

    @Test
    fun `seedIfEmpty is idempotent`() = runTest {
        repository.seedIfEmpty()
        repository.seedIfEmpty()

        assertEquals(ChannelConfig.ALL.size, repository.observeAll().first().size)
    }

    @Test
    fun `adding a custom source appends it after the built-ins`() = runTest {
        repository.seedIfEmpty()

        val id = repository.add(
            ResolvedSource(ContentSourceType.PLAYLIST, "PLcustom0001", "My Climbs"),
            ContentCategory.Workout,
        )

        val added = repository.getById(id)!!
        assertEquals("PLcustom0001", added.youtubeId)
        assertEquals("My Climbs", added.displayName)
        assertEquals(ContentSourceType.PLAYLIST, added.sourceType)
        assertEquals(ContentCategory.Workout, added.category)
        assertFalse(added.builtIn)
        assertEquals(ChannelConfig.ALL.size, added.position)
    }

    @Test
    fun `hidden sources are excluded from the visible list but kept in the full list`() = runTest {
        repository.seedIfEmpty()
        val first = repository.observeAll().first().first()

        repository.setHidden(first.id, true)

        assertFalse(repository.observeVisible().first().any { it.id == first.id })
        assertTrue(repository.observeAll().first().any { it.id == first.id && it.hidden })
        assertEquals(ChannelConfig.ALL.size - 1, repository.visibleOnce().size)
    }

    @Test
    fun `unhiding restores a source to the visible list`() = runTest {
        repository.seedIfEmpty()
        val first = repository.observeAll().first().first()
        repository.setHidden(first.id, true)

        repository.setHidden(first.id, false)

        assertTrue(repository.observeVisible().first().any { it.id == first.id })
    }

    @Test
    fun `deleteCustom removes a user-added source`() = runTest {
        val id = repository.add(
            ResolvedSource(ContentSourceType.CHANNEL, "UCcustom00001", "Custom"),
            ContentCategory.Scenic,
        )

        repository.deleteCustom(id)

        assertNull(repository.getById(id))
    }

    @Test
    fun `deleteCustom refuses to delete a built-in source`() = runTest {
        repository.seedIfEmpty()
        val builtIn = repository.observeAll().first().first()

        repository.deleteCustom(builtIn.id)

        assertTrue(repository.getById(builtIn.id) != null)
    }

    @Test
    fun `adding a source that already exists does not duplicate it`() = runTest {
        repository.seedIfEmpty()
        val existing = ChannelConfig.ALL.first()

        repository.add(
            ResolvedSource(ContentSourceType.CHANNEL, existing.id, existing.displayName),
            ContentCategory.Scenic,
        )

        assertEquals(
            1,
            repository.observeAll().first().count { it.youtubeId == existing.id },
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContentSourceRepositoryTest*'
```

Expected: FAIL — `Unresolved reference: contentSourceDao` / `ContentSourceRepository`.

- [ ] **Step 3: Create the entity**

`core/content/ContentSource.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Whether a source is a whole channel's uploads or one curated playlist. */
enum class ContentSourceType {
    CHANNEL,
    PLAYLIST,
}

/**
 * A pasted URL or handle, resolved to something the content layer can actually fetch — the
 * hand-off between [dev.digitalducktape.openride.core.content.ChannelHandleResolver] and
 * [ContentSourceRepository.add]. Not yet persisted: the rider still picks a category first.
 */
data class ResolvedSource(
    val sourceType: ContentSourceType,
    val youtubeId: String,
    val displayName: String,
)

/**
 * One configured content source — a row on the Classes tab.
 *
 * The catalog lives in the database rather than in [ChannelConfig] because riders can add
 * their own channels and playlists and hide ones they don't want. [ChannelConfig] is now only
 * the seed for a fresh install (see [ContentSourceRepository.seedIfEmpty]).
 *
 * @param builtIn true for a seeded channel. Built-ins can be hidden but never deleted, so a
 *   rider who hides one can always get it back without reinstalling.
 * @param position display order of the rows on the Classes tab; seeded rows keep their
 *   catalog order and custom sources append to the end.
 */
@Entity(
    tableName = "content_sources",
    indices = [Index(value = ["youtubeId"], unique = true)],
)
data class ContentSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: ContentSourceType,
    val youtubeId: String,
    val displayName: String,
    val category: ContentCategory,
    val builtIn: Boolean,
    val hidden: Boolean = false,
    val position: Int,
)
```

- [ ] **Step 4: Create the DAO**

`core/content/ContentSourceDao.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentSourceDao {
    @Query("SELECT * FROM content_sources ORDER BY position ASC")
    fun observeAll(): Flow<List<ContentSource>>

    @Query("SELECT * FROM content_sources WHERE hidden = 0 ORDER BY position ASC")
    fun observeVisible(): Flow<List<ContentSource>>

    @Query("SELECT * FROM content_sources WHERE hidden = 0 ORDER BY position ASC")
    suspend fun visibleOnce(): List<ContentSource>

    @Query("SELECT * FROM content_sources WHERE id = :id")
    suspend fun getById(id: Long): ContentSource?

    @Query("SELECT COUNT(*) FROM content_sources")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM content_sources")
    suspend fun maxPosition(): Int

    /** Ignores a source whose `youtubeId` is already configured — adding a duplicate is a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: ContentSource): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sources: List<ContentSource>)

    @Query("UPDATE content_sources SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** Built-ins are protected here rather than in the repository so no caller can bypass it. */
    @Query("DELETE FROM content_sources WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long)

    @Query("SELECT * FROM content_sources WHERE youtubeId = :youtubeId")
    suspend fun findByYoutubeId(youtubeId: String): ContentSource?
}
```

- [ ] **Step 5: Create the repository**

`core/content/ContentSourceRepository.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import kotlinx.coroutines.flow.Flow

/**
 * The Classes tab's source list: the seeded catalog plus whatever the rider has added, minus
 * whatever they've hidden.
 */
class ContentSourceRepository(private val dao: ContentSourceDao) {

    /** Populates a fresh install's catalog from [ChannelConfig]. No-op once anything exists. */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        dao.insertAll(
            ChannelConfig.ALL.mapIndexed { index, channel ->
                ContentSource(
                    sourceType = ContentSourceType.CHANNEL,
                    youtubeId = channel.id,
                    displayName = channel.displayName,
                    category = channel.category,
                    builtIn = true,
                    hidden = false,
                    position = index,
                )
            },
        )
    }

    fun observeAll(): Flow<List<ContentSource>> = dao.observeAll()

    fun observeVisible(): Flow<List<ContentSource>> = dao.observeVisible()

    suspend fun visibleOnce(): List<ContentSource> = dao.visibleOnce()

    suspend fun getById(id: Long): ContentSource? = dao.getById(id)

    /**
     * Adds a resolved source at the end of the list. Adding one that's already configured
     * returns the existing row's id instead of creating a duplicate.
     */
    suspend fun add(resolved: ResolvedSource, category: ContentCategory): Long {
        dao.findByYoutubeId(resolved.youtubeId)?.let { return it.id }
        val inserted = dao.insert(
            ContentSource(
                sourceType = resolved.sourceType,
                youtubeId = resolved.youtubeId,
                displayName = resolved.displayName,
                category = category,
                builtIn = false,
                hidden = false,
                position = dao.maxPosition() + 1,
            ),
        )
        return if (inserted > 0) inserted else dao.findByYoutubeId(resolved.youtubeId)!!.id
    }

    suspend fun setHidden(id: Long, hidden: Boolean) = dao.setHidden(id, hidden)

    /** Deletes a rider-added source. Built-ins are unaffected — hide those instead. */
    suspend fun deleteCustom(id: Long) = dao.deleteCustom(id)
}
```

- [ ] **Step 6: Register the entity, DAO, and migration**

In `core/data/OpenRideDatabase.kt`, update the imports and annotation:

```kotlin
import dev.digitalducktape.openride.core.content.ContentSource
import dev.digitalducktape.openride.core.content.ContentSourceDao

@Database(
    entities = [Profile::class, Ride::class, RideSample::class, ContentSource::class],
    version = 5,
    exportSchema = true,
)
abstract class OpenRideDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun rideDao(): RideDao
    abstract fun contentSourceDao(): ContentSourceDao
    ...
}
```

and append the migration:

```kotlin
/**
 * Adds the rider-manageable Classes catalog (`content_sources`): the channel list moves out
 * of the hard-coded `ChannelConfig` object so channels and playlists can be added and hidden
 * at runtime. The table is created empty and seeded on first access by
 * [dev.digitalducktape.openride.core.content.ContentSourceRepository.seedIfEmpty], so an
 * upgrading install gets the same catalog a fresh one does — including channels added to the
 * seed list in later app versions, for anyone whose table is still empty.
 */
val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `content_sources` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sourceType` TEXT NOT NULL, `youtubeId` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`builtIn` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_content_sources_youtubeId` " +
                "ON `content_sources` (`youtubeId`)",
        )
    }
}
```

- [ ] **Step 7: Add a migration test**

Append to `app/src/test/java/dev/digitalducktape/openride/core/data/DatabaseMigrationTest.kt`, following the existing tests' style (they build the prior schema by hand and call `migrate` directly):

```kotlin
    @Test
    fun `migration 4 to 5 creates an empty content_sources table`() {
        val db = openV1Database()
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)

        MIGRATION_4_5.migrate(db)

        db.query("SELECT COUNT(*) FROM content_sources").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.query("PRAGMA table_info(content_sources)").use { cursor ->
            val columns = buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertEquals(
                listOf("id", "sourceType", "youtubeId", "displayName", "category", "builtIn", "hidden", "position"),
                columns,
            )
        }
    }
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContentSourceRepositoryTest*' --tests '*DatabaseMigrationTest*'
```

Expected: PASS (8 repository tests + the existing migration tests + the new one). Room also writes `app/schemas/dev.digitalducktape.openride.core.data.OpenRideDatabase/5.json` — commit it.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/core/content \
        app/src/main/java/dev/digitalducktape/openride/core/data/OpenRideDatabase.kt \
        app/src/test/java/dev/digitalducktape/openride/core app/schemas
git commit -m "Move the Classes catalog into a rider-manageable content_sources table"
```

---

## Task 6: Repository merge, filter, and creator content

**Files:**
- Modify: `app/src/main/java/dev/digitalducktape/openride/core/content/YouTubeContentRepository.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/core/content/ChannelSection.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/core/content/CreatorContent.kt`
- Modify: `app/src/test/java/dev/digitalducktape/openride/core/content/YouTubeContentRepositoryTest.kt` (rewrite)

**Interfaces:**
- Consumes: `ContentSourceRepository.visibleOnce()`, `ContentSource` (Task 5); `YouTubePageParser` (Task 3); `retryingOnce`, `fetchText` (Task 4); `AtomFeedParser`, `ContentCache` (existing).
- Produces:
  - `ChannelSection` gains `sourceId: Long` as its first parameter (`channelId` stays, holding the `UC…`/`PL…` id).
  - `data class CreatorContent(val sourceId: Long, val displayName: String, val latest: List<Video>, val playlists: List<PlaylistSummary>, val refreshFailed: Boolean)`
  - `YouTubeContentRepository(context, sourceRepository, fetcher, feedParser, pageParser, cache)` with `suspend fun channelSections(): List<ChannelSection>`, `suspend fun creatorContent(sourceId: Long): CreatorContent?`, `suspend fun playlistVideos(playlistId: String, displayName: String): List<Video>`
  - `const val MIN_CLASS_DURATION_SEC = 600`

- [ ] **Step 1: Write the failing test**

Replace `app/src/test/java/dev/digitalducktape/openride/core/content/YouTubeContentRepositoryTest.kt` entirely:

```kotlin
package dev.digitalducktape.openride.core.content

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class YouTubeContentRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var db: OpenRideDatabase
    private lateinit var sources: ContentSourceRepository

    /** Matches the ids in `fixtures/channel_videos_page.html`: long / short / no-duration. */
    private fun pageHtml() = readFixture("channel_videos_page.html")
    private fun playlistsHtml() = readFixture("channel_playlists_page.html")

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    private fun feedXml(): InputStream =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        sources = ContentSourceRepository(db.contentSourceDao())
        sources.add(
            ResolvedSource(ContentSourceType.CHANNEL, "UCTestChannel00000000", "Test Cycling Channel"),
            ContentCategory.Scenic,
        )
        context.filesDir.resolve("content_cache").deleteRecursively()
    }

    @After
    fun tearDown() = db.close()

    /** Serves page HTML for `/videos` and `/playlists` URLs, and the Atom fixture for feeds. */
    private fun fetcher(
        page: (() -> String)? = { pageHtml() },
        feed: (() -> InputStream)? = { feedXml() },
        playlists: () -> String = { playlistsHtml() },
    ) = FeedFetcher { url ->
        when {
            url.contains("/playlists") -> playlists().byteInputStream()
            url.contains("feeds/videos.xml") ->
                feed?.invoke() ?: throw IOException("feed down")
            else -> (page?.invoke() ?: throw IOException("page down")).byteInputStream()
        }
    }

    private fun repository(fetcher: FeedFetcher) = YouTubeContentRepository(
        context = context,
        sourceRepository = sources,
        fetcher = fetcher,
        cache = ContentCache(context),
    )

    @Test
    fun `page videos carry durations and drop anything under ten minutes`() = runTest {
        val section = repository(fetcher()).channelSections().single()

        assertFalse(section.refreshFailed)
        // vidShort0002 is 5:04 and must be filtered out; the LIVE tile has no known duration
        // and is kept.
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), section.videos.map { it.id })
        assertEquals(1103, section.videos.first().durationSec)
    }

    @Test
    fun `section reports the source row it came from`() = runTest {
        val source = sources.visibleOnce().single()

        val section = repository(fetcher()).channelSections().single()

        assertEquals(source.id, section.sourceId)
        assertEquals("UCTestChannel00000000", section.channelId)
        assertEquals("Test Cycling Channel", section.channelName)
        assertEquals(ContentCategory.Scenic, section.category)
    }

    @Test
    fun `videos absent from the page listing are dropped as non-public`() = runTest {
        // The Atom fixture's ids are not present in the page fixture, so a successful page
        // fetch means none of them survive — that's the members-only rule.
        val section = repository(fetcher()).channelSections().single()

        assertTrue(section.videos.none { it.id.startsWith("video") })
    }

    @Test
    fun `a failed page fetch degrades to the feed with unknown durations`() = runTest {
        val section = repository(fetcher(page = null)).channelSections().single()

        assertFalse(section.refreshFailed)
        assertEquals(3, section.videos.size)
        assertTrue(section.videos.all { it.durationSec == null })
    }

    @Test
    fun `both fetches failing with no cache yields an empty section flagged as failed`() = runTest {
        val section = repository(fetcher(page = null, feed = null)).channelSections().single()

        assertTrue(section.refreshFailed)
        assertTrue(section.videos.isEmpty())
    }

    @Test
    fun `both fetches failing falls back to the cached list`() = runTest {
        repository(fetcher()).channelSections() // primes the cache

        val section = repository(fetcher(page = null, feed = null)).channelSections().single()

        assertTrue(section.refreshFailed)
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), section.videos.map { it.id })
    }

    @Test
    fun `a transient failure is retried once before falling back`() = runTest {
        var attempts = 0
        val flaky = FeedFetcher { url ->
            if (url.contains("feeds/videos.xml")) feedXml()
            else {
                attempts++
                if (attempts == 1) throw IOException("HTTP 500") else pageHtml().byteInputStream()
            }
        }

        val section = repository(flaky).channelSections().single()

        assertEquals(2, attempts)
        assertFalse(section.refreshFailed)
        assertEquals(1103, section.videos.first().durationSec)
    }

    @Test
    fun `hidden sources are not fetched`() = runTest {
        val source = sources.visibleOnce().single()
        sources.setHidden(source.id, true)

        assertTrue(repository(fetcher()).channelSections().isEmpty())
    }

    @Test
    fun `creatorContent returns the latest videos and the creator's playlists`() = runTest {
        val source = sources.visibleOnce().single()

        val content = repository(fetcher()).creatorContent(source.id)!!

        assertEquals("Test Cycling Channel", content.displayName)
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), content.latest.map { it.id })
        assertEquals(listOf("PLtheme000000001", "PLtheme000000002"), content.playlists.map { it.id })
        assertFalse(content.refreshFailed)
    }

    @Test
    fun `creatorContent returns null for an unknown source id`() = runTest {
        assertNull(repository(fetcher()).creatorContent(9999L))
    }

    @Test
    fun `creatorContent still lists videos when the playlists tab fails`() = runTest {
        val fetcher = FeedFetcher { url ->
            when {
                url.contains("/playlists") -> throw IOException("playlists down")
                url.contains("feeds/videos.xml") -> feedXml()
                else -> pageHtml().byteInputStream()
            }
        }
        val source = sources.visibleOnce().single()

        val content = repository(fetcher).creatorContent(source.id)!!

        assertEquals(2, content.latest.size)
        assertTrue(content.playlists.isEmpty())
    }

    @Test
    fun `playlistVideos applies the same duration filter`() = runTest {
        val videos = repository(fetcher()).playlistVideos("PLtheme000000001", "Themed Rides")

        assertEquals(listOf("vidLong00001", "vidNoDur0003"), videos.map { it.id })
        assertEquals("Themed Rides", videos.first().channelName)
    }

    @Test
    fun `a playlist source is fetched from its playlist page`() = runTest {
        val playlistId = sources.add(
            ResolvedSource(ContentSourceType.PLAYLIST, "PLtheme000000001", "Themed Rides"),
            ContentCategory.Workout,
        )
        var requestedPlaylistPage = false
        val fetcher = FeedFetcher { url ->
            if (url.contains("playlist?list=PLtheme000000001")) requestedPlaylistPage = true
            when {
                url.contains("feeds/videos.xml") -> feedXml()
                else -> pageHtml().byteInputStream()
            }
        }

        val sections = repository(fetcher).channelSections()

        assertTrue(requestedPlaylistPage)
        assertEquals(2, sections.size)
        assertEquals(playlistId, sections.last().sourceId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*YouTubeContentRepositoryTest*'
```

Expected: FAIL — the `sourceRepository` constructor parameter and `sourceId` don't exist yet.

- [ ] **Step 3: Add sourceId to ChannelSection and create CreatorContent**

In `core/content/ChannelSection.kt`, add the field to the data class (keep the existing KDoc, adding the new param):

```kotlin
/**
 * @param sourceId the `content_sources` row this came from — what the creator page and the
 *   filter UI key off, since a rider can configure two rows for the same creator.
 */
data class ChannelSection(
    val sourceId: Long,
    val channelId: String,
    val channelName: String,
    val category: ContentCategory,
    val videos: List<Video>,
    val refreshFailed: Boolean,
)
```

`core/content/CreatorContent.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

/**
 * Everything the creator page shows for one configured source: their recent uploads plus the
 * playlists they've curated, which is how creators organize themed sets of classes.
 *
 * @param playlists empty when the creator has none, or when the playlists tab couldn't be
 *   read — either way the page still shows [latest] rather than an error.
 * @param refreshFailed true only when the video list itself is stale/empty.
 */
data class CreatorContent(
    val sourceId: Long,
    val displayName: String,
    val latest: List<Video>,
    val playlists: List<PlaylistSummary>,
    val refreshFailed: Boolean,
)
```

- [ ] **Step 4: Rewrite the repository**

Replace `core/content/YouTubeContentRepository.kt`:

```kotlin
package dev.digitalducktape.openride.core.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Classes shorter than this aren't a meaningful ride, so they never reach the catalog. */
const val MIN_CLASS_DURATION_SEC = 600

/**
 * Content layer for the Classes browser: for each configured source it fetches the public
 * channel/playlist page and the RSS feed, merges them, applies the catalog filters, and
 * caches the result — falling back to the last-known cache (flagged via
 * [ChannelSection.refreshFailed]) when nothing can be reached, so a network blip degrades to
 * stale content rather than an empty screen.
 *
 * Why two sources rather than one:
 * - The **page** ([YouTubePageParser]) is the only key-free source of *duration* and the only
 *   listing that excludes members-only videos, so both catalog filters depend on it. It also
 *   carries roughly twice as many videos as the feed.
 * - The **feed** ([AtomFeedParser]) is the only source of exact publish timestamps (the page
 *   only says "4 months ago"), and it still works if YouTube changes its page internals.
 *
 * Page entries win on content; feed entries contribute their exact timestamps where the ids
 * match. If the page can't be read, the feed alone drives the row with durations unknown —
 * degraded filtering, but a working catalog. Only when both fail does the row go stale.
 */
class YouTubeContentRepository(
    context: Context,
    private val sourceRepository: ContentSourceRepository,
    private val fetcher: FeedFetcher = HttpUrlFeedFetcher(),
    private val feedParser: AtomFeedParser = AtomFeedParser(),
    private val pageParser: YouTubePageParser = YouTubePageParser(),
    private val cache: ContentCache = ContentCache(context),
) {

    /** One [ChannelSection] per visible configured source, in display order. */
    suspend fun channelSections(): List<ChannelSection> = withContext(Dispatchers.IO) {
        sourceRepository.seedIfEmpty()
        sourceRepository.visibleOnce().map { source -> fetchSection(source) }
    }

    /** The creator page's payload, or `null` if [sourceId] isn't a configured source. */
    suspend fun creatorContent(sourceId: Long): CreatorContent? = withContext(Dispatchers.IO) {
        val source = sourceRepository.getById(sourceId) ?: return@withContext null
        val section = fetchSection(source)
        CreatorContent(
            sourceId = source.id,
            displayName = source.displayName,
            latest = section.videos,
            playlists = fetchPlaylists(source),
            refreshFailed = section.refreshFailed,
        )
    }

    /** One playlist's rideable videos, filtered like any other row. */
    suspend fun playlistVideos(playlistId: String, displayName: String): List<Video> =
        withContext(Dispatchers.IO) {
            fetchVideos(ContentSourceType.PLAYLIST, playlistId, displayName)?.let(::rideable)
                ?: cache.read(playlistId).orEmpty()
        }

    private fun fetchSection(source: ContentSource): ChannelSection {
        val videos = fetchVideos(source.sourceType, source.youtubeId, source.displayName)
        return if (videos == null) {
            ChannelSection(
                sourceId = source.id,
                channelId = source.youtubeId,
                channelName = source.displayName,
                category = source.category,
                videos = cache.read(source.youtubeId).orEmpty(),
                refreshFailed = true,
            )
        } else {
            val filtered = rideable(videos)
            cache.write(source.youtubeId, filtered)
            ChannelSection(
                sourceId = source.id,
                channelId = source.youtubeId,
                channelName = source.displayName,
                category = source.category,
                videos = filtered,
                refreshFailed = false,
            )
        }
    }

    /** Merged page+feed videos, or `null` when neither source could be read. */
    private fun fetchVideos(
        type: ContentSourceType,
        youtubeId: String,
        displayName: String,
    ): List<Video>? {
        val pageVideos = tryFetch {
            pageParser.parseVideos(fetcher.fetchText(contentPageUrl(type, youtubeId)), displayName)
        }
        val feedVideos = tryFetch {
            fetcher.fetch(feedUrl(type, youtubeId)).use { feedParser.parse(it, displayName) }
        }

        return when {
            // Page listing is authoritative: it's public-only, so an id the feed knows about
            // that the page doesn't is a members-only video and must not appear.
            pageVideos != null -> {
                val publishedById = feedVideos.orEmpty().associate { it.id to it.publishedEpochMs }
                pageVideos.map { video ->
                    publishedById[video.id]?.let { video.copy(publishedEpochMs = it) } ?: video
                }
            }
            feedVideos != null -> feedVideos
            else -> null
        }
    }

    private fun fetchPlaylists(source: ContentSource): List<PlaylistSummary> {
        if (source.sourceType != ContentSourceType.CHANNEL) return emptyList()
        return tryFetch {
            pageParser.parsePlaylists(
                fetcher.fetchText("https://www.youtube.com/channel/${source.youtubeId}/playlists"),
            )
        }.orEmpty()
    }

    /** Drops classes too short to be a real ride; unknown duration is always kept. */
    private fun rideable(videos: List<Video>): List<Video> =
        videos.filter { video -> video.durationSec == null || video.durationSec >= MIN_CLASS_DURATION_SEC }

    /**
     * Runs a fetch with one retry, converting *any* failure — network error, malformed XML,
     * unreadable page JSON — into `null`. One bad response must never crash the Classes tab.
     */
    private fun <T> tryFetch(block: () -> T): T? = try {
        retryingOnce(block)
    } catch (e: Exception) {
        null
    }

    private fun contentPageUrl(type: ContentSourceType, youtubeId: String) = when (type) {
        ContentSourceType.CHANNEL -> "https://www.youtube.com/channel/$youtubeId/videos"
        ContentSourceType.PLAYLIST -> "https://www.youtube.com/playlist?list=$youtubeId"
    }

    private fun feedUrl(type: ContentSourceType, youtubeId: String) = when (type) {
        ContentSourceType.CHANNEL -> "https://www.youtube.com/feeds/videos.xml?channel_id=$youtubeId"
        ContentSourceType.PLAYLIST -> "https://www.youtube.com/feeds/videos.xml?playlist_id=$youtubeId"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*YouTubeContentRepositoryTest*'
```

Expected: PASS (13 tests). The rest of the suite won't compile yet — `ClassesViewModel` and `MainScaffold` still pass the old constructor arguments; Tasks 7–10 fix those. If the compile failure blocks this run, apply the minimal call-site fix in `AppContainer.kt` now:

```kotlin
    val contentSourceRepository: ContentSourceRepository by lazy {
        ContentSourceRepository(database.contentSourceDao())
    }

    val contentRepository: YouTubeContentRepository by lazy {
        YouTubeContentRepository(applicationContext, contentSourceRepository)
    }
```

plus `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)` and the matching imports, and update `ClassesViewModelTest`'s `repository(...)` helper to build a `ContentSourceRepository` the same way `YouTubeContentRepositoryTest` does.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/core/content \
        app/src/main/java/dev/digitalducktape/openride/AppContainer.kt \
        app/src/test/java/dev/digitalducktape/openride
git commit -m "Merge page and feed content, filter short and members-only classes"
```

---

## Task 7: Filters, sort, and Random Ride in the view model

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/classes/ClassFilters.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/classes/ClassesUiState.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/classes/ClassesViewModel.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/ui/classes/ClassFiltersTest.kt`
- Modify: `app/src/test/java/dev/digitalducktape/openride/ui/classes/ClassesViewModelTest.kt`

**Interfaces:**
- Consumes: `ChannelSection` (with `sourceId`), `Video`, `ContentCategory` (Task 6).
- Produces:
  - `enum class CategoryFilter { All, Workout, Scenic }`
  - `enum class ClassSort { Newest, Oldest, Random }`
  - `enum class LengthFilter(val label: String, val minSec: Int, val maxSec: Int?) { Any(...), Under20(...), From20To30(...), From30To45(...), Over45(...) }`
  - `data class ClassFilters(val category: CategoryFilter = CategoryFilter.All, val sort: ClassSort = ClassSort.Newest, val length: LengthFilter = LengthFilter.Any)` with `val isDefaultBrowse: Boolean`
  - `object ClassFiltering` with `fun matchesLength(video, length): Boolean`, `fun rows(sections, category): List<ChannelSection>`, `fun grid(sections, filters, random: Random): List<Video>`
  - `ClassesUiState.Loaded(sections, anyRefreshFailed)` unchanged; view model adds `filters: StateFlow<ClassFilters>`, `rows: StateFlow<List<ChannelSection>>`, `gridVideos: StateFlow<List<Video>?>`, `setCategory/setSort/setLength`, `fun randomVideo(): Video?`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/dev/digitalducktape/openride/ui/classes/ClassFiltersTest.kt`:

```kotlin
package dev.digitalducktape.openride.ui.classes

import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.Video
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassFiltersTest {

    private fun video(id: String, durationSec: Int?, publishedEpochMs: Long) = Video(
        id = id,
        title = "Class $id",
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        channelName = "Channel",
        durationSec = durationSec,
        publishedEpochMs = publishedEpochMs,
    )

    private val workout = ChannelSection(
        sourceId = 1L,
        channelId = "UCworkout",
        channelName = "Workout Channel",
        category = ContentCategory.Workout,
        videos = listOf(
            video("w15", 15 * 60, 3_000L),
            video("w25", 25 * 60, 1_000L),
            video("w50", 50 * 60, 5_000L),
        ),
        refreshFailed = false,
    )

    private val scenic = ChannelSection(
        sourceId = 2L,
        channelId = "UCscenic",
        channelName = "Scenic Channel",
        category = ContentCategory.Scenic,
        videos = listOf(
            video("s40", 40 * 60, 4_000L),
            video("sNull", null, 2_000L),
        ),
        refreshFailed = false,
    )

    private val sections = listOf(workout, scenic)

    @Test
    fun `Any length matches everything including unknown durations`() {
        assertTrue(ClassFiltering.matchesLength(video("a", null, 0L), LengthFilter.Any))
        assertTrue(ClassFiltering.matchesLength(video("b", 12 * 60, 0L), LengthFilter.Any))
    }

    @Test
    fun `length buckets are inclusive at their lower bound and exclusive at their upper`() {
        assertTrue(ClassFiltering.matchesLength(video("a", 19 * 60, 0L), LengthFilter.Under20))
        assertFalse(ClassFiltering.matchesLength(video("b", 20 * 60, 0L), LengthFilter.Under20))
        assertTrue(ClassFiltering.matchesLength(video("c", 20 * 60, 0L), LengthFilter.From20To30))
        assertFalse(ClassFiltering.matchesLength(video("d", 30 * 60, 0L), LengthFilter.From20To30))
        assertTrue(ClassFiltering.matchesLength(video("e", 30 * 60, 0L), LengthFilter.From30To45))
        assertTrue(ClassFiltering.matchesLength(video("f", 45 * 60, 0L), LengthFilter.Over45))
    }

    @Test
    fun `a video with unknown duration matches no specific bucket`() {
        val unknown = video("x", null, 0L)

        assertFalse(ClassFiltering.matchesLength(unknown, LengthFilter.Under20))
        assertFalse(ClassFiltering.matchesLength(unknown, LengthFilter.Over45))
    }

    @Test
    fun `rows keeps every section when the category is All`() {
        assertEquals(listOf(1L, 2L), ClassFiltering.rows(sections, CategoryFilter.All).map { it.sourceId })
    }

    @Test
    fun `rows keeps only the matching category`() {
        assertEquals(listOf(2L), ClassFiltering.rows(sections, CategoryFilter.Scenic).map { it.sourceId })
        assertEquals(listOf(1L), ClassFiltering.rows(sections, CategoryFilter.Workout).map { it.sourceId })
    }

    @Test
    fun `grid sorts newest first by default`() {
        val filters = ClassFilters(sort = ClassSort.Newest)

        val grid = ClassFiltering.grid(sections, filters, Random(0))

        assertEquals(listOf("w50", "s40", "w15", "sNull", "w25"), grid.map { it.id })
    }

    @Test
    fun `grid sorts oldest first`() {
        val grid = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Oldest), Random(0))

        assertEquals(listOf("w25", "sNull", "w15", "s40", "w50"), grid.map { it.id })
    }

    @Test
    fun `grid random ordering is a permutation of the same videos`() {
        val grid = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Random), Random(7))

        assertEquals(5, grid.size)
        assertEquals(
            setOf("w15", "w25", "w50", "s40", "sNull"),
            grid.map { it.id }.toSet(),
        )
    }

    @Test
    fun `grid random ordering is stable for a given seed`() {
        val first = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Random), Random(7))
        val second = ClassFiltering.grid(sections, ClassFilters(sort = ClassSort.Random), Random(7))

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `grid applies category and length together`() {
        val filters = ClassFilters(
            category = CategoryFilter.Workout,
            sort = ClassSort.Newest,
            length = LengthFilter.From20To30,
        )

        assertEquals(listOf("w25"), ClassFiltering.grid(sections, filters, Random(0)).map { it.id })
    }

    @Test
    fun `default browse is only the untouched filter set`() {
        assertTrue(ClassFilters().isDefaultBrowse)
        assertTrue(ClassFilters(category = CategoryFilter.Scenic).isDefaultBrowse)
        assertFalse(ClassFilters(sort = ClassSort.Oldest).isDefaultBrowse)
        assertFalse(ClassFilters(length = LengthFilter.Over45).isDefaultBrowse)
    }
}
```

And append to `ClassesViewModelTest.kt` (keeping its existing tests; its `repository(...)` helper now builds a `ContentSourceRepository` seeded with the test channel, per Task 6 Step 5):

```kotlin
    @Test
    fun `filters start at newest with no category or length restriction`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        assertEquals(ClassFilters(), viewModel.filters.value)
    }

    @Test
    fun `setting a length filter switches the screen into grid mode`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)
        viewModel.refresh()

        assertNull(viewModel.gridVideos.value)

        viewModel.setLength(LengthFilter.Over45)

        assertNotNull(viewModel.gridVideos.value)
    }

    @Test
    fun `category filtering applies to the rows in browse mode`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)
        viewModel.refresh()

        viewModel.setCategory(CategoryFilter.Workout)

        // The seeded test source is Scenic, so a Workout filter empties the rows.
        assertTrue(viewModel.rows.value.isEmpty())

        viewModel.setCategory(CategoryFilter.Scenic)

        assertEquals(1, viewModel.rows.value.size)
    }

    @Test
    fun `randomVideo returns null before content has loaded`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        assertNull(viewModel.randomVideo())
    }

    @Test
    fun `randomVideo returns a video from the filtered pool`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)
        viewModel.refresh()

        val picked = viewModel.randomVideo()!!

        assertTrue(viewModel.rows.value.single().videos.any { it.id == picked.id })
    }

    @Test
    fun `randomVideo returns null when the filters match nothing`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)
        viewModel.refresh()

        viewModel.setCategory(CategoryFilter.Workout)

        assertNull(viewModel.randomVideo())
    }
```

Add these imports to `ClassesViewModelTest.kt`: `org.junit.Assert.assertNotNull`, `org.junit.Assert.assertNull`.

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests '*ClassFiltersTest*' --tests '*ClassesViewModelTest*'
```

Expected: FAIL — `Unresolved reference: ClassFiltering` / `LengthFilter` / `viewModel.filters`.

- [ ] **Step 3: Create the filter model**

`ui/classes/ClassFilters.kt`:

```kotlin
package dev.digitalducktape.openride.ui.classes

import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.Video
import kotlin.random.Random

/** Which category chip is selected on the Classes tab. */
enum class CategoryFilter(val label: String) {
    All("All"),
    Workout("Workout"),
    Scenic("Scenic"),
}

/** How the flat class list is ordered. */
enum class ClassSort(val label: String) {
    Newest("Newest"),
    Oldest("Oldest"),
    Random("Random"),
}

/**
 * Class-length buckets. Bounds are inclusive at [minSec] and exclusive at [maxSec] so the
 * buckets tile the range without overlapping.
 *
 * A video whose duration is unknown matches only [Any] — pretending it belongs in a specific
 * bucket would put classes in front of a rider who asked for a specific length and didn't get
 * one. (Duration is unknown only on the RSS-fallback path; see `YouTubeContentRepository`.)
 */
enum class LengthFilter(val label: String, val minSec: Int, val maxSec: Int?) {
    Any("Any", 0, null),
    Under20("< 20 min", 0, 20 * 60),
    From20To30("20–30 min", 20 * 60, 30 * 60),
    From30To45("30–45 min", 30 * 60, 45 * 60),
    Over45("45+ min", 45 * 60, null),
}

/**
 * The Classes tab's current filter selection.
 *
 * @property isDefaultBrowse true while the tab should show its per-creator rows. Category
 *   narrows which rows appear, so it doesn't leave browse mode; a sort or length choice is a
 *   question about the whole catalog and switches to the flat grid.
 */
data class ClassFilters(
    val category: CategoryFilter = CategoryFilter.All,
    val sort: ClassSort = ClassSort.Newest,
    val length: LengthFilter = LengthFilter.Any,
) {
    val isDefaultBrowse: Boolean
        get() = sort == ClassSort.Newest && length == LengthFilter.Any
}

/** Pure filtering/sorting over loaded sections — no state, so it's directly testable. */
object ClassFiltering {

    fun matchesLength(video: Video, length: LengthFilter): Boolean {
        if (length == LengthFilter.Any) return true
        val duration = video.durationSec ?: return false
        return duration >= length.minSec && (length.maxSec == null || duration < length.maxSec)
    }

    /** The creator rows to show for [category], in configured order. */
    fun rows(sections: List<ChannelSection>, category: CategoryFilter): List<ChannelSection> =
        when (category) {
            CategoryFilter.All -> sections
            CategoryFilter.Workout -> sections.filter { it.category == ContentCategory.Workout }
            CategoryFilter.Scenic -> sections.filter { it.category == ContentCategory.Scenic }
        }

    /** Every matching video across creators, ordered by [ClassFilters.sort]. */
    fun grid(
        sections: List<ChannelSection>,
        filters: ClassFilters,
        random: Random,
    ): List<Video> {
        val matching = rows(sections, filters.category)
            .flatMap { it.videos }
            .filter { matchesLength(it, filters.length) }
        return when (filters.sort) {
            ClassSort.Newest -> matching.sortedByDescending { it.publishedEpochMs }
            ClassSort.Oldest -> matching.sortedBy { it.publishedEpochMs }
            ClassSort.Random -> matching.shuffled(random)
        }
    }
}
```

- [ ] **Step 4: Wire the filters into the view model**

In `ui/classes/ClassesViewModel.kt`, add these imports:

```kotlin
import dev.digitalducktape.openride.core.content.ChannelSection
import dev.digitalducktape.openride.core.content.Video
import kotlin.random.Random
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
```

add a `random` constructor parameter (last, defaulted, so existing call sites are unaffected):

```kotlin
class ClassesViewModel(
    private val contentRepository: YouTubeContentRepository,
    private val rideSessionManager: RideSessionManager,
    private val activeProfileHolder: ActiveProfileHolder,
    rideRepository: RideRepository,
    private val random: Random = Random.Default,
) : ViewModel() {
```

and add the filter state and derived lists:

```kotlin
    private val _filters = MutableStateFlow(ClassFilters())
    val filters: StateFlow<ClassFilters> = _filters.asStateFlow()

    /**
     * Bumped whenever Random sort is (re-)selected, so the shuffle is stable while the rider
     * scrolls but reshuffles when they ask for a new order.
     */
    private val shuffleSeed = MutableStateFlow(random.nextLong())

    private val loadedSections: StateFlow<List<ChannelSection>> = _uiState
        .map { state -> (state as? ClassesUiState.Loaded)?.sections.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Creator rows for browse mode — already narrowed to the chosen category. */
    val rows: StateFlow<List<ChannelSection>> = combine(loadedSections, _filters) { sections, filters ->
        ClassFiltering.rows(sections, filters.category).filter { it.videos.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The flat grid, or `null` while the tab should show creator rows instead. */
    val gridVideos: StateFlow<List<Video>?> =
        combine(loadedSections, _filters, shuffleSeed) { sections, filters, seed ->
            if (filters.isDefaultBrowse) {
                null
            } else {
                ClassFiltering.grid(sections, filters, Random(seed))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setCategory(category: CategoryFilter) {
        _filters.value = _filters.value.copy(category = category)
    }

    fun setSort(sort: ClassSort) {
        if (sort == ClassSort.Random) shuffleSeed.value = random.nextLong()
        _filters.value = _filters.value.copy(sort = sort)
    }

    fun setLength(length: LengthFilter) {
        _filters.value = _filters.value.copy(length = length)
    }

    /**
     * A uniformly random class from everything the current filters allow — the Random Ride
     * button. Returns `null` when nothing matches (button is disabled in that state), and
     * deliberately ignores the *sort*, which only affects presentation order.
     */
    fun randomVideo(): Video? =
        ClassFiltering.grid(
            loadedSections.value,
            _filters.value.copy(sort = ClassSort.Newest),
            random,
        ).randomOrNull(random)
```

Add the remaining imports this needs: `androidx.lifecycle.viewModelScope`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.stateIn`.

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests '*ClassFiltersTest*' --tests '*ClassesViewModelTest*'
```

Expected: PASS (11 filter tests + the view model's existing tests plus 6 new ones).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/ui/classes \
        app/src/test/java/dev/digitalducktape/openride/ui/classes
git commit -m "Add category, length, and sort filters plus random class selection"
```

---

## Task 8: Classes screen — filter bar, grid mode, Random Ride

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/classes/VideoCard.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/classes/ClassesScreen.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/main/MainScaffold.kt`

**Interfaces:**
- Consumes: `ClassesViewModel.rows/gridVideos/filters/setCategory/setSort/setLength/randomVideo` (Task 7); `ChannelSection.sourceId` (Task 6).
- Produces:
  - `@Composable fun VideoCard(video: Video, takenEpochMs: Long?, onClick: () -> Unit, modifier: Modifier = Modifier)` — moved out of `ClassesScreen.kt` for reuse by the creator page (Task 9).
  - `ClassesScreen(viewModel, onStartVideoRide: (String) -> Unit, onOpenCreator: (Long) -> Unit, modifier)` — new `onOpenCreator` parameter carrying `ChannelSection.sourceId`.

This task is UI-only. The project has no Compose UI tests and no emulator, so it is verified by
compiling and by the behavior already covered in Task 7's view-model tests. Do not add
`src/androidTest` tests.

- [ ] **Step 1: Extract VideoCard into its own file**

Create `ui/classes/VideoCard.kt` and move `VideoCard`, `TakenBadge`, `DurationBadge`,
`CardWidth`, and `CardThumbnailHeight` there verbatim from `ClassesScreen.kt`, changing
`private fun VideoCard` to `fun VideoCard` and `private val CardWidth` to `val CardWidth`
(the creator page reuses both). Keep the existing KDoc comments on the badges. The file needs
these imports:

```kotlin
package dev.digitalducktape.openride.ui.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.common.TimeFormat
import dev.digitalducktape.openride.ui.theme.MetricTextStyles
import dev.digitalducktape.openride.ui.theme.OpenRideColors
```

Then delete those declarations (and any now-unused imports) from `ClassesScreen.kt`.

- [ ] **Step 2: Verify the extraction compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, with `ClassesScreen` still rendering exactly as before.

- [ ] **Step 3: Commit the extraction on its own**

```bash
git add app/src/main/java/dev/digitalducktape/openride/ui/classes
git commit -m "Extract VideoCard from ClassesScreen for reuse"
```

- [ ] **Step 4: Add the filter bar and Random Ride button**

In `ClassesScreen.kt`, replace the screen-level composable and `LoadedContent` with the
version below. The header keeps the existing `Classes` title and refresh banner, gains the
Random Ride button on the title row, and gains a filter bar; the body renders either creator
rows or the flat grid depending on `gridVideos`.

```kotlin
/**
 * Classes tab: browse by creator, or filter the whole catalog at once.
 *
 * Two layouts, one screen. With the default sort and no length filter the tab shows one
 * horizontal row per configured creator, which is how someone browses when they don't have
 * something specific in mind. Choosing a sort or a length is a question about the entire
 * catalog ("a 30-minute class, any creator"), so those switch to a flat grid — per-creator
 * rows can't answer it. Category chips narrow both layouts.
 */
@Composable
fun ClassesScreen(
    viewModel: ClassesViewModel,
    onStartVideoRide: (videoId: String) -> Unit,
    onOpenCreator: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val takenVideos by viewModel.takenVideos.collectAsState(initial = emptyMap())
    val filters by viewModel.filters.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val gridVideos by viewModel.gridVideos.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    val startRide: (Video) -> Unit = { video ->
        if (viewModel.startRideForVideo(video.id)) onStartVideoRide(video.id)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is ClassesUiState.Loading -> LoadingContent()
            is ClassesUiState.Loaded -> LoadedContent(
                anyRefreshFailed = state.anyRefreshFailed,
                filters = filters,
                rows = rows,
                gridVideos = gridVideos,
                takenVideos = takenVideos,
                onCategorySelected = viewModel::setCategory,
                onSortSelected = viewModel::setSort,
                onLengthSelected = viewModel::setLength,
                onVideoSelected = startRide,
                onOpenCreator = onOpenCreator,
                onRandomRide = { viewModel.randomVideo()?.let(startRide) },
                onRetry = { scope.launch { viewModel.refresh() } },
            )
        }
    }
}

@Composable
private fun LoadedContent(
    anyRefreshFailed: Boolean,
    filters: ClassFilters,
    rows: List<ChannelSection>,
    gridVideos: List<Video>?,
    takenVideos: Map<String, Long>,
    onCategorySelected: (CategoryFilter) -> Unit,
    onSortSelected: (ClassSort) -> Unit,
    onLengthSelected: (LengthFilter) -> Unit,
    onVideoSelected: (Video) -> Unit,
    onOpenCreator: (Long) -> Unit,
    onRandomRide: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasAnything = gridVideos?.isNotEmpty() ?: rows.any { it.videos.isNotEmpty() }

    Column(modifier = modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Classes",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(onClick = onRandomRide, enabled = hasAnything) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "Random Ride", modifier = Modifier.padding(start = 8.dp))
            }
        }

        FilterBar(
            filters = filters,
            onCategorySelected = onCategorySelected,
            onSortSelected = onSortSelected,
            onLengthSelected = onLengthSelected,
            modifier = Modifier.padding(top = 16.dp),
        )

        if (anyRefreshFailed) {
            RefreshFailedBanner(modifier = Modifier.padding(top = 16.dp))
        }

        if (!hasAnything) {
            EmptyContent(isFiltered = !filters.isDefaultBrowse, onRetry = onRetry)
        } else if (gridVideos != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = CardWidth),
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(gridVideos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        takenEpochMs = takenVideos[video.id],
                        onClick = { onVideoSelected(video) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(rows, key = { it.sourceId }) { section ->
                    ChannelRow(
                        section = section,
                        takenVideos = takenVideos,
                        onVideoSelected = onVideoSelected,
                        onOpenCreator = { onOpenCreator(section.sourceId) },
                    )
                }
            }
        }
    }
}

/** Empty state: a filter that matched nothing is a different problem than no content at all. */
@Composable
private fun EmptyContent(isFiltered: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isFiltered) {
                    "No classes match these filters"
                } else {
                    "No classes available right now — check your connection and try again"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isFiltered) {
                Text(
                    text = "Tap to retry",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRetry),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Add the filter bar composable**

Append to `ClassesScreen.kt`:

```kotlin
@Composable
private fun FilterBar(
    filters: ClassFilters,
    onCategorySelected: (CategoryFilter) -> Unit,
    onSortSelected: (ClassSort) -> Unit,
    onLengthSelected: (LengthFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryFilter.entries.forEach { category ->
            FilterChip(
                selected = filters.category == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category.label) },
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        DropdownFilter(
            label = filters.sort.label,
            options = ClassSort.entries.map { it to it.label },
            onSelected = onSortSelected,
        )
        DropdownFilter(
            label = filters.length.label,
            options = LengthFilter.entries.map { it to it.label },
            onSelected = onLengthSelected,
        )
    }
}

/** A compact dropdown — the bike's screen is wide but touch targets need to stay large. */
@Composable
private fun <T> DropdownFilter(
    label: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Text(label)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}
```

Update `ChannelRow` to make the creator name tappable:

```kotlin
@Composable
private fun ChannelRow(
    section: ChannelSection,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    onOpenCreator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 28.dp)) {
        // Tapping the shelf eyebrow opens the creator's own page — their full recent catalog
        // plus the playlists they've curated, which the single row here can't show.
        Row(
            modifier = Modifier.clickable(onClick = onOpenCreator),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.channelName.uppercase(),
                style = MetricTextStyles.SectionEyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open ${section.channelName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.videos, key = { it.id }) { video ->
                VideoCard(
                    video = video,
                    takenEpochMs = takenVideos[video.id],
                    onClick = { onVideoSelected(video) },
                )
            }
        }
    }
}
```

Add the imports this file now needs:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

- [ ] **Step 6: Pass onOpenCreator from MainScaffold**

In `ui/main/MainScaffold.kt`, extend the `ClassesScreen` call (the creator route arrives in
Task 9; wire it to the outer nav controller now):

```kotlin
                ClassesScreen(
                    viewModel = viewModel,
                    onStartVideoRide = { videoId ->
                        outerNavController.navigate(Destinations.videoRide(videoId))
                    },
                    onOpenCreator = { sourceId ->
                        outerNavController.navigate(Destinations.creator(sourceId))
                    },
                )
```

- [ ] **Step 7: Verify it compiles and the suite still passes**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: FAIL on `Destinations.creator` — that's added in Task 9. Add it now to unblock, in
`ui/navigation/Destinations.kt`:

```kotlin
    /** A creator's own page: their recent classes plus the playlists they've curated. */
    private const val CreatorBase = "creator"
    const val SourceIdArg = "sourceId"
    const val Creator = "$CreatorBase/{$SourceIdArg}"

    fun creator(sourceId: Long) = "$CreatorBase/$sourceId"
```

Then:

```bash
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL and all tests PASS. (Navigating to the creator route does nothing
until Task 9 registers it; that's fine — the button that reaches it is new in this task and
the route lands in the next one.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/ui
git commit -m "Add Classes filter bar, grid mode, and Random Ride button"
```

---

## Task 9: Creator page

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/creator/CreatorUiState.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/creator/CreatorViewModel.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/creator/CreatorScreen.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/navigation/OpenRideNavHost.kt`
- Test: `app/src/test/java/dev/digitalducktape/openride/ui/creator/CreatorViewModelTest.kt`

**Interfaces:**
- Consumes: `YouTubeContentRepository.creatorContent(sourceId)`, `playlistVideos(playlistId, displayName)` (Task 6); `VideoCard`, `CardWidth` (Task 8); `Destinations.Creator`/`creator(sourceId)`/`SourceIdArg` (added in Task 8 Step 7).
- Produces:
  - `sealed interface CreatorUiState { Loading; data class Loaded(displayName, latest, playlistRows, refreshFailed) : CreatorUiState; data object NotFound }`
  - `data class PlaylistRow(val playlist: PlaylistSummary, val videos: List<Video>, val isLoading: Boolean, val loadFailed: Boolean)`
  - `CreatorViewModel(contentRepository, rideSessionManager, activeProfileHolder, rideRepository, sourceId)` with `uiState`, `takenVideos`, `suspend fun load()`, `suspend fun loadPlaylist(playlistId: String)`, `fun startRideForVideo(videoId: String): Boolean`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/dev/digitalducktape/openride/ui/creator/CreatorViewModelTest.kt`:

```kotlin
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.digitalducktape.openride.ui.creator

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.content.ContentCache
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.ContentSourceRepository
import dev.digitalducktape.openride.core.content.ContentSourceType
import dev.digitalducktape.openride.core.content.FeedFetcher
import dev.digitalducktape.openride.core.content.ResolvedSource
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import dev.digitalducktape.openride.core.data.Profile
import dev.digitalducktape.openride.core.data.ProfileRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.FakeBikeDataSource
import dev.digitalducktape.openride.core.ride.RideSessionManager
import dev.digitalducktape.openride.core.ride.RideSessionState
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CreatorViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var db: OpenRideDatabase
    private lateinit var sources: ContentSourceRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var rideRepository: RideRepository
    private lateinit var activeProfileHolder: ActiveProfileHolder
    private var sourceId = 0L

    private fun readFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    private fun feedXml(): InputStream =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/sample_atom_feed.xml"))

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, OpenRideDatabase::class.java).build()
        sources = ContentSourceRepository(db.contentSourceDao())
        profileRepository = ProfileRepository(db.profileDao())
        rideRepository = RideRepository(db, db.rideDao())
        activeProfileHolder = ActiveProfileHolder(context)
        sourceId = sources.add(
            ResolvedSource(ContentSourceType.CHANNEL, "UCTestChannel00000000", "Test Cycling Channel"),
            ContentCategory.Workout,
        )
        context.filesDir.resolve("content_cache").deleteRecursively()
    }

    @After
    fun tearDown() = db.close()

    private fun fetcher(playlistsFail: Boolean = false) = FeedFetcher { url ->
        when {
            url.contains("/playlists") ->
                if (playlistsFail) throw IOException("down")
                else readFixture("channel_playlists_page.html").byteInputStream()
            url.contains("feeds/videos.xml") -> feedXml()
            else -> readFixture("channel_videos_page.html").byteInputStream()
        }
    }

    private fun viewModel(
        scope: CoroutineScope,
        fetcher: FeedFetcher = fetcher(),
        id: Long = sourceId,
    ): Pair<CreatorViewModel, RideSessionManager> {
        val repository = YouTubeContentRepository(
            context = context,
            sourceRepository = sources,
            fetcher = fetcher,
            cache = ContentCache(context),
        )
        val manager = RideSessionManager(FakeBikeDataSource(), rideRepository, scope)
        return CreatorViewModel(repository, manager, activeProfileHolder, rideRepository, id) to manager
    }

    @Test
    fun `starts in Loading before load is called`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        assertTrue(viewModel.uiState.value is CreatorUiState.Loading)
    }

    @Test
    fun `load exposes the creator's latest videos and playlist rows`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        viewModel.load()

        val loaded = viewModel.uiState.value as CreatorUiState.Loaded
        assertEquals("Test Cycling Channel", loaded.displayName)
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), loaded.latest.map { it.id })
        assertEquals(
            listOf("PLtheme000000001", "PLtheme000000002"),
            loaded.playlistRows.map { it.playlist.id },
        )
        assertFalse(loaded.refreshFailed)
    }

    @Test
    fun `playlist rows start empty and unloaded`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)

        viewModel.load()

        val loaded = viewModel.uiState.value as CreatorUiState.Loaded
        assertTrue(loaded.playlistRows.all { it.videos.isEmpty() && !it.isLoading && !it.loadFailed })
    }

    @Test
    fun `loadPlaylist fills in that row's videos and leaves the others alone`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)
        viewModel.load()

        viewModel.loadPlaylist("PLtheme000000001")

        val rows = (viewModel.uiState.value as CreatorUiState.Loaded).playlistRows
        assertEquals(listOf("vidLong00001", "vidNoDur0003"), rows.first().videos.map { it.id })
        assertFalse(rows.first().isLoading)
        assertTrue(rows.last().videos.isEmpty())
    }

    @Test
    fun `loadPlaylist flags a row that could not be loaded`() = runTest {
        val failing = FeedFetcher { url ->
            when {
                url.contains("playlist?list=") -> throw IOException("down")
                url.contains("/playlists") -> readFixture("channel_playlists_page.html").byteInputStream()
                url.contains("feeds/videos.xml") -> feedXml()
                else -> readFixture("channel_videos_page.html").byteInputStream()
            }
        }
        val (viewModel, _) = viewModel(backgroundScope, failing)
        viewModel.load()

        viewModel.loadPlaylist("PLtheme000000001")

        val row = (viewModel.uiState.value as CreatorUiState.Loaded).playlistRows.first()
        assertTrue(row.loadFailed)
        assertFalse(row.isLoading)
    }

    @Test
    fun `loadPlaylist is a no-op for a playlist this creator does not have`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope)
        viewModel.load()
        val before = (viewModel.uiState.value as CreatorUiState.Loaded).playlistRows

        viewModel.loadPlaylist("PLnotHere0001")

        assertEquals(before, (viewModel.uiState.value as CreatorUiState.Loaded).playlistRows)
    }

    @Test
    fun `a creator page still loads when the playlists tab fails`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope, fetcher(playlistsFail = true))

        viewModel.load()

        val loaded = viewModel.uiState.value as CreatorUiState.Loaded
        assertEquals(2, loaded.latest.size)
        assertTrue(loaded.playlistRows.isEmpty())
    }

    @Test
    fun `an unknown source id resolves to NotFound`() = runTest {
        val (viewModel, _) = viewModel(backgroundScope, id = 9999L)

        viewModel.load()

        assertEquals(CreatorUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `startRideForVideo starts a session for the active profile`() = runTest {
        val profileId = profileRepository.createProfile(
            Profile(name = "Ed", avatarEmoji = "🚴", avatarColor = 0xFF00AAFF.toInt(), weightKg = null, ftp = null),
        )
        activeProfileHolder.setActiveProfile(profileId)
        val (viewModel, manager) = viewModel(backgroundScope)

        assertTrue(viewModel.startRideForVideo("vidLong00001"))
        assertEquals(RideSessionState.Active, manager.state.value)
    }

    @Test
    fun `startRideForVideo returns false with no active profile`() = runTest {
        val (viewModel, manager) = viewModel(backgroundScope)

        assertFalse(viewModel.startRideForVideo("vidLong00001"))
        assertEquals(RideSessionState.Idle, manager.state.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*CreatorViewModelTest*'
```

Expected: FAIL — `Unresolved reference: CreatorViewModel`.

- [ ] **Step 3: Create the UI state**

`ui/creator/CreatorUiState.kt`:

```kotlin
package dev.digitalducktape.openride.ui.creator

import dev.digitalducktape.openride.core.content.PlaylistSummary
import dev.digitalducktape.openride.core.content.Video

/**
 * One playlist shelf on a creator's page. Playlists load lazily — a creator can have twenty
 * of them and each one is its own page fetch, so opening the creator page fetches none of
 * them up front and each row asks for its own content when it first appears.
 */
data class PlaylistRow(
    val playlist: PlaylistSummary,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)

sealed interface CreatorUiState {
    data object Loading : CreatorUiState

    /** @param refreshFailed true when [latest] is stale cached content, same as the Classes tab. */
    data class Loaded(
        val displayName: String,
        val latest: List<Video>,
        val playlistRows: List<PlaylistRow>,
        val refreshFailed: Boolean,
    ) : CreatorUiState

    /** The source was removed (e.g. deleted on the Content Sources screen) while navigating. */
    data object NotFound : CreatorUiState
}
```

- [ ] **Step 4: Create the view model**

`ui/creator/CreatorViewModel.kt`:

```kotlin
package dev.digitalducktape.openride.ui.creator

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.content.YouTubeContentRepository
import dev.digitalducktape.openride.core.data.RideRepository
import dev.digitalducktape.openride.core.profile.ActiveProfileHolder
import dev.digitalducktape.openride.core.ride.RideSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A creator's own page: their recent classes plus each playlist they've curated.
 *
 * Playlists are how creators organize themed sets of classes — a single row on the Classes
 * tab can only ever show recent uploads, so this is the way to reach the rest of what a
 * creator has published without an API key or a full back-catalog crawl.
 *
 * Like [dev.digitalducktape.openride.ui.classes.ClassesViewModel], the loads here are plain
 * `suspend fun`s driven from the screen rather than launched on `viewModelScope`, so tests
 * can drive them directly from `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreatorViewModel(
    private val contentRepository: YouTubeContentRepository,
    private val rideSessionManager: RideSessionManager,
    private val activeProfileHolder: ActiveProfileHolder,
    rideRepository: RideRepository,
    private val sourceId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreatorUiState>(CreatorUiState.Loading)
    val uiState: StateFlow<CreatorUiState> = _uiState.asStateFlow()

    /** Same "already ridden" badges the Classes tab shows, so they don't vanish on this screen. */
    val takenVideos: Flow<Map<String, Long>> =
        activeProfileHolder.activeProfileId.flatMapLatest { profileId ->
            if (profileId == null) {
                flowOf(emptyMap())
            } else {
                rideRepository.observeTakenVideos(profileId)
                    .map { taken -> taken.associate { it.videoId to it.lastTakenEpochMs } }
            }
        }

    suspend fun load() {
        _uiState.value = CreatorUiState.Loading
        val content = contentRepository.creatorContent(sourceId)
        _uiState.value = if (content == null) {
            CreatorUiState.NotFound
        } else {
            CreatorUiState.Loaded(
                displayName = content.displayName,
                latest = content.latest,
                playlistRows = content.playlists.map { PlaylistRow(playlist = it) },
                refreshFailed = content.refreshFailed,
            )
        }
    }

    /** Fetches one playlist's videos. Safe to call for a playlist that isn't on this page. */
    suspend fun loadPlaylist(playlistId: String) {
        val loaded = _uiState.value as? CreatorUiState.Loaded ?: return
        val row = loaded.playlistRows.firstOrNull { it.playlist.id == playlistId } ?: return

        updateRow(playlistId) { it.copy(isLoading = true, loadFailed = false) }
        val videos = contentRepository.playlistVideos(playlistId, row.playlist.title)
        updateRow(playlistId) {
            it.copy(videos = videos, isLoading = false, loadFailed = videos.isEmpty())
        }
    }

    private fun updateRow(playlistId: String, transform: (PlaylistRow) -> PlaylistRow) {
        val loaded = _uiState.value as? CreatorUiState.Loaded ?: return
        _uiState.value = loaded.copy(
            playlistRows = loaded.playlistRows.map { row ->
                if (row.playlist.id == playlistId) transform(row) else row
            },
        )
    }

    /** Same contract as [dev.digitalducktape.openride.ui.classes.ClassesViewModel.startRideForVideo]. */
    fun startRideForVideo(videoId: String): Boolean {
        val profileId = activeProfileHolder.activeProfileId.value ?: return false
        rideSessionManager.start(profileId, videoId)
        return true
    }
}
```

Note the `loadFailed = videos.isEmpty()` rule: `playlistVideos` returns an empty list both
when the fetch failed and when a playlist genuinely has no rideable classes. Both cases show
the same "couldn't load" row, which is honest for the rider either way — there's nothing to
ride there.

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*CreatorViewModelTest*'
```

Expected: PASS (10 tests).

- [ ] **Step 6: Create the screen**

`ui/creator/CreatorScreen.kt`:

```kotlin
package dev.digitalducktape.openride.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.content.Video
import dev.digitalducktape.openride.ui.classes.VideoCard
import dev.digitalducktape.openride.ui.theme.MetricTextStyles

/**
 * A creator's page, reached by tapping their shelf name on the Classes tab: a LATEST shelf of
 * their recent classes, then one shelf per playlist they've curated.
 */
@Composable
fun CreatorScreen(
    viewModel: CreatorViewModel,
    onStartVideoRide: (videoId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val takenVideos by viewModel.takenVideos.collectAsState(initial = emptyMap())

    LaunchedEffect(Unit) { viewModel.load() }

    val startRide: (Video) -> Unit = { video ->
        if (viewModel.startRideForVideo(video.id)) onStartVideoRide(video.id)
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = uiState) {
            is CreatorUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is CreatorUiState.NotFound -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "That creator is no longer in your catalog",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is CreatorUiState.Loaded -> LoadedCreator(
                state = state,
                takenVideos = takenVideos,
                onVideoSelected = startRide,
                onPlaylistVisible = { playlistId -> viewModel.loadPlaylist(playlistId) },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun LoadedCreator(
    state: CreatorUiState.Loaded,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    onPlaylistVisible: suspend (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to classes",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (state.refreshFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Couldn't refresh this creator — showing the last saved list",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
            item {
                Shelf(
                    title = "LATEST",
                    videos = state.latest,
                    takenVideos = takenVideos,
                    onVideoSelected = onVideoSelected,
                )
            }
            items(state.playlistRows, key = { it.playlist.id }) { row ->
                // Each playlist is its own page fetch, so it's requested when its shelf is
                // first composed rather than all at once when the page opens.
                LaunchedEffect(row.playlist.id) {
                    if (row.videos.isEmpty() && !row.isLoading && !row.loadFailed) {
                        onPlaylistVisible(row.playlist.id)
                    }
                }
                Shelf(
                    title = row.playlist.title.uppercase(),
                    videos = row.videos,
                    takenVideos = takenVideos,
                    onVideoSelected = onVideoSelected,
                    isLoading = row.isLoading,
                    emptyMessage = if (row.loadFailed) "Couldn't load this playlist" else null,
                )
            }
        }
    }
}

@Composable
private fun Shelf(
    title: String,
    videos: List<Video>,
    takenVideos: Map<String, Long>,
    onVideoSelected: (Video) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    emptyMessage: String? = null,
) {
    Column(modifier = modifier.padding(top = 28.dp)) {
        Text(
            text = title,
            style = MetricTextStyles.SectionEyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            isLoading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp).size(28.dp),
            )
            videos.isEmpty() && emptyMessage != null -> Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        takenEpochMs = takenVideos[video.id],
                        onClick = { onVideoSelected(video) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 7: Register the route**

In `ui/navigation/OpenRideNavHost.kt`, add the imports and a `composable` block alongside the
existing ones (`Destinations.Creator` was added in Task 8 Step 7):

```kotlin
import dev.digitalducktape.openride.ui.creator.CreatorScreen
import dev.digitalducktape.openride.ui.creator.CreatorViewModel
```

```kotlin
        composable(
            route = Destinations.Creator,
            arguments = listOf(navArgument(Destinations.SourceIdArg) { type = NavType.LongType }),
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getLong(Destinations.SourceIdArg) ?: 0L
            val viewModel: CreatorViewModel = viewModel(
                key = "creator_$sourceId",
                factory = viewModelFactory {
                    CreatorViewModel(
                        appContainer.contentRepository,
                        appContainer.rideSessionManager,
                        appContainer.activeProfileHolder,
                        appContainer.rideRepository,
                        sourceId,
                    )
                },
            )
            CreatorScreen(
                viewModel = viewModel,
                onStartVideoRide = { videoId ->
                    navController.navigate(Destinations.videoRide(videoId))
                },
                onBack = { navController.popBackStack() },
            )
        }
```

- [ ] **Step 8: Verify the whole suite passes**

```bash
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL, all tests PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride/ui \
        app/src/test/java/dev/digitalducktape/openride/ui/creator
git commit -m "Add creator page with latest classes and curated playlist shelves"
```

---

## Task 10: Content Sources screen and final wiring

**Files:**
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/sources/ContentSourcesViewModel.kt`
- Create: `app/src/main/java/dev/digitalducktape/openride/ui/sources/ContentSourcesScreen.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/AppContainer.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/navigation/Destinations.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/navigation/OpenRideNavHost.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/profile/ProfileTabScreen.kt`
- Modify: `app/src/main/java/dev/digitalducktape/openride/ui/main/MainScaffold.kt`
- Modify: `docs/DECISIONS.md`
- Test: `app/src/test/java/dev/digitalducktape/openride/ui/sources/ContentSourcesViewModelTest.kt`

**Interfaces:**
- Consumes: `ContentSourceRepository` (Task 5), `ChannelHandleResolver`/`ResolvedSource` (Task 4).
- Produces:
  - `sealed interface AddSourceState { Idle; Resolving; data class Resolved(val source: ResolvedSource); data class Failed(val message: String) }`
  - `ContentSourcesViewModel(sourceRepository, resolver)` with `sources: Flow<List<ContentSource>>`, `addState: StateFlow<AddSourceState>`, `suspend fun resolve(input: String)`, `suspend fun confirmAdd(category: ContentCategory)`, `fun cancelAdd()`, `suspend fun setHidden(id, hidden)`, `suspend fun deleteCustom(id)`
  - `Destinations.ContentSources = "content_sources"`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/dev/digitalducktape/openride/ui/sources/ContentSourcesViewModelTest.kt`:

```kotlin
package dev.digitalducktape.openride.ui.sources

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.digitalducktape.openride.core.content.ChannelHandleResolver
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.ContentSourceRepository
import dev.digitalducktape.openride.core.content.ContentSourceType
import dev.digitalducktape.openride.core.content.FeedFetcher
import dev.digitalducktape.openride.core.data.OpenRideDatabase
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentSourcesViewModelTest {

    private lateinit var db: OpenRideDatabase
    private lateinit var repository: ContentSourceRepository

    private val channelPage = """
        <html><head><title>Kaleigh Cohen Cycling - YouTube</title></head>
        <body><script>{"externalId":"UChY_9WJx0saa0St48lSdytQ"}</script></body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenRideDatabase::class.java,
        ).build()
        repository = ContentSourceRepository(db.contentSourceDao())
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(fetcher: FeedFetcher = FeedFetcher { channelPage.byteInputStream() }) =
        ContentSourcesViewModel(repository, ChannelHandleResolver(fetcher))

    @Test
    fun `add state starts idle`() = runTest {
        assertEquals(AddSourceState.Idle, viewModel().addState.value)
    }

    @Test
    fun `resolving a handle exposes the resolved source for confirmation`() = runTest {
        val viewModel = viewModel()

        viewModel.resolve("@kaleigh")

        val resolved = viewModel.addState.value as AddSourceState.Resolved
        assertEquals("UChY_9WJx0saa0St48lSdytQ", resolved.source.youtubeId)
        assertEquals("Kaleigh Cohen Cycling", resolved.source.displayName)
        assertEquals(ContentSourceType.CHANNEL, resolved.source.sourceType)
    }

    @Test
    fun `resolving does not add anything until confirmed`() = runTest {
        val viewModel = viewModel()

        viewModel.resolve("@kaleigh")

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `confirmAdd saves the resolved source with the chosen category`() = runTest {
        val viewModel = viewModel()
        viewModel.resolve("@kaleigh")

        viewModel.confirmAdd(ContentCategory.Scenic)

        val saved = repository.observeAll().first().single()
        assertEquals("UChY_9WJx0saa0St48lSdytQ", saved.youtubeId)
        assertEquals(ContentCategory.Scenic, saved.category)
        assertFalse(saved.builtIn)
        assertEquals(AddSourceState.Idle, viewModel.addState.value)
    }

    @Test
    fun `confirmAdd with nothing resolved does nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.confirmAdd(ContentCategory.Workout)

        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `a failed resolution surfaces a message and saves nothing`() = runTest {
        val viewModel = viewModel(FeedFetcher { throw IOException("offline") })

        viewModel.resolve("@kaleigh")

        assertTrue(viewModel.addState.value is AddSourceState.Failed)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `a page without a channel id reports not found`() = runTest {
        val viewModel = viewModel(FeedFetcher { "<html>nothing</html>".byteInputStream() })

        viewModel.resolve("@ghost")

        assertTrue(viewModel.addState.value is AddSourceState.Failed)
    }

    @Test
    fun `cancelAdd clears a pending resolution`() = runTest {
        val viewModel = viewModel()
        viewModel.resolve("@kaleigh")

        viewModel.cancelAdd()

        assertEquals(AddSourceState.Idle, viewModel.addState.value)
    }

    @Test
    fun `hiding and unhiding a source is reflected in the list`() = runTest {
        val viewModel = viewModel()
        repository.seedIfEmpty()
        val source = viewModel.sources.first().first()

        viewModel.setHidden(source.id, true)
        assertTrue(viewModel.sources.first().single { it.id == source.id }.hidden)

        viewModel.setHidden(source.id, false)
        assertFalse(viewModel.sources.first().single { it.id == source.id }.hidden)
    }

    @Test
    fun `deleting a custom source removes it from the list`() = runTest {
        val viewModel = viewModel()
        viewModel.resolve("@kaleigh")
        viewModel.confirmAdd(ContentCategory.Workout)
        val added = viewModel.sources.first().single()

        viewModel.deleteCustom(added.id)

        assertTrue(viewModel.sources.first().isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContentSourcesViewModelTest*'
```

Expected: FAIL — `Unresolved reference: ContentSourcesViewModel`.

- [ ] **Step 3: Create the view model**

`ui/sources/ContentSourcesViewModel.kt`:

```kotlin
package dev.digitalducktape.openride.ui.sources

import androidx.lifecycle.ViewModel
import dev.digitalducktape.openride.core.content.ChannelHandleResolver
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.ContentSource
import dev.digitalducktape.openride.core.content.ContentSourceRepository
import dev.digitalducktape.openride.core.content.ResolvedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the "add a source" flow currently is. */
sealed interface AddSourceState {
    data object Idle : AddSourceState

    data object Resolving : AddSourceState

    /** Resolved and awaiting the rider's category choice — nothing has been saved yet. */
    data class Resolved(val source: ResolvedSource) : AddSourceState

    data class Failed(val message: String) : AddSourceState
}

/**
 * The Content Sources settings screen: which creators and playlists the Classes tab draws
 * from.
 *
 * Adding is deliberately two-step — resolve, then confirm with a category — because a pasted
 * link resolves to a name the rider should see before it becomes a row on their Classes tab,
 * and because a failed resolution must never leave a half-configured source behind.
 */
class ContentSourcesViewModel(
    private val sourceRepository: ContentSourceRepository,
    private val resolver: ChannelHandleResolver,
) : ViewModel() {

    val sources: Flow<List<ContentSource>> = sourceRepository.observeAll()

    private val _addState = MutableStateFlow<AddSourceState>(AddSourceState.Idle)
    val addState: StateFlow<AddSourceState> = _addState.asStateFlow()

    suspend fun resolve(input: String) {
        _addState.value = AddSourceState.Resolving
        _addState.value = resolver.resolve(input).fold(
            onSuccess = { AddSourceState.Resolved(it) },
            onFailure = { error ->
                AddSourceState.Failed(
                    when (error) {
                        is java.io.IOException -> "No connection — try again"
                        else -> "Couldn't find that channel or playlist"
                    },
                )
            },
        )
    }

    suspend fun confirmAdd(category: ContentCategory) {
        val resolved = (_addState.value as? AddSourceState.Resolved)?.source ?: return
        sourceRepository.add(resolved, category)
        _addState.value = AddSourceState.Idle
    }

    fun cancelAdd() {
        _addState.value = AddSourceState.Idle
    }

    suspend fun setHidden(id: Long, hidden: Boolean) = sourceRepository.setHidden(id, hidden)

    suspend fun deleteCustom(id: Long) = sourceRepository.deleteCustom(id)
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContentSourcesViewModelTest*'
```

Expected: PASS (10 tests).

- [ ] **Step 5: Create the screen**

`ui/sources/ContentSourcesScreen.kt`:

```kotlin
package dev.digitalducktape.openride.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.digitalducktape.openride.core.content.ContentCategory
import dev.digitalducktape.openride.core.content.ContentSource
import dev.digitalducktape.openride.core.content.ContentSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Content Sources: add your own creators and playlists, and hide the built-in ones you don't
 * ride. Reached from the Profile tab.
 */
@Composable
fun ContentSourcesScreen(
    viewModel: ContentSourcesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources by viewModel.sources.collectAsStateList()
    val addState by viewModel.addState.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 32.dp, horizontal = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Content sources",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(
                text = "Paste a YouTube channel link, @handle, or playlist link",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text("@creator or https://youtube.com/…") },
                    modifier = Modifier.width(520.dp),
                )
                Button(
                    onClick = { scope.launch { viewModel.resolve(input) } },
                    enabled = input.isNotBlank() && addState !is AddSourceState.Resolving,
                ) {
                    Text("Look up")
                }
            }

            when (val state = addState) {
                is AddSourceState.Resolving -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )

                is AddSourceState.Failed -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )

                is AddSourceState.Resolved -> Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Add \"${state.source.displayName}\" as:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = {
                            scope.launch {
                                viewModel.confirmAdd(ContentCategory.Workout)
                                input = ""
                            }
                        }) { Text("Workout") }
                        Button(onClick = {
                            scope.launch {
                                viewModel.confirmAdd(ContentCategory.Scenic)
                                input = ""
                            }
                        }) { Text("Scenic") }
                        OutlinedButton(onClick = viewModel::cancelAdd) { Text("Cancel") }
                    }
                }

                is AddSourceState.Idle -> Unit
            }

            LazyColumn(
                modifier = Modifier.padding(top = 24.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        onToggleHidden = { scope.launch { viewModel.setHidden(source.id, !source.hidden) } },
                        onDelete = { scope.launch { viewModel.deleteCustom(source.id) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: ContentSource,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = buildString {
                    append(if (source.sourceType == ContentSourceType.PLAYLIST) "Playlist" else "Channel")
                    append("  •  ")
                    append(source.category.name)
                    if (source.builtIn) append("  •  Built in")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = !source.hidden, onCheckedChange = { onToggleHidden() })
        // Built-ins can only be hidden — keeping them means a rider can always get the
        // original catalog back without reinstalling.
        if (!source.builtIn) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove ${source.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun <T> Flow<List<T>>.collectAsStateList() = collectAsState(initial = emptyList())
```

- [ ] **Step 6: Wire the route and the Profile-tab entry point**

In `ui/navigation/Destinations.kt`:

```kotlin
    /** Rider-managed Classes catalog (channels and playlists), reachable from the Profile tab. */
    const val ContentSources = "content_sources"
```

In `ui/navigation/OpenRideNavHost.kt`, add the imports and route:

```kotlin
import dev.digitalducktape.openride.ui.sources.ContentSourcesScreen
import dev.digitalducktape.openride.ui.sources.ContentSourcesViewModel
```

```kotlin
        composable(Destinations.ContentSources) {
            val viewModel: ContentSourcesViewModel = viewModel(
                factory = viewModelFactory {
                    ContentSourcesViewModel(
                        appContainer.contentSourceRepository,
                        appContainer.channelHandleResolver,
                    )
                },
            )
            ContentSourcesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
```

In `AppContainer.kt`, confirm the content wiring from Task 6 Step 5 is present and add the
resolver:

```kotlin
    /** Resolves pasted channel/playlist links for the Content Sources screen. */
    val channelHandleResolver: ChannelHandleResolver by lazy {
        ChannelHandleResolver()
    }
```

In `ui/profile/ProfileTabScreen.kt`, add an `onManageContentSources: () -> Unit` parameter
next to `onManageAppUpdates`, and a button after the "App updates" one:

```kotlin
            OutlinedButton(
                onClick = onManageContentSources,
                modifier = Modifier.width(280.dp).padding(top = 16.dp),
            ) {
                Text("Content sources")
            }
```

In `ui/main/MainScaffold.kt`, pass it through on the `ProfileTabScreen` call:

```kotlin
                    onManageContentSources = {
                        outerNavController.navigate(Destinations.ContentSources)
                    },
```

- [ ] **Step 7: Record the decisions**

Append to `docs/DECISIONS.md`:

```markdown
## Classes catalog: page scraping alongside RSS

The Classes catalog fetches each source's public YouTube page in addition to its RSS feed.
The feed carries neither video duration nor any visibility flag and stops at 15 videos, so
"no classes under 10 minutes" and "no members-only classes" are both impossible from it. The
page's embedded `ytInitialData` has durations, is public-only, and covers ~30 videos.

The alternative was the YouTube Data API, which would mean an API key and a quota — this app
is deliberately key-free. The cost of scraping is fragility: YouTube can change the page
shape whenever it likes. That's contained by treating a parse failure exactly like a network
failure, so the catalog falls back to RSS (working, but unfiltered by length) and then to the
on-disk cache, and by keeping the parser's expected structure pinned in fixtures.

Every content fetch also retries once: YouTube's feed and page endpoints intermittently
return 404/500 for valid URLs — observed alternating between 200 and 404 seconds apart.

## The Classes catalog lives in the database

`ChannelConfig` is now only a seed list. The live catalog is the `content_sources` table so
riders can add their own channels and playlists and hide built-ins they don't ride. Built-ins
are hideable but not deletable, so the original catalog is always recoverable.
```

- [ ] **Step 8: Verify everything**

```bash
./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL and every test PASS. Report the actual test count and any failures
rather than assuming success.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/dev/digitalducktape/openride \
        app/src/test/java/dev/digitalducktape/openride/ui/sources docs/DECISIONS.md
git commit -m "Add Content Sources screen for rider-managed channels and playlists"
```

---

## Verification checklist

Before declaring the work done, confirm each of these against actual command output:

- [ ] `./gradlew :app:testDebugUnitTest` passes with no failures.
- [ ] `./gradlew :app:assembleDebug` produces an APK.
- [ ] `app/schemas/…/5.json` exists and is committed.
- [ ] `grep -rn "ContentCategory.Rides" app/src` returns nothing.
- [ ] The spec's five goals each map to a merged task: new channels (Task 1), filters (Task 6),
      classes-page controls and Random Ride (Tasks 7–8), creator page (Task 9), custom sources
      (Task 10).

Live behavior on the bike can't be verified from this session (no emulator, and the content
layer needs real network access). When handing back, say plainly which parts were verified by
tests and which were only verified by compiling.
