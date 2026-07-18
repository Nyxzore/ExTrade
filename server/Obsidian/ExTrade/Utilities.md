# Utilities

The ExoTrade app relies on several core utility classes to handle cross-cutting concerns like networking, image processing, and external social integration.

## Core Utilities

### [[Helpers.kt]]
A singleton `object` containing static helpers used throughout the Android app.
- **`loadImage()`**: Centralized Glide wrapper for loading profile pictures and listing images with specific placeholder and error handling.
- **`isNetworkAvailable()`**: Checks for active internet connectivity using `ConnectivityManager`.
- **`updateUnreadBadge()`**: Queries the backend and updates the Bottom Navigation notification badge.
- **`updateNavProfileIcon()`**: Fetches the user's profile picture and renders it as a circular icon directly into the Bottom Navigation menu.
- **`checkAdminNotifications()`**: Background check for reported items (Admin-only).

### [[SocialLinkUtils.kt]]
Manages the integration with 3rd-party social apps (See [[Social Links]]).
- **`normalizeWhatsApp()`**: Formats phone numbers into the international standard required by the `wa.me` API.
- **`openWhatsApp()`**, **`openInstagram()`**, **`openFacebook()`**: Standardized `Intent` logic for launching external apps with fallback to browser.
- **`getIconForPlatform()`**: Maps social platform IDs to drawable resources.

### [[ImageUtils.kt]]
Handles media preparation before upload.
- **`compressImage()`**: Resizes high-resolution camera photos to 1024px (max) and applies JPEG compression to reduce bandwidth usage.
- **`handleRotation()`**: Corrects EXIF rotation data from various Android camera implementations to ensure images appear upright.
- **`toBase64()`**: Converts processed images into Base64 strings for transmission to the Go backend.

### [[NavigationHelper.kt]]
Encapsulates the Bottom Navigation logic to ensure consistency across the main activities (`BrowseListings`, `BreedingFeed`, `InboxActivity`, `FriendsActivity`, `Profile`).
- **`setup()`**: Configures the listener for tab selections and handles the `Activity` transition logic (including disabling animations for a "fragment-like" feel).

### [[ShareUtils.kt]]
Provides tools for creating shareable content.
- **`generateShareImage()`**: (Planned/Implemented) Creates a composite image containing listing details and a QR code or direct link for social media sharing.
- **`shareText()`**: Standard Android `ACTION_SEND` intent for sharing listing URLs.

### [[ReportDialog.kt]]
A reusable Material 3 dialog for reporting listings, profiles, or messages.
- Handles the UI for selecting a report reason and calling the `/api/admin/report` endpoint via `ApiService`.
