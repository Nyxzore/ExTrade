package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"net/http"
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
