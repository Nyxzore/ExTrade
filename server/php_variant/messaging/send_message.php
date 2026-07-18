<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$conv_id = $_POST['conversation_id'] ?? null;
$body = $_POST['body'] ?? null;
$nonce = $_POST['nonce'] ?? null;

if (!$conv_id || !$body || !$nonce) {
    send_error("Conversation ID, message body, and nonce are required");
}


// Verify user is part of the conversation
verify_conversation_membership($db, $conv_id, $user_id);

$query = "INSERT INTO messages (conversation_id, sender_id, body, nonce) VALUES ($1, $2, $3, $4) RETURNING id, sent_at";
$result = pg_query_params($db, $query, array($conv_id, $user_id, $body, $nonce));

if ($result) {
    $row = pg_fetch_assoc($result);
    pg_close($db);
    send_response("success", "Message sent", ["message_id" => $row['id'], "sent_at" => $row['sent_at']]);
} else {
    pg_close($db);
    send_error("Failed to send message");
}
?>
