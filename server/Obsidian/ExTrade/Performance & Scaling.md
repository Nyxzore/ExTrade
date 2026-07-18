# Performance & Scaling

ExoTrade is designed for responsiveness and efficiency, utilizing both backend and client-side optimizations.

## Client-Side Optimizations (Kotlin)

### Networking & Data
- **Ktor vs. Legacy**: The shift from OkHttp to **Ktor** in the `:shared` module provides better coroutine integration and reduced boilerplate for multi-platform support.
- **Offset Pagination**: `BrowseListings.kt` pages through the feed using `LIMIT 10 OFFSET $N`, with a seeded randomization (`seed` param) to keep the ordering stable across pages of the same session. Note: this is standard offset pagination, not keyset pagination — offset cost grows with page depth. If deep-feed performance becomes an issue, migrating to keyset (cursor-based) pagination is a future optimization, not something currently implemented.
- **Species Pre-loading**: `SpeciesRepository.preloadCache()` downloads taxonomy names once per session, making autocomplete fields instantaneous without hitting the network.

### Media & Rendering
- **Glide Image Caching**: Profiles and listings use Glide's memory and disk LRU caches to prevent redundant downloads and UI flickering.
- **[[ImageUtils.kt]]**: Compresses images to ~200KB before upload, significantly reducing server storage costs and user data consumption.
- **Circular Bitmap Drawing**: `Helpers.drawCircularBitmap()` uses a `Canvas` to render profile icons with subscription-colored borders once and caches the `Bitmap`, rather than re-calculating on every list scroll.

### UI Responsiveness
- **Transition Control**: `NavigationHelper.kt` disables "fading" animations for bottom navigation, giving the app a fast, single-activity feel even when switching between core activities.
- **Badge Debouncing**: `Helpers.updateUnreadBadge()` uses a timestamp-based debounce to prevent excessive API calls when the user rapidly navigates the app.

## Backend Optimizations (Go/Postgres)
- **GIN Trigram Indexing**: Used on `taxa` and `users` tables to allow high-speed fuzzy search (`ILIKE` and `%` operators).
- **Seeded Randomization**: MD5-based seed randomization ensures that users see a fresh feed on every refresh, but the same feed remains stable during pagination (avoiding duplicate or missing items).
- **Connection Pooling**: `pgxpool` manages a pool of PostgreSQL connections, minimizing the overhead of creating new handshakes for each API request.

## Test Cases
### Scaling
- **TC-PERF-01**: Benchmark `SpeciesRepository.getNames()` with 2k+ entries to ensure retrieval from cache is under 5ms.
- **TC-PERF-02**: Verify that `Helpers.drawCircularBitmap()` hits the memory cache for previously rendered profile icons.
- **TC-PERF-03**: Measure API latency for the randomized listings feed under a simulated load of 50 concurrent requests/sec.

