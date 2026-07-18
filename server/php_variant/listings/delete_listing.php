<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$listing_id = $_POST['listing_id'] ?? null;

if (!$listing_id) {
    send_error("Listing ID required");
}

// Check ownership
$res = pg_query_params($db, "SELECT seller_id FROM listings WHERE id = $1", [$listing_id]);
if (!$res || pg_num_rows($res) === 0) {
    send_error("Listing not found");
}
if (pg_fetch_assoc($res)['seller_id'] !== $user_id) {
    send_error("Unauthorized to delete this listing");
}

$query = "DELETE FROM listings WHERE id = $1";
$result = pg_query_params($db, $query, [$listing_id]);

if ($result) {
    send_response("success", "Listing deleted successfully");
} else {
    send_error("Failed to delete listing");
}

pg_close($db);
?>
