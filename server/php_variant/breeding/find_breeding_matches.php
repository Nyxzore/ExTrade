<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$authenticated_user_id = verify_auth($db);

$listing_id = $_POST['listing_id'] ?? null;
if (!$listing_id) send_error("Listing ID missing");

// Verify ownership and get species/sex
$check = pg_query_params($db, "SELECT species_lsid, sex FROM breeding_listings WHERE id = $1 AND seller_id = $2", [$listing_id, $authenticated_user_id]);
if (!$check || pg_num_rows($check) === 0) {
    send_error("Unauthorized or listing not found");
}
$base = pg_fetch_assoc($check);
$species_lsid = $base['species_lsid'];
$opposite_sex = ($base['sex'] === 'Male' ? 'Female' : 'Male');

$query = "SELECT bl.*, t.genus, t.species, t.common_name, u.username as seller_name, u.subscription_tier,
                 u.whatsapp, u.facebook, u.instagram
          FROM breeding_listings bl
          JOIN taxa t ON bl.species_lsid = t.species_lsid
          JOIN users u ON bl.seller_id = u.id
          WHERE bl.status = 'active'
            AND bl.species_lsid = $1
            AND bl.sex = $2
            AND bl.seller_id != $3
          ORDER BY bl.listed_time DESC";

$result = pg_query_params($db, $query, [$species_lsid, $opposite_sex, $authenticated_user_id]);
$matches = [];

if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $scientific_name = trim($row['genus'] . ' ' . $row['species']);
        $common_name = !empty($row['common_name']) ? $row['common_name'] : $scientific_name;

        $price_display = "Seeking Partner";
        if ($row['breeding_type'] === 'loan') {
            $price_display = $row['loan_fee'] ? "Stud Fee: R " . number_format($row['loan_fee'], 2) : "Stud Fee: Contact";
        }

        $matches[] = [
            "id" => $row['id'],
            "seller_id" => $row['seller_id'],
            "seller_name" => $row['seller_name'],
            "scientific_name" => $scientific_name,
            "common_name" => $common_name,
            "price" => $price_display,
            "description" => $row['description'],
            "image_url" => $row['image_url'],
            "sex" => $row['sex'],
            "breeding_type" => $row['breeding_type'],
            "listed_time" => $row['listed_time'],
            "subscription_tier" => (int)($row['subscription_tier'] ?? 0),
            "whatsapp" => $row['whatsapp'],
            "facebook" => $row['facebook'],
            "instagram" => $row['instagram']
        ];
    }
    send_response("success", "Matches found", ["listings" => $matches]);
} else {
    send_error("Failed to search matches");
}

pg_close($db);
?>
