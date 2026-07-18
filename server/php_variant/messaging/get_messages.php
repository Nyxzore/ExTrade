<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$conv_id = $_GET['conversation_id'] ?? null;
$limit = $_GET['limit'] ?? 20;

// Keysets for cursor-based pagination
$last_id = $_GET['last_id'] ?? null;
$last_sent_at = $_GET['last_sent_at'] ?? null;

// Since cursor for polling new messages
$since_id = $_GET['since_id'] ?? null;

if (!$conv_id) {
    send_error("Conversation ID is required");
}

// Verify user is part of the conversation
verify_conversation_membership($db, $conv_id, $user_id);

if ($since_id) {
    // Polling mode: get only messages newer than since_id
    $query = "SELECT m.*, u.profile_picture as sender_profile_pic, u.username as sender_username, u.public_key as sender_public_key, u.subscription_tier as sender_subscription_tier
              FROM messages m
              JOIN users u ON m.sender_id = u.id
              WHERE m.conversation_id = $1 AND m.id > $2
              ORDER BY m.sent_at ASC, m.id ASC";
    $params = array($conv_id, $since_id);
} elseif ($last_id && $last_sent_at) {
    // Pagination mode: get messages older than the provided keyset
    $query = "SELECT m.*, u.profile_picture as sender_profile_pic, u.username as sender_username, u.public_key as sender_public_key, u.subscription_tier as sender_subscription_tier
              FROM messages m
              JOIN users u ON m.sender_id = u.id
              WHERE m.conversation_id = $1
                AND (m.sent_at < $2 OR (m.sent_at = $2 AND m.id < $3))
              ORDER BY m.sent_at DESC, m.id DESC
              LIMIT $4";
    $params = array($conv_id, $last_sent_at, $last_id, $limit);
} else {
    // Initial load: get latest messages
    $query = "SELECT m.*, u.profile_picture as sender_profile_pic, u.username as sender_username, u.public_key as sender_public_key, u.subscription_tier as sender_subscription_tier
              FROM messages m
              JOIN users u ON m.sender_id = u.id
              WHERE m.conversation_id = $1
              ORDER BY m.sent_at DESC, m.id DESC
              LIMIT $2";
    $params = array($conv_id, $limit);
}

$result = pg_query_params($db, $query, $params);

$messages = [];
if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $messages[] = $row;
    }
}

// If not polling, reverse the messages so they are in chronological order for the client
if (!$since_id) {
    $messages = array_reverse($messages);
}

pg_close($db);
send_response("success", "Messages fetched", ["messages" => $messages]);
?>
