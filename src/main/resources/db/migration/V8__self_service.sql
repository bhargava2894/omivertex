-- Self-service: an ASSOCIATE-role login is linked to their roster record.
ALTER TABLE app_users ADD COLUMN associate_id bigint REFERENCES associates(id);
ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('ADMIN','VIEWER','ASSOCIATE'));
