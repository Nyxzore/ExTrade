package handlers

import (
	"context"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"net/http"

	"github.com/gin-gonic/gin"
)

func GetVersions(c *gin.Context) {
	rows, err := db.Pool.Query(context.Background(), "SELECT object, version FROM versions")
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to fetch versions", nil)
		return
	}
	defer rows.Close()

	versions := make(map[string]any)
	for rows.Next() {
		var object string
		var version any
		if err := rows.Scan(&object, &version); err != nil {
			continue
		}
		versions[object] = version
	}

	utils.SendSuccess(c, "Versions fetched", map[string]any{
		"versions": versions,
	})
}

func ReportItem(c *gin.Context) {
	userID, _ := c.Get("userID")

	targetType := c.PostForm("target_type")
	targetID := c.PostForm("target_id")
	reason := c.PostForm("reason")
	details := c.DefaultPostForm("details", "")

	if targetType == "" || targetID == "" || reason == "" {
		utils.SendError(c, http.StatusBadRequest, "Target type, ID, and reason are required", nil)
		return
	}

	allowedTypes := map[string]bool{
		"listing":  true,
		"breeding": true,
		"user":     true,
	}

	if !allowedTypes[targetType] {
		utils.SendError(c, http.StatusBadRequest, "Invalid target type", nil)
		return
	}

	query := "INSERT INTO reports (reporter_id, target_type, target_id, reason, details) VALUES ($1, $2, $3, $4, $5)"
	_, err := db.Pool.Exec(context.Background(), query, userID, targetType, targetID, reason, details)

	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to submit report", nil)
		return
	}

	utils.SendSuccess(c, "Thank you for your report. We will review it shortly.", nil)
}
