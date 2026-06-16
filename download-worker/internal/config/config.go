package config

import (
	"fmt"
	"time"

	"github.com/spf13/viper"
)

type Config struct {
	Server   ServerConfig   `mapstructure:"server"`
	RabbitMQ RabbitMQConfig `mapstructure:"rabbitmq"`
	Redis    RedisConfig    `mapstructure:"redis"`
	Download DownloadConfig `mapstructure:"download"`
	Callback CallbackConfig `mapstructure:"callback"`
}

type ServerConfig struct {
	Name string `mapstructure:"name"`
}

type RabbitMQConfig struct {
	Host              string        `mapstructure:"host"`
	Port              int           `mapstructure:"port"`
	Username          string        `mapstructure:"username"`
	Password          string        `mapstructure:"password"`
	Vhost             string        `mapstructure:"vhost"`
	Queue             string        `mapstructure:"queue"`
	Exchange          string        `mapstructure:"exchange"`
	RoutingKey        string        `mapstructure:"routing_key"`
	PrefetchCount     int           `mapstructure:"prefetch_count"`
	ReconnectInterval time.Duration `mapstructure:"reconnect_interval"`
}

type RedisConfig struct {
	Host           string        `mapstructure:"host"`
	Port           int           `mapstructure:"port"`
	Password       string        `mapstructure:"password"`
	DB             int           `mapstructure:"db"`
	ProgressPrefix string        `mapstructure:"progress_prefix"`
	PoolSize       int           `mapstructure:"pool_size"`
	MinIdleConns   int           `mapstructure:"min_idle_conns"`
	ReadTimeout    time.Duration `mapstructure:"read_timeout"`
	WriteTimeout   time.Duration `mapstructure:"write_timeout"`
	DialTimeout    time.Duration `mapstructure:"dial_timeout"`
}

type DownloadConfig struct {
	Timeout       time.Duration `mapstructure:"timeout"`
	MaxRetries    int           `mapstructure:"max_retries"`
	RetryInterval time.Duration `mapstructure:"retry_interval"`
	ChunkSize     int           `mapstructure:"chunk_size"`
	DownloadDir   string        `mapstructure:"download_dir"`
	MaxConcurrent int           `mapstructure:"max_concurrent"`
	UserAgent     string        `mapstructure:"user_agent"`
}

type CallbackConfig struct {
	Timeout       time.Duration `mapstructure:"timeout"`
	MaxRetries    int           `mapstructure:"max_retries"`
	RetryInterval time.Duration `mapstructure:"retry_interval"`
}

func Load() (*Config, error) {
	v := viper.New()
	v.SetConfigName("config")
	v.SetConfigType("yaml")
	v.AddConfigPath(".")
	v.AddConfigPath("./config")

	v.SetDefault("server.name", "download-worker")
	v.SetDefault("rabbitmq.host", "localhost")
	v.SetDefault("rabbitmq.port", 5672)
	v.SetDefault("rabbitmq.username", "guest")
	v.SetDefault("rabbitmq.password", "guest")
	v.SetDefault("rabbitmq.vhost", "/")
	v.SetDefault("rabbitmq.queue", "offline.download.queue")
	v.SetDefault("rabbitmq.exchange", "offline.download.exchange")
	v.SetDefault("rabbitmq.routing_key", "offline.download.routing")
	v.SetDefault("rabbitmq.prefetch_count", 5)
	v.SetDefault("rabbitmq.reconnect_interval", "5s")
	v.SetDefault("download.timeout", "3600s")
	v.SetDefault("download.max_retries", 3)
	v.SetDefault("download.retry_interval", "5s")
	v.SetDefault("download.chunk_size", 1048576)
	v.SetDefault("download.download_dir", "./downloads")
	v.SetDefault("download.max_concurrent", 10)
	v.SetDefault("download.user_agent", "DownloadWorker/1.0")
	v.SetDefault("callback.timeout", "30s")
	v.SetDefault("callback.max_retries", 5)
	v.SetDefault("callback.retry_interval", "2s")
	v.SetDefault("redis.host", "localhost")
	v.SetDefault("redis.port", 6379)
	v.SetDefault("redis.password", "")
	v.SetDefault("redis.db", 0)
	v.SetDefault("redis.progress_prefix", "offline:progress:")
	v.SetDefault("redis.pool_size", 16)
	v.SetDefault("redis.min_idle_conns", 4)
	v.SetDefault("redis.read_timeout", "1s")
	v.SetDefault("redis.write_timeout", "1s")
	v.SetDefault("redis.dial_timeout", "2s")

	if err := v.ReadInConfig(); err != nil {
		fmt.Printf("Warning: config file not found, using defaults: %v\n", err)
	}

	var cfg Config
	if err := v.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("unmarshal config failed: %w", err)
	}

	return &cfg, nil
}

func (c *RabbitMQConfig) URL() string {
	return fmt.Sprintf("amqp://%s:%s@%s:%d%s",
		c.Username, c.Password, c.Host, c.Port, c.Vhost)
}

func (c *RedisConfig) Addr() string {
	return fmt.Sprintf("%s:%d", c.Host, c.Port)
}
