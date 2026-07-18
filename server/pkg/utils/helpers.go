package utils

import (
	"fmt"
	"regexp"
)

func IsValidEmail(email string) bool {
	re := regexp.MustCompile(`^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,4}$`)
	return re.MatchString(email)
}

func FormatAge(days *int) string {
	if days == nil {
		return ""
	}
	d := *days
	if d < 30 {
		return fmt.Sprintf("%d days", d)
	}
	if d < 365 {
		months := float64(d) / 30.0
		if months < 1.5 {
			return "1 month"
		}
		return fmt.Sprintf("%.0f months", months)
	}
	years := float64(d) / 365.0
	if years < 1.05 {
		return "1 year"
	}
	return fmt.Sprintf("%.1f years", years)
}
