<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

verify_admin($db);

$query = "SELECT r.*, u.username as reporter_name
          FROM reports r
          JOIN users u ON r.reporter_id = u.id
          WHERE r.status = 'pending'
          ORDER BY r.created_at DESC";

$result = pg_query($db, $query);
$reports = [];

if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $reports[] = $row;
    }
    pg_close($db);
    send_response("success", "Reports fetched", ["reports" => $reports]);
} else {
    pg_close($db);
    send_error("Failed to fetch reports");
}
?>
