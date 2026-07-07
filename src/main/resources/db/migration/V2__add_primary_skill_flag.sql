-- Marks one of an associate's rated skills as their primary (headline) skill.
-- The primary/secondary headline strings on `associates` are derived from this.
ALTER TABLE associate_skills ADD COLUMN is_primary boolean NOT NULL DEFAULT false;
