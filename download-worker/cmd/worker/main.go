package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"sync"
	"syscall"

	"download-worker/internal/callback"
	"download-worker/internal/config"
	"download-worker/internal/download"
	"download-worker/internal/model"
	"download-worker/internal/mq"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Load config failed: %v", err)
	}

	if err := os.MkdirAll(cfg.Download.DownloadDir, 0755); err != nil {
		log.Fatalf("Create download directory failed: %v", err)
	}

	consumer := mq.NewConsumer(&cfg.RabbitMQ)
	defer consumer.Close()

	downloader := download.NewDownloader(&cfg.Download)
	callbackClient := callback.NewClient(&cfg.Callback)

	sem := make(chan struct{}, cfg.Download.MaxConcurrent)
	var wg sync.WaitGroup

	consumer.SetHandler(func(task model.DownloadTaskMessage) error {
		sem <- struct{}{}
		wg.Add(1)

		go func() {
			defer func() {
				<-sem
				wg.Done()
			}()

			ctx, cancel := context.WithTimeout(context.Background(), cfg.Download.Timeout)
			defer cancel()

			downloader.SetProgressCallback(func(progress model.DownloadProgress) {
				_ = callbackClient.NotifyDownloading(ctx, task.CallbackURL, task.CallbackSecret, task.TaskID, progress)
				log.Printf("Task %s progress: %.2f%%, speed: %d KB/s",
					task.TaskID, progress.Percentage, progress.Speed/1024)
			})

			log.Printf("Start downloading task: %s, URL: %s", task.TaskID, task.FileURL)

			filePath, fileSize, fileType, err := downloader.Download(ctx, task)
			if err != nil {
				log.Printf("Download task %s failed: %v", task.TaskID, err)
				_ = callbackClient.NotifyFailed(context.Background(), task.CallbackURL, task.CallbackSecret, task.TaskID, err.Error())
				return
			}

			fileName := filepath.Base(filePath)
			log.Printf("Download task %s completed: %s, size: %d bytes", task.TaskID, filePath, fileSize)

			savePath := task.SavePath
			if savePath == "" {
				savePath = "/" + fileName
			}

			err = callbackClient.NotifyCompleted(context.Background(), task.CallbackURL, task.CallbackSecret,
				task.TaskID, fileSize, fileName, fileType, savePath)
			if err != nil {
				log.Printf("Notify completed failed for task %s: %v", task.TaskID, err)
			}
		}()

		return nil
	})

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigChan
		log.Printf("Received signal: %v, shutting down...", sig)
		cancel()
	}()

	log.Printf("Download worker started, max concurrent: %d", cfg.Download.MaxConcurrent)
	if err := consumer.Start(ctx); err != nil && err != context.Canceled {
		log.Printf("Consumer error: %v", err)
	}

	log.Println("Waiting for running tasks to complete...")
	wg.Wait()
	log.Println("Download worker stopped gracefully")
}
