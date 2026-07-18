package utils

import (
	"encoding/base64"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/google/uuid"
)

func SaveBase64Image(base64Str string, subfolder string) (string, error) {
	if base64Str == "" {
		return "", nil
	}

	// Basic size check (approx 5MB)
	if len(base64Str) > 7000000 {
		return "", fmt.Errorf("image too large")
	}

	// Remove header if present (e.g., data:image/jpeg;base64,)
	idx := strings.Index(base64Str, ",")
	if idx != -1 {
		base64Str = base64Str[idx+1:]
	}

	data, err := base64.StdEncoding.DecodeString(base64Str)
	if err != nil {
		return "", err
	}

	// Detect mime type
	mimeType := http.DetectContentType(data)
	var ext string
	switch mimeType {
	case "image/jpeg":
		ext = ".jpg"
	case "image/png":
		ext = ".png"
	case "image/gif":
		ext = ".gif"
	default:
		return "", fmt.Errorf("unsupported image type: %s", mimeType)
	}

	filename := uuid.New().String() + ext
	dir := filepath.Join(".", subfolder)

	if err := os.MkdirAll(dir, 0755); err != nil {
		return "", err
	}

	path := filepath.Join(dir, filename)
	if err := os.WriteFile(path, data, 0644); err != nil {
		return "", err
	}

	return filepath.Join(subfolder, filename), nil
}
