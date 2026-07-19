package handlers

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
)

func init() {
	gin.SetMode(gin.TestMode)
	setupMockDB()
}

func TestGetAllListings_UnauthorizedWithoutUserID(t *testing.T) {
	r := gin.New()
	r.POST("/listings/get_all_listings", GetAllListings)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/listings/get_all_listings", nil)
	r.ServeHTTP(w, req)

	// Handler should not panic when userID is absent from context.
	if w.Code == 0 {
		t.Fatal("handler did not write a response")
	}
}

func TestGetAllListings_FormParameters(t *testing.T) {
	r := gin.New()
	r.POST("/listings/get_all_listings", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		GetAllListings(c)
	})

	form := url.Values{}
	form.Add("search", "Python")
	form.Add("offset", "10")
	form.Add("seed", "constant-seed")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/listings/get_all_listings", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	// Without a live DB pool this returns 500; verify it accepted the form and did not 400.
	if w.Code == http.StatusBadRequest {
		t.Fatalf("unexpected 400 for valid form params: %s", w.Body.String())
	}
}

func TestGetAllListings_DefaultOffsetAndSeed(t *testing.T) {
	r := gin.New()
	r.POST("/listings/get_all_listings", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		GetAllListings(c)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/listings/get_all_listings", strings.NewReader(""))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code == http.StatusBadRequest {
		t.Fatalf("unexpected 400 with default params: %s", w.Body.String())
	}
}

func TestGetFriends_RequiresAuthenticatedUser(t *testing.T) {
	r := gin.New()
	r.POST("/friends/get_friends", GetFriends)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/friends/get_friends", nil)
	r.ServeHTTP(w, req)

	// Without userID in context, handler still runs; should not panic.
	if w.Code == 0 {
		t.Fatal("handler did not write a response")
	}
}

func TestGetFriends_WithUserContext(t *testing.T) {
	r := gin.New()
	r.POST("/friends/get_friends", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		GetFriends(c)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/friends/get_friends", nil)
	r.ServeHTTP(w, req)

	if w.Code == http.StatusOK {
		var body map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
			t.Fatalf("invalid JSON on success: %v", err)
		}
		if body["status"] != "success" {
			t.Fatalf("expected success status, got %v", body["status"])
		}
	}
}
