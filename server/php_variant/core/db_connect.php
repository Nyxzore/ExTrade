<?php
require_once __DIR__ . '/secrets.php';

function get_db_connection() {
    $host = getenv('DB_HOST') ?: "localhost";
    $port = getenv('DB_PORT') ?: "5432";
    $dbname = getenv('DB_NAME') ?: "ExoTrade";
    $user = getenv('DB_USER') ?: "sgroup2689";

    $connection_string = "host=$host port=$port dbname=$dbname user=$user";

    if (defined('PHPUNIT_RUNNING')) {
        // Use persistent connection during tests so pg_close() calls in scripts don't actually close it
        $db = pg_pconnect($connection_string);
    } else {
        $db = pg_connect($connection_string);
    }

    if (!$db) {
        if (!defined('PHPUNIT_RUNNING')) {
            header('Content-Type: application/json');
            die(json_encode(array("status" => "error", "message" => "Database connection failed")));
        } else {
            throw new Exception("Database connection failed");
        }
    }
    return $db;
}

function send_response($status, $message, $extra = []) {
    $response = array("status" => $status, "message" => $message);
    $json = json_encode(array_merge($response, $extra));

    if (defined('PHPUNIT_RUNNING')) {
        echo $json;
        // Throwing an exception to stop script execution in tests
        throw new Exception("SEND_RESPONSE: " . $json);
    }

    header('Content-Type: application/json');
    echo $json;
    exit;
}

function send_error($message, $extra = []) {
    send_response("error", $message, $extra);
}

function check_app_version($db) {
    $client_version = $_POST['app_version'] ?? $_GET['app_version'] ?? null;

    $query = "SELECT version FROM versions WHERE object = 'app_min_version'";
    $result = pg_query($db, $query);

    if ($result && pg_num_rows($result) > 0) {
        $min_version = (int) pg_fetch_assoc($result)['version'];
        if ($client_version === null || (int) $client_version < $min_version) {
            send_error("Please update the app to continue.", ["update_required" => true]);
        }
    }
}

function verify_auth($db) {
    check_app_version($db);
    $user_id = $_POST['user_id'] ?? $_GET['user_id']  ?? null;
    $token = $_POST['auth_token'] ?? $_GET['auth_token'] ?? null;

    if ($user_id === null || $token === null || $user_id === '' || $token === '') {
        send_error("Authentication required", ["force_logout" => true]);
    }

    $query = "SELECT u.is_banned FROM user_sessions s
              JOIN users u ON s.user_id = u.id
              WHERE s.user_id = $1 AND s.token = $2 AND s.expires_at > NOW()";
    $result = pg_query_params($db, $query, array($user_id, $token));

    if (!$result || pg_num_rows($result) === 0) {
        send_error("Invalid or expired session", ["force_logout" => true]);
    }

    $row = pg_fetch_assoc($result);
    if ($row['is_banned'] === 't') {
        send_error("Your account has been banned for violating community guidelines.", ["force_logout" => true]);
    }

    return $user_id;
}

function verify_admin($db) {
    $user_id = verify_auth($db);
    $query = "SELECT is_admin FROM users WHERE id = $1";
    $res = pg_query_params($db, $query, [$user_id]);
    $row = pg_fetch_assoc($res);
    if ($row['is_admin'] !== 't') {
        send_error("Unauthorized: Admin access required");
    }
    return $user_id;
}

function verify_conversation_membership($db, $conv_id, $user_id) {
    $query = "SELECT 1 FROM conversations WHERE id = $1 AND (user_a_id = $2 OR user_b_id = $3)";
    $res = pg_query_params($db, $query, array($conv_id, $user_id, $user_id));
    if (!$res || pg_num_rows($res) === 0) {
        send_error("Access denied to this conversation");
    }
}

function save_base64_image($base64_string, $subfolder = 'uploads') {
    if (!$base64_string) return null;

    // Limit size (e.g., 5MB)
    if (strlen($base64_string) > 7000000) { // ~5MB after base64 overhead
        return null;
    }

    $data = base64_decode($base64_string);
    if (!$data) return null;

    // Basic content validation
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime_type = $finfo->buffer($data);
    $allowed_types = ['image/jpeg', 'image/png', 'image/gif'];
    if (!in_array($mime_type, $allowed_types)) {
        return null;
    }

    $extension = ($mime_type === 'image/jpeg') ? '.jpg' : (($mime_type === 'image/png') ? '.png' : '.gif');
    $filename = uniqid() . $extension;
    $dir = dirname(__DIR__) . '/' . $subfolder;

    if (!is_dir($dir)) {
        mkdir($dir, 0755, true);
    }

    $path = $dir . '/' . $filename;
    if (file_put_contents($path, $data)) {
        return $subfolder . '/' . $filename;
    }
    return null;
}

/**
 * Optimized batch impression logging.
 * Prevents duplicates within 1 hour to reduce DB churn.
 * Works for both 'listing_impressions' and 'breeding_impressions'.
 */
function log_impressions($db, $user_id, $listing_ids, $table = 'listing_impressions') {
    if (empty($listing_ids)) return;

    $placeholders = array();
    $params = array();
    $i = 1;
    foreach ($listing_ids as $id) {
        $placeholders[] = "(\$" . $i++ . "::integer, \$" . $i++ . "::uuid)";
        $params[] = $id;
        $params[] = $user_id;
    }

    $query = "
        INSERT INTO $table (listing_id, user_id)
        SELECT data.listing_id, data.user_id
        FROM (VALUES " . implode(", ", $placeholders) . ") AS data(listing_id, user_id)
        WHERE NOT EXISTS (
            SELECT 1 FROM $table
            WHERE user_id = data.user_id
              AND listing_id = data.listing_id
              AND shown_at > NOW() - INTERVAL '1 hour'
        )";

    pg_query_params($db, $query, $params);
}

function format_age($days) {
    if ($days === null || $days === '') return null;
    if ($days < 30) return $days . " days";
    if ($days < 365) {
        $months = round($days / 30);
        return $months . ($months == 1 ? " month" : " months");
    }
    $years = round($days / 365, 1);
    return $years . ($years == 1 ? " year" : " years");
}
?>
