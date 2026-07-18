# Implementation Plan: Port PHP Endpoints to Go

This plan outlines the systematic porting of legacy PHP endpoints to the Go backend, ensuring behavioral parity with the existing Android client.

## User Review Required

> [!IMPORTANT]
> - All Go handlers will use the established `utils.SendSuccess`/`utils.SendError` pattern to maintain consistent JSON response envelopes.
> - Authentication and authorization checks will be strictly ported to match the legacy logic.
> - No changes will be made to the Android client; Go routes will be registered using identical `.php` paths.

## Proposed Changes

The porting will proceed in the following groups:

### 1. Core Endpoints
- **[NEW] [core.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/core.go)**:
    - `core/get_versions.php`
    - `core/report_item.php`
- **[NEW] [social.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/pkg/utils/social.go)**: Port shared link normalization logic from `core/social_helpers.php`.
- **[MODIFY] [main.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/main.go)**:
    - Register core routes.
    - Serve `get-app.php` (APK download info/page).

### 2. Profile Endpoints
- **[NEW] [profile.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/profile.go)**:
    - `profile/get_profile.php`
    - `profile/update_profile.php`

### 3. Listings (Remaining)
- **[MODIFY] [listings.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/listings.go)**:
    - `listings/create_listing.php`
    - `listings/update_listing.php`
    - `listings/delete_listing.php`
    - `listings/get_listing_details.php`

### 4. Breeding (Remaining)
- **[MODIFY] [breeding.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/breeding.go)**:
    - `breeding/create_breeding_listing.php`
    - `breeding/update_breeding_listing.php`
    - `breeding/delete_breeding_listing.php`
    - `breeding/get_breeding_listing_details.php`
    - `breeding/get_my_breeding_status.php`
    - `breeding/find_breeding_matches.php`

### 5. Friends (Remaining)
- **[MODIFY] [friends.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/friends.go)**:
    - `friends/send_friend_request.php`
    - `friends/accept_friend_request.php`
    - `friends/decline_friend_request.php`
    - `friends/remove_friend.php`
    - `friends/get_friend_requests.php`
    - `friends/search_users.php`

### 6. Messaging (Remaining)
- **[MODIFY] [messaging.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/messaging.go)**:
    - `messaging/get_conversations.php`
    - `messaging/get_messages.php`
    - `messaging/mark_read.php`
    - `messaging/start_or_get_conversation.php`
    - `messaging/get_backup.php`

### 7. Admin Endpoints
- **[NEW] [admin.go](file:///home/nyxzore/AndroidStudioProjects/exotrade/server/internal/api/handlers/admin.go)**:
    - `admin/get_flagged_items.php`
    - `admin/resolve_report.php`
    - `admin/take_down_listing.php`
    - `admin/ban_user.php`
    - `admin/get_notifications.php`

## Verification Plan

### Automated Tests
- Create `_test.go` files for each handler domain (e.g., `profile_test.go`, `admin_test.go`).
- Run `go test ./...` in the `server/` directory to verify all handlers.

### Manual Verification
- Perform side-by-side JSON response comparisons between PHP and Go for key endpoints using `curl` or similar tools.
- Verify end-to-end flows in the Android app (if applicable via manual testing/logs).
