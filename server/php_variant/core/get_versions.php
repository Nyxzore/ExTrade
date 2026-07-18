<?php
require_once __DIR__ . '/db_connect.php';
$db = get_db_connection();

$query = "SELECT object, version FROM versions";
$result = pg_query($db, $query);

if (!$result) {
    send_error("Failed to fetch versions");
}

$versions = [];
while ($row = pg_fetch_assoc($result)) {
    $versions[$row['object']] = $row['version'];
}

pg_close($db);
send_response("success", "Versions fetched", ["versions" => $versions]);
?>
