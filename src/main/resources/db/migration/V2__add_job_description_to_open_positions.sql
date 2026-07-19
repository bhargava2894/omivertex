-- V2__add_job_description_to_open_positions.sql
-- Add job_description and required_skill (which was missing in baseline V1) columns to open_positions
ALTER TABLE open_positions ADD COLUMN required_skill character varying(255);
ALTER TABLE open_positions ADD COLUMN job_description text;
