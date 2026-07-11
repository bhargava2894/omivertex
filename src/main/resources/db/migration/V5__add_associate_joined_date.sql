-- Day the associate joined the company. Anchors the bench clock for
-- never-allocated associates so roster imports don't reset bench aging.
ALTER TABLE associates ADD COLUMN joined_date date;
