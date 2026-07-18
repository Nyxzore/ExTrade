<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

// Securely verify authentication
$user_id = verify_auth($db);

// Fetch the user's own key material
$query = "SELECT encrypted_private_key, private_key_nonce, kdf_salt FROM users WHERE id = $1";
$result = pg_query_params($db, $query, array($user_id));

if ($result && pg_num_rows($result) > 0) {
    $row = pg_fetch_assoc($result);
    pg_close($db);
    send_response("success", "Backup retrieved", [
        "encrypted_private_key" => $row['encrypted_private_key'],
        "private_key_nonce" => $row['private_key_nonce'],
        "kdf_salt" => $row['kdf_salt']
    ]);
} else {
    pg_close($db);
    send_error("User material not found");
}
?>
