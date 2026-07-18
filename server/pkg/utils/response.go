package utils

import (
	"github.com/gin-gonic/gin"
)

type Response struct {
	Status  string         `json:"status"`
	Message string         `json:"message"`
	Data    map[string]any `json:"data,omitempty"`
}

func SendResponse(c *gin.Context, httpStatus int, status string, message string, extra map[string]any) {
	resp := gin.H{
		"status":  status,
		"message": message,
	}
	for k, v := range extra {
		resp[k] = v
	}
	c.JSON(httpStatus, resp)
}

func SendError(c *gin.Context, httpStatus int, message string, extra map[string]any) {
	SendResponse(c, httpStatus, "error", message, extra)
}

func SendSuccess(c *gin.Context, message string, extra map[string]any) {
	SendResponse(c, 200, "success", message, extra)
}
