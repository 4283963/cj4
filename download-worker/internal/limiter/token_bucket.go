package limiter

import (
	"context"
	"time"
)

type TokenBucket struct {
	rate       int64
	capacity   int64
	tokens     int64
	lastRefill time.Time
}

func NewTokenBucket(rateBytesPerSec int64) *TokenBucket {
	if rateBytesPerSec <= 0 {
		return nil
	}
	return &TokenBucket{
		rate:       rateBytesPerSec,
		capacity:   rateBytesPerSec,
		tokens:     rateBytesPerSec,
		lastRefill: time.Now(),
	}
}

func (tb *TokenBucket) refill() {
	now := time.Now()
	elapsed := now.Sub(tb.lastRefill).Seconds()
	if elapsed > 0 {
		newTokens := int64(elapsed * float64(tb.rate))
		if newTokens > 0 {
			tb.tokens += newTokens
			if tb.tokens > tb.capacity {
				tb.tokens = tb.capacity
			}
			tb.lastRefill = now
		}
	}
}

func (tb *TokenBucket) Take(ctx context.Context, n int64) (int64, error) {
	if tb == nil {
		return n, nil
	}

	for n > 0 {
		select {
		case <-ctx.Done():
			return 0, ctx.Err()
		default:
		}

		tb.refill()

		if tb.tokens <= 0 {
			sleepTime := time.Duration(float64(n-tb.tokens) / float64(tb.rate) * float64(time.Second))
			if sleepTime > time.Second {
				sleepTime = time.Second
			}
			if sleepTime < time.Millisecond {
				sleepTime = time.Millisecond
			}
			select {
			case <-ctx.Done():
				return 0, ctx.Err()
			case <-time.After(sleepTime):
			}
			continue
		}

		take := n
		if take > tb.tokens {
			take = tb.tokens
		}
		tb.tokens -= take
		return take, nil
	}
	return 0, nil
}

func (tb *TokenBucket) SetRate(newRate int64) {
	if tb == nil {
		return
	}
	if newRate <= 0 {
		return
	}
	tb.rate = newRate
	tb.capacity = newRate
}
