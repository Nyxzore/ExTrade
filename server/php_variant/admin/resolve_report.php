<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

verify_admin($db);

$report_id = $_POST['report_id'] ?? null;
$action = $_POST['action'] ?? null; // 'dismiss', 'delete', 'ban'

if (!$report_id || !$action) {
    send_error("Report ID and action are required");
}

// Fetch report details
$res = pg_query_params($db, "SELECT * FROM reports WHERE id = $1", [$report_id]);
if (!$res || pg_num_rows($res) === 0) {
    send_error("Report not found");
}
$report = pg_fetch_assoc($res);

if ($action === 'delete') {
    if ($report['target_type'] === 'listing') {
        pg_query_params($db, "DELETE FROM listings WHERE id = $1", [$report['target_id']]);
    } else if ($report['target_type'] === 'breeding') {
        pg_query_params($db, "DELETE FROM breeding_listings WHERE id = $1", [$report['target_id']]);
    }
    pg_query_params($db, "UPDATE reports SET status = 'resolved' WHERE id = $1", [$report_id]);
    send_response("success", "Item deleted and report resolved");

} else if ($action === 'ban') {
    $target_user_id = $report['target_id'];
    if ($report['target_type'] === 'user') {
        pg_query_params($db, "UPDATE users SET is_banned = TRUE WHERE id = $1", [$target_user_id]);
    } else {
        // If reporting a listing, we might want to ban the seller
        $table = ($report['target_type'] === 'listing') ? 'listings' : 'breeding_listings';
        $res = pg_query_params($db, "SELECT seller_id FROM $table WHERE id = $1", [$report['target_id']]);
        if ($res && $row = pg_fetch_assoc($res)) {
            pg_query_params($db, "UPDATE users SET is_banned = TRUE WHERE id = $1", [$row['seller_id']]);
        }
    }
    pg_query_params($db, "UPDATE reports SET status = 'resolved' WHERE id = $1", [$report_id]);
    send_response("success", "User banned and report resolved");

} else if ($action === 'dismiss') {
    pg_query_params($db, "UPDATE reports SET status = 'dismissed' WHERE id = $1", [$report_id]);
    send_response("success", "Report dismissed");
} else {
    send_error("Invalid action");
}

pg_close($db);
?>
