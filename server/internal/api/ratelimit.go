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

const maxRateLimiterEntries = 10_000

type limiterEntry struct {
	limiter  *rate.Limiter
	lastSeen time.Time
}

// RateLimiter applies token-bucket rate limiting per key with strict bounded memory.
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
	if len(rl.entries) >= maxRateLimiterEntries {
		// First evict stale entries older than 15 minutes.
		for k, e := range rl.entries {
			if now.Sub(e.lastSeen) > 15*time.Minute {
				delete(rl.entries, k)
			}
		}
		// Hard memory ceiling under DDoS: evict oldest entries if still full.
		if len(rl.entries) >= maxRateLimiterEntries {
			var oldestKey string
			var oldestTime time.Time
			first := true
			for k, e := range rl.entries {
				if first || e.lastSeen.Before(oldestTime) {
					oldestKey = k
					oldestTime = e.lastSeen
					first = false
				}
			}
			if oldestKey != "" {
				delete(rl.entries, oldestKey)
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

// ClientIP extracts the real client IP safely:
// - Ignores forwarding headers unless RemoteAddr is in trustedProxies.
// - Walks X-Forwarded-For from RIGHT to LEFT, skipping trusted proxies, so
//   attacker-injected leftmost IPs can never spoof ClientIP or bypass rate limits.
func ClientIP(r *http.Request, trustedProxies []netip.Prefix) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	remoteAddr, err := netip.ParseAddr(strings.TrimSpace(host))
	if err != nil {
		return host
	}
	remoteAddr = remoteAddr.Unmap()

	if !isTrustedProxy(remoteAddr, trustedProxies) {
		return remoteAddr.String()
	}

	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		for i := len(parts) - 1; i >= 0; i-- {
			candidate := strings.TrimSpace(parts[i])
			parsed, perr := netip.ParseAddr(candidate)
			if perr != nil {
				// Stop at malformed header entry; do not trust anything further left.
				break
			}
			parsed = parsed.Unmap()
			if !isTrustedProxy(parsed, trustedProxies) {
				return parsed.String()
			}
		}
	}

	if xri := strings.TrimSpace(r.Header.Get("X-Real-IP")); xri != "" {
		if parsed, perr := netip.ParseAddr(xri); perr == nil {
			parsed = parsed.Unmap()
			if !isTrustedProxy(parsed, trustedProxies) {
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
