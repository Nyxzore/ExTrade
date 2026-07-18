<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$listing_id = $_POST['listing_id'] ?? null;
$price = $_POST['price'] ?? null;
$description = $_POST['description'] ?? null;
$status = $_POST['status'] ?? null;
$sex = $_POST['sex'] ?? null;

if (!$listing_id) {
    send_error("Listing ID required");
}

// Check ownership
$res = pg_query_params($db, "SELECT seller_id FROM listings WHERE id = $1", [$listing_id]);
if (!$res || pg_num_rows($res) === 0) {
    send_error("Listing not found");
}
if (pg_fetch_assoc($res)['seller_id'] !== $user_id) {
    send_error("Unauthorized to update this listing");
}

if ($price !== null && !is_numeric($price)) {
    send_error("Price must be numeric");
}

$updates = [];
$params = [$listing_id];
$i = 2;

if ($price !== null) { $updates[] = "price = $" . $i++; $params[] = $price; }
if ($description !== null) { $updates[] = "description = $" . $i++; $params[] = $description; }
if ($status !== null) { $updates[] = "status = $" . $i++; $params[] = $status; }
if ($sex !== null) { $updates[] = "sex = $" . $i++; $params[] = $sex; }

if (empty($updates)) {
    send_error("No fields to update");
}

$query = "UPDATE listings SET " . implode(", ", $updates) . " WHERE id = $1";
$result = pg_query_params($db, $query, $params);

if ($result) {
    send_response("success", "Listing updated successfully");
} else {
    send_error("Failed to update listing");
}

pg_close($db);
?>
