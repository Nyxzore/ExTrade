<?php
require_once __DIR__ . '/../core/db_connect.php';
$db = get_db_connection();

$user_id = verify_auth($db);

$search = $_POST['search'] ?? null;
$breeding_type = $_POST['breeding_type'] ?? null;
$offset = (int)($_POST['offset'] ?? 0);
$seed = $_POST['seed'] ?? ($user_id . date('Ymd'));

// The Hotness Algorithm applied to Breeding
$query =
    "WITH impression_counts AS (
    SELECT listing_id, COUNT(*) as times_shown_recently
    FROM breeding_impressions
    WHERE user_id = $1
     AND shown_at > NOW() - INTERVAL '24 hours'
    GROUP BY listing_id
),
scored AS (
    SELECT bl.id,
           bl.seller_id,
           u.username as seller_name,
           TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
           t.common_name,
           bl.description,
           bl.sex,
           bl.status,
           bl.breeding_type,
           bl.loan_fee,
           bl.image_url,
           bl.listed_time,
           u.subscription_tier,
           u.whatsapp,
           u.facebook,
           u.instagram,
           (((CASE COALESCE(u.subscription_tier, 0)
                WHEN 2 THEN 5
                WHEN 1 THEN 2
                ELSE 1
            END)
           + EXP(-LN(2) * EXTRACT(EPOCH FROM (NOW() - COALESCE(bl.listed_time, NOW()))) / 86400.0
                 / (CASE COALESCE(u.subscription_tier, 0)
                        WHEN 2 THEN 5
                        WHEN 1 THEN 2
                        ELSE 1
                    END)))
           / POWER(1 + COALESCE(ic.times_shown_recently, 0), 0.15))
           * (CASE WHEN $2::text IS NOT NULL
                   THEN (POWER(similarity(t.common_name, $2::text), 3) * 50.0 + 1.0)
                   ELSE 1.0
              END) as exposure_score
    FROM breeding_listings bl
    JOIN taxa t ON bl.species_lsid = t.species_lsid
    JOIN users u ON bl.seller_id = u.id
    LEFT JOIN impression_counts ic ON ic.listing_id = bl.id
    WHERE bl.status = 'active'";

$params = [$user_id, $search, $seed];
$param_count = 4;

if ($search) {
    $query .= " AND (t.common_name % $2 OR t.genus % $2 OR t.species % $2 OR t.common_name ILIKE $" . $param_count . ")";
    $params[] = "%" . $search . "%";
    $param_count++;
}

if ($breeding_type && in_array($breeding_type, ['loan', 'seeking'])) {
    $query .= " AND bl.breeding_type = $" . $param_count;
    $params[] = $breeding_type;
    $param_count++;
}

$query .= "
),
randomized AS (
    SELECT *,
           (ABS(('x' || SUBSTR(MD5(id::text || $3), 1, 8))::bit(32)::integer)::double precision / 2147483647.0)
           ^ (1.0 / exposure_score) as probability
    FROM scored
)
SELECT * FROM randomized
ORDER BY probability DESC
LIMIT 10 OFFSET $" . $param_count;

$params[] = $offset;

$result = pg_query_params($db, $query, $params);
$listings = [];

if ($result) {
    while ($row = pg_fetch_assoc($result)) {
        $scientific_name = $row['scientific_name'];
        $common_name = !empty($row['common_name']) ? $row['common_name'] : $scientific_name;

        $price_display = "Seeking Partner";
        if ($row['breeding_type'] === 'loan') {
            $price_display = $row['loan_fee'] ? "Stud Fee: R " . number_format($row['loan_fee'], 2) : "Stud Fee: Contact";
        }

        $listings[] = [
            "id" => $row['id'],
            "seller_id" => $row['seller_id'],
            "seller_name" => $row['seller_name'],
            "scientific_name" => $scientific_name,
            "common_name" => $common_name,
            "price" => $price_display,
            "description" => $row['description'],
            "image_url" => $row['image_url'],
            "sex" => $row['sex'],
            "status" => $row['status'],
            "breeding_type" => $row['breeding_type'],
            "listed_time" => $row['listed_time'],
            "probability" => (float)$row['probability'],
            "subscription_tier" => (int)($row['subscription_tier'] ?? 0),
            "whatsapp" => $row['whatsapp'],
            "facebook" => $row['facebook'],
            "instagram" => $row['instagram']
        ];
    }

    if (!empty($listings)) {
        $ids = array_column($listings, 'id');
        log_impressions($db, $user_id, $ids, 'breeding_impressions');
    }

    send_response("success", "Breeding listings fetched", ["listings" => $listings]);
} else {
    send_error("Failed to fetch breeding listings");
}

pg_close($db);
?>
