<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);
$friend_id = $_POST['target_user_id'] ?? null;

if (!$friend_id) {
    send_error("Target user ID required");
}

if ($user_id == $friend_id) {
    send_error("Cannot add yourself as a friend");
}

$u1 = ($user_id < $friend_id) ? $user_id : $friend_id;
$u2 = ($user_id < $friend_id) ? $friend_id : $user_id;

// Check if already friends or request pending
$check_query = "SELECT status, action_user_id FROM friendships WHERE user_id1 = $1 AND user_id2 = $2";
$check_res = pg_query_params($db, $check_query, array($u1, $u2));

if ($check_res && pg_num_rows($check_res) > 0) {
    $row = pg_fetch_assoc($check_res);
    if ($row['status'] == 'accepted') {
        send_error("Already friends");
    } else {
        if ($row['action_user_id'] == $user_id) {
            send_error("Friend request already sent");
        } else {
            // The other person already sent a request, so let's just accept it
            $query = "UPDATE friendships SET status = 'accepted', action_user_id = $1 WHERE user_id1 = $2 AND user_id2 = $3";
            pg_query_params($db, $query, array($user_id, $u1, $u2));
            pg_close($db);
            send_response("success", "Friend request accepted (they sent one too!)");
        }
    }
}

$query = "INSERT INTO friendships (user_id1, user_id2, status, action_user_id) VALUES ($1, $2, 'pending', $3)";
$result = pg_query_params($db, $query, array($u1, $u2, $user_id));

if ($result) {
    pg_close($db);
    send_response("success", "Friend request sent");
} else {
    pg_close($db);
    send_error("Failed to send friend request");
}
?>
