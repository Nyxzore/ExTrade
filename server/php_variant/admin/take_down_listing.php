<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$admin_id = verify_admin($db);

$listing_id = $_POST['listing_id'] ?? null;
$reason = $_POST['reason'] ?? 'Violation of community guidelines';
$kind = $_POST['kind'] ?? 'sale'; // 'sale' or 'breeding'

if (!$listing_id) {
    send_error("Listing ID required");
}

$table = ($kind === 'breeding') ? 'breeding_listings' : 'listings';

// Find seller_id
$res = pg_query_params($db, "SELECT seller_id FROM $table WHERE id = $1", [$listing_id]);
if (!$res || pg_num_rows($res) === 0) {
    send_error("Listing not found in $table");
}

$row = pg_fetch_assoc($res);
$seller_id = $row['seller_id'];

// Start transaction
pg_query($db, "BEGIN");

// Update listing status
$query = "UPDATE $table SET status = 'taken_down' WHERE id = $1";
$res_update = pg_query_params($db, $query, [$listing_id]);

// Add notification for the user
$msg = "Your listing was taken down because: " . $reason;
$query_notify = "INSERT INTO admin_notifications (user_id, message) VALUES ($1, $2)";
$res_notify = pg_query_params($db, $query_notify, [$seller_id, $msg]);

if ($res_update && $res_notify) {
    pg_query($db, "COMMIT");
    send_response("success", "Listing taken down and user notified");
} else {
    pg_query($db, "ROLLBACK");
    send_error("Failed to take down listing");
}

pg_close($db);
?>
