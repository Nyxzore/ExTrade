package middleware

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

func AppVersionCheck() gin.HandlerFunc {
	return func(c *gin.Context) {
		clientVersionStr := c.DefaultPostForm("app_version", c.Query("app_version"))
		if clientVersionStr == "" {
			c.Next()
			return
		}

		clientVersion, _ := strconv.Atoi(clientVersionStr)

		var minVersion int
		err := db.Pool.QueryRow(context.Background(), "SELECT version FROM versions WHERE object = 'app_min_version'").Scan(&minVersion)
		if err == nil {
			if clientVersion < minVersion {
				utils.SendError(c, http.StatusForbidden, "Please update the app to continue.", map[string]any{"update_required": true})
				c.Abort()
				return
			}
		}
		c.Next()
	}
}

func AuthRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID := c.DefaultPostForm("user_id", c.Query("user_id"))
		token := c.DefaultPostForm("auth_token", c.Query("auth_token"))

		if userID == "" || token == "" {
			utils.SendError(c, http.StatusUnauthorized, "Authentication required", map[string]any{"force_logout": true})
			c.Abort()
			return
		}

		var isBanned bool
		query := `SELECT u.is_banned FROM user_sessions s
                  JOIN users u ON s.user_id = u.id
                  WHERE s.user_id = $1 AND s.token = $2 AND s.expires_at > NOW()`
		err := db.Pool.QueryRow(context.Background(), query, userID, token).Scan(&isBanned)

		if err != nil {
			utils.SendError(c, http.StatusUnauthorized, "Invalid or expired session", map[string]any{"force_logout": true})
			c.Abort()
			return
		}

		if isBanned {
			utils.SendError(c, http.StatusForbidden, "Your account has been banned for violating community guidelines.", map[string]any{"force_logout": true})
			c.Abort()
			return
		}

		c.Set("userID", userID)
		c.Next()
	}
}

func AdminRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		userID, exists := c.Get("userID")
		if !exists {
			utils.SendError(c, http.StatusUnauthorized, "Authentication required", nil)
			c.Abort()
			return
		}

		var isAdmin bool
		err := db.Pool.QueryRow(context.Background(), "SELECT is_admin FROM users WHERE id = $1", userID).Scan(&isAdmin)
		if err != nil || !isAdmin {
			utils.SendError(c, http.StatusForbidden, "Unauthorized: Admin access required", nil)
			c.Abort()
			return
		}

		c.Next()
	}
}
