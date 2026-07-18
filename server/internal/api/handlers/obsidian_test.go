package handlers

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/gin-gonic/gin"
)

func init() {
	gin.SetMode(gin.TestMode)
}

func setupObsidianTestDir(t *testing.T) func() {
	obsidianDir := "./Obsidian/ExTrade"
	// Ensure the directory exists
	if err := os.MkdirAll(obsidianDir, 0755); err != nil {
		t.Fatalf("Failed to create test directory: %v", err)
	}

	// Create some dummy notes
	notes := map[string]string{
		"A.md": "Link to [[B]] and [[C|Alias]].",
		"B.md": "Link back to [[A]].",
		"C.md": "Isolated note.",
	}

	for name, content := range notes {
		path := filepath.Join(obsidianDir, name)
		if err := os.WriteFile(path, []byte(content), 0644); err != nil {
			t.Fatalf("Failed to write test file %s: %v", name, err)
		}
	}

	return func() {
		os.RemoveAll("./Obsidian")
	}
}

func TestGetGraphData(t *testing.T) {
	cleanup := setupObsidianTestDir(t)
	defer cleanup()

	r := gin.New()
	r.GET("/obsidian/graph", GetGraphData)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/obsidian/graph", nil)
	r.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("Expected 200, got %d. Body: %s", w.Code, w.Body.String())
	}

	var resp struct {
		Nodes []map[string]any `json:"nodes"`
		Links []map[string]any `json:"links"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("Failed to unmarshal JSON: %v", err)
	}

	// We created A, B, C
	if len(resp.Nodes) != 3 {
		t.Errorf("Expected 3 nodes, got %d", len(resp.Nodes))
	}

	// Links: A -> B, A -> C (from [[C|Alias]]), B -> A
	if len(resp.Links) != 3 {
		t.Errorf("Expected 3 links, got %d", len(resp.Links))
	}
}

func TestGetNoteContent_PathTraversal(t *testing.T) {
	cleanup := setupObsidianTestDir(t)
	defer cleanup()

	r := gin.New()
	r.GET("/obsidian/note", GetNoteContent)

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/obsidian/note?note=../../main", nil)
	r.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound && w.Code != http.StatusBadRequest {
		t.Errorf("expected 404 or 400 for path traversal, got %d body=%s", w.Code, w.Body.String())
	}
}
