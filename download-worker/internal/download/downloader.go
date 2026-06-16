package download

import (
	"context"
	"fmt"
	"io"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"download-worker/internal/config"
	"download-worker/internal/model"
)

type Downloader struct {
	cfg            *config.DownloadConfig
	progressFunc   func(model.DownloadProgress)
	httpClient     *http.Client
}

func NewDownloader(cfg *config.DownloadConfig) *Downloader {
	return &Downloader{
		cfg: cfg,
		httpClient: &http.Client{
			Timeout: cfg.Timeout,
			Transport: &http.Transport{
				MaxIdleConns:        100,
				IdleConnTimeout:     90 * time.Second,
				TLSHandshakeTimeout: 10 * time.Second,
				DisableCompression:  false,
			},
		},
	}
}

func (d *Downloader) SetProgressCallback(fn func(model.DownloadProgress)) {
	d.progressFunc = fn
}

func (d *Downloader) Download(ctx context.Context, task model.DownloadTaskMessage) (string, int64, string, error) {
	var lastErr error
	for i := 0; i < d.cfg.MaxRetries; i++ {
		filePath, fileSize, fileType, err := d.downloadWithRetry(ctx, task, i)
		if err == nil {
			return filePath, fileSize, fileType, nil
		}
		lastErr = err
		if i < d.cfg.MaxRetries-1 {
			time.Sleep(d.cfg.RetryInterval)
		}
	}
	return "", 0, "", fmt.Errorf("download failed after %d attempts: %w", d.cfg.MaxRetries, lastErr)
}

func (d *Downloader) downloadWithRetry(ctx context.Context, task model.DownloadTaskMessage, attempt int) (string, int64, string, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", task.FileURL, nil)
	if err != nil {
		return "", 0, "", fmt.Errorf("create request failed: %w", err)
	}

	req.Header.Set("User-Agent", d.cfg.UserAgent)
	req.Header.Set("Accept", "*/*")

	fileName := task.FileName
	if fileName == "" {
		fileName = filepath.Base(task.FileURL)
	}

	downloadDir := filepath.Join(d.cfg.DownloadDir, task.TenantID)
	if err := os.MkdirAll(downloadDir, 0755); err != nil {
		return "", 0, "", fmt.Errorf("create download dir failed: %w", err)
	}

	tempFilePath := filepath.Join(downloadDir, fileName+".part")
	finalFilePath := filepath.Join(downloadDir, fileName)

	var downloadedSize int64
	if fileInfo, err := os.Stat(tempFilePath); err == nil {
		downloadedSize = fileInfo.Size()
		if downloadedSize > 0 {
			req.Header.Set("Range", fmt.Sprintf("bytes=%d-", downloadedSize))
		}
	}

	resp, err := d.httpClient.Do(req)
	if err != nil {
		return "", 0, "", fmt.Errorf("http request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		if resp.StatusCode != 206 {
			return "", 0, "", fmt.Errorf("unexpected status code: %d", resp.StatusCode)
		}
	}

	var totalSize int64
	if contentRange := resp.Header.Get("Content-Range"); contentRange != "" {
		if parts := strings.Split(contentRange, "/"); len(parts) == 2 {
			if size, err := strconv.ParseInt(parts[1], 10, 64); err == nil {
				totalSize = size
			}
		}
	} else {
		totalSize = resp.ContentLength
	}

	var fileType string
	if contentType := resp.Header.Get("Content-Type"); contentType != "" {
		fileType = contentType
		if ext, err := mime.ExtensionsByType(contentType); err == nil && len(ext) > 0 {
			fileType = ext[0]
		}
	}

	flag := os.O_CREATE | os.O_WRONLY
	if resp.StatusCode == 206 && downloadedSize > 0 {
		flag |= os.O_APPEND
	} else {
		downloadedSize = 0
	}

	outFile, err := os.OpenFile(tempFilePath, flag, 0644)
	if err != nil {
		return "", 0, "", fmt.Errorf("open temp file failed: %w", err)
	}
	defer outFile.Close()

	buf := make([]byte, d.cfg.ChunkSize)
	startTime := time.Now()
	lastReportTime := startTime

	for {
		select {
		case <-ctx.Done():
			return "", 0, "", ctx.Err()
		default:
		}

		n, err := resp.Body.Read(buf)
		if n > 0 {
			if _, writeErr := outFile.Write(buf[:n]); writeErr != nil {
				return "", 0, "", fmt.Errorf("write file failed: %w", writeErr)
			}
			downloadedSize += int64(n)

			now := time.Now()
			if now.Sub(lastReportTime) >= time.Second {
				elapsed := now.Sub(startTime).Seconds()
				var speed int
				if elapsed > 0 {
					speed = int(float64(downloadedSize) / elapsed)
				}

				var percentage float64
				if totalSize > 0 {
					percentage = float64(downloadedSize) / float64(totalSize) * 100
				}

				if d.progressFunc != nil {
					d.progressFunc(model.DownloadProgress{
						DownloadedSize: downloadedSize,
						Speed:          speed,
						Percentage:     percentage,
					})
				}
				lastReportTime = now
			}
		}

		if err != nil {
			if err == io.EOF {
				break
			}
			return "", 0, "", fmt.Errorf("read response failed: %w", err)
		}
	}

	if err := outFile.Close(); err != nil {
		return "", 0, "", fmt.Errorf("close temp file failed: %w", err)
	}

	if err := os.Rename(tempFilePath, finalFilePath); err != nil {
		return "", 0, "", fmt.Errorf("rename file failed: %w", err)
	}

	return finalFilePath, downloadedSize, fileType, nil
}

func (d *Downloader) GetFileInfo(ctx context.Context, url string) (int64, string, error) {
	req, err := http.NewRequestWithContext(ctx, "HEAD", url, nil)
	if err != nil {
		return 0, "", err
	}
	req.Header.Set("User-Agent", d.cfg.UserAgent)

	resp, err := d.httpClient.Do(req)
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return 0, "", fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	var fileType string
	if contentType := resp.Header.Get("Content-Type"); contentType != "" {
		fileType = contentType
	}

	return resp.ContentLength, fileType, nil
}
