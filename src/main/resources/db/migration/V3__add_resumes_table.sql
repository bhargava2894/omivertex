-- One résumé per associate. The blob lives here (not on `associates`) so roster
-- queries never load file bytes. Replaced on re-upload (see ResumeService).
-- ON DELETE CASCADE: deleting an associate removes their résumé automatically.
CREATE TABLE resumes (
    id            BIGSERIAL PRIMARY KEY,
    associate_id  BIGINT NOT NULL UNIQUE REFERENCES associates(id) ON DELETE CASCADE,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    byte_size     BIGINT NOT NULL,
    content       BYTEA NOT NULL,
    uploaded_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
