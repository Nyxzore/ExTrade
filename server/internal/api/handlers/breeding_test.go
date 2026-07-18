package handlers

import (
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

func TestGetBreedingListings_Filters(t *testing.T) {
	r := gin.New()
	r.POST("/breeding/get_breeding_listings.php", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		GetBreedingListings(c)
	})

	form := url.Values{}
	form.Add("breeding_type", "loan")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/breeding/get_breeding_listings.php", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code == http.StatusBadRequest {
		t.Errorf("unexpected 400 for valid breeding_type filter: %s", w.Body.String())
	}
}

func TestGetBreedingListings_DefaultPagination(t *testing.T) {
	r := gin.New()
	r.POST("/breeding/get_breeding_listings.php", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		GetBreedingListings(c)
	})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/breeding/get_breeding_listings.php", strings.NewReader(""))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code == http.StatusBadRequest {
		t.Errorf("unexpected 400 with default params: %s", w.Body.String())
	}
}

func TestGetBreedingListings_OffsetParam(t *testing.T) {
	r := gin.New()
	r.POST("/breeding/get_breeding_listings.php", func(c *gin.Context) {
		c.Set("userID", "test-user-uuid")
		GetBreedingListings(c)
	})

	form := url.Values{}
	form.Add("offset", "20")
	form.Add("seed", "breeding-seed")

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/breeding/get_breeding_listings.php", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	r.ServeHTTP(w, req)

	if w.Code == http.StatusBadRequest {
		t.Errorf("unexpected 400 for offset/seed params: %s", w.Body.String())
	}
}
