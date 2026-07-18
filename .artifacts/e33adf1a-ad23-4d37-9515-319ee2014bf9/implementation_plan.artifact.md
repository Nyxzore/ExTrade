# Implementation Plan: Complete Migration from PHP to Go

The goal is to finalize the transition to the Go backend by removing all PHP-style route suffixes (`.php`), updating the Android client to use clean API paths, and deleting the legacy PHP source code.

## Proposed Changes

### 1. Go Server Refactoring
#### [MODIFY] [main.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/main.go)
- Remove `.php` from all registered routes.
- Update `r.StaticFile("/get-app.php", ...)` to a cleaner path like `/get-app`.
- Standardize on clean, extension-less paths.

### 2. Android Client Update
#### [MODIFY] [Multiple Files](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/java/com/example/exotrade/)
- Use a project-wide search and replace to remove `.php` from all API endpoint strings in Kotlin code.
- Impacted files include:
    - `SpeciesRepository.kt`
    - `AdminActivity.kt`
    - `BreedingFeed.kt`
    - `ChatActivity.kt`
    - `BrowseListingsViewModel.kt`
    - (and many others identified in the research phase)

### 3. Legacy Code Cleanup
#### [DELETE] [php_variant/](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/php_variant/)
- Permanently remove the `server/php_variant/` directory and its contents.

### 4. Documentation Update
#### [MODIFY] [Backend.md](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/Obsidian/ExTrade/Backend.md)
- Update the API documentation to reflect the new clean paths.
- Remove references to PHP as a "variant" and mark it as fully decommissioned.

## Verification Plan

### Automated Tests
- Run the full Go test suite (`go test ./...`) to ensure no logic was broken.
- Since paths are changing, I will need to update any tests that explicitly use the `.php` suffixes (though most use handler functions directly).

### Manual Verification
- Deploy the updated Go server.
- Deploy the updated Android app.
- Perform a smoke test of all major features:
    - Authentication (Login/Register)
    - Fetching Listings/Breeding listings
    - Messaging flow
    - Social/Friends interactions
    - Admin actions
- Verify static assets (images) still load correctly via the new fallback routing.
