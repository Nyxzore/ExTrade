<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$listing_id = $_GET['id'] ?? $_POST['id'] ?? null;

if (!$listing_id) {
    send_error("Listing ID required");
}

$query = "
    SELECT l.*,
           u.username as seller_name,
           u.subscription_tier,
           u.whatsapp, u.facebook, u.instagram,
           t.genus, t.species, t.common_name
    FROM listings l
    JOIN users u ON l.seller_id = u.id
    JOIN taxa t ON l.species_lsid = t.species_lsid
    WHERE l.id = $1";

$result = pg_query_params($db, $query, [$listing_id]);

if ($result && pg_num_rows($result) > 0) {
    $row = pg_fetch_assoc($result);

    // Log impression for single view
    log_impressions($db, $user_id, [$listing_id], 'listing_impressions');

    $row['subscription_tier'] = (int)$row['subscription_tier'];
    $row['price_formatted'] = $row['price'] ? "R " . number_format($row['price'], 2) : "Contact";

    send_response("success", "Listing details fetched", $row);
} else {
    send_error("Listing not found");
}

pg_close($db);
?>
