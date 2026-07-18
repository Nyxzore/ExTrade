<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$query = $_POST['query'] ?? '';

if (empty($query)) {
    send_response("success", "Results", ["users" => []]);
}

$search_sql = "SELECT id, username, profile_picture, subscription_tier
               FROM users
               WHERE (username ILIKE $1 OR username % $3) AND id != $2
               ORDER BY similarity(username, $3) DESC, subscription_tier DESC
               LIMIT 20";

$result = pg_query_params($db, $search_sql, array('%' . $query . '%', $user_id, $query));

$users = [];
if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $users[] = [
            "id" => $row['id'],
            "username" => $row['username'],
            "profile_pic" => $row['profile_picture'],
            "subscription_tier" => (int)$row['subscription_tier']
        ];
    }
}

pg_close($db);
send_response("success", "Results", ["users" => $users]);
?>
