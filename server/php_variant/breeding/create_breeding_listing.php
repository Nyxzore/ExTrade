<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$species_lsid = $_POST['species_lsid'] ?? null;
$sex = $_POST['sex'] ?? null;
$breeding_type = $_POST['breeding_type'] ?? null;
$loan_fee_input = $_POST['loan_fee'] ?? null;
$description = $_POST['description'] ?? '';
$size_in_cm = !empty($_POST['size_in_cm']) ? $_POST['size_in_cm'] : null;
$age = !empty($_POST['age_in_days']) ? (int)$_POST['age_in_days'] : null;
$image_url = save_base64_image($_POST['image_data'] ?? null, 'breeding');

if (!$species_lsid || !$sex || !$breeding_type) {
    send_error("Required fields missing");
}

if (!in_array($sex, ['Male', 'Female'])) {
    send_error("Invalid sex. Breeding listings require 'Male' or 'Female'");
}

if (!in_array($breeding_type, ['loan', 'seeking'])) {
    send_error("Invalid breeding type");
}

$loan_fee = null;
if ($breeding_type === 'loan' && $loan_fee_input !== null && $loan_fee_input !== '') {
    $loan_fee = str_replace(',', '.', $loan_fee_input);
    if (!is_numeric($loan_fee)) {
        send_error("Loan fee must be numeric");
    }
}

$query = "INSERT INTO breeding_listings (seller_id, species_lsid, sex, size_in_cm, age, description, image_url, breeding_type, loan_fee)
          VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING id";

$result = pg_query_params($db, $query, [
    $user_id,
    $species_lsid,
    $sex,
    $size_in_cm,
    $age,
    $description,
    $image_url,
    $breeding_type,
    $loan_fee
]);

if ($result) {
    $row = pg_fetch_assoc($result);
    send_response("success", "Breeding listing created successfully", ["id" => $row['id']]);
} else {
    send_error("Failed to create breeding listing: " . pg_last_error($db));
}

pg_close($db);
?>
