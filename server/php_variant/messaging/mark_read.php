<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$conv_id = $_POST['conversation_id'] ?? null;

if (!$conv_id) {
    send_error("Conversation ID is required");
}

$query = "UPDATE messages SET read_at = NOW()
          WHERE conversation_id = $1 AND sender_id != $2 AND read_at IS NULL";
$result = pg_query_params($db, $query, array($conv_id, $user_id));

if ($result) {
    pg_close($db);
    send_response("success", "Messages marked as read");
} else {
    pg_close($db);
    send_error("Failed to mark messages as read");
}
?>
