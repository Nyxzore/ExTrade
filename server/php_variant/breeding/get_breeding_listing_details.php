<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

verify_auth($db);

$id = $_POST['id'] ?? $_GET['id'] ?? null;
if (!$id) send_error("ID missing");

$query = "SELECT bl.*, t.genus, t.species, t.subspecies, t.common_name, t.distribution,
                 u.username as seller_name, u.public_key as seller_public_key,
                 u.whatsapp, u.facebook, u.instagram
          FROM breeding_listings bl
          JOIN taxa t ON bl.species_lsid = t.species_lsid
          JOIN users u ON bl.seller_id = u.id
          WHERE bl.id = $1";

$result = pg_query_params($db, $query, [$id]);

if ($result && pg_num_rows($result) > 0) {
    $row = pg_fetch_assoc($result);

    $scientific_name = trim($row['genus'] . ' ' . $row['species'] . ' ' . $row['subspecies']);
    $common_name = !empty($row['common_name']) ? $row['common_name'] : $scientific_name;

    $price_display = "Seeking Partner";
    if ($row['breeding_type'] === 'loan') {
        $price_display = $row['loan_fee'] ? "Stud Fee: R " . number_format($row['loan_fee'], 2) : "Stud Fee: Contact";
    }

    $response = [
        "id" => $row['id'],
        "scientific_name" => $scientific_name,
        "common_name" => $common_name,
        "price" => $price_display,
        "description" => $row['description'] ?? '',
        "sex" => $row['sex'],
        "size_in_cm" => $row['size_in_cm'] ?? '',
        "age" => format_age($row['age']),
        "age_raw" => $row['age'],
        "image_url" => $row['image_url'] ?? '',
        "breeding_type" => $row['breeding_type'],
        "loan_fee" => $row['loan_fee'],
        "listing_status" => $row['status'],
        "listed_time" => $row['listed_time'],
        "distribution" => $row['distribution'],
        "seller_name" => $row['seller_name'],
        "seller_id" => $row['seller_id'],
        "seller_public_key" => $row['seller_public_key'],
        "whatsapp" => $row['whatsapp'],
        "facebook" => $row['facebook'],
        "instagram" => $row['instagram'],
        "species_lsid" => $row['species_lsid']
    ];

    send_response("success", "Breeding details fetched", $response);
} else {
    send_error("Breeding listing not found");
}

pg_close($db);
?>
