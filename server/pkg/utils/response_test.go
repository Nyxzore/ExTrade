package utils

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func init() {
	gin.SetMode(gin.TestMode)
}

func TestSendError_IncludesStatusAndMessage(t *testing.T) {
	w := httptest.NewRecorder()
	c, _ := gin.CreateTestContext(w)

	SendError(c, http.StatusUnauthorized, "Authentication required", map[string]any{"force_logout": true})

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected status %d, got %d", http.StatusUnauthorized, w.Code)
	}

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["status"] != "error" {
		t.Fatalf("expected status error, got %v", body["status"])
	}
	if body["message"] != "Authentication required" {
		t.Fatalf("unexpected message: %v", body["message"])
	}
	if body["force_logout"] != true {
		t.Fatalf("expected force_logout true, got %v", body["force_logout"])
	}
}

func TestSendSuccess_IncludesExtraFields(t *testing.T) {
	w := httptest.NewRecorder()
	c, _ := gin.CreateTestContext(w)

	SendSuccess(c, "Login successful", map[string]any{
		"uuid":       "user-1",
		"auth_token": "token-abc",
	})

	if w.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", w.Code)
	}

	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["status"] != "success" {
		t.Fatalf("expected status success, got %v", body["status"])
	}
	if body["uuid"] != "user-1" {
		t.Fatalf("expected uuid in response, got %v", body["uuid"])
	}
	if body["auth_token"] != "token-abc" {
		t.Fatalf("expected auth_token in response, got %v", body["auth_token"])
	}
}
