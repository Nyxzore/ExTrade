# Walkthrough - Complete PHP to Go Cutover

The migration from PHP to Go is now complete. Every trace of PHP has been removed from the internal API surface, and the codebase has been cleaned up.

## Changes Made

### 1. Go Server Refactoring
- **Route Optimization**: Removed the `.php` extension from all Go endpoints. The API now uses clean, modern RESTful paths (e.g., `/auth/auth`, `/listings/get_all_listings`).
- **Static Content**: Updated the APK download page route from `/get-app.php` to `/get-app`.

### 2. Android Client Sync
- **Endpoint Update**: Updated all API calls in the Kotlin codebase to use the new extension-less paths.
- **Smart Preservation**: Intentionally preserved the `profile.php` string in `SocialLinkUtils.kt` to ensure external Facebook profile links continue to be correctly parsed and handled.

### 3. Decommissioning PHP
- **Cleanup**: Permanently deleted the `server/php_variant/` directory. The Go server is now the sole and authoritative backend.
- **Documentation**: Fully updated `Backend.md`, `Breeding System.md`, `Friends System.md`, and `Dev Setup.md` to reflect the final architecture and remove all "deprecated PHP" warnings.

## Routing & Authentication
- **Parameter Mapping**: Fixed a 401 Unauthorized issue where the Android client was sending the user identifier as `uuid` while the server middleware was strictly expecting `user_id`. The server now correctly accepts both `user_id` and `uuid` for authentication.
- **Fallback Static Asset Handling**: Moved static asset logic to a `NoRoute` fallback to prevent routing conflicts between directories (like `/listings/`) and specific API endpoints under the same path.
- **Support for Both Methods**: Standardized handlers like `GetListingDetails` and `GetMessages` to support both `GET` and `POST` as used by different parts of the Android client.

## Verification Results

### Build Status
- **Go Server**: Verified successful build (`go build`).
- **Android App**: Verified successful build (`:app:assembleDebug`).

### Connectivity
- Internal API routes are now standardized and synchronized between client and server.
- Fallback routing for static assets (listings/profile pics) remains functional without wildcard conflicts.

> [!CAUTION]
> **Restart Required**: Ensure you have restarted the Go server (`go run main.go`) to deactivate the old PHP-style routes and enable the clean API paths.
