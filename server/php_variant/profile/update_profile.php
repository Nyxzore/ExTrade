<?php
require_once '../core/db_connect.php';
require_once '../core/social_helpers.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$username = $_POST['username'] ?? null;
$email = $_POST['email'] ?? null;
$profile_picture_data = $_POST['profile_picture_data'] ?? null;
$new_password = $_POST['new_password'] ?? null;
$whatsapp = $_POST['whatsapp'] ?? null;
$facebook = $_POST['facebook'] ?? null;
$instagram = $_POST['instagram'] ?? null;

if (!$username || !$email) {
    send_error("Username and email are required");
}

if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    send_error("Invalid email format");
}

// Check if username is taken by someone else
$res = pg_query_params($db, "SELECT id FROM users WHERE username = $1 AND id != $2", [$username, $user_id]);
if ($res && pg_num_rows($res) > 0) {
    send_error("Username already taken");
}

// Check if email is taken by someone else
$res = pg_query_params($db, "SELECT id FROM users WHERE email = $1 AND id != $2", [$email, $user_id]);
if ($res && pg_num_rows($res) > 0) {
    send_error("Email already registered");
}

$social = sanitize_social_fields($whatsapp, $facebook, $instagram);
$whatsapp = $social['whatsapp'];
$facebook = $social['facebook'];
$instagram = $social['instagram'];

$update_fields = [
    "username = $1",
    "email = $2",
    "whatsapp = $3",
    "facebook = $4",
    "instagram = $5"
];
$params = [$username, $email, $whatsapp, $facebook, $instagram];

if ($profile_picture_data) {
    $path = save_base64_image($profile_picture_data, 'profile_pics');
    if ($path) {
        $update_fields[] = "profile_picture = $" . (count($params) + 1);
        $params[] = $path;
    }
}

if ($new_password) {
    $update_fields[] = "password_hash = $" . (count($params) + 1);
    $params[] = password_hash($new_password . AUTH_PEPPER, PASSWORD_BCRYPT);

    // Backup rotation: client MUST send new encrypted blobs when changing password
    $enc_key = $_POST['encrypted_private_key'] ?? null;
    $nonce = $_POST['private_key_nonce'] ?? null;
    $salt = $_POST['kdf_salt'] ?? null;

    if (!$enc_key) {
        send_error("Security backup rotation material missing");
    }

    $update_fields[] = "encrypted_private_key = $" . (count($params) + 1);
    $params[] = $enc_key;
    $update_fields[] = "private_key_nonce = $" . (count($params) + 1);
    $params[] = $nonce;
    $update_fields[] = "kdf_salt = $" . (count($params) + 1);
    $params[] = $salt;
}

$params[] = $user_id;
$query = "UPDATE users SET " . implode(", ", $update_fields) . " WHERE id = $" . count($params);

$result = pg_query_params($db, $query, $params);

if ($result) {
    send_response("success", "Profile updated successfully");
} else {
    error_log("Profile update failed: " . pg_last_error($db));
    send_error("Profile update failed");
}

pg_close($db);
?>
