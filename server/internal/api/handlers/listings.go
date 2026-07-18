package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
)

func GetAllListings(c *gin.Context) {
	userID, _ := c.Get("userID")
	search := c.PostForm("search")
	offset, _ := strconv.Atoi(c.DefaultPostForm("offset", "0"))
	seed := c.DefaultPostForm("seed", fmt.Sprintf("%v%s", userID, time.Now().Format("20060102")))

	query := `
    WITH impression_counts AS (
        SELECT listing_id, COUNT(*) as times_shown_recently
        FROM listing_impressions
        WHERE user_id = $1
         AND shown_at > NOW() - INTERVAL '24 hours'
        GROUP BY listing_id
    ),
    scored AS (
        SELECT l.id,
               l.seller_id,
               u.username as seller_name,
               TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
               t.common_name,
               l.price,
               l.description,
               l.image_url,
               l.sex,
               l.status,
               l.listed_time,
               u.subscription_tier,
               u.whatsapp,
               u.facebook,
               u.instagram,
               (((CASE COALESCE(u.subscription_tier, 0)
                    WHEN 2 THEN 5
                    WHEN 1 THEN 2
                    ELSE 1
                END)
               + EXP(-LN(2) * EXTRACT(EPOCH FROM (NOW() - COALESCE(l.listed_time, NOW()))) / 86400.0
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
        FROM listings l
        JOIN taxa t ON l.species_lsid = t.species_lsid
        JOIN users u ON l.seller_id = u.id
        LEFT JOIN impression_counts ic ON ic.listing_id = l.id
        WHERE l.status = 'active'`

	params := []any{userID, search, seed}
	paramCount := 4

	if search != "" {
		query += fmt.Sprintf(" AND (t.common_name %% $2 OR t.genus %% $2 OR t.species %% $2 OR t.common_name ILIKE $%d)", paramCount)
		params = append(params, "%"+search+"%")
		paramCount++
	}

	query += fmt.Sprintf(`
    ),
    randomized AS (
        SELECT *,
               (ABS(('x' || SUBSTR(MD5(id::text || $3), 1, 8))::bit(32)::integer)::double precision / 2147483647.0)
               ^ (1.0 / exposure_score) as probability
        FROM scored
    )
    SELECT * FROM randomized
    ORDER BY probability DESC
    LIMIT 10 OFFSET $%d`, paramCount)

	params = append(params, offset)

	rows, err := db.Pool.Query(context.Background(), query, params...)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch listings", nil)
		return
	}
	defer rows.Close()

	listings := []map[string]any{}
	ids := []int64{}


	for rows.Next() {
		var (
			id, subscriptionTier                                        int
			sellerID, sellerName, scientificName, description           string
			imageURL, sex, status                                       string
			commonName, whatsapp, facebook, instagram                   *string
			price                                                       *float64
			listedTime                                                  time.Time
			exposureScore, probability                                  float64
		)

		if err := rows.Scan(&id, &sellerID, &sellerName, &scientificName, &commonName, &price,
			&description, &imageURL, &sex, &status, &listedTime, &subscriptionTier,
			&whatsapp, &facebook, &instagram, &exposureScore, &probability); err != nil {
			continue
		}

		displayCommonName := scientificName
		if commonName != nil && *commonName != "" {
			displayCommonName = *commonName
		}

		priceDisplay := "Contact"
		if price != nil {
			priceDisplay = fmt.Sprintf("R %.2f", *price)
		}

		listings = append(listings, map[string]any{
			"id":                id,
			"seller_id":         sellerID,
			"seller_name":       sellerName,
			"scientific_name":   scientificName,
			"common_name":       displayCommonName,
			"price":             priceDisplay,
			"description":       description,
			"image_url":         imageURL,
			"sex":               sex,
			"status":            status,
			"listed_time":       listedTime,
			"exposure_score":    exposureScore,
			"probability":       probability,
			"subscription_tier": subscriptionTier,
			"whatsapp":          whatsapp,
			"facebook":          facebook,
			"instagram":         instagram,
		})
		ids = append(ids, int64(id))
	}

	if len(ids) > 0 {
		if err := db.LogImpressions(context.Background(), fmt.Sprintf("%v", userID), ids, "listing_impressions"); err != nil {
			// Impression logging failure shouldn't fail the listings response
		}
	}

	utils.SendSuccess(c, "Listings fetched", map[string]any{"listings": listings})
}
