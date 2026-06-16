package mq

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/rabbitmq/amqp091-go"
	"download-worker/internal/config"
	"download-worker/internal/model"
)

const maxRetryCount = 3

type Consumer struct {
	cfg           *config.RabbitMQConfig
	conn          *amqp091.Connection
	channel       *amqp091.Channel
	mu            sync.Mutex
	handler       func(model.DownloadTaskMessage) error
}

func NewConsumer(cfg *config.RabbitMQConfig) *Consumer {
	return &Consumer{
		cfg: cfg,
	}
}

func (c *Consumer) SetHandler(handler func(model.DownloadTaskMessage) error) {
	c.handler = handler
}

func (c *Consumer) Connect() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	var err error
	c.conn, err = amqp091.Dial(c.cfg.URL())
	if err != nil {
		return fmt.Errorf("connect to rabbitmq failed: %w", err)
	}

	c.channel, err = c.conn.Channel()
	if err != nil {
		c.conn.Close()
		return fmt.Errorf("create channel failed: %w", err)
	}

	if err := c.channel.Qos(c.cfg.PrefetchCount, 0, false); err != nil {
		return fmt.Errorf("set qos failed: %w", err)
	}

	log.Println("Connected to RabbitMQ successfully")
	return nil
}

func (c *Consumer) Start(ctx context.Context) error {
	if err := c.Connect(); err != nil {
		return err
	}

	go c.handleReconnect(ctx)

	msgs, err := c.channel.Consume(
		c.cfg.Queue,
		"",
		false,
		false,
		false,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("consume queue failed: %w", err)
	}

	log.Printf("Start consuming queue: %s", c.cfg.Queue)

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case msg, ok := <-msgs:
			if !ok {
				time.Sleep(c.cfg.ReconnectInterval)
				continue
			}
			go c.processMessage(msg)
		}
	}
}

func (c *Consumer) processMessage(msg amqp091.Delivery) {
	retryCount := getRetryCount(msg)
	taskID := getTaskID(msg)

	log.Printf("Received message: task=%s, retry=%d/%d", taskID, retryCount, maxRetryCount)

	if retryCount >= maxRetryCount {
		log.Printf("Task %s exceeded max retries (%d), sending to DLX", taskID, maxRetryCount)
		msg.Nack(false, false)
		return
	}

	var task model.DownloadTaskMessage
	if err := json.Unmarshal(msg.Body, &task); err != nil {
		log.Printf("Unmarshal message failed: %v", err)
		msg.Nack(false, false)
		return
	}

	log.Printf("Processing download task: %s, URL: %s", task.TaskID, task.FileURL)

	if c.handler != nil {
		if err := c.handler(task); err != nil {
			log.Printf("Handle task %s failed (retry %d/%d): %v", task.TaskID, retryCount, maxRetryCount, err)

			if isFatalError(err) {
				log.Printf("Task %s failed with fatal error, acknowledging to remove from queue", task.TaskID)
				msg.Ack(false)
				return
			}

			backoff := getBackoffDuration(retryCount)
			log.Printf("Task %s will be retried after %v", task.TaskID, backoff)
			time.Sleep(backoff)
			msg.Nack(false, true)
			return
		}
	}

	msg.Ack(false)
	log.Printf("Task %s processed successfully, message acknowledged", task.TaskID)
}

func getTaskID(msg amqp091.Delivery) string {
	if msg.MessageId != "" {
		return msg.MessageId
	}
	var task model.DownloadTaskMessage
	if err := json.Unmarshal(msg.Body, &task); err == nil {
		return task.TaskID
	}
	return "unknown"
}

func getRetryCount(msg amqp091.Delivery) int {
	xDeath, ok := msg.Headers["x-death"]
	if !ok {
		return 0
	}

	deathList, ok := xDeath.([]interface{})
	if !ok || len(deathList) == 0 {
		return 0
	}

	for _, death := range deathList {
		deathMap, ok := death.(map[string]interface{})
		if !ok {
			continue
		}
		if count, ok := deathMap["count"].(int64); ok {
			return int(count)
		}
	}

	return 0
}

func getBackoffDuration(retryCount int) time.Duration {
	base := time.Second
	switch retryCount {
	case 0:
		return base
	case 1:
		return base * 5
	case 2:
		return base * 15
	default:
		return base * 30
	}
}

func isFatalError(err error) bool {
	if err == nil {
		return false
	}
	errStr := err.Error()
	fatalErrors := []string{
		"fatal",
		"404",
		"401",
		"403",
		"文件不存在",
		"无权限访问",
		"文件过大",
		"磁盘空间不足",
		"磁盘检查失败",
		"预检查失败",
	}
	for _, fe := range fatalErrors {
		if containsIgnoreCase(errStr, fe) {
			return true
		}
	}
	return false
}

func containsIgnoreCase(s, substr string) bool {
	sLower := toLower(s)
	subLower := toLower(substr)
	return len(sLower) >= len(subLower) && containsSubstring(sLower, subLower)
}

func toLower(s string) string {
	result := make([]byte, len(s))
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		result[i] = c
	}
	return string(result)
}

func containsSubstring(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		match := true
		for j := 0; j < len(substr); j++ {
			if s[i+j] != substr[j] {
				match = false
				break
			}
		}
		if match {
			return true
		}
	}
	return false
}

func (c *Consumer) handleReconnect(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case err := <-c.conn.NotifyClose(make(chan *amqp091.Error)):
			log.Printf("RabbitMQ connection closed: %v", err)
			for {
				select {
				case <-ctx.Done():
					return
				default:
					log.Println("Attempting to reconnect RabbitMQ...")
					if err := c.Connect(); err == nil {
						return
					}
					time.Sleep(c.cfg.ReconnectInterval)
				}
			}
		}
	}
}

func (c *Consumer) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.channel != nil {
		c.channel.Close()
	}
	if c.conn != nil {
		c.conn.Close()
	}
	log.Println("RabbitMQ consumer closed")
	return nil
}
