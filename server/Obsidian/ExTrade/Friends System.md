# Friends System

The Friends System manages the user's social graph, utilizing trigram similarity for discovery and a secure request flow.

## Kotlin Implementation

### Activities
- **[[FriendsActivity.kt]]**:
    - The social hub containing a two-tab interface (Friends / Requests).
    - Uses a `SearchView` to trigger real-time user discovery via `friends/search_users`.
    - Manages the state of friend requests (Accept/Decline/Cancel).
- **[[UserProfileBottomSheet.kt]]** & **[[ProfileBottomSheet.kt]]**:
    - Contextual UI components that allow users to send friend requests from the chat or listings feed without leaving their current screen.

### Logic & Rendering
- **[[FriendAdapter.kt]]**:
    - A versatile adapter that changes its layout and actions based on the "Friendship Status" (e.g., Pending, Friend, or Stranger).
    - Uses `Helpers.loadImage()` for user avatars.
- **[[User (Model)]]** (Shared):
    - Contains the basic identity fields: `uuid`, `username`, `profile_pic`, and `is_friend` flag.

## Flow & API
1. **Search**: `FriendsActivity` calls `friends/search_users`.
2. **Request**: Clicking "Add Friend" triggers `friends/send_friend_request`.
3. **Observation**: Pending requests are fetched via `friends/get_friend_requests.php` (Legacy PHP - **Deprecated**) and displayed in the "Requests" tab of `FriendsActivity`.
4. **Acceptance**: Clicking "Accept" triggers `friends/accept_friend_request.php` (Legacy PHP - **Deprecated**), which updates the local list and moves the user to the "Friends" tab.

## Database Integration
- **`friendships` Table**: Stores the UUID pairs and status.
## Test Cases
### Social Graph
- **TC-FRIEND-01**: Verify the friend request state machine: `pending` -> `accepted` correctly updates the `action_user_id` and status.
- **TC-FRIEND-02**: Ensure that `FriendsActivity` search results exclude users who are already friends or have a pending request.
- **TC-FRIEND-03**: Verify that removing a friend deletes the entry from the `friendships` table for both users.

### Discovery
- **TC-FRIEND-04**: Benchmark trigram search on a database of 5k+ users to ensure search results appear within 200ms of typing.
