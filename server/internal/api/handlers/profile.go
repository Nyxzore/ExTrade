package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

func GetProfile(c *gin.Context) {
	authenticatedUserID, _ := c.Get("userID")
	targetUserID := c.DefaultPostForm("target_user_id", authenticatedUserID.(string))

	var (
		username, email, profilePicture, publicKey string
		subscriptionTier                            int
		isAdmin                                     bool
		whatsapp, facebook, instagram               *string
	)

	query := "SELECT username, email, profile_picture, public_key, subscription_tier, is_admin, whatsapp, facebook, instagram FROM users WHERE id = $1"
	err := db.Pool.QueryRow(context.Background(), query, targetUserID).Scan(
		&username, &email, &profilePicture, &publicKey, &subscriptionTier, &isAdmin, &whatsapp, &facebook, &instagram,
	)

	if err != nil {
		utils.SendError(c, http.StatusNotFound, "User not found", nil)
		return
	}

	// Email is private unless it's your own profile
	displayEmail := ""
	if targetUserID == authenticatedUserID {
		displayEmail = email
	}

	// Fetch both sale and breeding listings
	listingsQuery := `
    (SELECT l.id, l.seller_id, t.species_lsid,
            TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
            t.common_name, l.price as raw_price, l.description, l.image_url,
            'sale' as kind, NULL as breeding_type, l.listed_time, l.sex, l.status,
            u.subscription_tier
     FROM listings l
     JOIN taxa t ON l.species_lsid = t.species_lsid
     JOIN users u ON l.seller_id = u.id
     WHERE l.seller_id = $1)
    UNION ALL
    (SELECT bl.id, bl.seller_id, t.species_lsid,
            TRIM(CONCAT_WS(' ', t.genus, t.species, t.subspecies)) as scientific_name,
            t.common_name, bl.loan_fee as raw_price, bl.description, bl.image_url,
            'breeding' as kind, bl.breeding_type, bl.listed_time, bl.sex, bl.status,
            u.subscription_tier
     FROM breeding_listings bl
     JOIN taxa t ON bl.species_lsid = t.species_lsid
     JOIN users u ON bl.seller_id = u.id
     WHERE bl.seller_id = $1)
    ORDER BY listed_time DESC`

	rows, err := db.Pool.Query(context.Background(), listingsQuery, targetUserID)
	listings := []map[string]any{}

	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var (
				id                                                                 int
				sellerID, speciesLSID, scientificName, description, imageURL       string
				kind, sex, status                                                  string
				breedingType                                                       *string
				commonName                                                         *string
				rawPrice                                                           *float64
				listedTime                                                         time.Time
				subscriptionTier                                                   int
			)

			if err := rows.Scan(&id, &sellerID, &speciesLSID, &scientificName, &commonName, &rawPrice,
				&description, &imageURL, &kind, &breedingType, &listedTime, &sex, &status, &subscriptionTier); err != nil {
				continue
			}

			displayCommonName := scientificName
			if commonName != nil && *commonName != "" {
				displayCommonName = *commonName
			}

			priceDisplay := "Contact"
			if kind == "sale" {
				if rawPrice != nil {
					priceDisplay = fmt.Sprintf("R %.2f", *rawPrice)
				}
			} else {
				if breedingType != nil && *breedingType == "loan" {
					if rawPrice != nil {
						priceDisplay = fmt.Sprintf("Stud Fee: R %.2f", *rawPrice)
					} else {
						priceDisplay = "Stud Fee: Contact"
					}
				} else {
					priceDisplay = "Seeking Partner"
				}
			}

			listings = append(listings, map[string]any{
				"id":                id,
				"seller_id":         sellerID,
				"species_lsid":      speciesLSID,
				"scientific_name":   scientificName,
				"common_name":       displayCommonName,
				"price":             priceDisplay,
				"description":       description,
				"image_url":         imageURL,
				"kind":              kind,
				"sex":               sex,
				"status":            status,
				"subscription_tier": subscriptionTier,
			})
		}
	}

	utils.SendSuccess(c, "Profile fetched", map[string]any{
		"username":          username,
		"email":             displayEmail,
		"profile_picture":   profilePicture,
		"public_key":        publicKey,
		"subscription_tier": subscriptionTier,
		"is_admin":          isAdmin,
		"whatsapp":          whatsapp,
		"facebook":          facebook,
		"instagram":         instagram,
		"listings":          listings,
	})
}

func UpdateProfile(c *gin.Context) {
	userID, _ := c.Get("userID")

	username := c.PostForm("username")
	email := c.PostForm("email")
	profilePictureData := c.PostForm("profile_picture_data")
	newPassword := c.PostForm("new_password")
	whatsapp := c.PostForm("whatsapp")
	facebook := c.PostForm("facebook")
	instagram := c.PostForm("instagram")

	if username == "" || email == "" {
		utils.SendError(c, http.StatusBadRequest, "Username and email are required", nil)
		return
	}

	// Basic email validation
	if !utils.IsValidEmail(email) {
		utils.SendError(c, http.StatusBadRequest, "Invalid email format", nil)
		return
	}

	// Check duplicates
	var exists bool
	db.Pool.QueryRow(context.Background(), "SELECT EXISTS(SELECT 1 FROM users WHERE username = $1 AND id != $2)", username, userID).Scan(&exists)
	if exists {
		utils.SendError(c, http.StatusConflict, "Username already taken", nil)
		return
	}
	db.Pool.QueryRow(context.Background(), "SELECT EXISTS(SELECT 1 FROM users WHERE email = $1 AND id != $2)", email, userID).Scan(&exists)
	if exists {
		utils.SendError(c, http.StatusConflict, "Email already registered", nil)
		return
	}

	social, errStr := utils.SanitizeSocialFields(whatsapp, facebook, instagram)
	if errStr != "" {
		utils.SendError(c, http.StatusBadRequest, errStr, nil)
		return
	}

	updateFields := []string{
		"username = $1",
		"email = $2",
		"whatsapp = $3",
		"facebook = $4",
		"instagram = $5",
	}
	params := []any{username, email, social.WhatsApp, social.Facebook, social.Instagram}

	if profilePictureData != "" {
		path, err := utils.SaveBase64Image(profilePictureData, "profile_pics")
		if err == nil {
			updateFields = append(updateFields, fmt.Sprintf("profile_picture = $%d", len(params)+1))
			params = append(params, path)
		}
	}

	if newPassword != "" {
		pepper := os.Getenv("AUTH_PEPPER")
		hashedPassword, _ := bcrypt.GenerateFromPassword([]byte(newPassword+pepper), bcrypt.DefaultCost)
		updateFields = append(updateFields, fmt.Sprintf("password_hash = $%d", len(params)+1))
		params = append(params, string(hashedPassword))

		encKey := c.PostForm("encrypted_private_key")
		nonce := c.PostForm("private_key_nonce")
		salt := c.PostForm("kdf_salt")

		if encKey == "" {
			utils.SendError(c, http.StatusBadRequest, "Security backup rotation material missing", nil)
			return
		}

		updateFields = append(updateFields, fmt.Sprintf("encrypted_private_key = $%d", len(params)+1))
		params = append(params, encKey)
		updateFields = append(updateFields, fmt.Sprintf("private_key_nonce = $%d", len(params)+1))
		params = append(params, nonce)
		updateFields = append(updateFields, fmt.Sprintf("kdf_salt = $%d", len(params)+1))
		params = append(params, salt)
	}

	params = append(params, userID)
	query := fmt.Sprintf("UPDATE users SET %s WHERE id = $%d", strings.Join(updateFields, ", "), len(params))

	_, err := db.Pool.Exec(context.Background(), query, params...)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Profile update failed", nil)
		return
	}

	utils.SendSuccess(c, "Profile updated successfully", nil)
}
