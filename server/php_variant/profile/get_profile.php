<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

// Securely verify authentication
$authenticated_user_id = verify_auth($db);

$target_user_id = $_POST['target_user_id'] ?? $authenticated_user_id;

// Fetch user info
$user_result = pg_query_params($db, "SELECT username, email, profile_picture, public_key, subscription_tier, is_admin, whatsapp, facebook, instagram FROM users WHERE id = $1", array($target_user_id));
if (!$user_result || pg_num_rows($user_result) == 0) {
    pg_close($db);
    send_error("User not found");
}
$user_row = pg_fetch_assoc($user_result);
$username = $user_row['username'];
$email = ($target_user_id === $authenticated_user_id) ? $user_row['email'] : null;
$profile_picture = $user_row['profile_picture'];
$public_key = $user_row['public_key'];
$subscription_tier = (int)($user_row['subscription_tier'] ?? 0);
$is_admin = $user_row['is_admin'] === 't';
$whatsapp = $user_row['whatsapp'];
$facebook = $user_row['facebook'];
$instagram = $user_row['instagram'];

// Fetch both sale and breeding listings
$query = "
    (SELECT l.id, l.seller_id, t.species_lsid,
            TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
            t.common_name, l.price as raw_price, l.description, l.image_url,
            'sale' as kind, NULL as breeding_type, l.listed_time, l.sex, l.status,
            u.subscription_tier
     FROM listings l
     JOIN taxa t ON l.species_lsid = t.species_lsid
     JOIN users u ON l.seller_id = u.id
     WHERE l.seller_id = $1)
    UNION ALL
    (SELECT bl.id, bl.seller_id, t.species_lsid,
            TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
            t.common_name, bl.loan_fee as raw_price, bl.description, bl.image_url,
            'breeding' as kind, bl.breeding_type, bl.listed_time, bl.sex, bl.status,
            u.subscription_tier
     FROM breeding_listings bl
     JOIN taxa t ON bl.species_lsid = t.species_lsid
     JOIN users u ON bl.seller_id = u.id
     WHERE bl.seller_id = $1)
    ORDER BY listed_time DESC";

$listings_result = pg_query_params($db, $query, array($target_user_id));
$listings = array();

if ($listings_result) {
    while ($row = pg_fetch_assoc($listings_result)) {
        $scientific_name = $row['scientific_name'];
        $common_name = !empty($row['common_name']) ? $row['common_name'] : $scientific_name;

        $price_display = "";
        if ($row['kind'] === 'sale') {
            $price_display = "R " . number_format($row['raw_price'], 2);
        } else {
            // Breeding listing logic
            if ($row['breeding_type'] === 'loan') {
                $price_display = $row['raw_price'] ? "Stud Fee: R " . number_format($row['raw_price'], 2) : "Stud Fee: Contact";
            } else {
                $price_display = "Seeking Partner";
            }
        }

        $listings[] = array(
            "id" => $row['id'],
            "seller_id" => $row['seller_id'],
            "species_lsid" => $row['species_lsid'],
            "scientific_name" => $scientific_name,
            "common_name" => $common_name,
            "price" => $price_display,
            "description" => $row['description'],
            "image_url" => $row['image_url'],
            "kind" => $row['kind'],
            "sex" => $row['sex'],
            "status" => $row['status'],
            "subscription_tier" => (int)$row['subscription_tier']
        );
    }
}

pg_close($db);
send_response("success", "Profile fetched", [
    "username" => $username,
    "email" => $email,
    "profile_picture" => $profile_picture,
    "public_key" => $public_key,
    "subscription_tier" => $subscription_tier,
    "is_admin" => $is_admin,
    "whatsapp" => $whatsapp,
    "facebook" => $facebook,
    "instagram" => $instagram,
    "listings" => $listings
]);
?>
