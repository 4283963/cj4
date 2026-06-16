package callback

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"download-worker/internal/config"
	"download-worker/internal/model"
)

type Client struct {
	cfg        *config.CallbackConfig
	httpClient *http.Client
}

func NewClient(cfg *config.CallbackConfig) *Client {
	return &Client{
		cfg: cfg,
		httpClient: &http.Client{
			Timeout: cfg.Timeout,
			Transport: &http.Transport{
				MaxIdleConns:        100,
				IdleConnTimeout:     90 * time.Second,
				TLSHandshakeTimeout: 10 * time.Second,
			},
		},
	}
}

func (c *Client) SendCallback(ctx context.Context, callbackURL string, secret string, req model.DownloadCallbackRequest) error {
	var lastErr error
	for i := 0; i < c.cfg.MaxRetries; i++ {
		err := c.send(ctx, callbackURL, secret, req)
		if err == nil {
			return nil
		}
		lastErr = err
		log.Printf("Callback attempt %d failed for task %s: %v", i+1, req.TaskID, err)
		if i < c.cfg.MaxRetries-1 {
			time.Sleep(c.cfg.RetryInterval)
		}
	}
	return fmt.Errorf("callback failed after %d attempts: %w", c.cfg.MaxRetries, lastErr)
}

func (c *Client) send(ctx context.Context, callbackURL string, secret string, req model.DownloadCallbackRequest) error {
	body, err := json.Marshal(req)
	if err != nil {
		return fmt.Errorf("marshal callback request failed: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, "POST", callbackURL, bytes.NewBuffer(body))
	if err != nil {
		return fmt.Errorf("create callback request failed: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("X-Callback-Secret", secret)

	resp, err := c.httpClient.Do(httpReq)
	if err != nil {
		return fmt.Errorf("send callback request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("callback returned non-success status: %d", resp.StatusCode)
	}

	log.Printf("Callback sent successfully for task %s, status: %s", req.TaskID, req.Status)
	return nil
}

func (c *Client) NotifyDownloading(ctx context.Context, callbackURL string, secret string, taskID string, progress model.DownloadProgress) error {
	req := model.DownloadCallbackRequest{
		TaskID:         taskID,
		Status:         "DOWNLOADING",
		DownloadedSize: progress.DownloadedSize,
		Speed:          progress.Speed,
	}
	return c.SendCallback(ctx, callbackURL, secret, req)
}

func (c *Client) NotifyCompleted(ctx context.Context, callbackURL string, secret string, taskID string, fileSize int64, fileName string, fileType string, savePath string) error {
	req := model.DownloadCallbackRequest{
		TaskID:   taskID,
		Status:   "COMPLETED",
		FileSize: fileSize,
		FileName: fileName,
		FileType: fileType,
		SavePath: savePath,
	}
	return c.SendCallback(ctx, callbackURL, secret, req)
}

func (c *Client) NotifyFailed(ctx context.Context, callbackURL string, secret string, taskID string, errorMsg string) error {
	req := model.DownloadCallbackRequest{
		TaskID:       taskID,
		Status:       "FAILED",
		ErrorMessage: errorMsg,
	}
	return c.SendCallback(ctx, callbackURL, secret, req)
}
