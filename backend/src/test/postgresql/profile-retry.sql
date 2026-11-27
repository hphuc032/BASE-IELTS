\set ON_ERROR_STOP 1
BEGIN;
CREATE TEMP TABLE retry_profile AS
SELECT gen_random_uuid() AS id, clock_timestamp() - interval '1 day' AS original;
INSERT INTO identity.users(id,canonical_email,display_name,account_kind,status,password_hash,verified_at,verification_method,updated_at)
SELECT id,id::text || '@identity-test.invalid','Initial fixture','PUBLIC_LEARNER','ACTIVE',
    'fixture-not-for-authentication',clock_timestamp(),'MAILBOX_TOKEN',original FROM retry_profile;
GRANT SELECT ON retry_profile TO base_ielts_runtime;
SET LOCAL ROLE base_ielts_runtime;
SELECT display_name FROM retry_profile f CROSS JOIN LATERAL identity_api.profile(f.id,0,'Changed once');
RESET ROLE;
CREATE TEMP TABLE committed_profile AS
SELECT u.id,u.updated_at,u.security_revision FROM identity.users u JOIN retry_profile f ON f.id=u.id;
SET LOCAL ROLE base_ielts_runtime;
SELECT display_name FROM retry_profile f CROSS JOIN LATERAL identity_api.profile(f.id,0,'Changed once');
RESET ROLE;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT FROM identity.users u JOIN committed_profile c ON c.id=u.id
        JOIN retry_profile f ON f.id=u.id
        WHERE u.display_name='Changed once' AND u.updated_at > f.original
          AND u.updated_at=c.updated_at AND u.security_revision=c.security_revision
    ) THEN
        RAISE EXCEPTION 'Retry changed the durable state of an already satisfied profile intent';
    END IF;
END $$;
ROLLBACK;
