FROM golang:1.21-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o download-worker ./cmd/worker

FROM alpine:latest
WORKDIR /app
RUN apk add --no-cache ca-certificates tzdata
COPY --from=builder /app/download-worker .
COPY config.yaml .
RUN mkdir -p /app/downloads
ENV TZ=Asia/Shanghai
ENTRYPOINT ["./download-worker"]
