package model

import "encoding/json"

type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type ServerInfo struct {
	APIVersion    string `json:"api_version"`
	ServerVersion string `json:"server_version"`
	InstanceID    string `json:"instance_id"`
	SetupRequired bool   `json:"setup_required"`
}

type User struct {
	ID           string `json:"id"`
	Username     string `json:"username"`
	PasswordHash string `json:"-"`
	IsAdmin      bool   `json:"is_admin"`
	SyncSeq      int64  `json:"-"`
	CreatedAt    int64  `json:"created_at"`
	UpdatedAt    int64  `json:"updated_at"`
}

type SessionInfo struct {
	ID         string `json:"id"`
	DeviceName string `json:"device_name"`
	CreatedAt  int64  `json:"created_at"`
	LastUsedAt int64  `json:"last_used_at"`
	ExpiresAt  int64  `json:"expires_at"`
	Current    bool   `json:"current"`
}

type AuthTokensResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int64  `json:"expires_in"`
	SessionID    string `json:"session_id"`
	User         User   `json:"user"`
}

type Note struct {
	ID               string  `json:"id"`
	Title            string  `json:"title"`
	Body             string  `json:"body"`
	Color            string  `json:"color"`
	IsPinned         bool    `json:"is_pinned"`
	IsReadOnly       bool    `json:"is_read_only"`
	Version          int64   `json:"version"`
	ServerSeq        int64   `json:"server_seq"`
	ConflictOfNoteID *string `json:"conflict_of_note_id,omitempty"`
	CreatedAt        int64   `json:"created_at"`
	UpdatedAt        int64   `json:"updated_at"`
	TrashedAt        *int64  `json:"trashed_at,omitempty"`
	PurgedAt         *int64  `json:"purged_at,omitempty"`
}

type ShoppingItem struct {
	ID        string `json:"id"`
	Text      string `json:"text"`
	IsChecked bool   `json:"is_checked"`
	SortOrder string `json:"sort_order"`
	Version   int64  `json:"version"`
	ServerSeq int64  `json:"server_seq"`
	CreatedAt int64  `json:"created_at"`
	UpdatedAt int64  `json:"updated_at"`
	DeletedAt *int64 `json:"deleted_at,omitempty"`
}

type SyncPullResponse struct {
	InstanceID    string         `json:"instance_id"`
	ServerVersion string         `json:"server_version,omitempty"`
	APIVersion    string         `json:"api_version,omitempty"`
	CurrentSeq    int64          `json:"current_seq"`
	HasMore       bool           `json:"has_more"`
	Notes         []Note         `json:"notes"`
	ShoppingItems []ShoppingItem `json:"shopping_items"`
}

type SyncPushRequest struct {
	ClientDevice string         `json:"client_device"`
	Mutations    []SyncMutation `json:"mutations"`
}

type SyncMutation struct {
	MutationID  string          `json:"mutation_id"`
	Entity      string          `json:"entity"` // "note" | "shopping_item"
	Op          string          `json:"op"`     // "upsert" | "set_pin" | "set_lock" | "trash" | "restore" | "purge" | "delete"
	EntityID    string          `json:"entity_id"`
	BaseVersion int64           `json:"base_version"`
	Payload     json.RawMessage `json:"payload"`
}

type SyncMutationResult struct {
	MutationID   string        `json:"mutation_id"`
	Entity       string        `json:"entity"`
	EntityID     string        `json:"entity_id"`
	Status       string        `json:"status"` // "applied" | "conflict_copy_created" | "duplicate" | "rejected"
	ErrorCode    string        `json:"error_code,omitempty"`
	ErrorMessage string        `json:"error_message,omitempty"`
	Note         *Note         `json:"note,omitempty"`
	ConflictCopy *Note         `json:"conflict_copy,omitempty"`
	ShoppingItem *ShoppingItem `json:"shopping_item,omitempty"`
}

type SyncPushResponse struct {
	InstanceID string               `json:"instance_id"`
	CurrentSeq int64                `json:"current_seq"`
	Results    []SyncMutationResult `json:"results"`
}

type ExportBundle struct {
	FormatVersion int            `json:"format_version"`
	ExportedAt    int64          `json:"exported_at"`
	ServerVersion string         `json:"server_version"`
	Username      string         `json:"username"`
	Notes         []Note         `json:"notes"`
	ShoppingItems []ShoppingItem `json:"shopping_items"`
}
