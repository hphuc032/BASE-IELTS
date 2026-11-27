\set ON_ERROR_STOP 1
BEGIN;
CREATE TEMP TABLE cleanup_fixture AS
SELECT gen_random_uuid() AS live, gen_random_uuid() AS recent,
       gen_random_uuid() AS expired, gen_random_uuid() AS consumed, gen_random_uuid() AS invalidated;
GRANT SELECT ON cleanup_fixture TO maintenance_cleanup;
INSERT INTO identity.users(id, canonical_email, display_name)
SELECT f.id, f.id::text || '@retention-test.invalid', 'Retention fixture'
FROM cleanup_fixture c CROSS JOIN LATERAL (VALUES(c.live),(c.recent),(c.expired),(c.consumed),(c.invalidated)) f(id);
INSERT INTO identity.verification_tokens(user_id,email_revision,token_digest,issued_at,expires_at,consumed_at,invalidated_at)
SELECT f.id, 0, sha256(uuid_send(f.id)), clock_timestamp()-interval '20 days',
    clock_timestamp()+f.expiry, clock_timestamp()+f.consumed, clock_timestamp()+f.invalidated
FROM cleanup_fixture c CROSS JOIN LATERAL (
 VALUES (c.live,interval '1 day',NULL::interval,NULL::interval),
 (c.recent,interval '-1 day',NULL,NULL),
 (c.expired,interval '-8 days',NULL,NULL),
 (c.consumed,interval '-1 day',interval '-2 days',NULL),
 (c.invalidated,interval '-9 days',NULL,interval '-2 days')
) f(id,expiry,consumed,invalidated);
SET LOCAL ROLE maintenance_cleanup;
DO $$
BEGIN
    BEGIN
        PERFORM identity_maintenance."cleanupExpiredTokens"('-infinity'::timestamptz,1);
        RAISE EXCEPTION 'Non-finite cutoff was accepted';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
    BEGIN
        PERFORM identity_maintenance."cleanupExpiredTokens"(clock_timestamp()-interval '168 hours',501);
        RAISE EXCEPTION 'Oversized cleanup batch was accepted';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
    BEGIN
        PERFORM identity_maintenance."cleanupExpiredTokens"(clock_timestamp(),1);
        RAISE EXCEPTION 'Recently terminal tokens must be protected';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
END $$;
SAVEPOINT before_cleanup;
SELECT identity_maintenance."cleanupExpiredTokens"(clock_timestamp()-interval '168 hours',500);
ROLLBACK TO before_cleanup;
RESET ROLE;
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM identity.verification_tokens t JOIN cleanup_fixture c ON t.user_id=c.expired) THEN
        RAISE EXCEPTION 'Cleanup escaped the caller transaction';
    END IF;
END $$;
SET LOCAL ROLE maintenance_cleanup;
DO $$
BEGIN
    PERFORM identity_maintenance."cleanupExpiredTokens"(clock_timestamp()-interval '168 hours',500);
END $$;
RESET ROLE;
DO $$
BEGIN
    IF EXISTS (SELECT FROM identity.verification_tokens t JOIN cleanup_fixture c ON t.user_id=c.expired)
      OR (SELECT count(*) FROM identity.verification_tokens t JOIN cleanup_fixture c
           ON t.user_id IN(c.live,c.recent,c.consumed,c.invalidated)) <> 4 THEN
        RAISE EXCEPTION 'Incorrect terminal-state retention';
    END IF;
END $$;
SET LOCAL ROLE base_ielts_runtime;
DO $$
BEGIN
    BEGIN
        PERFORM identity_maintenance."cleanupExpiredTokens"(clock_timestamp()-interval '168 hours',1);
        RAISE EXCEPTION 'Application runtime unexpectedly executed cleanup';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET ROLE;
ROLLBACK;
