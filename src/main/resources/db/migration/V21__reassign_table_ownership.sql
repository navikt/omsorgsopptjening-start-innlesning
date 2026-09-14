-- Reassign ownership from cloudsqlsuperuser (used during Cloud SQL instance
-- migration) to the application user running this migration.
-- Skipped if the role doesn't exist, e.g. local dev.
-- See: https://docs.nais.io/persistence/cloudsql/how-to/migrate-to-new-instance/
DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cloudsqlsuperuser') THEN
            EXECUTE format('REASSIGN OWNED BY cloudsqlsuperuser TO %I', CURRENT_USER);
        END IF;
    END
$$;
