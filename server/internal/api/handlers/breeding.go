package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

func GetBreedingListings(c *gin.Context) {
	userID, _ := c.Get("userID")
	search := c.PostForm("search")
	breedingType := c.PostForm("breeding_type")
	offset, _ := strconv.Atoi(c.DefaultPostForm("offset", "0"))
	seed := c.DefaultPostForm("seed", fmt.Sprintf("%v%s", userID, time.Now().Format("20060102")))

	query := `
    WITH impression_counts AS (
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
        WHERE bl.status = 'active'`

	params := []any{userID, search, seed}
	paramCount := 4

	if search != "" {
		query += fmt.Sprintf(" AND (t.common_name %% $2 OR t.genus %% $2 OR t.species %% $2 OR t.common_name ILIKE $%d)", paramCount)
		params = append(params, "%"+search+"%")
		paramCount++
	}

	if breedingType == "loan" || breedingType == "seeking" {
		query += fmt.Sprintf(" AND bl.breeding_type = $%d", paramCount)
		params = append(params, breedingType)
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
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch breeding listings", nil)
		return
	}
	defer rows.Close()

	listings := []map[string]any{}
	ids := []int64{}

	for rows.Next() {
		var (
			id, subscriptionTier                              int
			sellerID, sellerName, scientificName, description string
			sex, status, breedingType, imageURL               string
			commonName, whatsapp, facebook, instagram          *string
			loanFee                                            *float64
			listedTime                                         time.Time
			exposureScore, probability                         float64
		)

		if err := rows.Scan(&id, &sellerID, &sellerName, &scientificName, &commonName,
			&description, &sex, &status, &breedingType, &loanFee, &imageURL, &listedTime,
			&subscriptionTier, &whatsapp, &facebook, &instagram, &exposureScore, &probability); err != nil {
			continue
		}

		displayCommonName := scientificName
		if commonName != nil && *commonName != "" {
			displayCommonName = *commonName
		}

		priceDisplay := "Seeking Partner"
		if breedingType == "loan" {
			if loanFee != nil {
				priceDisplay = fmt.Sprintf("Stud Fee: R %.2f", *loanFee)
			} else {
				priceDisplay = "Stud Fee: Contact"
			}
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
			"breeding_type":     breedingType,
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
		if err := db.LogImpressions(context.Background(), fmt.Sprintf("%v", userID), ids, "breeding_impressions"); err != nil {
			// Impression logging failure shouldn't fail the listings response
		}
	}

	utils.SendSuccess(c, "Breeding listings fetched", map[string]any{"listings": listings})
}

func CreateBreedingListing(c *gin.Context) {
	userID, _ := c.Get("userID")

	speciesLSID := c.PostForm("species_lsid")
	sex := c.PostForm("sex")
	breedingType := c.PostForm("breeding_type")
	loanFeeStr := c.PostForm("loan_fee")
	description := c.DefaultPostForm("description", "")
	sizeInCm := c.PostForm("size_in_cm")
	ageInDaysStr := c.PostForm("age_in_days")
	imageBase64 := c.PostForm("image_data")

	if speciesLSID == "" || sex == "" || breedingType == "" {
		utils.SendError(c, http.StatusBadRequest, "Required fields missing", nil)
		return
	}

	if sex != "Male" && sex != "Female" {
		utils.SendError(c, http.StatusBadRequest, "Invalid sex. Breeding listings require 'Male' or 'Female'", nil)
		return
	}

	if breedingType != "loan" && breedingType != "seeking" {
		utils.SendError(c, http.StatusBadRequest, "Invalid breeding type", nil)
		return
	}

	var loanFee *float64
	if breedingType == "loan" && loanFeeStr != "" {
		fee, err := strconv.ParseFloat(strings.ReplaceAll(loanFeeStr, ",", "."), 64)
		if err != nil {
			utils.SendError(c, http.StatusBadRequest, "Loan fee must be numeric", nil)
			return
		}
		loanFee = &fee
	}

	var ageInDays *int
	if ageInDaysStr != "" {
		age, err := strconv.Atoi(ageInDaysStr)
		if err == nil {
			ageInDays = &age
		}
	}

	var size *string
	if sizeInCm != "" {
		size = &sizeInCm
	}

	imageURL, _ := utils.SaveBase64Image(imageBase64, "breeding")

	var listingID int
	query := `INSERT INTO breeding_listings (seller_id, species_lsid, sex, size_in_cm, age, description, image_url, breeding_type, loan_fee)
              VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING id`
	err := db.Pool.QueryRow(context.Background(), query, userID, speciesLSID, sex, size, ageInDays, description, imageURL, breedingType, loanFee).Scan(&listingID)

	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to create breeding listing", nil)
		return
	}

	utils.SendSuccess(c, "Breeding listing created successfully", map[string]any{"id": listingID})
}

func UpdateBreedingListing(c *gin.Context) {
	userID, _ := c.Get("userID")

	id := c.PostForm("id")
	if id == "" {
		utils.SendError(c, http.StatusBadRequest, "ID missing", nil)
		return
	}

	sex := c.PostForm("sex")
	breedingType := c.PostForm("breeding_type")
	loanFeeStr := c.PostForm("loan_fee")
	description := c.PostForm("description")
	sizeInCm := c.PostForm("size_in_cm")
	ageInDaysStr := c.PostForm("age_in_days")
	speciesLSID := c.PostForm("species_lsid")
	imageBase64 := c.PostForm("image_data")

	// Verify ownership
	var currentBreedingType string
	err := db.Pool.QueryRow(context.Background(), "SELECT breeding_type FROM breeding_listings WHERE id = $1 AND seller_id = $2", id, userID).Scan(&currentBreedingType)
	if err != nil {
		utils.SendError(c, http.StatusForbidden, "Unauthorized or listing not found", nil)
		return
	}

	updateFields := []string{}
	params := []any{}
	i := 1

	if sex != "" {
		if sex != "Male" && sex != "Female" {
			utils.SendError(c, http.StatusBadRequest, "Invalid sex", nil)
			return
		}
		updateFields = append(updateFields, fmt.Sprintf("sex = $%d", i))
		params = append(params, sex)
		i++
	}

	if breedingType != "" {
		if breedingType != "loan" && breedingType != "seeking" {
			utils.SendError(c, http.StatusBadRequest, "Invalid breeding type", nil)
			return
		}
		updateFields = append(updateFields, fmt.Sprintf("breeding_type = $%d", i))
		params = append(params, breedingType)
		i++
		currentBreedingType = breedingType
	}

	_, hasLoanFee := c.GetPostForm("loan_fee")
	if hasLoanFee {
		if currentBreedingType == "loan" {
			var loanFee *float64
			if loanFeeStr != "" {
				fee, err := strconv.ParseFloat(strings.ReplaceAll(loanFeeStr, ",", "."), 64)
				if err != nil {
					utils.SendError(c, http.StatusBadRequest, "Loan fee must be numeric", nil)
					return
				}
				loanFee = &fee
			}
			updateFields = append(updateFields, fmt.Sprintf("loan_fee = $%d", i))
			params = append(params, loanFee)
			i++
		} else {
			updateFields = append(updateFields, "loan_fee = NULL")
		}
	}

	if description != "" {
		updateFields = append(updateFields, fmt.Sprintf("description = $%d", i))
		params = append(params, description)
		i++
	}

	if sizeInCm != "" {
		updateFields = append(updateFields, fmt.Sprintf("size_in_cm = $%d", i))
		params = append(params, sizeInCm)
		i++
	}

	if ageInDaysStr != "" {
		age, err := strconv.Atoi(ageInDaysStr)
		if err == nil {
			updateFields = append(updateFields, fmt.Sprintf("age = $%d", i))
			params = append(params, age)
			i++
		}
	}

	if speciesLSID != "" {
		updateFields = append(updateFields, fmt.Sprintf("species_lsid = $%d", i))
		params = append(params, speciesLSID)
		i++
	}

	if imageBase64 != "" {
		path, err := utils.SaveBase64Image(imageBase64, "breeding")
		if err == nil {
			updateFields = append(updateFields, fmt.Sprintf("image_url = $%d", i))
			params = append(params, path)
			i++
		}
	}

	if len(updateFields) == 0 {
		utils.SendError(c, http.StatusBadRequest, "No fields to update", nil)
		return
	}

	params = append(params, id, userID)
	query := fmt.Sprintf("UPDATE breeding_listings SET %s WHERE id = $%d AND seller_id = $%d", strings.Join(updateFields, ", "), i, i+1)

	_, err = db.Pool.Exec(context.Background(), query, params...)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to update breeding listing", nil)
		return
	}

	utils.SendSuccess(c, "Breeding listing updated", nil)
}

func DeleteBreedingListing(c *gin.Context) {
	userID, _ := c.Get("userID")
	id := c.PostForm("id")

	if id == "" {
		utils.SendError(c, http.StatusBadRequest, "ID missing", nil)
		return
	}

	var exists bool
	err := db.Pool.QueryRow(context.Background(), "SELECT EXISTS(SELECT 1 FROM breeding_listings WHERE id = $1 AND seller_id = $2)", id, userID).Scan(&exists)
	if err != nil || !exists {
		utils.SendError(c, http.StatusForbidden, "Failed to delete or unauthorized", nil)
		return
	}

	_, err = db.Pool.Exec(context.Background(), "DELETE FROM breeding_listings WHERE id = $1 AND seller_id = $2", id, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to delete", nil)
		return
	}

	utils.SendSuccess(c, "Breeding listing deleted", nil)
}

func GetBreedingListingDetails(c *gin.Context) {
	id := c.DefaultPostForm("id", c.Query("id"))
	if id == "" {
		utils.SendError(c, http.StatusBadRequest, "ID missing", nil)
		return
	}

	query := `SELECT bl.*, t.genus, t.species, t.subspecies, t.common_name, t.distribution,
                     u.username as seller_name, u.public_key as seller_public_key,
                     u.whatsapp, u.facebook, u.instagram
              FROM breeding_listings bl
              JOIN taxa t ON bl.species_lsid = t.species_lsid
              JOIN users u ON bl.seller_id = u.id
              WHERE bl.id = $1`

	var (
		listingID                                                       int
		sellerID, speciesLSID, sellerName, sellerPublicKey              string
		genus, species, subspecies                                      string
		breedingType, sex, status                                       string
		imageURL                                                        *string
		distribution, desc, sizeInCm                                    *string
		commonName, whatsapp, facebook, instagram                       *string
		loanFee                                                         *float64
		ageInDays                                                       *int
		listedTime                                                      time.Time
	)

	err := db.Pool.QueryRow(context.Background(), query, id).Scan(
		&listingID, &sellerID, &speciesLSID, &sex, &sizeInCm, &ageInDays, &desc, &imageURL,
		&breedingType, &loanFee, &status, &listedTime, &genus, &species, &subspecies,
		&commonName, &distribution, &sellerName, &sellerPublicKey, &whatsapp, &facebook, &instagram,
	)

	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Breeding listing not found", nil)
		return
	}

	scientificName := strings.TrimSpace(fmt.Sprintf("%s %s %s", genus, species, subspecies))
	displayCommonName := scientificName
	if commonName != nil && *commonName != "" {
		displayCommonName = *commonName
	}

	priceDisplay := "Seeking Partner"
	if breedingType == "loan" {
		if loanFee != nil {
			priceDisplay = fmt.Sprintf("Stud Fee: R %.2f", *loanFee)
		} else {
			priceDisplay = "Stud Fee: Contact"
		}
	}

	utils.SendSuccess(c, "Breeding details fetched", map[string]any{
		"id":                listingID,
		"scientific_name":   scientificName,
		"common_name":       displayCommonName,
		"price":             priceDisplay,
		"description":       desc,
		"sex":               sex,
		"size_in_cm":        sizeInCm,
		"age":               utils.FormatAge(ageInDays),
		"age_raw":           ageInDays,
		"image_url":         imageURL,
		"breeding_type":     breedingType,
		"loan_fee":          loanFee,
		"listing_status":    status,
		"listed_time":       listedTime,
		"distribution":      distribution,
		"seller_name":       sellerName,
		"seller_id":         sellerID,
		"seller_public_key": sellerPublicKey,
		"whatsapp":          whatsapp,
		"facebook":          facebook,
		"instagram":         instagram,
		"species_lsid":      speciesLSID,
	})
}

func GetMyBreedingStatus(c *gin.Context) {
	userID, _ := c.Get("userID")

	query := `
        SELECT bl.id, bl.sex, bl.status, bl.breeding_type, bl.image_url, bl.listed_time, bl.species_lsid,
               t.genus, t.species, t.common_name,
               (SELECT COUNT(*) FROM breeding_listings m
                WHERE m.status = 'active'
                  AND m.species_lsid = bl.species_lsid
                  AND m.sex = (CASE WHEN bl.sex = 'Male' THEN 'Female' ELSE 'Male' END)
                  AND m.seller_id != bl.seller_id) as match_count
        FROM breeding_listings bl
        JOIN taxa t ON bl.species_lsid = t.species_lsid
        WHERE bl.seller_id = $1
        ORDER BY bl.listed_time DESC`

	rows, err := db.Pool.Query(context.Background(), query, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch status", nil)
		return
	}
	defer rows.Close()

	listings := []map[string]any{}
	for rows.Next() {
		var (
			id, matchCount                                     int
			sex, status, breedingType, speciesLSID             string
			imageURL                                           *string
			genus, species                                     string
			commonName                                         *string
			listedTime                                         time.Time
		)

		if err := rows.Scan(&id, &sex, &status, &breedingType, &imageURL, &listedTime, &speciesLSID,
			&genus, &species, &commonName, &matchCount); err != nil {
			continue
		}

		scientificName := strings.TrimSpace(genus + " " + species)
		displayCommonName := scientificName
		if commonName != nil && *commonName != "" {
			displayCommonName = *commonName
		}

		listings = append(listings, map[string]any{
			"id":                id,
			"scientific_name":   scientificName,
			"common_name":       displayCommonName,
			"match_count":       matchCount,
			"status":            status,
			"breeding_type":     breedingType,
			"image_url":         imageURL,
			"sex":               sex,
		})
	}

	utils.SendSuccess(c, "My breeding status fetched", map[string]any{"listings": listings})
}

func FindBreedingMatches(c *gin.Context) {
	authenticatedUserID, _ := c.Get("userID")
	listingID := c.PostForm("listing_id")

	if listingID == "" {
		utils.SendError(c, http.StatusBadRequest, "Listing ID missing", nil)
		return
	}

	var speciesLSID, sex string
	err := db.Pool.QueryRow(context.Background(), "SELECT species_lsid, sex FROM breeding_listings WHERE id = $1 AND seller_id = $2", listingID, authenticatedUserID).Scan(&speciesLSID, &sex)
	if err != nil {
		utils.SendError(c, http.StatusForbidden, "Unauthorized or listing not found", nil)
		return
	}

	oppositeSex := "Male"
	if sex == "Male" {
		oppositeSex = "Female"
	}

	query := `SELECT bl.*, t.genus, t.species, t.common_name, u.username as seller_name, u.subscription_tier,
                     u.whatsapp, u.facebook, u.instagram
              FROM breeding_listings bl
              JOIN taxa t ON bl.species_lsid = t.species_lsid
              JOIN users u ON bl.seller_id = u.id
              WHERE bl.status = 'active'
                AND bl.species_lsid = $1
                AND bl.sex = $2
                AND bl.seller_id != $3
              ORDER BY bl.listed_time DESC`

	rows, err := db.Pool.Query(context.Background(), query, speciesLSID, oppositeSex, authenticatedUserID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to search matches", nil)
		return
	}
	defer rows.Close()

	matches := []map[string]any{}
	for rows.Next() {
		var (
			id, subscriptionTier                                     int
			sellerID, speciesLSID, sellerName                        string
			genus, species, breedingType, sex, status                string
			imageURL                                                 *string
			desc, sizeInCm                                           *string
			commonName, whatsapp, facebook, instagram                *string
			loanFee                                                  *float64
			ageInDays                                                *int
			listedTime                                               time.Time
		)

		if err := rows.Scan(&id, &sellerID, &speciesLSID, &sex, &sizeInCm, &ageInDays, &desc, &imageURL,
			&breedingType, &loanFee, &status, &listedTime, &genus, &species, &commonName,
			&sellerName, &subscriptionTier, &whatsapp, &facebook, &instagram); err != nil {
			continue
		}

		scientificName := strings.TrimSpace(genus + " " + species)
		displayCommonName := scientificName
		if commonName != nil && *commonName != "" {
			displayCommonName = *commonName
		}

		priceDisplay := "Seeking Partner"
		if breedingType == "loan" {
			if loanFee != nil {
				priceDisplay = fmt.Sprintf("Stud Fee: R %.2f", *loanFee)
			} else {
				priceDisplay = "Stud Fee: Contact"
			}
		}

		matches = append(matches, map[string]any{
			"id":                id,
			"seller_id":         sellerID,
			"seller_name":       sellerName,
			"scientific_name":   scientificName,
			"common_name":       displayCommonName,
			"price":             priceDisplay,
			"description":       desc,
			"image_url":         imageURL,
			"sex":               sex,
			"breeding_type":     breedingType,
			"listed_time":       listedTime,
			"subscription_tier": subscriptionTier,
			"whatsapp":          whatsapp,
			"facebook":          facebook,
			"instagram":         instagram,
		})
	}

	utils.SendSuccess(c, "Matches found", map[string]any{"listings": matches})
}
