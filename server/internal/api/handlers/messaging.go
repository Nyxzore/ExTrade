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

func SendMessage(c *gin.Context) {
	userID, _ := c.Get("userID")
	convID := c.PostForm("conversation_id")
	body := c.PostForm("body")
	nonce := c.PostForm("nonce")

	if convID == "" || body == "" || nonce == "" {
		utils.SendError(c, http.StatusBadRequest, "Missing required fields", nil)
		return
	}

	// Verify membership
	var exists bool
	err := db.Pool.QueryRow(context.Background(), "SELECT EXISTS(SELECT 1 FROM conversations WHERE id = $1 AND (user_a_id = $2 OR user_b_id = $3))", convID, userID, userID).Scan(&exists)
	if err != nil || !exists {
		utils.SendError(c, http.StatusForbidden, "Access denied to this conversation", nil)
		return
	}

	var messageID int64
	var sentAt time.Time
	query := "INSERT INTO messages (conversation_id, sender_id, body, nonce) VALUES ($1, $2, $3, $4) RETURNING id, sent_at"
	err = db.Pool.QueryRow(context.Background(), query, convID, userID, body, nonce).Scan(&messageID, &sentAt)

	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to send message", nil)
		return
	}

	utils.SendSuccess(c, "Message sent", map[string]any{
		"message_id": messageID,
		"sent_at":    sentAt,
	})
}

func GetConversations(c *gin.Context) {
	userID, _ := c.Get("userID")

	query := `
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
        ORDER BY last_message_time DESC NULLS LAST`

	rows, err := db.Pool.Query(context.Background(), query, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch conversations", nil)
		return
	}
	defer rows.Close()

	conversations := []map[string]any{}
	totalUnread := 0
	for rows.Next() {
		var (
			convID, otherUserID, otherUsername                            string
			otherProfilePic, otherPublicKey, lastMessage, lastMessageNonce *string
			otherSubscriptionTier, unreadCount                            int
			lastMessageTime                                               *time.Time
			isLastMessageEncrypted                                        *bool
		)

		if err := rows.Scan(&convID, &otherUserID, &otherUsername, &otherProfilePic, &otherSubscriptionTier, &otherPublicKey,
			&lastMessage, &lastMessageTime, &lastMessageNonce, &isLastMessageEncrypted, &unreadCount); err != nil {
			continue
		}

		conversations = append(conversations, map[string]any{
			"conversation_id":           convID,
			"other_user_id":             otherUserID,
			"other_username":            otherUsername,
			"other_profile_pic":         otherProfilePic,
			"other_subscription_tier":   otherSubscriptionTier,
			"other_public_key":          otherPublicKey,
			"last_message":              lastMessage,
			"last_message_time":         lastMessageTime,
			"last_message_nonce":        lastMessageNonce,
			"is_last_message_encrypted": isLastMessageEncrypted,
			"unread_count":              unreadCount,
		})
		totalUnread += unreadCount
	}

	utils.SendSuccess(c, "Conversations fetched", map[string]any{
		"conversations": conversations,
		"total_unread":  totalUnread,
	})
}

func GetMessages(c *gin.Context) {
	userID, _ := c.Get("userID")

	convID := c.Query("conversation_id")
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "20"))
	lastID := c.Query("last_id")
	lastSentAt := c.Query("last_sent_at")
	sinceID := c.Query("since_id")

	if convID == "" {
		utils.SendError(c, http.StatusBadRequest, "Conversation ID is required", nil)
		return
	}

	// Verify membership
	var exists bool
	db.Pool.QueryRow(context.Background(), "SELECT EXISTS(SELECT 1 FROM conversations WHERE id = $1 AND (user_a_id = $2 OR user_b_id = $3))", convID, userID, userID).Scan(&exists)
	if !exists {
		utils.SendError(c, http.StatusForbidden, "Access denied to this conversation", nil)
		return
	}

	var query string
	var params []any
	if sinceID != "" {
		query = `SELECT m.id, m.conversation_id, m.sender_id, m.body, m.nonce, m.is_encrypted, m.sent_at, m.read_at, u.profile_picture as sender_profile_pic, u.username as sender_username, u.public_key as sender_public_key, u.subscription_tier as sender_subscription_tier
                  FROM messages m
                  JOIN users u ON m.sender_id = u.id
                  WHERE m.conversation_id = $1 AND m.id > $2
                  ORDER BY m.sent_at ASC, m.id ASC`
		params = []any{convID, sinceID}
	} else if lastID != "" && lastSentAt != "" {
		query = `SELECT m.id, m.conversation_id, m.sender_id, m.body, m.nonce, m.is_encrypted, m.sent_at, m.read_at, u.profile_picture as sender_profile_pic, u.username as sender_username, u.public_key as sender_public_key, u.subscription_tier as sender_subscription_tier
                  FROM messages m
                  JOIN users u ON m.sender_id = u.id
                  WHERE m.conversation_id = $1
                    AND (m.sent_at < $2 OR (m.sent_at = $2 AND m.id < $3))
                  ORDER BY m.sent_at DESC, m.id DESC
                  LIMIT $4`
		params = []any{convID, lastSentAt, lastID, limit}
	} else {
		query = `SELECT m.id, m.conversation_id, m.sender_id, m.body, m.nonce, m.is_encrypted, m.sent_at, m.read_at, u.profile_picture as sender_profile_pic, u.username as sender_username, u.public_key as sender_public_key, u.subscription_tier as sender_subscription_tier
                  FROM messages m
                  JOIN users u ON m.sender_id = u.id
                  WHERE m.conversation_id = $1
                  ORDER BY m.sent_at DESC, m.id DESC
                  LIMIT $2`
		params = []any{convID, limit}
	}

	rows, err := db.Pool.Query(context.Background(), query, params...)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch messages", nil)
		return
	}
	defer rows.Close()

	messages := []map[string]any{}
	for rows.Next() {
		var (
			id                                                               int64
			convIDIn, senderID, body, nonce, profilePic, username, pubKey    string
			isEncrypted                                                       bool
			sentAt                                                            time.Time
			readAt                                                            *time.Time
			tier                                                              int
		)
		if err := rows.Scan(&id, &convIDIn, &senderID, &body, &nonce, &isEncrypted, &sentAt, &readAt, &profilePic, &username, &pubKey, &tier); err == nil {
			messages = append(messages, map[string]any{
				"id":                       strconv.FormatInt(id, 10),
				"conversation_id":          convIDIn,
				"sender_id":                senderID,
				"body":                     body,
				"nonce":                    nonce,
				"is_encrypted":             isEncrypted,
				"sent_at":                  sentAt,
				"read_at":                  readAt,
				"sender_profile_pic":       profilePic,
				"sender_username":          username,
				"sender_public_key":        pubKey,
				"sender_subscription_tier": tier,
			})
		} else {
			fmt.Printf("Scan error in GetMessages: %v\n", err)
		}
	}

	if sinceID == "" {
		// Reverse for chronological order if not polling
		for i, j := 0, len(messages)-1; i < j; i, j = i+1, j-1 {
			messages[i], messages[j] = messages[j], messages[i]
		}
	}

	utils.SendSuccess(c, "Messages fetched", map[string]any{"messages": messages})
}

func MarkRead(c *gin.Context) {
	userID, _ := c.Get("userID")
	convID := c.PostForm("conversation_id")

	if convID == "" {
		utils.SendError(c, http.StatusBadRequest, "Conversation ID is required", nil)
		return
	}

	_, err := db.Pool.Exec(context.Background(), "UPDATE messages SET read_at = NOW() WHERE conversation_id = $1 AND sender_id != $2 AND read_at IS NULL", convID, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to mark messages as read", nil)
		return
	}

	utils.SendSuccess(c, "Messages marked as read", nil)
}

func UploadAttachment(c *gin.Context) {
	imageBase64 := c.PostForm("image_data")
	if imageBase64 == "" {
		utils.SendError(c, http.StatusBadRequest, "No image provided", nil)
		return
	}
	url, err := utils.SaveBase64Image(imageBase64, "chat_attachments")
	if err != nil || url == "" {
		utils.SendError(c, http.StatusBadRequest, "Failed to upload image", nil)
		return
	}
	utils.SendSuccess(c, "Attachment uploaded", map[string]any{"url": url})
}

func StartOrGetConversation(c *gin.Context) {
	userIDVal, _ := c.Get("userID")
	userID, _ := userIDVal.(string)
	listingID := c.PostForm("listing_id")
	sellerID := c.DefaultPostForm("seller_id", c.PostForm("target_user_id"))
	listingKind := c.DefaultPostForm("listing_kind", "sale")

	if sellerID == "" {
		utils.SendError(c, http.StatusBadRequest, "Target user ID is required", nil)
		return
	}

	if userID == sellerID {
		utils.SendError(c, http.StatusBadRequest, "You cannot start a conversation with yourself", nil)
		return
	}

	var otherUser struct {
		Username   string  `json:"username"`
		ProfilePic *string `json:"profile_pic"`
		PublicKey  string  `json:"public_key"`
	}
	err := db.Pool.QueryRow(context.Background(), "SELECT username, profile_picture, public_key FROM users WHERE id = $1", sellerID).Scan(&otherUser.Username, &otherUser.ProfilePic, &otherUser.PublicKey)
	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Seller not found", nil)
		return
	}

	// Friend/DM path: open or create a conversation without a listing context
	if listingID == "" {
		u1, u2 := userID, sellerID
		if u1 > u2 {
			u1, u2 = u2, u1
		}
		var convID string
		err = db.Pool.QueryRow(context.Background(), "SELECT id FROM conversations WHERE user_a_id = $1 AND user_b_id = $2", u1, u2).Scan(&convID)
		if err != nil {
			err = db.Pool.QueryRow(context.Background(), "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2) RETURNING id", u1, u2).Scan(&convID)
			if err != nil {
				utils.SendError(c, http.StatusInternalServerError, "Failed to create conversation", nil)
				return
			}
		}
		utils.SendSuccess(c, "Conversation retrieved", map[string]any{
			"conversation_id": convID,
			"other_user":      otherUser,
		})
		return
	}

	var listingDetails map[string]any
	if listingKind == "sale" {
		var row struct {
			ID                                 int
			CommonName                         *string
			Genus, Species, Subspecies, ImageURL string
			Price                              *float64
		}
		query := `SELECT l.id, t.common_name, t.genus, t.species, t.subspecies, l.price, l.image_url
                  FROM listings l
                  JOIN taxa t ON l.species_lsid = t.species_lsid
                  WHERE l.id = $1 AND l.seller_id = $2`
		err = db.Pool.QueryRow(context.Background(), query, listingID, sellerID).Scan(&row.ID, &row.CommonName, &row.Genus, &row.Species, &row.Subspecies, &row.Price, &row.ImageURL)
		if err == nil {
			sciName := strings.TrimSpace(fmt.Sprintf("%s %s %s", row.Genus, row.Species, row.Subspecies))
			comName := sciName
			if row.CommonName != nil && *row.CommonName != "" {
				comName = *row.CommonName
			}
			priceDisp := "Contact"
			if row.Price != nil {
				priceDisp = fmt.Sprintf("R %.2f", *row.Price)
			}
			listingDetails = map[string]any{
				"id":              row.ID,
				"common_name":     comName,
				"scientific_name": sciName,
				"image_url":       row.ImageURL,
				"kind":            listingKind,
				"price":           priceDisp,
			}
		}
	} else {
		var row struct {
			ID                                 int
			CommonName                         *string
			Genus, Species, Subspecies, ImageURL string
			LoanFee                            *float64
			BreedingType                       string
		}
		query := `SELECT l.id, t.common_name, t.genus, t.species, t.subspecies, l.loan_fee as price, l.image_url, l.breeding_type
                  FROM breeding_listings l
                  JOIN taxa t ON l.species_lsid = t.species_lsid
                  WHERE l.id = $1 AND l.seller_id = $2`
		err = db.Pool.QueryRow(context.Background(), query, listingID, sellerID).Scan(&row.ID, &row.CommonName, &row.Genus, &row.Species, &row.Subspecies, &row.LoanFee, &row.ImageURL, &row.BreedingType)
		if err == nil {
			sciName := strings.TrimSpace(fmt.Sprintf("%s %s %s", row.Genus, row.Species, row.Subspecies))
			comName := sciName
			if row.CommonName != nil && *row.CommonName != "" {
				comName = *row.CommonName
			}
			priceDisp := "Seeking Partner"
			if row.BreedingType == "loan" {
				if row.LoanFee != nil {
					priceDisp = fmt.Sprintf("Stud Fee: R %.2f", *row.LoanFee)
				} else {
					priceDisp = "Stud Fee: Contact"
				}
			}
			listingDetails = map[string]any{
				"id":              row.ID,
				"common_name":     comName,
				"scientific_name": sciName,
				"image_url":       row.ImageURL,
				"kind":            listingKind,
				"price":           priceDisp,
			}
		}
	}

	if listingDetails == nil {
		utils.SendError(c, http.StatusNotFound, "Listing not found or owner mismatch", nil)
		return
	}

	u1, u2 := userID, sellerID
	if u1 > u2 {
		u1, u2 = u2, u1
	}

	var convID string
	err = db.Pool.QueryRow(context.Background(), "SELECT id FROM conversations WHERE user_a_id = $1 AND user_b_id = $2", u1, u2).Scan(&convID)
	if err != nil {
		err = db.Pool.QueryRow(context.Background(), "INSERT INTO conversations (user_a_id, user_b_id) VALUES ($1, $2) RETURNING id", u1, u2).Scan(&convID)
		if err != nil {
			utils.SendError(c, http.StatusInternalServerError, "Failed to create conversation", nil)
			return
		}
	}

	utils.SendSuccess(c, "Conversation retrieved", map[string]any{
		"conversation_id": convID,
		"listing_details": listingDetails,
		"other_user":      otherUser,
	})
}

func GetBackup(c *gin.Context) {
	userID, _ := c.Get("userID")

	var encryptedPrivateKey, privateKeyNonce, kdfSalt *string
	err := db.Pool.QueryRow(context.Background(), "SELECT encrypted_private_key, private_key_nonce, kdf_salt FROM users WHERE id = $1", userID).Scan(&encryptedPrivateKey, &privateKeyNonce, &kdfSalt)

	if err != nil {
		utils.SendError(c, http.StatusNotFound, "User material not found", nil)
		return
	}

	utils.SendSuccess(c, "Backup retrieved", map[string]any{
		"encrypted_private_key": encryptedPrivateKey,
		"private_key_nonce":     privateKeyNonce,
		"kdf_salt":              kdfSalt,
	})
}
