package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/fragge1337/kluddra/server/internal/api"
	"github.com/fragge1337/kluddra/server/internal/auth"
	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	kluddrasync "github.com/fragge1337/kluddra/server/internal/sync"
)

func main() {
	if len(os.Args) > 1 {
		switch os.Args[1] {
		case "healthcheck":
			os.Exit(runHealthcheck())
		case "backup":
			os.Exit(runBackupCLI())
		case "user":
			os.Exit(runUserCLI(os.Args[2:]))
		case "version":
			fmt.Printf("kluddra-server %s (api v%s)\n", config.Version, config.APIVersion)
			return
		}
	}

	if err := runServer(); err != nil {
		fmt.Fprintf(os.Stderr, "kluddra fatal: %v\n", err)
		os.Exit(1)
	}
}

func runServer() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}
	logger := buildLogger(cfg.LogLevel)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	store, err := db.Open(ctx, cfg.DataDir, logger)
	if err != nil {
		return err
	}
	defer func() {
		if cerr := store.CheckpointAndClose(); cerr != nil {
			logger.Error("database shutdown error", "err", cerr)
		}
	}()

	authSvc, err := auth.NewService(ctx, store, cfg)
	if err != nil {
		return err
	}
	syncSvc := kluddrasync.NewService(store, cfg)
	apiServer := api.NewServer(cfg, store, authSvc, syncSvc, logger)

	if code := authSvc.SetupCode(); code != "" {
		logger.Info("==================================================================")
		logger.Info("Kluddra initial setup required!")
		logger.Info("Open the Kluddra Android app, enter your server URL and this code:")
		logger.Info("SETUP CODE", "code", code)
		logger.Info("==================================================================")
	}

	// Background maintenance loop (daily online VACUUM INTO backup & expired trash cleanup)
	go runMaintenanceLoop(ctx, cfg, store, logger)

	httpServer := &http.Server{
		Addr:              cfg.ListenAddress(),
		Handler:           apiServer.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
		MaxHeaderBytes:    16 << 10,
	}

	errCh := make(chan error, 1)
	go func() {
		logger.Info("kluddra server listening", "addr", cfg.ListenAddress(), "version", config.Version)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
		close(errCh)
	}()

	select {
	case <-ctx.Done():
		logger.Info("shutting down gracefully...")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(shutdownCtx); err != nil {
			return fmt.Errorf("graceful shutdown failed: %w", err)
		}
		logger.Info("kluddra server stopped cleanly")
		return nil
	case err := <-errCh:
		return err
	}
}

func runMaintenanceLoop(ctx context.Context, cfg config.Config, store *db.Store, logger *slog.Logger) {
	cleanupTicker := time.NewTicker(6 * time.Hour)
	defer cleanupTicker.Stop()

	var backupCh <-chan time.Time
	if cfg.BackupEnabled && cfg.BackupInterval > 0 {
		bt := time.NewTicker(cfg.BackupInterval)
		defer bt.Stop()
		backupCh = bt.C
	}

	for {
		select {
		case <-ctx.Done():
			return
		case <-cleanupTicker.C:
			if err := store.CleanupExpired(ctx, cfg.TrashRetainDays); err != nil {
				logger.Error("expired cleanup failed", "err", err)
			}
		case <-backupCh:
			backupDir := filepath.Join(cfg.DataDir, "backups")
			path, err := store.Backup(ctx, backupDir)
			if err != nil {
				logger.Error("scheduled backup failed", "err", err)
				continue
			}
			_ = db.PruneBackups(backupDir, cfg.BackupRetainDays)
			logger.Info("scheduled sqlite backup complete", "file", filepath.Base(path))
		}
	}
}

func runHealthcheck() int {
	cfg, err := config.Load()
	if err != nil {
		return 1
	}
	client := &http.Client{Timeout: 3 * time.Second}
	resp, err := client.Get(fmt.Sprintf("http://127.0.0.1:%d/api/v1/health", cfg.Port))
	if err != nil {
		return 1
	}
	_ = resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return 1
	}
	return 0
}

func runBackupCLI() int {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "config error: %v\n", err)
		return 1
	}
	ctx := context.Background()
	store, err := db.Open(ctx, cfg.DataDir, buildLogger("error"))
	if err != nil {
		fmt.Fprintf(os.Stderr, "db error: %v\n", err)
		return 1
	}
	defer store.CheckpointAndClose()

	backupDir := filepath.Join(cfg.DataDir, "backups")
	path, err := store.Backup(ctx, backupDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "backup failed: %v\n", err)
		return 1
	}
	_ = db.PruneBackups(backupDir, cfg.BackupRetainDays)
	fmt.Printf("Backup created: %s\n", path)
	return 0
}

func runUserCLI(args []string) int {
	if len(args) < 1 || args[0] != "create" {
		fmt.Fprintln(os.Stderr, "Usage: kluddra user create -username <name> -password <pass> [-admin]")
		return 2
	}
	fs := flag.NewFlagSet("user create", flag.ExitOnError)
	username := fs.String("username", "", "Username (2-64 chars)")
	password := fs.String("password", "", "Password (8-128 chars)")
	isAdmin := fs.Bool("admin", false, "Grant administrator privileges")
	_ = fs.Parse(args[1:])

	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "config error: %v\n", err)
		return 1
	}
	ctx := context.Background()
	store, err := db.Open(ctx, cfg.DataDir, buildLogger("error"))
	if err != nil {
		fmt.Fprintf(os.Stderr, "db error: %v\n", err)
		return 1
	}
	defer store.CheckpointAndClose()

	authSvc, err := auth.NewService(ctx, store, cfg)
	if err != nil {
		fmt.Fprintf(os.Stderr, "auth init error: %v\n", err)
		return 1
	}
	user, err := authSvc.CreateUser(ctx, *username, *password, *isAdmin)
	if err != nil {
		fmt.Fprintf(os.Stderr, "create user failed: %v\n", err)
		return 1
	}
	fmt.Printf("Created user %q (id=%s, admin=%t)\n", user.Username, user.ID, user.IsAdmin)
	return 0
}

func buildLogger(levelStr string) *slog.Logger {
	var level slog.Level
	switch levelStr {
	case "debug":
		level = slog.LevelDebug
	case "warn":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: level}))
}
