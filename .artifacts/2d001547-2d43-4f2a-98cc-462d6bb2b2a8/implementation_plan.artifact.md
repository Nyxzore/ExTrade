# Implementation Plan - Navigation Refinement and Create Listing UI Overhaul

This plan focuses on improving the app's navigation lifecycle and overhauling the "Create Listing" UI to be more user-friendly and less cluttered.

## User Review Required

> [!IMPORTANT]
> I will be changing the core navigation logic to keep the main activities (`BrowseListings`, `InboxActivity`, `CreateListing`, and `Profile`) alive in the background instead of finishing them. This allows users to switch tabs without losing their progress (e.g., while filling out a listing).

## Proposed Changes

### 1. Navigation Lifecycle Improvements

#### [MODIFY] [NavigationHelper.kt](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/java/com/example/exotrade/utils/NavigationHelper.kt)
- Update the tab switching logic to use `Intent.FLAG_ACTIVITY_REORDER_TO_FRONT`.
- Remove `activity.finish()` to ensure activities remain in the backstack.

### 2. UI Overhaul: Create Listing

#### [MODIFY] [listing_activity_create.xml](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/res/layout/listing_activity_create.xml)
- **Header**: Add an "Information" icon button (`ic_info_24`) to the `MaterialToolbar`.
- **Photo Card**: Reduce height to `180dp` and simplify the overlay.
- **Clutter Reduction**:
    - Remove redundant section labels (e.g., "Species Information", "Pricing & Terms").
    - Use subtle dividers instead of large headers.
- **Input Consistency**:
    - Align "Sex" and "Size" in a single row with equal weights.
    - Group "Age" and "Unit" more cleanly.
- **Spacing**: Increase vertical spacing between groups and standardize internal paddings.

#### [MODIFY] [CreateListing.kt](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/java/com/example/exotrade/activities/listings/CreateListing.kt)
- Add logic to show a "Listing Help" dialog when the information button is pressed.
- Remove redundant navigation overrides if any.

### 3. UI Overhaul: Create Breeding Listing

#### [MODIFY] [breeding_activity_create.xml](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/res/layout/breeding_activity_create.xml)
- Apply the same UI refinements as `listing_activity_create.xml` for consistency.

## Verification Plan

### Manual Verification
- **Navigation**:
    1. Open "Create Listing".
    2. Start filling in some text.
    3. Switch to the "Home" tab.
    4. Switch back to the "Add" tab.
    5. Verify that the text you entered is still there (activity was not finished).
- **UI**:
    1. Verify the new, cleaner layout on both "For Sale" and "For Breeding" screens.
    2. Verify the information button opens a helpful dialog.
    3. Verify that the activity only finishes after successfully publishing a listing.
