<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$query = "
    SELECT bl.*, t.genus, t.species, t.common_name,
           (SELECT COUNT(*) FROM breeding_listings m
            WHERE m.status = 'active'
              AND m.species_lsid = bl.species_lsid
              AND m.sex = (CASE WHEN bl.sex = 'Male' THEN 'Female' ELSE 'Male' END)
              AND m.seller_id != bl.seller_id) as match_count
    FROM breeding_listings bl
    JOIN taxa t ON bl.species_lsid = t.species_lsid
    WHERE bl.seller_id = $1
    ORDER BY bl.listed_time DESC
";

$result = pg_query_params($db, $query, [$user_id]);
$listings = [];

if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $scientific_name = trim($row['genus'] . ' ' . $row['species']);
        $common_name = !empty($row['common_name']) ? $row['common_name'] : $scientific_name;

        $listings[] = [
            "id" => $row['id'],
            "scientific_name" => $scientific_name,
            "common_name" => $common_name,
            "match_count" => (int)$row['match_count'],
            "status" => $row['status'],
            "breeding_type" => $row['breeding_type'],
            "image_url" => $row['image_url'],
            "sex" => $row['sex']
        ];
    }
    send_response("success", "My breeding status fetched", ["listings" => $listings]);
} else {
    send_error("Failed to fetch status");
}

pg_close($db);
?>
