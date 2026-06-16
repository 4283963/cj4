package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	"download-worker/internal/callback"
	"download-worker/internal/config"
	"download-worker/internal/download"
	"download-worker/internal/model"
	"download-worker/internal/mq"
)

type taskError struct {
	err     error
	isFatal bool
}

func (e *taskError) Error() string {
	return e.err.Error()
}

func fatalError(err error) *taskError {
	return &taskError{err: err, isFatal: true}
}

func retryableError(err error) *taskError {
	return &taskError{err: err, isFatal: false}
}

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

		done := make(chan error, 1)

		go func() {
			defer func() {
				<-sem
				wg.Done()
			}()

			ctx, cancel := context.WithTimeout(context.Background(), cfg.Download.Timeout)
			defer cancel()

			taskErr := processTask(ctx, downloader, callbackClient, task, cfg)
			done <- taskErr
		}()

		taskErr := <-done
		if taskErr != nil {
			log.Printf("Task %s failed: %v", task.TaskID, taskErr)
			if te, ok := taskErr.(*taskError); ok && te.isFatal {
				return nil
			}
			return taskErr
		}

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

func processTask(ctx context.Context, downloader *download.Downloader, callbackClient *callback.Client,
	task model.DownloadTaskMessage, cfg *config.Config) error {

	if err := preCheckTask(ctx, downloader, task, cfg); err != nil {
		log.Printf("Pre-check failed for task %s: %v", task.TaskID, err)
		_ = callbackClient.NotifyFailed(context.Background(), task.CallbackURL, task.CallbackSecret,
			task.TaskID, fmt.Sprintf("预检查失败: %v", err))
		return fatalError(err)
	}

	savePath := task.SavePath
	if savePath == "" {
		savePath = "/" + filepath.Base(task.FileURL)
	}

	_ = callbackClient.NotifyDownloading(context.Background(), task.CallbackURL, task.CallbackSecret,
		task.TaskID, model.DownloadProgress{DownloadedSize: 0, Speed: 0, Percentage: 0})

	lastCallbackTime := time.Now()
	var lastProgress model.DownloadProgress

	downloader.SetProgressCallback(func(progress model.DownloadProgress) {
		now := time.Now()
		if now.Sub(lastCallbackTime) >= 10*time.Second || progress.Percentage-lastProgress.Percentage >= 5 {
			_ = callbackClient.NotifyDownloading(context.Background(), task.CallbackURL, task.CallbackSecret,
				task.TaskID, progress)
			lastCallbackTime = now
			lastProgress = progress
			log.Printf("Task %s progress: %.2f%%, speed: %d KB/s",
				task.TaskID, progress.Percentage, progress.Speed/1024)
		}
	})

	log.Printf("Start downloading task: %s, URL: %s", task.TaskID, task.FileURL)

	filePath, fileSize, fileType, err := downloader.Download(ctx, task)
	if err != nil {
		log.Printf("Download task %s failed: %v", task.TaskID, err)
		_ = callbackClient.NotifyFailed(context.Background(), task.CallbackURL, task.CallbackSecret,
			task.TaskID, err.Error())

		if isTemporaryError(err) {
			return retryableError(err)
		}
		return fatalError(err)
	}

	fileName := filepath.Base(filePath)
	log.Printf("Download task %s completed: %s, size: %d bytes", task.TaskID, filePath, fileSize)

	err = callbackClient.NotifyCompleted(context.Background(), task.CallbackURL, task.CallbackSecret,
		task.TaskID, fileSize, fileName, fileType, savePath)
	if err != nil {
		log.Printf("Notify completed failed for task %s: %v", task.TaskID, err)
		return retryableError(err)
	}

	return nil
}

func preCheckTask(ctx context.Context, downloader *download.Downloader, task model.DownloadTaskMessage, cfg *config.Config) error {
	fileSize, fileType, err := downloader.GetFileInfo(ctx, task.FileURL)
	if err != nil {
		return fmt.Errorf("URL 无法访问: %w", err)
	}

	if fileSize > 0 {
		maxFileSize := int64(10) * 1024 * 1024 * 1024
		if fileSize > maxFileSize {
			return fmt.Errorf("文件过大 (%.2f GB)，最大支持 10 GB", float64(fileSize)/1024/1024/1024)
		}
	}

	downloadDir := filepath.Join(cfg.Download.DownloadDir, task.TenantID)
	if err := os.MkdirAll(downloadDir, 0755); err != nil {
		return fmt.Errorf("创建下载目录失败: %w", err)
	}

	if err := downloader.CheckDiskSpace(downloadDir, fileSize); err != nil {
		return fmt.Errorf("磁盘检查失败: %w", err)
	}

	if fileType != "" {
		log.Printf("Task %s file type: %s, estimated size: %d bytes", task.TaskID, fileType, fileSize)
	}

	return nil
}

func isTemporaryError(err error) bool {
	if err == nil {
		return false
	}
	errStr := strings.ToLower(err.Error())
	temporaryErrors := []string{
		"connection reset",
		"connection refused",
		"timeout",
		"temporary",
		"502",
		"503",
		"504",
		"i/o timeout",
	}
	for _, te := range temporaryErrors {
		if strings.Contains(errStr, te) {
			return true
		}
	}
	return false
}
