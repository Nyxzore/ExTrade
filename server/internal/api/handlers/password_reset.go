package handlers

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"exotrade-server/internal/db"
	"exotrade-server/pkg/utils"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

func generateResetToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func ForgotPasswordHandler(c *gin.Context) {
	email := c.PostForm("email")
	if email == "" {
		utils.SendError(c, http.StatusBadRequest, "Email is required", nil)
		return
	}

	var userID string
	err := db.Pool.QueryRow(context.Background(), "SELECT id FROM users WHERE email = $1", email).Scan(&userID)
	if err != nil {
		// We return success even if user doesn't exist for security (don't leak emails)
		utils.SendSuccess(c, "If an account exists with that email, a reset link has been sent.", nil)
		return
	}

	token, err := generateResetToken()
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to generate reset token", nil)
		return
	}

	_, err = db.Pool.Exec(context.Background(), "INSERT INTO password_resets (token, user_id) VALUES ($1, $2)", token, userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to store reset token", nil)
		return
	}

	err = utils.SendPasswordResetEmail(email, token)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to send reset email", nil)
		return
	}

	utils.SendSuccess(c, "If an account exists with that email, a reset link has been sent.", nil)
}

func ResetPasswordHandler(c *gin.Context) {
	token := c.PostForm("token")
	newPassword := c.PostForm("password")
	pepper := os.Getenv("AUTH_PEPPER")

	if token == "" || newPassword == "" {
		utils.SendError(c, http.StatusBadRequest, "Token and new password are required", nil)
		return
	}

	var userID string
	err := db.Pool.QueryRow(context.Background(), `
		SELECT user_id FROM password_resets
		WHERE token = $1 AND expires_at > NOW()`, token).Scan(&userID)

	if err != nil {
		utils.SendError(c, http.StatusUnauthorized, "Invalid or expired token", nil)
		return
	}

	hashedPassword, _ := bcrypt.GenerateFromPassword([]byte(newPassword+pepper), bcrypt.DefaultCost)

	// Transaction to update password and delete token
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Internal server error", nil)
		return
	}
	defer tx.Rollback(context.Background())

	_, err = tx.Exec(context.Background(), "UPDATE users SET password_hash = $1 WHERE id = $2", string(hashedPassword), userID)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to update password", nil)
		return
	}

	_, err = tx.Exec(context.Background(), "DELETE FROM password_resets WHERE token = $1", token)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to clean up token", nil)
		return
	}

	if err := tx.Commit(context.Background()); err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Failed to commit changes", nil)
		return
	}

	utils.SendSuccess(c, "Password reset successful. You can now log in with your new password.", nil)
}
