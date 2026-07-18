# Implementation Plan - Set Login as Entry Point

This plan will change the app's entry point from the boilerplate `MainActivity` (the "hello world menu") to the `Login` activity.

## Proposed Changes

### 1. Update Android Manifest
Move the `MAIN` and `LAUNCHER` intent filter from `MainActivity` to `.activities.auth.Login`.

#### [MODIFY] [AndroidManifest.xml](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/AndroidManifest.xml)
- Remove the intent-filter from `MainActivity`.
- Add the intent-filter to `.activities.auth.Login`.
- Set `MainActivity` as not exported or delete it.

### 2. Cleanup
Delete the boilerplate `MainActivity` files.

#### [DELETE] [MainActivity.kt](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/java/com/example/exotrade/MainActivity.kt)
#### [DELETE] [activity_main.xml](file:///home/nyxzore/AndroidStudioProjects/exotrade/app/src/main/res/layout/activity_main.xml)

## Verification Plan

### Automated Tests
- Build the app to ensure no references to `MainActivity` remain.
- Deploy the app to verify it starts directly at the Login screen (or redirects to BrowseListings if already logged in).

### Manual Verification
- Verify that the "Hello World" screen is no longer shown.
