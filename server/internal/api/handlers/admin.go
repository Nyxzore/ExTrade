package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"fmt"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

func GetFlaggedItems(c *gin.Context) {
	query := `SELECT r.*, u.username as reporter_name
              FROM reports r
              JOIN users u ON r.reporter_id = u.id
              WHERE r.status = 'pending'
              ORDER BY r.created_at DESC`

	rows, err := db.Pool.Query(context.Background(), query)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch reports", nil)
		return
	}
	defer rows.Close()

	reports := []map[string]any{}
	for rows.Next() {
		var (
			id                                       int
			reporterID, targetType, targetID, reason string
			details, status, reporterName            string
			createdAt                                time.Time
		)
		if err := rows.Scan(&id, &reporterID, &targetType, &targetID, &reason, &details, &status, &createdAt, &reporterName); err == nil {
			reports = append(reports, map[string]any{
				"id":            id,
				"reporter_id":   reporterID,
				"target_type":   targetType,
				"target_id":     targetID,
				"reason":        reason,
				"details":       details,
				"status":        status,
				"created_at":    createdAt,
				"reporter_name": reporterName,
			})
		}
	}

	utils.SendSuccess(c, "Reports fetched", map[string]any{"reports": reports})
}

func ResolveReport(c *gin.Context) {
	reportID := c.PostForm("report_id")
	action := c.PostForm("action") // 'dismiss', 'delete', 'ban'

	if reportID == "" || action == "" {
		utils.SendError(c, http.StatusBadRequest, "Report ID and action are required", nil)
		return
	}

	var targetType, targetID string
	err := db.Pool.QueryRow(context.Background(), "SELECT target_type, target_id FROM reports WHERE id = $1", reportID).Scan(&targetType, &targetID)
	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Report not found", nil)
		return
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Transaction failed", nil)
		return
	}
	defer tx.Rollback(context.Background())

	if action == "delete" {
		if targetType == "listing" {
			tx.Exec(context.Background(), "DELETE FROM listings WHERE id = $1", targetID)
		} else if targetType == "breeding" {
			tx.Exec(context.Background(), "DELETE FROM breeding_listings WHERE id = $1", targetID)
		}
		tx.Exec(context.Background(), "UPDATE reports SET status = 'resolved' WHERE id = $1", reportID)
		tx.Commit(context.Background())
		utils.SendSuccess(c, "Item deleted and report resolved", nil)

	} else if action == "ban" {
		targetUserID := targetID
		if targetType != "user" {
			table := "listings"
			if targetType == "breeding" {
				table = "breeding_listings"
			}
			query := fmt.Sprintf("SELECT seller_id FROM %s WHERE id = $1", table)
			tx.QueryRow(context.Background(), query, targetID).Scan(&targetUserID)
		}
		tx.Exec(context.Background(), "UPDATE users SET is_banned = TRUE WHERE id = $1", targetUserID)
		tx.Exec(context.Background(), "UPDATE reports SET status = 'resolved' WHERE id = $1", reportID)
		tx.Commit(context.Background())
		utils.SendSuccess(c, "User banned and report resolved", nil)

	} else if action == "dismiss" {
		tx.Exec(context.Background(), "UPDATE reports SET status = 'dismissed' WHERE id = $1", reportID)
		tx.Commit(context.Background())
		utils.SendSuccess(c, "Report dismissed", nil)
	} else {
		utils.SendError(c, http.StatusBadRequest, "Invalid action", nil)
	}
}

func TakeDownListing(c *gin.Context) {
	listingID := c.PostForm("listing_id")
	reason := c.DefaultPostForm("reason", "Violation of community guidelines")
	kind := c.DefaultPostForm("kind", "sale") // 'sale' or 'breeding'

	if listingID == "" {
		utils.SendError(c, http.StatusBadRequest, "Listing ID required", nil)
		return
	}

	table := "listings"
	if kind == "breeding" {
		table = "breeding_listings"
	}

	var sellerID string
	query := fmt.Sprintf("SELECT seller_id FROM %s WHERE id = $1", table)
	err := db.Pool.QueryRow(context.Background(), query, listingID).Scan(&sellerID)
	if err != nil {
		utils.SendError(c, http.StatusNotFound, "Listing not found", nil)
		return
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Transaction failed", nil)
		return
	}
	defer tx.Rollback(context.Background())

	_, err = tx.Exec(context.Background(), fmt.Sprintf("UPDATE %s SET status = 'taken_down' WHERE id = $1", table), listingID)
	if err != nil {
		return
	}

	msg := "Your listing was taken down because: " + reason
	_, err = tx.Exec(context.Background(), "INSERT INTO admin_notifications (user_id, message) VALUES ($1, $2)", sellerID, msg)
	if err != nil {
		return
	}

	if err := tx.Commit(context.Background()); err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to take down listing", nil)
		return
	}

	utils.SendSuccess(c, "Listing taken down and user notified", nil)
}

func BanUser(c *gin.Context) {
	adminID, _ := c.Get("userID")
	targetUserID := c.PostForm("target_user_id")
	// reason := c.DefaultPostForm("reason", "Violation of community guidelines")

	if targetUserID == "" {
		utils.SendError(c, http.StatusBadRequest, "Target user ID required", nil)
		return
	}

	if targetUserID == adminID {
		utils.SendError(c, http.StatusBadRequest, "You cannot ban yourself", nil)
		return
	}

	_, err := db.Pool.Exec(context.Background(), "UPDATE users SET is_banned = TRUE WHERE id = $1", targetUserID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to ban user", nil)
		return
	}

	utils.SendSuccess(c, "User has been banned", nil)
}

func GetNotifications(c *gin.Context) {
	userID, _ := c.Get("userID")

	query := "SELECT id, message, created_at FROM admin_notifications WHERE user_id = $1 AND is_read = FALSE ORDER BY created_at DESC"
	rows, err := db.Pool.Query(context.Background(), query, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch notifications", nil)
		return
	}
	defer rows.Close()

	notifications := []map[string]any{}
	for rows.Next() {
		var id int
		var message string
		var createdAt time.Time
		if err := rows.Scan(&id, &message, &createdAt); err == nil {
			notifications = append(notifications, map[string]any{
				"id":         id,
				"message":    message,
				"created_at": createdAt,
			})
		}
	}

	if len(notifications) > 0 {
		db.Pool.Exec(context.Background(), "UPDATE admin_notifications SET is_read = TRUE WHERE user_id = $1", userID)
	}

	utils.SendSuccess(c, "Notifications fetched", map[string]any{"notifications": notifications})
}
