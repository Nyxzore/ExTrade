package handlers

import (
	"context"
	"encoding/json"
	"exotrade-server/internal/db"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"golang.org/x/crypto/bcrypt"
)

func init() {
	gin.SetMode(gin.TestMode)
	setupMockDB()
}

func TestAuthHandler_MissingLoginFields(t *testing.T) {
	r := gin.New()
	r.POST("/auth/auth", AuthHandler)

	form := url.Values{}
	form.Set("mode", "login")
	form.Set("username", "")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/auth/auth", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d body=%s", w.Code, w.Body.String())
	}

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["status"] != "error" {
		t.Fatalf("expected error status, got %v", body["status"])
	}
}

func TestAuthHandler_RegisterMissingEmail(t *testing.T) {
	r := gin.New()
	r.POST("/auth/auth", AuthHandler)

	form := url.Values{}
	form.Set("mode", "register")
	form.Set("username", "newuser")
	form.Set("password", "password123")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/auth/auth", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d body=%s", w.Code, w.Body.String())
	}
}

func TestAuthHandler_RegisterMissingE2EEKeys(t *testing.T) {
	r := gin.New()
	r.POST("/auth/auth", AuthHandler)

	form := url.Values{}
	form.Set("mode", "register")
	form.Set("username", "newuser")
	form.Set("email", "new@example.com")
	form.Set("password", "password123")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/auth/auth", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for missing E2EE keys, got %d body=%s", w.Code, w.Body.String())
	}
}

func TestAuthHandler_LoginBanned(t *testing.T) {
	r := gin.New()
	r.POST("/auth/auth", AuthHandler)

	db.Pool.(*MockPool).QueryRowFunc = func(ctx context.Context, sql string, args ...any) pgx.Row {
		return &MockRow{
			ScanFunc: func(dest ...any) error {
				*dest[0].(*string) = "banned-uuid"
				*dest[1].(*string) = "hash"
				*dest[2].(*bool) = true // isBanned
				*dest[3].(*bool) = false
				return nil
			},
		}
	}

	form := url.Values{}
	form.Set("mode", "login")
	form.Set("username", "banneduser")
	form.Set("password", "password")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/auth/auth", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for banned user, got %d", w.Code)
	}
}

func TestAuthHandler_LoginSuccess(t *testing.T) {
	r := gin.New()
	r.POST("/auth/auth", AuthHandler)

	pepper := "testpepper"
	os.Setenv("AUTH_PEPPER", pepper)
	hash, _ := bcrypt.GenerateFromPassword([]byte("password"+pepper), bcrypt.DefaultCost)

	db.Pool.(*MockPool).QueryRowFunc = func(ctx context.Context, sql string, args ...any) pgx.Row {
		if strings.Contains(sql, "SELECT id, password_hash") {
			return &MockRow{
				ScanFunc: func(dest ...any) error {
					*dest[0].(*string) = "user-uuid"
					*dest[1].(*string) = string(hash)
					*dest[2].(*bool) = false
					*dest[3].(*bool) = true // isAdmin
					return nil
				},
			}
		}
		return &MockRow{}
	}
	db.Pool.(*MockPool).ExecFunc = func(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error) {
		return pgconn.NewCommandTag("INSERT 1"), nil
	}

	form := url.Values{}
	form.Set("mode", "login")
	form.Set("username", "admin")
	form.Set("password", "password")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/auth/auth", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}

	var body map[string]any
	json.Unmarshal(w.Body.Bytes(), &body)
	if body["uuid"] != "user-uuid" {
		t.Errorf("expected user-uuid, got %v", body["uuid"])
	}
	if body["is_admin"] != true {
		t.Errorf("expected is_admin true, got %v", body["is_admin"])
	}
}
