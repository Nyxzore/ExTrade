package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"net/http"

	"github.com/gin-gonic/gin"
)

// friendshipStatusBetween returns none | pending_sent | pending_received | friends | self
func friendshipStatusBetween(viewerID, targetID string) string {
	if viewerID == "" || targetID == "" {
		return "none"
	}
	if viewerID == targetID {
		return "self"
	}
	u1, u2 := viewerID, targetID
	if u1 > u2 {
		u1, u2 = u2, u1
	}
	var status, actionUserID string
	err := db.Pool.QueryRow(context.Background(),
		"SELECT status, action_user_id::text FROM friendships WHERE user_id1 = $1 AND user_id2 = $2",
		u1, u2,
	).Scan(&status, &actionUserID)
	if err != nil {
		return "none"
	}
	if status == "accepted" {
		return "friends"
	}
	if actionUserID == viewerID {
		return "pending_sent"
	}
	return "pending_received"
}

func GetFriends(c *gin.Context) {
	userID, _ := c.Get("userID")

	query := `SELECT u.id, u.username, u.profile_picture, COALESCE(u.subscription_tier, 0)
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
		if err := rows.Scan(&id, &username, &profilePic, &tier); err != nil {
			continue
		}
		friends = append(friends, map[string]any{
			"id":                id,
			"username":          username,
			"profile_pic":       profilePic,
			"subscription_tier": tier,
		})
	}

	utils.SendSuccess(c, "Friends fetched", map[string]any{"friends": friends})
}

func SendFriendRequest(c *gin.Context) {
	userIDVal, _ := c.Get("userID")
	userID, _ := userIDVal.(string)
	friendID := c.PostForm("target_user_id")

	if friendID == "" {
		utils.SendError(c, http.StatusBadRequest, "Target user ID required", nil)
		return
	}

	if userID == "" || userID == friendID {
		utils.SendError(c, http.StatusBadRequest, "Cannot add yourself as a friend", nil)
		return
	}

	u1, u2 := userID, friendID
	if u1 > u2 {
		u1, u2 = u2, u1
	}

	// Check if already friends or request pending
	var status, actionUserID string
	err := db.Pool.QueryRow(context.Background(), "SELECT status, action_user_id FROM friendships WHERE user_id1 = $1 AND user_id2 = $2", u1, u2).Scan(&status, &actionUserID)

	if err == nil {
		if status == "accepted" {
			utils.SendError(c, http.StatusBadRequest, "Already friends", nil)
			return
		}
		if actionUserID == userID {
			utils.SendError(c, http.StatusBadRequest, "Friend request already sent", nil)
			return
		}
		// The other person already sent a request, so let's just accept it
		_, err = db.Pool.Exec(context.Background(), "UPDATE friendships SET status = 'accepted', action_user_id = $1 WHERE user_id1 = $2 AND user_id2 = $3", userID, u1, u2)
		if err != nil {
			utils.SendError(c, http.StatusInternalServerError, "Failed to accept friend request", nil)
			return
		}
		utils.SendSuccess(c, "Friend request accepted (they sent one too!)", nil)
		return
	}

	_, err = db.Pool.Exec(context.Background(), "INSERT INTO friendships (user_id1, user_id2, status, action_user_id) VALUES ($1, $2, 'pending', $3)", u1, u2, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to send friend request", nil)
		return
	}

	utils.SendSuccess(c, "Friend request sent", nil)
}

func AcceptFriendRequest(c *gin.Context) {
	userID, _ := c.Get("userID")
	requesterID := c.PostForm("requester_id")

	if requesterID == "" {
		utils.SendError(c, http.StatusBadRequest, "Requester ID required", nil)
		return
	}

	u1, u2 := userID.(string), requesterID
	if u1 > u2 {
		u1, u2 = u2, u1
	}

	res, err := db.Pool.Exec(context.Background(),
		"UPDATE friendships SET status = 'accepted', action_user_id = $1 WHERE user_id1 = $2 AND user_id2 = $3 AND status = 'pending' AND action_user_id = $4",
		userID, u1, u2, requesterID)

	if err != nil || res.RowsAffected() == 0 {
		utils.SendError(c, http.StatusBadRequest, "Request not found or already handled", nil)
		return
	}

	utils.SendSuccess(c, "Friend request accepted", nil)
}

func DeclineFriendRequest(c *gin.Context) {
	userID, _ := c.Get("userID")
	requesterID := c.PostForm("requester_id")

	if requesterID == "" {
		utils.SendError(c, http.StatusBadRequest, "Requester ID required", nil)
		return
	}

	u1, u2 := userID.(string), requesterID
	if u1 > u2 {
		u1, u2 = u2, u1
	}

	res, err := db.Pool.Exec(context.Background(),
		"DELETE FROM friendships WHERE user_id1 = $1 AND user_id2 = $2 AND status = 'pending' AND action_user_id = $3",
		u1, u2, requesterID)

	if err != nil || res.RowsAffected() == 0 {
		utils.SendError(c, http.StatusBadRequest, "Request not found or already handled", nil)
		return
	}

	utils.SendSuccess(c, "Friend request declined", nil)
}

func RemoveFriend(c *gin.Context) {
	userID, _ := c.Get("userID")
	friendID := c.PostForm("friend_id")

	if friendID == "" {
		utils.SendError(c, http.StatusBadRequest, "Friend ID required", nil)
		return
	}

	u1, u2 := userID.(string), friendID
	if u1 > u2 {
		u1, u2 = u2, u1
	}

	res, err := db.Pool.Exec(context.Background(), "DELETE FROM friendships WHERE user_id1 = $1 AND user_id2 = $2 AND status = 'accepted'", u1, u2)

	if err != nil || res.RowsAffected() == 0 {
		utils.SendError(c, http.StatusBadRequest, "Friendship not found", nil)
		return
	}

	utils.SendSuccess(c, "Friend removed", nil)
}

func GetFriendRequests(c *gin.Context) {
	userID, _ := c.Get("userID")

	query := `SELECT u.id, u.username, u.profile_picture, COALESCE(u.subscription_tier, 0)
              FROM friendships f
              JOIN users u ON (f.user_id1 = u.id OR f.user_id2 = u.id)
              WHERE ((f.user_id1 = $1 OR f.user_id2 = $1) AND u.id != $1)
                AND f.status = 'pending'
                AND f.action_user_id != $1`

	rows, err := db.Pool.Query(context.Background(), query, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch requests", nil)
		return
	}
	defer rows.Close()

	requests := []map[string]any{}
	for rows.Next() {
		var id, username string
		var profilePic *string
		var tier int
		if err := rows.Scan(&id, &username, &profilePic, &tier); err != nil {
			continue
		}
		requests = append(requests, map[string]any{
			"id":                id,
			"username":          username,
			"profile_pic":       profilePic,
			"subscription_tier": tier,
		})
	}

	utils.SendSuccess(c, "Requests fetched", map[string]any{"requests": requests})
}

func SearchUsers(c *gin.Context) {
	userID, _ := c.Get("userID")
	query := c.PostForm("query")

	if query == "" {
		utils.SendSuccess(c, "Results", map[string]any{"users": []any{}})
		return
	}

	sql := `SELECT u.id, u.username, u.profile_picture, COALESCE(u.subscription_tier, 0),
                   f.status, f.action_user_id::text
            FROM users u
            LEFT JOIN friendships f ON (
                (f.user_id1 = u.id AND f.user_id2 = $2) OR
                (f.user_id2 = u.id AND f.user_id1 = $2)
            )
            WHERE (u.username ILIKE $1 OR u.username % $3) AND u.id != $2
            ORDER BY similarity(u.username, $3) DESC, COALESCE(u.subscription_tier, 0) DESC
            LIMIT 20`

	rows, err := db.Pool.Query(context.Background(), sql, "%"+query+"%", userID, query)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to search", nil)
		return
	}
	defer rows.Close()

	users := []map[string]any{}
	for rows.Next() {
		var id, username string
		var profilePic *string
		var tier int
		var status, actionUserID *string
		if err := rows.Scan(&id, &username, &profilePic, &tier, &status, &actionUserID); err != nil {
			continue
		}
		viewerID, _ := userID.(string)
		friendshipStatus := "none"
		if status != nil {
			switch *status {
			case "accepted":
				friendshipStatus = "friends"
			case "pending":
				if actionUserID != nil && *actionUserID == viewerID {
					friendshipStatus = "pending_sent"
				} else {
					friendshipStatus = "pending_received"
				}
			}
		}
		users = append(users, map[string]any{
			"id":                id,
			"username":          username,
			"profile_pic":       profilePic,
			"subscription_tier": tier,
			"friendship_status": friendshipStatus,
			"is_friend":         friendshipStatus == "friends",
		})
	}

	utils.SendSuccess(c, "Results", map[string]any{"users": users})
}
