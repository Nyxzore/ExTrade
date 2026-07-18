# Task Checklist - PHP to Go Porting

## Group 1: Core
- [x] Port `core/get_versions.php` to `core.go`
- [x] Port `core/report_item.php` to `core.go`
- [x] Port `core/social_helpers.php` to `server/pkg/utils/social.go`
- [x] Port `get-app.php` to `main.go` (served via `get-app.html`)
- [x] Register Group 1 routes in `main.go`
- [x] Verify Group 1 with compilation

## Group 2: Profile
- [x] Port `profile/get_profile.php` to `profile.go`
- [x] Port `profile/update_profile.php` to `profile.go`
- [x] Register Group 2 routes in `main.go`
- [x] Verify Group 2 with compilation

## Group 3: Listings (Remaining)
- [x] Port `listings/create_listing.php` to `listings.go`
- [x] Port `listings/update_listing.php` to `listings.go`
- [x] Port `listings/delete_listing.php` to `listings.go`
- [x] Port `listings/get_listing_details.php` to `listings.go`
- [x] Register Group 3 routes in `main.go`
- [x] Verify Group 3 with compilation

## Group 4: Breeding (Remaining)
- [x] Port `breeding/create_breeding_listing.php` to `breeding.go`
- [x] Port `breeding/update_breeding_listing.php` to `breeding.go`
- [x] Port `breeding/delete_breeding_listing.php` to `breeding.go`
- [x] Port `breeding/get_breeding_listing_details.php` to `breeding.go`
- [x] Port `breeding/get_my_breeding_status.php` to `breeding.go`
- [x] Port `breeding/find_breeding_matches.php` to `breeding.go`
- [x] Register Group 4 routes in `main.go`
- [x] Verify Group 4 with compilation

## Group 5: Friends (Remaining)
- [x] Port `friends/send_friend_request.php` to `friends.go`
- [x] Port `friends/accept_friend_request.php` to `friends.go`
- [x] Port `friends/decline_friend_request.php` to `friends.go`
- [x] Port `friends/remove_friend.php` to `friends.go`
- [x] Port `friends/get_friend_requests.php` to `friends.go`
- [x] Port `friends/search_users.php` to `friends.go`
- [x] Register Group 5 routes in `main.go`
- [x] Verify Group 5 with compilation

## Group 6: Messaging (Remaining)
- [x] Port `messaging/get_conversations.php` to `messaging.go`
- [x] Port `messaging/get_messages.php` to `messaging.go`
- [x] Port `messaging/mark_read.php` to `messaging.go`
- [x] Port `messaging/start_or_get_conversation.php` to `messaging.go`
- [x] Port `messaging/get_backup.php` to `messaging.go`
- [x] Register Group 6 routes in `main.go`
- [x] Verify Group 6 with compilation

## Group 7: Admin
- [x] Port `admin/get_flagged_items.php` to `admin.go`
- [x] Port `admin/resolve_report.php` to `admin.go`
- [x] Port `admin/take_down_listing.php` to `admin.go`
- [x] Port `admin/ban_user.php` to `admin.go`
- [x] Port `admin/get_notifications.php` to `admin.go`
- [x] Register Group 7 routes in `main.go`
- [x] Verify Group 7 with compilation
