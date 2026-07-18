<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);
$id = $_POST['id'] ?? null;

if (!$id) send_error("ID missing");

// Check ownership
$check = pg_query_params($db, "SELECT 1 FROM breeding_listings WHERE id = $1 AND seller_id = $2", [$id, $user_id]);
if (!$check || pg_num_rows($check) === 0) {
    send_error("Failed to delete or unauthorized");
}

// Conversation model changed: listing_id removed from conversations.

$query = "DELETE FROM breeding_listings WHERE id = $1 AND seller_id = $2";
$result = pg_query_params($db, $query, [$id, $user_id]);

if ($result && pg_affected_rows($result) > 0) {
    send_response("success", "Breeding listing deleted");
} else {
    send_error("Failed to delete");
}

pg_close($db);
?>
