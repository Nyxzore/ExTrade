<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$id = $_POST['id'] ?? null;
if (!$id) send_error("ID missing");

$sex = $_POST['sex'] ?? null;
$breeding_type = $_POST['breeding_type'] ?? null;
$loan_fee_input = $_POST['loan_fee'] ?? null;
$description = $_POST['description'] ?? null;
$size_in_cm = $_POST['size_in_cm'] ?? null;
$age = $_POST['age'] ?? null;
$species_lsid = $_POST['species_lsid'] ?? null;

// Verify ownership
$check = pg_query_params($db, "SELECT breeding_type FROM breeding_listings WHERE id = $1 AND seller_id = $2", [$id, $user_id]);
if (!$check || pg_num_rows($check) === 0) {
    send_error("Unauthorized or listing not found");
}
$current_breeding_type = pg_fetch_result($check, 0, 0);

$update_fields = [];
$params = [];
$i = 1;

if ($sex) {
    if (!in_array($sex, ['Male', 'Female'])) send_error("Invalid sex");
    $update_fields[] = "sex = $" . $i++;
    $params[] = $sex;
}

if ($breeding_type) {
    if (!in_array($breeding_type, ['loan', 'seeking'])) send_error("Invalid breeding type");
    $update_fields[] = "breeding_type = $" . $i++;
    $params[] = $breeding_type;
    $current_breeding_type = $breeding_type;
}

if (isset($_POST['loan_fee'])) {
    if ($current_breeding_type === 'loan') {
        $loan_fee = ($loan_fee_input === '' || $loan_fee_input === null) ? null : str_replace(',', '.', $loan_fee_input);
        if ($loan_fee !== null && !is_numeric($loan_fee)) send_error("Loan fee must be numeric");
        $update_fields[] = "loan_fee = $" . $i++;
        $params[] = $loan_fee;
    } else {
        $update_fields[] = "loan_fee = NULL";
    }
}

if ($description !== null) { $update_fields[] = "description = $" . $i++; $params[] = $description; }
if ($size_in_cm !== null) { $update_fields[] = "size_in_cm = $" . $i++; $params[] = ($size_in_cm === '' ? null : $size_in_cm); }

if (isset($_POST['age_in_days'])) {
    $update_fields[] = "age = $" . $i++;
    $params[] = (int)$_POST['age_in_days'];
}

if ($species_lsid) { $update_fields[] = "species_lsid = $" . $i++; $params[] = $species_lsid; }

$image_data = $_POST['image_data'] ?? null;
if ($image_data) {
    $path = save_base64_image($image_data, 'breeding');
    if ($path) {
        $update_fields[] = "image_url = $" . $i++;
        $params[] = $path;
    }
}

if (empty($update_fields)) send_error("No fields to update");

$params[] = $id;
$params[] = $user_id;
$query = "UPDATE breeding_listings SET " . implode(", ", $update_fields) . " WHERE id = $" . $i++ . " AND seller_id = $" . $i;

$result = pg_query_params($db, $query, $params);
if ($result) send_response("success", "Breeding listing updated");
else send_error("Failed to update: " . pg_last_error($db));

pg_close($db);
?>
