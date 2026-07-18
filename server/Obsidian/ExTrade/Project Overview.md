# ExoTrade Project Overview

ExoTrade is a modern Android application for trading exotic animals, emphasizing security, performance, and a content-first UI.

## Architecture & Tech Stack
The project uses a hybrid architecture combining the flexibility of a Go backend with the power of Kotlin Multiplatform.

### 1. Android App (`:app`)
- **Language**: Kotlin.
- **UI Framework**: Material 3 (XML-based Views with a transition path to Compose).
- **Architecture**: MVVM (Model-View-ViewModel) with data binding.
- **Dependency Injection**: Manual DI via `SharedContainer.kt`.

### 2. Shared Logic (`:shared`)
- **KMP**: Kotlin Multiplatform module sharing logic between Android and potential future platforms.
- **Networking**: Ktor HttpClient.
- **Database**: Room (KMP).
- **Serialization**: Kotlinx Serialization.
- **See [[Shared Logic]] for a deep dive into individual files.**

### 3. Backend (`server/`)
- **Language**: Go (Golang).
- **Framework**: Gin Gonic.
- **Database**: PostgreSQL (with Trigram indexing for fuzzy search).
- **Status**: The Go backend is the active, primary API. The PHP variant is **deprecated**.
- **See [[Backend]] for endpoint and handler details.**

## Key Kotlin Files (App Layer)

### Application Lifecycle
- **[[ExoTradeApplication.kt]]**: The entry point. Initializes the `SharedContainer` and global state.
- **[[SharedContainer.kt]]**: A simple service locator providing singletons for `ApiService`, `SessionRepository`, `SpeciesRepository`, and `EncryptionManager`.

### Utilities (`utils/`)
- **[[Helpers.kt]]**: Global utility functions for UI (Glide image loading, network checks, admin notifications).
- **[[NavigationHelper.kt]]**: Centralized logic for the Bottom Navigation view.
- **[[ImageUtils.kt]]**: Handles client-side image compression and rotation.
- **[[SocialLinkUtils.kt]]**: Logic for normalizing and launching WhatsApp, Instagram, and Facebook intents.
- **[[ShareUtils.kt]]**: Generates and manages the sharing of listing images.

### Foundation
- **[[ViewModelFactory.kt]]**: The boilerplate for instantiating ViewModels with constructor injection from the `SharedContainer`.

## UI Systems
ExoTrade is organized into several functional systems:
- **[[Listings System]]**: Core discovery and selling flow.
- **[[Breeding System]]**: Niche partner-finding engine.
- **[[Messaging System]]**: E2EE communications.
- **[[Friends System]]**: Social graph management.
- **[[Profile & Social]]**: User identity and account management (including [[Social Links]]).
- **[[Moderation System]]**: Safety and admin tools.

## Project Structure
- `app/src/main/java/com/example/exotrade/`
    - `activities/`: UI controllers.
    - `viewmodels/`: Platform-specific ViewModels.
    - `Adapters/`: RecyclerView adapters (See [[Adapters]]).
    - `utils/`: Core helper logic.
- `shared/src/`: The KMP module (See [[Shared Logic]]).
- `server/`: Go backend source code.

## iOS / Shared Module Status
The `:shared` module contains the core business logic, networking, and data persistence for ExoTrade. While the project currently only targets Android, the `:shared` module is built using Kotlin Multiplatform (KMP) to support future iOS development.
- **What's Shared**: Models, repositories, ViewModels, and E2EE logic.
- **What's Missing**: A native iOS UI project (`:iosApp`). Platform-specific implementations for iOS (e.g., `IosStorage`) are defined as interfaces but may need full integration once the iOS app is launched.
