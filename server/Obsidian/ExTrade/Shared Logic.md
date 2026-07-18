# Shared Logic (KMP)

The `:shared` module is the core of ExoTrade, containing platform-agnostic business logic, data models, and repository implementations. It ensures that critical logic like encryption, session management, and API communication remains consistent across platforms.

## Core Components

### iOS / Shared Module
The `:shared` module is designed for cross-platform support via Kotlin Multiplatform.
- **Common Logic**: All repositories (`SessionRepository`, `SpeciesRepository`), models, and ViewModels are in `commonMain` and available to both Android and iOS.
- **Networking**: `ApiService.kt` uses Ktor, which works on both platforms.
- **Persistence**: Room (KMP) is configured to use platform-specific drivers.
- **iOS Implementation**:
    - **Storage**: `IosStorage.kt` (in `iosMain`) implements the `Storage` interface using `NSUserDefaults`.
    - **Platform Utils**: `PlatformUtils.ios.kt` provides iOS-specific utilities.
    - **Encryption**: iOS-specific encryption logic (e.g., using CryptoKit or a native wrapper for Sodium) is interfaced via `EncryptionManager`.

### API & Networking (`api/`)
- **[[ApiService.kt]]**: The primary network client.
    - Uses **Ktor HttpClient** with the `OkHttp` engine (on Android).
    - Configured with `ContentNegotiation` and `KotlinxSerialization` for JSON.
    - **Methods**: `getListings()`, `get<T>()`, `post<T>()`, `postForm<T>()`.
    - Handles base URL injection and common headers.
    - *Note*: Some endpoints in `ApiService` (like `getListings`) call new API paths (e.g., `api/listings`), while others use the legacy Go paths for compatibility.

- **[[EncryptionManager.kt]]**: Interface for End-to-End Encryption (E2EE) logic.
    - **Android Implementation**: `AndroidEncryptionManager.kt` (using **LazySodium**).
    - **JNA version pin**: `jna` is pinned to `5.17.0` in `gradle/libs.versions.toml` for lazysodium-android compatibility.
    - **Functions**:
        - `generateIdentityKeys()`: Creates X25519 key pairs.
        - `deriveBackupKey(password, salt)`: Uses **Argon2id** with a salt to derive a 32-byte key from a user password.
        - `encryptPrivateKey()` / `decryptPrivateKey()`: Protects the user's identity key for server-side backup.
        - `encryptMessage()` / `decryptMessage()`: Performs authenticated symmetric encryption of message bodies.

### Database (`database/`)
- **[[AppDatabase.kt]]**: Room database definition (multiplatform).
- **[[SpeciesDao.kt]]**: Data Access Object for the `taxa` table.
    - **Methods**: `refreshSpecies()`, `getAllScientificNames()`, `getCommonName()`.

### Common Models (`models/`)
- **[[Listing.kt]]**: Detailed data class for animal listings (ID, Price, Species, Sex, etc.).
- **[[User.kt]]**: User profile data, including social handles and public keys.
- **[[Message.kt]]**: Chat message data, including encrypted body and sender info.
- **[[Conversation.kt]]**: Metadata for chat threads.
- **[[Species.kt]]**: Representation of a single entry in the taxonomy database.

### ViewModels (`viewmodels/`)
These ViewModels are part of the shared module to allow for shared state logic between platforms.
- **[[BrowseListingsViewModel.kt]]**:
    - Manages the state of the listings feed (`StateFlow<List<Listing>>`).
    - Handles pagination logic, pull-to-refresh, and search query debouncing.
- **[[LoginViewModel.kt]]**:
    - Orchestrates the login flow, including session verification and E2EE key recovery.
    - Interacts with `SessionRepository` and `EncryptionManager`.

## Test Cases
See [[Testing]] for run commands and TC-* implementation status.

### Repository Logic
- **TC-SHARED-01**: `SessionRepository` should correctly clear all keys on `clearSession()`, including sensitive identity keys.
- **TC-SHARED-02**: `SpeciesRepository` should use the local Room database as a fallback when the network is unavailable.
- **TC-SHARED-03**: `ApiService` should correctly map Go backend error JSON to a typed `ApiResponse.Error`.

### ViewModels
- **TC-SHARED-04**: `LoginViewModel` should transition from `Loading` to `Success` state only after successful session verification AND key recovery.

