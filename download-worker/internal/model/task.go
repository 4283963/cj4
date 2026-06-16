package model

type DownloadTaskMessage struct {
	TaskID         string `json:"taskId"`
	TenantID       string `json:"tenantId"`
	FileURL        string `json:"fileUrl"`
	FileName       string `json:"fileName"`
	SavePath       string `json:"savePath"`
	CallbackURL    string `json:"callbackUrl"`
	CallbackSecret string `json:"callbackSecret"`
}

type DownloadCallbackRequest struct {
	TaskID         string `json:"taskId"`
	Status         string `json:"status"`
	FileSize       int64  `json:"fileSize,omitempty"`
	FileName       string `json:"fileName,omitempty"`
	FileType       string `json:"fileType,omitempty"`
	SavePath       string `json:"savePath,omitempty"`
	ErrorMessage   string `json:"errorMessage,omitempty"`
	DownloadedSize int64  `json:"downloadedSize,omitempty"`
	Speed          int    `json:"speed,omitempty"`
}

type DownloadProgress struct {
	DownloadedSize int64
	Speed          int
	Percentage     float64
}
