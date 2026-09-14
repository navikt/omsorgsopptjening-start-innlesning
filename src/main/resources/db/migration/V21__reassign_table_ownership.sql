-- Reassign ownership of public schema tables from cloudsqlsuperuser (used
-- during Cloud SQL instance migration) to the application user running this
-- migration. Skipped if the role doesn't exist, e.g. local dev.
-- See: https://docs.nais.io/persistence/cloudsql/how-to/migrate-to-new-instance/
--
-- flyway_schema_history is excluded: Flyway itself actively reads/writes that
-- table for the duration of this migration run, so attempting to reassign its
-- owner from within the same migration self-conflicts on the lock. Flyway
-- doesn't require any particular owner on that table to keep working.
--
-- REASSIGN OWNED BY / ALTER TABLE OWNER TO take an ACCESS EXCLUSIVE lock on
-- each affected table, which can deadlock with concurrent traffic from
-- still-running app replicas during a rolling deploy. A short lock_timeout
-- bounds how long we wait: instead of hanging or deadlocking indefinitely, a
-- lock conflict raises an error that aborts this whole DO block. Since Flyway
-- runs the script in a single transaction, that means all reassignments are
-- rolled back together (all-or-nothing) and Flyway will retry on the next
-- deploy, once conflicting traffic has drained.
DO
$$
    DECLARE
        table_record RECORD;
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'cloudsqlsuperuser') THEN
            SET LOCAL lock_timeout = '5s';

            FOR table_record IN
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                  AND tableowner = 'cloudsqlsuperuser'
                  AND tablename != 'flyway_schema_history'
                LOOP
                    RAISE NOTICE 'Reassigning owner of table % to %', table_record.tablename, CURRENT_USER;
                    EXECUTE format('ALTER TABLE public.%I OWNER TO %I', table_record.tablename, CURRENT_USER);
                END LOOP;

            RAISE NOTICE 'Finished reassigning table ownership to %', CURRENT_USER;
        ELSE
            RAISE NOTICE 'Role cloudsqlsuperuser does not exist, skipping ownership reassignment';
        END IF;
    END
$$;
