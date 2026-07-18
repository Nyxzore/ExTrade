package utils

import (
	"encoding/base64"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSaveBase64Image_EmptyInput(t *testing.T) {
	path, err := SaveBase64Image("", "test_images")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if path != "" {
		t.Fatalf("expected empty path, got %q", path)
	}
}

func TestSaveBase64Image_InvalidBase64(t *testing.T) {
	_, err := SaveBase64Image("not-valid-base64!!!", "test_images")
	if err == nil {
		t.Fatal("expected error for invalid base64")
	}
}

func TestSaveBase64Image_TooLarge(t *testing.T) {
	_, err := SaveBase64Image(strings.Repeat("A", 7000001), "test_images")
	if err == nil {
		t.Fatal("expected error for oversized payload")
	}
}

func TestSaveBase64Image_ValidPNG(t *testing.T) {
	dir := filepath.Join(".", "test_images")
	defer os.RemoveAll(dir)

	// Minimal valid 1x1 PNG
	pngBytes := []byte{
		0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
		0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
		0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
		0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4,
		0x89, 0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41,
		0x54, 0x78, 0x9c, 0x63, 0x00, 0x01, 0x00, 0x00,
		0x05, 0x00, 0x01, 0x0d, 0x0a, 0x2d, 0xb4, 0x00,
		0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, 0xae,
		0x42, 0x60, 0x82,
	}
	encoded := base64.StdEncoding.EncodeToString(pngBytes)
	withHeader := "data:image/png;base64," + encoded

	path, err := SaveBase64Image(withHeader, "test_images")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.HasPrefix(path, "test_images/") {
		t.Fatalf("unexpected path: %q", path)
	}
	if !strings.HasSuffix(path, ".png") {
		t.Fatalf("expected .png extension, got %q", path)
	}
	if _, err := os.Stat(path); err != nil {
		t.Fatalf("saved file missing: %v", err)
	}
}
