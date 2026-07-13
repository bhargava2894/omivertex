-- Add headcount column to open_positions to allow multiple seats per position
ALTER TABLE open_positions ADD COLUMN headcount integer NOT NULL DEFAULT 1;
ALTER TABLE open_positions ADD CONSTRAINT open_positions_headcount_check CHECK (headcount >= 1);
