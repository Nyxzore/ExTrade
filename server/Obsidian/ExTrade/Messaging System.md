# Messaging System

ExoTrade features a custom-built, End-to-End Encrypted (E2EE) messaging system designed to protect user privacy without sacrificing performance.

## Kotlin Implementation

### Activities
- **[[InboxActivity.kt]]**:
    - Displays a list of active conversations.
    - Observes the `messaging/get_conversations` endpoint.
    - Uses `ConversationAdapter` to render threads.
- **[[ChatActivity.kt]]**:
    - The core messaging interface.
    - **Encryption**: Fetches the recipient's public key from the backend. Uses `EncryptionManager` to encrypt the `body` and generate a `nonce` before sending.
    - **Decryption**: Decrypts incoming message blobs using the user's private key (stored in `EncryptedSharedPreferences`).
    - **Polling**: Current implementation uses a `Handler` or `Coroutine` loop to poll `messaging/get_messages` every 3 seconds.
    - **Staged Messages**: When a user contacts a seller from a listing, a "staged" listing reference is injected into the chat.

### Core Logic
- **[[EncryptionManager.kt]]** (Shared):
    - Encapsulates the **Sodium** logic for message security.
    - **`encryptMessage()`**: Performs authenticated symmetric encryption.
    - **`decryptMessage()`**: Verifies and decrypts messages.
- **[[MessageAdapter.kt]]**:
    - Renders bubbles with distinct styles for the sender and receiver.
    - Supports "Listing Previews" as a specialized message type.

## Backend Interaction
- **`messaging/send_message`**: Receives an encrypted blob, nonce, and recipient ID. (Implemented in Go).
- **`messaging/get_messages`**: Returns encrypted blobs that only the recipient can open. (Legacy PHP - **Deprecated**).
- **`messaging/start_or_get_conversation`**: Facilitates the initial key exchange and conversation metadata setup. (Legacy PHP - **Deprecated**).

## Security Architecture
1. **Key Generation**: Identity keys (X25519) are generated on the first login via `EncryptionManager`.
2. **Key Storage**:
    - **Public Key**: Uploaded to the `users` table.
    - **Private Key**: Encrypted using a password-derived backup key (Argon2id) and stored in `EncryptedSharedPreferences`.
## Test Cases
### End-to-End Encryption
- **TC-MSG-01**: Verify that a message encrypted by Alice for Bob can only be decrypted by Bob's private key.
- **TC-MSG-02**: Ensure `EncryptionManager` correctly handles emojis and multi-byte UTF-8 characters without corruption during Base64 encoding.
- **TC-MSG-03**: Verify that tampered encrypted blobs (even a single bit change) are rejected by the decryption logic with a null result.

### UI & UX
- **TC-MSG-04**: Verify that "Staged Messages" render the listing preview card correctly with price and species name.
- **TC-MSG-05**: Ensure the 3-second polling loop pauses when the `ChatActivity` is in the background to save battery.
