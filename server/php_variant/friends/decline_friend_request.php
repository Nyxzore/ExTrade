<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);
$requester_id = $_POST['requester_id'] ?? null;

if (!$requester_id) {
    send_error("Requester ID required");
}

$u1 = ($user_id < $requester_id) ? $user_id : $requester_id;
$u2 = ($user_id < $requester_id) ? $requester_id : $user_id;

$query = "DELETE FROM friendships
          WHERE user_id1 = $1 AND user_id2 = $2 AND status = 'pending' AND action_user_id = $3";
$result = pg_query_params($db, $query, array($u1, $u2, $requester_id));

if ($result && pg_affected_rows($result) > 0) {
    pg_close($db);
    send_response("success", "Friend request declined");
} else {
    pg_close($db);
    send_error("Request not found or already handled");
}
?>
