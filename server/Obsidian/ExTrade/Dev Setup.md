# Dev Setup

## Running the stack locally
1. Copy `server/.env.example` to `server/.env` and fill in DB credentials and `AUTH_PEPPER`.
2. Start the Go server: `go run main.go` from `server/` (default port `8080`).
3. Start the Cloudflare Tunnel: `cloudflared tunnel run exotrade`
4. Confirm the Go bind address/port matches the tunnel's ingress config.

The Android app calls clean Go API paths.


## Shared KMP Module
The `:shared` module can be tested using the following Gradle tasks:
- `./gradlew :shared:testDebugUnitTest` (JVM-based unit tests).
- iOS tests require a macOS environment and are currently out of scope for the local Linux setup.

## Environment
- `server/.env` holds `AUTH_PEPPER`, PostgreSQL credentials, and `PORT` — never commit real values here.

## Testing
See [[Testing]] for the full test inventory, TC-* case mapping, and coverage gaps.

**Quick commands:**
```bash
# Go backend (unit tests, no DB required for most)
cd server && go test ./...

# Shared KMP module
./gradlew :shared:testDebugUnitTest

# Android app unit tests (Robolectric)
./gradlew :app:testDebugUnitTest

# Android instrumented tests (requires device/emulator)
./gradlew :app:connectedDebugAndroidTest
```
