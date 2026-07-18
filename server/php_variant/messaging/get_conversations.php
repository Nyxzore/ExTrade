<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

/**
 * Returns a list of plain DM conversations for the current user.
 * Joins with users to get the other participant's info.
 * Uses a LATERAL join to efficiently fetch the latest message and unread count.
 */
$query = "
    SELECT
        c.id as conversation_id,
        u.id as other_user_id,
        u.username as other_username,
        u.profile_picture as other_profile_pic,
        u.subscription_tier as other_subscription_tier,
        u.public_key as other_public_key,
        msg.body as last_message,
        msg.sent_at as last_message_time,
        msg.nonce as last_message_nonce,
        msg.is_encrypted as is_last_message_encrypted,
        unread.count as unread_count
    FROM conversations c
    JOIN users u ON (CASE WHEN c.user_a_id = $1 THEN c.user_b_id ELSE c.user_a_id END) = u.id
    LEFT JOIN LATERAL (
        SELECT body, sent_at, nonce, is_encrypted
        FROM messages
        WHERE conversation_id = c.id
        ORDER BY sent_at DESC, id DESC
        LIMIT 1
    ) msg ON true
    LEFT JOIN LATERAL (
        SELECT COUNT(*) as count
        FROM messages
        WHERE conversation_id = c.id AND sender_id != $1 AND read_at IS NULL
    ) unread ON true
    WHERE c.user_a_id = $1 OR c.user_b_id = $1
    ORDER BY last_message_time DESC NULLS LAST
";

$result = pg_query_params($db, $query, array($user_id));

$conversations = [];
$total_unread = 0;
if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $conversations[] = $row;
        $total_unread += (int)($row['unread_count'] ?? 0);
    }
}

pg_close($db);
send_response("success", "Conversations fetched", [
    "conversations" => $conversations,
    "total_unread" => $total_unread
]);
?>
