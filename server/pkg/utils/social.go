package utils

import (
	"net/url"
	"regexp"
	"strings"
)

func NormalizeWhatsApp(phone string) string {
	phone = strings.TrimSpace(phone)
	if phone == "" {
		return ""
	}

	re := regexp.MustCompile("[^0-9]")
	digits := re.ReplaceAllString(phone, "")
	if digits == "" {
		return ""
	}

	// South African local format: 0821234567 -> 27821234567
	if len(digits) == 10 && digits[0] == '0' {
		digits = "27" + digits[1:]
	}

	if len(digits) < 10 || len(digits) > 15 {
		return ""
	}

	return digits
}

func NormalizeFacebook(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}

	value = strings.TrimLeft(value, "@")

	if strings.HasPrefix(strings.ToLower(value), "http") ||
		strings.Contains(strings.ToLower(value), "facebook.com") ||
		strings.Contains(strings.ToLower(value), "fb.com") {

		if !strings.HasPrefix(strings.ToLower(value), "http") {
			value = "https://" + strings.TrimLeft(value, "/")
		}

		u, err := url.Parse(value)
		if err != nil {
			return ""
		}

		path := strings.Trim(u.Path, "/")
		if path == "" {
			return ""
		}

		if strings.HasPrefix(path, "profile.php") {
			q := u.Query()
			id := q.Get("id")
			if id != "" {
				return "profile.php?id=" + id
			}
			return ""
		}

		segments := strings.Split(path, "/")
		if segments[0] == "people" && len(segments) >= 2 {
			return segments[1]
		}
		if segments[0] == "pages" && len(segments) >= 2 {
			return segments[1]
		}

		if segments[0] != "" {
			return segments[0]
		}
		return ""
	}

	re := regexp.MustCompile(`^[A-Za-z0-9.]{1,100}$`)
	if !re.MatchString(value) {
		return ""
	}

	return value
}

func NormalizeInstagram(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}

	value = strings.TrimLeft(value, "@")

	if strings.HasPrefix(strings.ToLower(value), "http") ||
		strings.Contains(strings.ToLower(value), "instagram.com") {

		if !strings.HasPrefix(strings.ToLower(value), "http") {
			value = "https://" + strings.TrimLeft(value, "/")
		}

		u, err := url.Parse(value)
		if err != nil {
			return ""
		}

		path := strings.Trim(u.Path, "/")
		if path == "" {
			return ""
		}

		segments := strings.Split(path, "/")
		handle := segments[0]

		restricted := map[string]bool{
			"p":       true,
			"reel":    true,
			"stories": true,
			"explore": true,
		}

		if restricted[handle] {
			return ""
		}

		if handle != "" {
			return handle
		}
		return ""
	}

	// Remove fragment or query
	if idx := strings.IndexAny(value, "?#"); idx != -1 {
		value = value[:idx]
	}

	// If it contains slashes, take the first part
	if idx := strings.Index(value, "/"); idx != -1 {
		value = value[:idx]
	}

	re := regexp.MustCompile(`^[A-Za-z0-9._]{1,30}$`)
	if !re.MatchString(value) {
		return ""
	}

	return value
}

type SocialFields struct {
	WhatsApp  string
	Facebook  string
	Instagram string
}

func SanitizeSocialFields(whatsapp, facebook, instagram string) (*SocialFields, string) {
	normWhatsApp := NormalizeWhatsApp(whatsapp)
	if whatsapp != "" && normWhatsApp == "" {
		return nil, "Invalid WhatsApp number"
	}

	normFacebook := NormalizeFacebook(facebook)
	if facebook != "" && normFacebook == "" {
		return nil, "Invalid Facebook profile"
	}

	normInstagram := NormalizeInstagram(instagram)
	if instagram != "" && normInstagram == "" {
		return nil, "Invalid Instagram handle"
	}

	return &SocialFields{
		WhatsApp:  normWhatsApp,
		Facebook:  normFacebook,
		Instagram: normInstagram,
	}, ""
}
