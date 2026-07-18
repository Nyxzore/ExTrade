<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);
$friend_id = $_POST['friend_id'] ?? null;

if (!$friend_id) {
    send_error("Friend ID required");
}

$u1 = ($user_id < $friend_id) ? $user_id : $friend_id;
$u2 = ($user_id < $friend_id) ? $friend_id : $user_id;

$query = "DELETE FROM friendships WHERE user_id1 = $1 AND user_id2 = $2 AND status = 'accepted'";
$result = pg_query_params($db, $query, array($u1, $u2));

if ($result && pg_affected_rows($result) > 0) {
    pg_close($db);
    send_response("success", "Friend removed");
} else {
    pg_close($db);
    send_error("Friendship not found");
}
?>
