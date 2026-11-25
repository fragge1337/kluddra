package export

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"strings"
	"time"
	"unicode"

	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/model"
)

// BuildJSON serializes the user's complete notes and shopping list into an open JSON bundle.
func BuildJSON(username string, notes []model.Note, items []model.ShoppingItem) ([]byte, error) {
	bundle := model.ExportBundle{
		FormatVersion: 1,
		ExportedAt:    time.Now().UTC().UnixMilli(),
		ServerVersion: config.Version,
		Username:      username,
		Notes:         notes,
		ShoppingItems: items,
	}
	return json.MarshalIndent(bundle, "", "  ")
}

// BuildMarkdownZIP packages all notes as human-readable Markdown files plus shopping-list.md.
func BuildMarkdownZIP(notes []model.Note, items []model.ShoppingItem) ([]byte, error) {
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)

	usedNames := make(map[string]int)
	for _, n := range notes {
		folder := "notes"
		if n.TrashedAt != nil {
			folder = "trash"
		}
		base := slugify(n.Title)
		if base == "" {
			base = "note-" + n.ID[:8]
		}
		key := folder + "/" + base
		usedNames[key]++
		filename := fmt.Sprintf("%s/%s.md", folder, base)
		if usedNames[key] > 1 {
			filename = fmt.Sprintf("%s/%s-%d.md", folder, base, usedNames[key])
		}

		w, err := zw.Create(filename)
		if err != nil {
			return nil, err
		}

		var sb strings.Builder
		sb.WriteString("---\n")
		sb.WriteString(fmt.Sprintf("id: %s\n", n.ID))
		sb.WriteString(fmt.Sprintf("created_at: %s\n", time.UnixMilli(n.CreatedAt).UTC().Format(time.RFC3339)))
		sb.WriteString(fmt.Sprintf("updated_at: %s\n", time.UnixMilli(n.UpdatedAt).UTC().Format(time.RFC3339)))
		sb.WriteString(fmt.Sprintf("pinned: %t\n", n.IsPinned))
		sb.WriteString(fmt.Sprintf("read_only: %t\n", n.IsReadOnly))
		sb.WriteString("---\n\n")
		if strings.TrimSpace(n.Title) != "" {
			sb.WriteString("# " + n.Title + "\n\n")
		}
		sb.WriteString(n.Body)
		if !strings.HasSuffix(n.Body, "\n") {
			sb.WriteString("\n")
		}
		if _, err := w.Write([]byte(sb.String())); err != nil {
			return nil, err
		}
	}

	// Dedicated shopping list file
	sw, err := zw.Create("shopping-list.md")
	if err != nil {
		return nil, err
	}
	var ssb strings.Builder
	ssb.WriteString("# Inköpslista / Shopping List\n\n")
	for _, item := range items {
		mark := " "
		if item.IsChecked {
			mark = "x"
		}
		ssb.WriteString(fmt.Sprintf("- [%s] %s\n", mark, item.Text))
	}
	if _, err := sw.Write([]byte(ssb.String())); err != nil {
		return nil, err
	}

	if err := zw.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func slugify(s string) string {
	s = strings.TrimSpace(strings.ToLower(s))
	if s == "" {
		return ""
	}
	var b strings.Builder
	lastDash := false
	for _, r := range s {
		switch {
		case unicode.IsLetter(r) || unicode.IsDigit(r):
			b.WriteRune(r)
			lastDash = false
		case !lastDash && b.Len() > 0:
			b.WriteByte('-')
			lastDash = true
		}
		if b.Len() >= 48 {
			break
		}
	}
	return strings.Trim(b.String(), "-")
}
