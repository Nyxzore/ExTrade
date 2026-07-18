<?php
require_once __DIR__ . '/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$target_type = $_POST['target_type'] ?? null;
$target_id = $_POST['target_id'] ?? null;
$reason = $_POST['reason'] ?? null;
$details = $_POST['details'] ?? '';

if (!$target_type || !$target_id || !$reason) {
    send_error("Target type, ID, and reason are required");
}

$allowed_types = ['listing', 'breeding', 'user'];
if (!in_array($target_type, $allowed_types)) {
    send_error("Invalid target type");
}

$query = "INSERT INTO reports (reporter_id, target_type, target_id, reason, details) VALUES ($1, $2, $3, $4, $5)";
$result = pg_query_params($db, $query, array($user_id, $target_type, $target_id, $reason, $details));

if ($result) {
    pg_close($db);
    send_response("success", "Thank you for your report. We will review it shortly.");
} else {
    pg_close($db);
    send_error("Failed to submit report");
}
?>
