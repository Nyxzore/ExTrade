package middleware

import (
	"context"
	"encoding/json"
	"exotrade-server/internal/db"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

func init() {
	gin.SetMode(gin.TestMode)
	setupMockDB()
}

func TestAuthRequired_MissingCredentials(t *testing.T) {
	r := gin.New()
	r.POST("/protected", AuthRequired(), func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/protected", nil)
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Code)
	}
	assertErrorJSON(t, w.Body.Bytes(), "Authentication required", "force_logout")
}

func TestAuthRequired_MissingToken(t *testing.T) {
	r := gin.New()
	r.POST("/protected", AuthRequired(), func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})

	form := url.Values{}
	form.Set("user_id", "user-uuid")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/protected", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestAuthRequired_AcceptsQueryParams(t *testing.T) {
	r := gin.New()
	r.GET("/protected", AuthRequired(), func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/protected?user_id=uuid&auth_token=token", nil)
	r.ServeHTTP(w, req)

	// Without a live DB this should fail session validation, not missing-auth.
	if w.Code == http.StatusUnauthorized {
		var body map[string]any
		_ = json.Unmarshal(w.Body.Bytes(), &body)
		if body["message"] == "Authentication required" {
			t.Fatal("expected query params to be read, got missing-auth error")
		}
	}
}

func TestAdminRequired_NoUserInContext(t *testing.T) {
	r := gin.New()
	r.GET("/admin", AdminRequired(), func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/admin", nil)
	r.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestAuthRequired_ValidSession(t *testing.T) {
	r := gin.New()
	r.POST("/protected", AuthRequired(), func(c *gin.Context) {
		uid, _ := c.Get("userID")
		c.JSON(http.StatusOK, gin.H{"ok": true, "userID": uid})
	})

	db.Pool.(*MockPool).QueryRowFunc = func(ctx context.Context, sql string, args ...any) pgx.Row {
		return &MockRow{
			ScanFunc: func(dest ...any) error {
				*dest[0].(*bool) = false // isBanned
				return nil
			},
		}
	}

	form := url.Values{}
	form.Set("user_id", "valid-user-uuid")
	form.Set("auth_token", "valid-token")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/protected", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}

	var body map[string]any
	json.Unmarshal(w.Body.Bytes(), &body)
	if body["userID"] != "valid-user-uuid" {
		t.Errorf("expected userID valid-user-uuid, got %v", body["userID"])
	}
}

func assertErrorJSON(t *testing.T, raw []byte, message string, extraKey string) {
	t.Helper()
	var body map[string]any
	if err := json.Unmarshal(raw, &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["status"] != "error" {
		t.Fatalf("expected error status, got %v", body["status"])
	}
	if body["message"] != message {
		t.Fatalf("expected message %q, got %v", message, body["message"])
	}
	if extraKey != "" {
		if _, ok := body[extraKey]; !ok {
			t.Fatalf("expected extra key %q in response", extraKey)
		}
	}
}
