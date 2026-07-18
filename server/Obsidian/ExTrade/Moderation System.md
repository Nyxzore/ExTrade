# Moderation System

The Moderation System provides tools for community safety and administrative control.

## Kotlin Implementation

### Admin UI
- **[[AdminActivity.kt]]**:
    - Restricted to users where `isAdmin()` returns true.
    - Displays a feed of flagged content (Listings, Breeding, Users) fetched from `admin/get_flagged_items.php` (Legacy PHP - **Deprecated**).
    - Uses `ReportAdapter` to list complaints with their targets and reasons.
- **[[ReportAdapter.kt]]**:
    - Provides administrative actions:
        - **Resolve**: Marks a report as handled.
        - **Take Down**: Deactivates a listing or breeding post.
        - **Ban User**: Sets `is_banned = TRUE`.

### Reporting Logic
- **[[ReportDialog.kt]]**:
    - A shared UI component used across the app to flag content.
    - Allows users to select a reason (e.g., Scam, Illegal Species, Harassment).
    - Submits the report to the Go backend with the target's ID and type via `core/report_item.php` (Legacy PHP - **Deprecated**).
- **[[Report (Model)]]** (Shared):
    - Data class representing a moderation record.

## Security Integration
- **Banned User Protection**:
    - Every API call (via `ApiService`) returns a specific error code if the user is banned.
    - `LoginViewModel` or `Helpers.checkAdminNotifications()` monitors these states and triggers an automatic `clearSession()` and redirect to `Login` if a ban is detected.
- **Admin Verification**:
    - The backend middleware verifies the `is_admin` flag on the `users` table for all `admin/*` endpoints.
    - The app checks `SessionRepository.isAdmin()` before showing the "Admin Panel" in the profile or listing menus.

## Test Cases
### Moderation Flow
- **TC-MOD-01**: Verify that reported items appear instantly in the `AdminActivity` feed for authorized admins.
- **TC-MOD-02**: Ensure `Ban User` correctly invalidates all active sessions for that user ID.
- **TC-MOD-03**: Verify that `Take Down` listing correctly removes it from public feeds while preserving it in the database for legal/audit purposes (status set to `moderated`).

### Safety
- **TC-MOD-04**: Verify that non-admin users attempting to access `admin/get_flagged_items.php` (Legacy PHP) receive a `403 Forbidden` response.

