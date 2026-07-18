# Listings System

The listings system is the primary discovery engine for animal sales, integrated deeply with the taxonomy database and the hotness algorithm.

## Kotlin Implementation

### Activities & ViewModels
- **[[BrowseListings.kt]]**: The entry point for buyers.
    - Observes `BrowseListingsViewModel.listings` to update the UI.
    - Implements `SwipeRefreshLayout` for the `refresh()` flow.
    - Triggers `loadNextPage()` (offset-based) when the scroll reaches the bottom.
- **[[BrowseListingsViewModel.kt]]** (Shared):
    - Manages a `StateFlow` of `List<Listing>`.
    - Handles the `seed` generation for consistent randomization across pages.
    - Calls `apiService.getListings()` with current offset and filters.
- **[[ListingDetails.kt]]**:
    - Displays full metadata for a single animal.
    - Provides a "Contact Seller" button that initiates a chat or opens social links via `SocialLinkUtils`.
    - Shows an "Admin Actions" panel if the current user is an admin.
- **[[CreateListing.kt]]** & **[[EditListing.kt]]**:
    - Uses `SpeciesRepository` for autocomplete on scientific and common names.
    - Uses `ImageUtils` to compress specimen photos before uploading via a multipart request.
    - Validates species LSIDs against the local database before allowing submission.

### Rendering
- **[[ListingAdapter.kt]]**: Renders listing cards with Material 3 styling.
- **[[Listing (Model)]]**: The common data structure used across the Go API and Android app.

## Data Flow
1. `BrowseListings` requests data from `BrowseListingsViewModel`.
2. `BrowseListingsViewModel` fetches JSON from `/listings/get_all_listings` using `ApiService`.
3. The Go backend calculates exposure scores (See [[Backend]]) and returns a randomized list.
4. `BrowseListingsViewModel` updates its `StateFlow`, which is observed by the Activity.
5. `ListingAdapter` binds the models to the `RecyclerView`.

## Discovery Optimization
## Test Cases
### Discovery & Feed
- **TC-LISTING-01**: Verify `BrowseListingsViewModel` correctly increments the offset and maintains the same `seed` during pagination.
- **TC-LISTING-02**: Ensure `ListingAdapter` correctly highlights Tier 2 sellers with the designated Material 3 color surface.
- **TC-LISTING-03**: Verify `SpeciesRepository` synchronizes taxonomy data only when the server version key changes.

### Creation Flow
- **TC-LISTING-04**: Ensure `CreateListing` prevents submission if the selected species does not have a valid LSID in the local database.
- **TC-LISTING-05**: Verify `ImageUtils` reduces a 10MB photo to under 500KB before transmission without significant loss of specimen detail.
