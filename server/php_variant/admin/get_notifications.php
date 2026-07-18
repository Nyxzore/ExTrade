<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$query = "SELECT id, message, created_at FROM admin_notifications WHERE user_id = $1 AND is_read = FALSE ORDER BY created_at DESC";
$result = pg_query_params($db, $query, [$user_id]);

$notifications = [];
if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $notifications[] = $row;
    }
}

// Mark as read after fetching? Or let client handle it?
// Usually, for "online" alerts, we mark them read immediately so they don't pop up again.
if (!empty($notifications)) {
    pg_query_params($db, "UPDATE admin_notifications SET is_read = TRUE WHERE user_id = $1", [$user_id]);
}

pg_close($db);
send_response("success", "Notifications fetched", ["notifications" => $notifications]);
?>
