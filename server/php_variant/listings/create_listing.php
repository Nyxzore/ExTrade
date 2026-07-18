<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$species_lsid = $_POST['species_lsid'] ?? null;
$price = $_POST['price'] ?? null;
$description = $_POST['description'] ?? '';
$sex = $_POST['sex'] ?? 'Unsexed';
$image_base64 = $_POST['image_data'] ?? null;

if (!$species_lsid || !$price) {
    send_error("Required fields missing");
}

if (!is_numeric($price)) {
    send_error("Price must be numeric");
}

$image_url = save_base64_image($image_base64, 'listings');

$query = "INSERT INTO listings (seller_id, species_lsid, price, description, sex, image_url)
          VALUES ($1, $2, $3, $4, $5, $6) RETURNING id";

$result = pg_query_params($db, $query, [$user_id, $species_lsid, $price, $description, $sex, $image_url]);

if ($result) {
    $listing_id = pg_fetch_assoc($result)['id'];
    send_response("success", "Listing created successfully", ["listing_id" => $listing_id]);
} else {
    send_error("Failed to create listing");
}

pg_close($db);
?>
