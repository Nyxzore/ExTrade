package main

import (
	"exotrade-server/internal/api/handlers"
	"exotrade-server/internal/api/middleware"
	"exotrade-server/internal/db"
	"log"
	"os"

	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
)

func main() {
	// Load environment variables
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, using system environment variables")
	}

	// Initialize Database
	if err := db.InitDB(); err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer db.CloseDB()

	r := gin.Default()

	// Static files for images and downloads
	r.Static("/listings", "./listings")
	r.Static("/profile_pics", "./profile_pics")
	r.Static("/downloads", "./downloads")
	r.StaticFile("/exotrade-api-docs.html", "./exotrade-api-docs.html")

	// API Routes
	api := r.Group("/")
	{
		// Obsidian Graph (Public for now)
		api.GET("/get_graph_data.php", handlers.GetGraphData)
		api.GET("/get_note_content.php", handlers.GetNoteContent)

		// Auth
		api.POST("/auth/auth.php", handlers.AuthHandler)

		// Protected Routes
		protected := api.Group("/")
		protected.Use(middleware.AppVersionCheck())
		protected.Use(middleware.AuthRequired())
		{
			protected.POST("/listings/get_all_listings.php", handlers.GetAllListings)
			protected.POST("/breeding/get_breeding_listings.php", handlers.GetBreedingListings)
			protected.GET("/friends/get_friends.php", handlers.GetFriends)
			protected.POST("/messaging/send_message.php", handlers.SendMessage)
			// Add more handlers here as they are ported...
		}
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("Server starting on port %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
