<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$query = "SELECT u.id, u.username, u.profile_picture, u.subscription_tier
          FROM friendships f
          JOIN users u ON (f.user_id1 = u.id OR f.user_id2 = u.id)
          WHERE ((f.user_id1 = $1 OR f.user_id2 = $1) AND u.id != $1)
            AND f.status = 'pending'
            AND f.action_user_id != $1";

$result = pg_query_params($db, $query, array($user_id));

$requests = array();
if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $requests[] = [
            "id" => $row['id'],
            "username" => $row['username'],
            "profile_pic" => $row['profile_picture'],
            "subscription_tier" => (int)$row['subscription_tier']
        ];
    }
}

pg_close($db);
send_response("success", "Requests fetched", ["requests" => $requests]);
?>
