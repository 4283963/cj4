package redis

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
	"download-worker/internal/config"
	"download-worker/internal/model"
)

type Client struct {
	client         *redis.Client
	progressPrefix string
}

func NewClient(cfg *config.RedisConfig) (*Client, error) {
	rdb := redis.NewClient(&redis.Options{
		Addr:         cfg.Addr(),
		Password:     cfg.Password,
		DB:           cfg.DB,
		PoolSize:     cfg.PoolSize,
		MinIdleConns: cfg.MinIdleConns,
		ReadTimeout:  cfg.ReadTimeout,
		WriteTimeout: cfg.WriteTimeout,
		DialTimeout:  cfg.DialTimeout,
	})

	ctx, cancel := context.WithTimeout(context.Background(), cfg.DialTimeout)
	defer cancel()
	if err := rdb.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("ping redis failed: %w", err)
	}

	return &Client{
		client:         rdb,
		progressPrefix: cfg.ProgressPrefix,
	}, nil
}

func (c *Client) UpdateProgress(ctx context.Context, taskID string, progress model.DownloadProgress, status string, errorMsg string) error {
	key := c.progressPrefix + taskID

	fields := map[string]interface{}{
		"taskId":         taskID,
		"status":         status,
		"downloadedSize": progress.DownloadedSize,
		"speed":          progress.Speed,
		"percentage":     progress.Percentage,
		"maxSpeed":       progress.MaxSpeed,
		"updatedAt":      nowMillis(),
	}

	if progress.TotalSize > 0 {
		fields["fileSize"] = progress.TotalSize
	}
	if errorMsg != "" {
		fields["errorMessage"] = errorMsg
	}

	return c.client.HSet(ctx, key, fields).Err()
}

func (c *Client) Close() error {
	return c.client.Close()
}

func nowMillis() int64 {
	return int64(time.Now().UnixNano() / 1e6)
}
