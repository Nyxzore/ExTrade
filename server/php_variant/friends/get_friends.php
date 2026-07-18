<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$query = "SELECT u.id, u.username, u.profile_picture, u.subscription_tier
          FROM friendships f
          JOIN users u ON (f.user_id1 = $1 AND f.user_id2 = u.id)
                       OR (f.user_id2 = $1 AND f.user_id1 = u.id)
          WHERE f.status = 'accepted'";

$result = pg_query_params($db, $query, array($user_id));

$friends = [];
if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $friends[] = [
            "id" => $row['id'],
            "username" => $row['username'],
            "profile_pic" => $row['profile_picture'],
            "subscription_tier" => (int)$row['subscription_tier']
        ];
    }
}

pg_close($db);
send_response("success", "Friends fetched", ["friends" => $friends]);
?>
