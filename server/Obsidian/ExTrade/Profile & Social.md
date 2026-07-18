# Profile & Social

The profile system serves as the identity and account management hub for ExoTrade users.

## Kotlin Implementation

### Activities & ViewModels
- **[[Profile.kt]]**:
    - Displays user details and their personal listings feed.
    - Handles logic for viewing own profile vs. another user's profile.
    - Integrates `ListingAdapter` to show the user's active sales and breeding posts.
- **[[EditAccount.kt]]**:
    - High-security activity for updating credentials.
    - **Password Change**: Triggers a re-derivation of the E2EE backup key via `EncryptionManager.deriveBackupKey()` to ensure the private key remains accessible.
    - **[[Social Links]]**: Allows users to input and validate WhatsApp numbers, IG handles, and FB links.
- **[[Login.kt]]**:
    - Coordinates with `LoginViewModel` to handle authentication.
    - Implements "Remember Me" logic via `SessionRepository`.
    - **Key Recovery**: If logging in on a new device, it prompts for the password to decrypt the identity key backup from the server.
- **[[CreateAccount.kt]]**:
    - Collects initial user data and social handles.
    - Generates the initial E2EE identity key pair.
- **[[LoginViewModel.kt]]** (Shared):
    - Manages the authentication state (`isLoading`, `errorMessage`, `loginSuccess`).
    - Verifies existing sessions on app startup.

### Logic
- **[[SessionRepository.kt]]** (Shared):
    - The source of truth for the logged-in user's identity.
    - Manages the persistence of auth tokens and subscription tiers.
- **[[SocialLinkUtils.kt]]**:
    - Provides the validation and normalization logic for contact info displayed on profiles.

## Profile Features
- **Subscription Tiers**: Tiers (0, 1, 2) are reflected in the UI with specialized borders and verified badges, fetched via `SessionRepository`.
## Test Cases
### Session & Auth
- **TC-PROFILE-01**: Verify `LoginViewModel` correctly recovers identity keys from the server using the user's password and salt.
- **TC-PROFILE-02**: Ensure `EditAccount` correctly re-encrypts the identity private key when the user changes their account password.
- **TC-PROFILE-03**: Verify "Remember Me" persistence across app restarts using Robolectric.

### Social Integration
- **TC-PROFILE-04**: Ensure `SocialLinkUtils` correctly normalizes local phone numbers (e.g., `082...`) to international format (`+2782...`) for WhatsApp.
