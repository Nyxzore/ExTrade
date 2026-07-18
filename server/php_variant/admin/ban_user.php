<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$admin_id = verify_admin($db);

$target_user_id = $_POST['target_user_id'] ?? null;
$reason = $_POST['reason'] ?? 'Violation of community guidelines';

if (!$target_user_id) {
    send_error("Target user ID required");
}

// Don't let admins ban themselves
if ($target_user_id === $admin_id) {
    send_error("You cannot ban yourself");
}

$result = pg_query_params($db, "UPDATE users SET is_banned = TRUE WHERE id = $1", [$target_user_id]);

if ($result) {
    // Optionally log this action
    send_response("success", "User has been banned");
} else {
    send_error("Failed to ban user: " . pg_last_error($db));
}

pg_close($db);
?>
