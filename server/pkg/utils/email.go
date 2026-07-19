package utils

import (
	"fmt"
	"log"
	"net/smtp"
	"os"
)

func SendPasswordResetEmail(targetEmail, token string) error {
	host := os.Getenv("SMTP_HOST")
	port := os.Getenv("SMTP_PORT")
	user := os.Getenv("SMTP_USER")
	pass := os.Getenv("SMTP_PASS")
	from := os.Getenv("SMTP_FROM")

	resetLink := fmt.Sprintf("https://exotrade.co.za/reset-password?token=%s", token)
	subject := "Subject: ExoTrade Password Reset\n"
	body := fmt.Sprintf("Hello,\n\nYou requested a password reset for your ExoTrade account.\n\nPlease follow this link to reset your password:\n%s\n\nIf you did not request this, you can safely ignore this email.\n\nBest regards,\nThe ExoTrade Team", resetLink)
	message := []byte(subject + "\n" + body)

	// Fallback for development/testing
	if host == "" || user == "" || pass == "" {
		log.Printf("\n--- DEVELOPMENT MODE: EMAIL LOG ---\nTo: %s\n%s\n-----------------------------------\n", targetEmail, string(message))
		return nil
	}

	auth := smtp.PlainAuth("", user, pass, host)
	err := smtp.SendMail(host+":"+port, auth, from, []string{targetEmail}, message)
	if err != nil {
		log.Printf("Failed to send email: %v", err)
		return err
	}

	return nil
}
