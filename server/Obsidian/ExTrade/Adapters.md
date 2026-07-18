# Adapters

ExoTrade uses custom `RecyclerView.Adapter` implementations to handle the rendering of complex lists throughout the application.

## Core Adapters

### [[ListingAdapter.kt]]
Used in: `BrowseListings`, `BreedingFeed`, `Profile`.
- **Data Type**: `Listing`.
- **Logic**:
    - Binds listing metadata (Price, Common Name, Sex).
    - Uses `Helpers.loadImage()` for the main specimen photo.
    - Implements **Subscription Tier Highlighting**: Tier 2 sellers get a specialized border or badge.
    - Handles **Long-Press Social Actions**: Opens a context menu for direct WhatsApp/Social contact.

### [[MessageAdapter.kt]]
Used in: `ChatActivity`.
- **Data Type**: `Message`.
- **Logic**:
    - Multi-type view holder for "Sent" vs "Received" messages.
    - **Listing Previews**: Renders a specialized sub-view if a message contains a listing reference (staged message).
    - Displays timestamps and delivery status.

### [[ConversationAdapter.kt]]
Used in: `InboxActivity`.
- **Data Type**: `Conversation`.
- **Logic**:
    - Displays the recipient's profile picture and username.
    - Shows the "Last Message" snippet.
    - Renders an **Unread Indicator** (Material 3 Badge) if the conversation has new activity.

### [[FriendAdapter.kt]]
Used in: `FriendsActivity`.
- **Data Type**: `User`.
- **Logic**:
    - Context-aware action buttons:
        - "Accept" / "Decline" for incoming requests.
        - "Message" / "Profile" for established friends.
        - "Add" for search results.
    - Displays mutual friend counts (if applicable).

### [[ReportAdapter.kt]]
Used in: `AdminActivity`.
- **Data Type**: `Report`.
- **Logic**:
    - Lists reported items with their status (Pending, Resolved).
    - Provides buttons for "Take Down", "Ban User", or "Dismiss Report".

### [[UserAdapter.kt]]
Used in: General search contexts.
- Simple user row displaying profile picture and username.
