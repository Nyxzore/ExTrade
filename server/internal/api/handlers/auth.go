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

func generateSession(userID string) (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	token := hex.EncodeToString(b)

	_, err := db.Pool.Exec(context.Background(), "INSERT INTO user_sessions (token, user_id) VALUES ($1, $2)", token, userID)
	return token, err
}

func AuthHandler(c *gin.Context) {
	username := c.PostForm("username")
	email := c.PostForm("email")
	password := c.PostForm("password")
	mode := c.DefaultPostForm("mode", "login")
	pepper := os.Getenv("AUTH_PEPPER")

	if username == "" || password == "" || (mode == "register" && email == "") {
		utils.SendError(c, http.StatusBadRequest, "Required fields missing", nil)
		return
	}

	if mode == "verify" {
		uuid := c.PostForm("uuid")
		token := c.PostForm("auth_token")

		if uuid == "" || token == "" {
			utils.SendError(c, http.StatusBadRequest, "Session material missing", nil)
			return
		}

		var isBanned bool
		query := `SELECT u.is_banned FROM user_sessions s
                  JOIN users u ON s.user_id = u.id
                  WHERE s.user_id = $1 AND s.token = $2 AND s.expires_at > NOW()`
		err := db.Pool.QueryRow(context.Background(), query, uuid, token).Scan(&isBanned)

		if err != nil {
			utils.SendError(c, http.StatusUnauthorized, "Invalid or expired session", nil)
			return
		}

		if isBanned {
			utils.SendError(c, http.StatusForbidden, "Your account has been banned.", nil)
			return
		}

		// Return basic info for verification
		var username string
		var isAdmin bool
		var tier int
		db.Pool.QueryRow(context.Background(), "SELECT username, is_admin, subscription_tier FROM users WHERE id = $1", uuid).
			Scan(&username, &isAdmin, &tier)

		utils.SendSuccess(c, "Session verified", map[string]any{
			"username":          username,
			"is_admin":          isAdmin,
			"subscription_tier": tier,
		})
		return
	}

	if mode == "login" {
		var id string
		var passwordHash string
		var isBanned bool
		var isAdmin bool

		err := db.Pool.QueryRow(context.Background(), "SELECT id, password_hash, is_banned, is_admin FROM users WHERE username = $1", username).
			Scan(&id, &passwordHash, &isBanned, &isAdmin)

		if err != nil {
			utils.SendError(c, http.StatusUnauthorized, "Invalid username or password", nil)
			return
		}

		if isBanned {
			utils.SendError(c, http.StatusForbidden, "Your account has been banned for violating community guidelines.", nil)
			return
		}

		if err := bcrypt.CompareHashAndPassword([]byte(passwordHash), []byte(password+pepper)); err != nil {
			utils.SendError(c, http.StatusUnauthorized, "Invalid username or password", nil)
			return
		}

		token, err := generateSession(id)
		if err != nil {
			utils.SendError(c, http.StatusInternalServerError, "Failed to create session", nil)
			return
		}

		utils.SendSuccess(c, "Login successful", map[string]any{
			"uuid":       id,
			"auth_token": token,
			"is_admin":   isAdmin,
		})
	} else {
		// Registration
		if !utils.IsValidEmail(email) {
			utils.SendError(c, http.StatusBadRequest, "Invalid email format", nil)
			return
		}

		valid, err := utils.VerifyEmailDomain(email)
		if err != nil || !valid {
			utils.SendError(c, http.StatusBadRequest, "Email domain is unreachable or invalid", nil)
			return
		}

		var exists bool
		db.Pool.QueryRow(context.Background(), "SELECT EXISTS(SELECT 1 FROM users WHERE username = $1 OR email = $2)", username, email).Scan(&exists)
		if exists {
			utils.SendError(c, http.StatusConflict, "Username or email already taken", nil)
			return
		}

		hashedPassword, _ := bcrypt.GenerateFromPassword([]byte(password+pepper), bcrypt.DefaultCost)
		profilePic, _ := utils.SaveBase64Image(c.PostForm("profile_picture_data"), "profile_pics")

		publicKey := c.PostForm("public_key")
		encPrivKey := c.PostForm("encrypted_private_key")
		privKeyNonce := c.PostForm("private_key_nonce")
		kdfSalt := c.PostForm("kdf_salt")

		if publicKey == "" || encPrivKey == "" {
			utils.SendError(c, http.StatusBadRequest, "E2EE key material missing", nil)
			return
		}

		var userID string
		query := `INSERT INTO users (username, email, password_hash, profile_picture, public_key, encrypted_private_key, private_key_nonce, kdf_salt)
                  VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id`
		err = db.Pool.QueryRow(context.Background(), query, username, email, string(hashedPassword), profilePic, publicKey, encPrivKey, privKeyNonce, kdfSalt).Scan(&userID)

		if err != nil {
			utils.SendError(c, http.StatusInternalServerError, "Registration failed", nil)
			return
		}

		token, _ := generateSession(userID)
		utils.SendSuccess(c, "Registration successful", map[string]any{
			"uuid":       userID,
			"auth_token": token,
		})
	}
}
