package handlers

import (
	"exotrade-server/pkg/utils"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/gin-gonic/gin"
)

func GetGraphData(c *gin.Context) {
	obsidianDir := "./Obsidian/ExTrade"
	files, err := os.ReadDir(obsidianDir)
	if err != nil {
		utils.SendError(c, http.StatusInternalServerError, "Could not read Obsidian directory", nil)
		return
	}

	nodes := []map[string]any{}
	links := []map[string]any{}
	fileToId := make(map[string]bool)

	for _, file := range files {
		if !file.IsDir() && strings.HasSuffix(file.Name(), ".md") {
			name := strings.TrimSuffix(file.Name(), ".md")
			nodes = append(nodes, map[string]any{
				"id":    name,
				"label": name,
				"group": "note",
			})
			fileToId[name] = true
		}
	}

	re := regexp.MustCompile(`\[\[(.*?)\]\]`)

	for _, file := range files {
		if !file.IsDir() && strings.HasSuffix(file.Name(), ".md") {
			source := strings.TrimSuffix(file.Name(), ".md")
			content, _ := os.ReadFile(filepath.Join(obsidianDir, file.Name()))

			matches := re.FindAllStringSubmatch(string(content), -1)
			for _, match := range matches {
				parts := strings.Split(match[1], "|")
				target := strings.TrimSpace(parts[0])
				if fileToId[target] {
					links = append(links, map[string]any{
						"source": source,
						"target": target,
					})
				}
			}
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"nodes": nodes,
		"links": links,
	})
}

func GetNoteContent(c *gin.Context) {
	note := c.Query("note")
	if note == "" {
		c.String(http.StatusBadRequest, "No note specified")
		return
	}

	// Security: Prevent directory traversal
	note = filepath.Base(note)
	path := filepath.Join("./Obsidian/ExTrade", note+".md")

	content, err := os.ReadFile(path)
	if err != nil {
		c.String(http.StatusNotFound, "Note not found")
		return
	}

	c.String(http.StatusOK, string(content))
}
