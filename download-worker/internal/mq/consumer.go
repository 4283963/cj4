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

type Consumer struct {
	cfg       *config.RabbitMQConfig
	conn      *amqp091.Connection
	channel   *amqp091.Channel
	mu        sync.Mutex
	handler   func(model.DownloadTaskMessage) error
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
	var task model.DownloadTaskMessage
	if err := json.Unmarshal(msg.Body, &task); err != nil {
		log.Printf("Unmarshal message failed: %v", err)
		msg.Nack(false, false)
		return
	}

	log.Printf("Received download task: %s, URL: %s", task.TaskID, task.FileURL)

	if c.handler != nil {
		if err := c.handler(task); err != nil {
			log.Printf("Handle task %s failed: %v", task.TaskID, err)
			msg.Nack(false, true)
			return
		}
	}

	msg.Ack(false)
	log.Printf("Task %s processed successfully", task.TaskID)
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
