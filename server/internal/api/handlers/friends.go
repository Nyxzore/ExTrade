package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"net/http"

	"github.com/gin-gonic/gin"
)

func GetFriends(c *gin.Context) {
	userID, _ := c.Get("userID")

	query := `SELECT u.id, u.username, u.profile_picture, u.subscription_tier
              FROM friendships f
              JOIN users u ON (f.user_id1 = $1 AND f.user_id2 = u.id)
                           OR (f.user_id2 = $1 AND f.user_id1 = u.id)
              WHERE f.status = 'accepted'`

	rows, err := db.Pool.Query(context.Background(), query, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch friends", nil)
		return
	}
	defer rows.Close()

	friends := []map[string]any{}
	for rows.Next() {
		var id, username string
		var profilePic *string
		var tier int
		if err := rows.Scan(&id, &username, &profilePic, &tier); err == nil {
			friends = append(friends, map[string]any{
				"id":                id,
				"username":          username,
				"profile_pic":       profilePic,
				"subscription_tier": tier,
			})
		}
	}

	utils.SendSuccess(c, "Friends fetched", map[string]any{"friends": friends})
}
