<?php
require_once '../core/db_connect.php';
$db = get_db_connection();
check_app_version($db);

$username = $_POST['username'] ?? null;
$email = $_POST['email'] ?? null;
$password = $_POST['password'] ?? null;
$mode = $_POST['mode'] ?? 'login';

if (!$username || !$password || ($mode === 'register' && !$email)) {
    send_error("Required fields missing");
}

if ($mode === 'register' && !filter_var($email, FILTER_VALIDATE_EMAIL)) {
    send_error("Invalid email format");
}

if (!function_exists('generate_session')) {
    function generate_session($db, $user_id) {
        $token = bin2hex(random_bytes(32));
        $query = "INSERT INTO user_sessions (token, user_id) VALUES ($1, $2)";
        $result = pg_query_params($db, $query, array($token, $user_id));
        if ($result) {
            return $token;
        }
        return null;
    }
}

if ($mode === 'login') {
    $result = pg_select($db, 'users', array('username' => $username));
    $row = $result ? $result[0] : null ;

    if ($row && password_verify($password . AUTH_PEPPER, $row['password_hash'])) {
        if (isset($row['is_banned']) && $row['is_banned'] === 't') {
            pg_close($db);
            send_error("Your account has been banned for violating community guidelines.");
        }

        $token = generate_session($db, $row['id']);
        if ($token) {
            pg_close($db);
            send_response("success", "Login successful", [
                "uuid" => $row['id'],
                "auth_token" => $token,
                "is_admin" => ($row['is_admin'] ?? 'f') === 't'
            ]);
        } else {
            send_error("Failed to create session");
        }
    } else {
        pg_close($db);
        send_error("Invalid username or password");
    }
} else {
    // Check for existing username
    $result = pg_select($db, 'users', array('username' => $username));
    if ($result) {
        pg_close($db);
        send_error("Username taken");
    }

    // Check for existing email
    $result = pg_select($db, 'users', array('email' => $email));
    if ($result) {
        pg_close($db);
        send_error("Email already registered");
    }

    $hashed_password = password_hash($password . AUTH_PEPPER, PASSWORD_BCRYPT);
    $profile_picture = save_base64_image($_POST['profile_picture_data'] ?? null, 'profile_pics');

    $data = array(
        'username' => $username,
        'email' => $email,
        'password_hash' => $hashed_password,
        'profile_picture' => $profile_picture,
        'public_key' => $_POST['public_key'] ?? null,
        'encrypted_private_key' => $_POST['encrypted_private_key'] ?? null,
        'private_key_nonce' => $_POST['private_key_nonce'] ?? null,
        'kdf_salt' => $_POST['kdf_salt'] ?? null
    );

    // Verify E2EE material is present
    if (!$data['public_key'] || !$data['encrypted_private_key']) {
        send_error("E2EE key material missing");
    }

    $result = pg_insert($db, 'users', $data);

    if ($result) {
        $new_user = pg_select($db, 'users', array('username' => $username));
        $user_id = $new_user[0]['id'];
        $token = generate_session($db, $user_id);
        if ($token) {
            pg_close($db);
            send_response("success", "Registration successful", ["uuid" => $user_id, "auth_token" => $token]);
        } else {
            send_error("Failed to create session after registration");
        }
    } else {
        error_log("Registration failed: " . pg_last_error($db));
        pg_close($db);
        send_error("Registration failed");
    }
}
?>
