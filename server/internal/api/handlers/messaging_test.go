package handlers

import (
	"context"
	"encoding/json"
	"exotrade-server/internal/db"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

func init() {
	gin.SetMode(gin.TestMode)
	setupMockDB()
}

func TestSendMessage_MissingFields(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.POST("/messaging/send_message", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		SendMessage(c)
	})

	form := url.Values{}
	form.Set("conversation_id", "")
	form.Set("body", "")
	form.Set("nonce", "")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/messaging/send_message", strings.NewReader(form.Encode()))
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
	if body["message"] != "Missing required fields" {
		t.Fatalf("unexpected message: %v", body["message"])
	}
}

func TestSendMessage_Success(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.POST("/messaging/send_message", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		SendMessage(c)
	})

	db.Pool.(*MockPool).QueryRowFunc = func(ctx context.Context, sql string, args ...any) pgx.Row {
		if strings.Contains(sql, "EXISTS") {
			return &MockRow{
				ScanFunc: func(dest ...any) error {
					*dest[0].(*bool) = true
					return nil
				},
			}
		}
		if strings.Contains(sql, "INSERT INTO messages") {
			return &MockRow{
				ScanFunc: func(dest ...any) error {
					*dest[0].(*int64) = 123
					*dest[1].(*time.Time) = time.Now()
					return nil
				},
			}
		}
		return &MockRow{}
	}

	form := url.Values{}
	form.Set("conversation_id", "conv-1")
	form.Set("body", "encrypted-body")
	form.Set("nonce", "nonce-value")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/messaging/send_message", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}

	var body map[string]any
	json.Unmarshal(w.Body.Bytes(), &body)
	if body["status"] != "success" {
		t.Errorf("expected success status, got %v", body["status"])
	}
	if body["message_id"].(float64) != 123 {
		t.Errorf("expected message_id 123, got %v", body["message_id"])
	}
}
