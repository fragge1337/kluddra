-- Add synced note accent color (lavender, pistachio, salmon, butter, sky, rose, neutral)

ALTER TABLE notes ADD COLUMN color TEXT NOT NULL DEFAULT '';
