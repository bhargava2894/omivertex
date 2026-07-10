-- The seat's engagement window end. Filling a position copies it onto the
-- allocation so the roll-off radar sees position-driven engagements.
ALTER TABLE open_positions ADD COLUMN end_date date;
