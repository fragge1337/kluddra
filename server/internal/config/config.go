package config

import (
	"fmt"
	"net/netip"
	"os"
	"strconv"
	"strings"
	"time"
)

// Version is injected at build time via -ldflags.
var Version = "1.0.0-dev"

const APIVersion = "1"

// Config holds runtime settings loaded from KLUDDRA_* environment variables.
type Config struct {
	Port              int
	BindAddr          string
	DataDir           string
	LogLevel          string
	AllowRegistration bool
	JWTSecret         string
	AdminSetupToken   string

	AccessTokenTTL   time.Duration
	RefreshTokenTTL  time.Duration
	BackupEnabled    bool
	BackupInterval   time.Duration
	BackupRetainDays int
	TrashRetainDays  int

	Argon2MemoryKB    uint32
	Argon2Time        uint32
	Argon2Parallelism uint8

	TrustedProxies   []netip.Prefix
	MaxSyncBodyBytes int64
	MaxNoteBodyBytes int
	MaxNoteTitleLen  int
	MaxItemTextLen   int
}

// Load parses environment variables and enforces safe security floors.
func Load() (Config, error) {
	cfg := Config{
		Port:              envInt("KLUDDRA_PORT", 8080),
		BindAddr:          envString("KLUDDRA_BIND_ADDR", "0.0.0.0"),
		DataDir:           envString("KLUDDRA_DATA_DIR", "/data"),
		LogLevel:          strings.ToLower(envString("KLUDDRA_LOG_LEVEL", "info")),
		AllowRegistration: envBool("KLUDDRA_ALLOW_REGISTRATION", false),
		JWTSecret:         os.Getenv("KLUDDRA_JWT_SECRET"),
		AdminSetupToken:   strings.TrimSpace(os.Getenv("KLUDDRA_ADMIN_SETUP_TOKEN")),

		AccessTokenTTL:   time.Duration(envInt("KLUDDRA_ACCESS_TOKEN_MINUTES", 15)) * time.Minute,
		RefreshTokenTTL:  time.Duration(envInt("KLUDDRA_REFRESH_TOKEN_DAYS", 90)) * 24 * time.Hour,
		BackupEnabled:    envBool("KLUDDRA_BACKUP_ENABLED", true),
		BackupInterval:   time.Duration(envInt("KLUDDRA_BACKUP_INTERVAL_HOURS", 24)) * time.Hour,
		BackupRetainDays: envInt("KLUDDRA_BACKUP_RETAIN_DAYS", 14),
		TrashRetainDays:  envInt("KLUDDRA_TRASH_RETAIN_DAYS", 30),

		Argon2MemoryKB:    uint32(envInt("KLUDDRA_ARGON2_MEMORY_KB", 65536)),
		Argon2Time:        uint32(envInt("KLUDDRA_ARGON2_TIME", 3)),
		Argon2Parallelism: uint8(envInt("KLUDDRA_ARGON2_PARALLELISM", 2)),

		MaxSyncBodyBytes: int64(envInt("KLUDDRA_MAX_SYNC_BYTES", 1<<20)),
		MaxNoteBodyBytes: envInt("KLUDDRA_MAX_NOTE_BYTES", 256<<10),
		MaxNoteTitleLen:  300,
		MaxItemTextLen:   300,
	}

	if cfg.Port < 1 || cfg.Port > 65535 {
		return cfg, fmt.Errorf("invalid KLUDDRA_PORT: %d", cfg.Port)
	}
	if cfg.Argon2MemoryKB < 32768 {
		cfg.Argon2MemoryKB = 32768 // Safe minimum floor (32 MiB)
	}
	if cfg.Argon2Time < 2 {
		cfg.Argon2Time = 2
	}
	if cfg.Argon2Parallelism < 1 {
		cfg.Argon2Parallelism = 1
	}
	if cfg.AccessTokenTTL < 5*time.Minute {
		cfg.AccessTokenTTL = 5 * time.Minute
	}
	if cfg.RefreshTokenTTL < 24*time.Hour {
		cfg.RefreshTokenTTL = 24 * time.Hour
	}

	rawProxies := envString("KLUDDRA_TRUSTED_PROXIES", "127.0.0.1/32,::1/128")
	for _, part := range strings.Split(rawProxies, ",") {
		p := strings.TrimSpace(part)
		if p == "" {
			continue
		}
		if !strings.Contains(p, "/") {
			addr, err := netip.ParseAddr(p)
			if err != nil {
				return cfg, fmt.Errorf("invalid KLUDDRA_TRUSTED_PROXIES entry %q: %w", p, err)
			}
			bits := 32
			if addr.Is6() {
				bits = 128
			}
			cfg.TrustedProxies = append(cfg.TrustedProxies, netip.PrefixFrom(addr, bits))
			continue
		}
		prefix, err := netip.ParsePrefix(p)
		if err != nil {
			return cfg, fmt.Errorf("invalid KLUDDRA_TRUSTED_PROXIES CIDR %q: %w", p, err)
		}
		cfg.TrustedProxies = append(cfg.TrustedProxies, prefix)
	}

	return cfg, nil
}

// ListenAddress returns host:port for http.Server.
func (c Config) ListenAddress() string {
	return fmt.Sprintf("%s:%d", c.BindAddr, c.Port)
}

func envString(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && strings.TrimSpace(v) != "" {
		return strings.TrimSpace(v)
	}
	return fallback
}

func envInt(key string, fallback int) int {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}

func envBool(key string, fallback bool) bool {
	v := strings.TrimSpace(strings.ToLower(os.Getenv(key)))
	if v == "" {
		return fallback
	}
	switch v {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return fallback
	}
}
