# Fix Navigation Jitter and Selection Logic

The user reports that the navigation is "broken", highlights the wrong thing, and is "super jittery".

## Analysis of Jitter
1.  **Main Thread Work**: `Helpers.updateNavProfileIcon` runs on every `onResume`. It loops through menu items to tint them manually and creates new `Bitmap` and `BitmapDrawable` objects every time.
2.  **Untinted Icons**: `prepareBottomNav` sets `itemIconTintList` to `null` but doesn't apply a baseline tint. Icons only get tinted when `updateNavProfileIcon` runs, causing a visual "pop" or "flash".
3.  **Glide Callbacks**: Even cached Glide loads involve main thread callbacks that can cause micro-stutters if they trigger layout passes.

## Analysis of "Wrong Thing" / Broken Navigation
1.  **Tab Collision**: Both `BrowseListings` and `BreedingFeed` use `R.id.nav_home`. If the user is in `BreedingFeed` and clicks the "Home" icon, `NavigationHelper` thinks they are already "Home" and does nothing. This prevents switching back to the main sale feed.
2.  **Activity Stack**: Mixed use of `REORDER_TO_FRONT` and `finish()` might be causing inconsistent backstack behavior.

## Proposed Changes

### 1. Optimize `Helpers.kt` for Smoothness
- **Baseline Tinting**: Move the manual icon tinting to `prepareBottomNav` so icons are tinted immediately when the activity starts.
- **Drawable Caching**: Cache the actual `BitmapDrawable` (rendered profile icon) to avoid re-creating it on every screen switch.
- **Efficiency**: Only re-render the profile icon if the URL or Subscription Tier has changed.

### 2. Refine `NavigationHelper.kt` for Smarter Selection
- **Context-Aware Navigation**: Change the `itemId == selectedItemId` check. Even if they match, navigate if the current activity is NOT the "primary" activity for that tab (e.g., if in `BreedingFeed`, clicking `nav_home` should take you to `BrowseListings`).
- **Activity Mapping**: Define primary activities for each tab.

### 3. Stabilize Activity Transitions
- Ensure that switching between "Sale" and "Breeding" (via the toggle) is handled gracefully without breaking the bottom nav state.
- Keep `REORDER_TO_FRONT` for tabs, but be careful with `finish()` on the main tab activities.

## Verification Plan

### Manual Verification
- Verify that clicking the "Home" icon while in `BreedingFeed` successfully returns to `BrowseListings`.
- Verify that icons are correctly tinted immediately upon activity launch (no flashing).
- Rapidly switch between tabs and verify the profile icon remains stable and doesn't flicker.
- Check that the navigation bar height and selection state remain "perfect" as per previous requirements.
