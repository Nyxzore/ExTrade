# Testing

ExoTrade's test suite spans the Go backend and the Kotlin/Android client. Integration tests that hit PostgreSQL are not yet automated in CI — most Go tests are handler/middleware unit tests that run without a live database.

## Prerequisites
- **Go tests**: No DB needed for current unit tests. Handler tests that exercise SQL will return 500 if `db.Pool` is nil — that is expected in CI/local without Postgres.
- **Kotlin unit tests**: Robolectric (Android SDK 34). No device required.
- **Instrumented tests**: Connected Android device or emulator. LazySodium/JNA native libs must load correctly.

## Running tests

| Layer | Command | Location |
|-------|---------|----------|
| Go backend | `cd server && go test ./...` | `server/**/*_test.go` |
| Shared KMP | `./gradlew :shared:testDebugUnitTest` | `shared/src/commonTest/`, `shared/src/androidUnitTest/` |
| Android unit | `./gradlew :app:testDebugUnitTest` | `app/src/test/` |
| Android instrumented | `./gradlew :app:connectedDebugAndroidTest` | `app/src/androidTest/` |

## Go backend tests

### `pkg/utils/`
- `response_test.go` — `SendError`/`SendSuccess` JSON shape (`status`, `message`, extra fields).
- `image_test.go` — `SaveBase64Image` empty input, invalid base64, size limit, valid PNG round-trip.

### `internal/api/middleware/`
- `auth_test.go` — `AuthRequired` rejects missing credentials and validates valid/expired sessions (mocked); `AdminRequired` without context user; `AppVersionCheck` passes when `app_version` omitted.

### `internal/api/handlers/`
- `auth_test.go` — login/register validation (missing fields, missing E2EE keys), login success (mocked), banned user handling, and registration success.
- `friends_test.go` — `GetFriends` happy path smoke test and empty friend list handling.
- `messaging_test.go` — `SendMessage` rejects missing fields and handles successful message insertion (mocked).
- `obsidian_test.go` — `GetGraphData` and `GetNoteContent` (success, not found, missing param, and path traversal protection).

### Not yet covered (needs test DB or mocks)
- Auth login/register happy paths against real Postgres
- Hotness algorithm query benchmarks (TC-BACKEND-04)
- GIN trigram `EXPLAIN ANALYZE` checks (TC-BACKEND-05)
- Impression batch insert concurrency (TC-BACKEND-02)

## Kotlin / Android tests

### Shared (`shared/src/commonTest/`)
- `SessionRepositoryTest` — login session creation, `clearSession()` wipes auth + identity keys, `authParams()`, `updateUserInfo()`.

### App unit (`app/src/test/`)
| File | Covers |
|------|--------|
| `CryptoHelperTest` | E2EE round-trip, emoji UTF-8, Argon2id, tampered ciphertext (TC-MSG-01–03) |
| `SessionManagerTest` | Logout clears state, Remember Me persistence (TC-PROFILE-03) |
| `SocialLinkUtilsTest` | WhatsApp `082…` → `27…` normalization (TC-PROFILE-04) |
| `LoginBehaviorTest` | Auto-login connectivity vs auth rejection |
| `FriendsActivityTest` | Friends fetch, requests tab, empty state, search mode |
| `ChatActivityTest` | Polling deduplication (TC-MSG partial) |
| `ListingAdapterTest` | Pagination dedup by listing ID |

### App instrumented (`app/src/androidTest/`)
| File | Covers |
|------|--------|
| `E2EEncryptionFlowTest` | Full encrypt/decrypt flow on device |
| `ShareUtilsTest` | Listing share image PNG generation |
| `ListingFlowTest` | `ListingDetails` launch and `CreateListing` UI visibility |
| `BreedingFlowTest` | `BreedingListingDetails` launch and `CreateBreedingListing` UI visibility |

## TC-* case matrix

| ID | Description | Implemented in |
|----|-------------|----------------|
| TC-BACKEND-01 | Auth middleware rejects invalid sessions | `middleware/auth_test.go` (missing creds); full session check needs DB |
| TC-BACKEND-02 | Impression batch inserts | **Gap** |
| TC-BACKEND-03 | Listing details 404/403 | **Gap** (endpoint not ported to Go) |
| TC-BACKEND-04 | Hotness query benchmark | **Gap** |
| TC-BACKEND-05 | Trigram index usage | **Gap** |
| TC-SHARED-01 | `clearSession()` wipes identity keys | `SessionRepositoryTest` |
| TC-SHARED-02 | SpeciesRepository offline fallback | **Gap** |
| TC-SHARED-03 | ApiService error JSON mapping | **Gap** |
| TC-SHARED-04 | LoginViewModel state machine | **Gap** |
| TC-LISTING-01 | BrowseListingsViewModel offset/seed | **Gap** |
| TC-LISTING-02 | Tier 2 seller highlight | **Gap** |
| TC-LISTING-03 | Species version-key sync | **Gap** |
| TC-LISTING-04 | CreateListing LSID validation | **Gap** |
| TC-LISTING-05 | ImageUtils compression | **Gap** |
| TC-MSG-01–03 | E2EE encrypt/decrypt/tamper | `CryptoHelperTest` |
| TC-MSG-04–05 | Staged messages UI, polling pause | **Gap** |
| TC-PROFILE-03 | Remember Me persistence | `SessionManagerTest` |
| TC-PROFILE-04 | WhatsApp normalization | `SocialLinkUtilsTest` |
| TC-FRIEND-01–03 | Friend request state machine | **Gap** (needs Go port + tests) |
| TC-MOD-01–04 | Admin/moderation flows | **Gap** |
| TC-BREEDING-01–03 | Breeding matches/impressions | **Gap** (handler smoke tests only) |
| TC-PERF-01–03 | Cache/latency benchmarks | **Gap** |

## Adding new tests

### Go
- Place tests next to the package under test (`foo_test.go` in same directory).
- Use `gin.TestMode` and `httptest.NewRecorder` for handlers.
- Validation-only tests should not require Postgres; DB integration tests should use a dedicated `exotrade_test` database and run behind a build tag or env check.

### Kotlin
- Pure logic → `app/src/test/` with Robolectric where Android APIs are needed.
- KMP-shared repositories/viewmodels → `shared/src/commonTest/`.
- Device-only flows (LazySodium native, Espresso) → `app/src/androidTest/`.
