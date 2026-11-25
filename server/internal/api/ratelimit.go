package api

import (
	"net"
	"net/http"
	"net/netip"
	"strings"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

type limiterEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// RateLimiter applies token-bucket rate limiting per key with background cleanup.
type RateLimiter struct {
	mu      sync.Mutex
	entries map[string]*limiterEntry
	limit   rate.Limit
	burst   int
}

func NewRateLimiter(eventsPerMinute float64, burst int) *RateLimiter {
	return &RateLimiter{
		entries: make(map[string]*limiterEntry),
		limit:   rate.Limit(eventsPerMinute / 60.0),
		burst:   burst,
	}
}

func (rl *RateLimiter) Allow(key string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	now := time.Now()
	if len(rl.entries) > 2000 {
		for k, e := range rl.entries {
			if now.Sub(e.lastSeen) > 15*time.Minute {
				delete(rl.entries, k)
			}
		}
	}

	entry, ok := rl.entries[key]
	if !ok {
		entry = &limiterEntry{
			limiter:  rate.NewLimiter(rl.limit, rl.burst),
			lastSeen: now,
		}
		rl.entries[key] = entry
	}
	entry.lastSeen = now
	return entry.limiter.Allow()
}

// ClientIP extracts the real client IP, honoring X-Forwarded-For only from configured trusted proxies.
func ClientIP(r *http.Request, trustedProxies []netip.Prefix) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	remoteAddr, err := netip.ParseAddr(strings.TrimSpace(host))
	if err != nil {
		return host
	}

	if isTrustedProxy(remoteAddr, trustedProxies) {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			parts := strings.Split(xff, ",")
			if len(parts) > 0 {
				candidate := strings.TrimSpace(parts[0])
				if parsed, perr := netip.ParseAddr(candidate); perr == nil {
					return parsed.String()
				}
			}
		}
		if xri := strings.TrimSpace(r.Header.Get("X-Real-IP")); xri != "" {
			if parsed, perr := netip.ParseAddr(xri); perr == nil {
				return parsed.String()
			}
		}
	}

	return remoteAddr.String()
}

func isTrustedProxy(addr netip.Addr, prefixes []netip.Prefix) bool {
	for _, p := range prefixes {
		if p.Contains(addr) {
			return true
		}
	}
	return false
}
