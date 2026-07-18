<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$listing_id = $_POST['listing_id'] ?? null;
$seller_id = $_POST['seller_id'] ?? null;
$listing_kind = $_POST['listing_kind'] ?? 'sale';

if (!$listing_id || !$seller_id) {
    send_error("Listing ID and Seller ID are required");
}

if (!in_array($listing_kind, ['sale', 'breeding'])) {
    send_error("Invalid listing kind");
}

if ($user_id == $seller_id) {
    send_error("You cannot start a conversation with yourself");
}

// Fetch other user's basic info for the Chat header
$u_res = pg_query_params($db, "SELECT username, profile_picture, public_key FROM users WHERE id = $1", [$seller_id]);
if (!$u_res || pg_num_rows($u_res) === 0) {
    send_error("Seller not found");
}
$u_row = pg_fetch_assoc($u_res);
$other_user_info = [
    "username" => $u_row['username'],
    "profile_pic" => $u_row['profile_picture'],
    "public_key" => $u_row['public_key']
];

// Validate listing existence and ownership, and fetch details for auto-message
if ($listing_kind === 'sale') {
    $val_query = "SELECT l.id, t.common_name,
                         TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
                         l.price, l.image_url
                  FROM listings l
                  JOIN taxa t ON l.species_lsid = t.species_lsid
                  WHERE l.id = $1 AND l.seller_id = $2";
} else {
    $val_query = "SELECT l.id, t.common_name,
                         TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
                         l.loan_fee as price, l.image_url, l.breeding_type
                  FROM breeding_listings l
                  JOIN taxa t ON l.species_lsid = t.species_lsid
                  WHERE l.id = $1 AND l.seller_id = $2";
}

$val_result = pg_query_params($db, $val_query, [$listing_id, $seller_id]);

if (!$val_result || pg_num_rows($val_result) === 0) {
    send_error("Listing not found or owner mismatch");
}

$listing_row = pg_fetch_assoc($val_result);
$listing_details = [
    "id" => $listing_row['id'],
    "common_name" => $listing_row['common_name'],
    "scientific_name" => $listing_row['scientific_name'],
    "image_url" => $listing_row['image_url'],
    "kind" => $listing_kind
];

if ($listing_kind === 'sale') {
    $listing_details["price"] = "R " . number_format($listing_row['price'], 2);
} else {
    if ($listing_row['breeding_type'] === 'loan') {
        $listing_details["price"] = $listing_row['price'] ? "Stud Fee: R " . number_format($listing_row['price'], 2) : "Stud Fee: Contact";
    } else {
        $listing_details["price"] = "Seeking Partner";
    }
}

// Order user IDs to ensure a single thread per pair
$user_a = ($user_id < $seller_id) ? $user_id : $seller_id;
$user_b = ($user_id < $seller_id) ? $seller_id : $user_id;

// Check if conversation already exists
$query = "SELECT id FROM conversations WHERE user_a_id = $1 AND user_b_id = $2";
$result = pg_query_params($db, $query, array($user_a, $user_b));

if ($result && pg_num_rows($result) > 0) {
    $row = pg_fetch_assoc($result);
    $conv_id = $row['id'];
} else {
    // Create new conversation
    $query = "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2) RETURNING id";
    $result = pg_query_params($db, $query, array($user_a, $user_b));
    if ($result) {
        $row = pg_fetch_assoc($result);
        $conv_id = $row['id'];
    } else {
        send_error("Failed to create conversation: " . pg_last_error($db));
    }
}

pg_close($db);
send_response("success", "Conversation retrieved", [
    "conversation_id" => $conv_id,
    "listing_details" => $listing_details,
    "other_user" => $other_user_info
]);
?>
