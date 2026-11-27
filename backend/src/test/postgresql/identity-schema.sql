BEGIN;
DO $test$
DECLARE
    learner uuid;
    another uuid;
    role_uuid uuid;
    issued timestamptz := clock_timestamp();
BEGIN
    IF (SELECT count(*) FROM identity.roles) <> 3 OR
        (SELECT count(*) FROM identity.admin_governance) <> 1 THEN
        RAISE EXCEPTION 'Missing baseline role/governance seed';
    END IF;
    BEGIN
        INSERT INTO identity.users(canonical_email, display_name, status)
        VALUES ('null-status@identity-test.invalid', 'Fixture', NULL);
        RAISE EXCEPTION 'Expected NOT NULL rejection';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO identity.users(canonical_email, display_name, status)
        VALUES ('bad-status@identity-test.invalid', 'Fixture', 'UNKNOWN');
        RAISE EXCEPTION 'Expected enum rejection';
    EXCEPTION WHEN invalid_text_representation THEN NULL;
    END;
    BEGIN
        INSERT INTO identity.users(canonical_email, display_name, password_hash)
        VALUES ('pending-credential@identity-test.invalid','Fixture','nonfunctional-fixture');
        RAISE EXCEPTION 'Expected credential-state rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO identity.users(canonical_email, display_name, status)
        VALUES ('active-invalid@identity-test.invalid','Fixture','ACTIVE');
        RAISE EXCEPTION 'Expected credential-state rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO identity.users(canonical_email, display_name, status, verified_at)
        VALUES ('disabled-partial@identity-test.invalid','Fixture','DISABLED',issued);
        RAISE EXCEPTION 'Expected disabled partial tuple rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;

    INSERT INTO identity.users(canonical_email, display_name)
    VALUES ('pending@identity-test.invalid','Fixture') RETURNING id INTO learner;
    INSERT INTO identity.users(canonical_email, display_name, status, password_hash, verified_at, verification_method)
    VALUES ('active@identity-test.invalid','Fixture','ACTIVE','nonfunctional-fixture',issued,'MAILBOX_TOKEN')
    RETURNING id INTO another;
    BEGIN
        INSERT INTO identity.users(canonical_email, display_name)
        VALUES ('pending@identity-test.invalid','Fixture');
        RAISE EXCEPTION 'Expected duplicate email rejection';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE identity.users SET account_kind = 'DEDICATED_OPERATOR' WHERE id = learner;
        RAISE EXCEPTION 'Expected immutable kind rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE identity.users SET fence_guard = true WHERE id = learner;
        RAISE EXCEPTION 'Expected fixed fence value rejection';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    SELECT id INTO role_uuid FROM identity.roles WHERE code = 'LEARNER';
    INSERT INTO identity.user_roles(user_id, role_id) VALUES (learner, role_uuid);
    BEGIN
        DELETE FROM identity.users WHERE id = learner;
        RAISE EXCEPTION 'Expected restrictive FK rejection';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    INSERT INTO identity.verification_tokens(user_id,email_revision,token_digest,issued_at,expires_at)
    VALUES (learner,0,decode(repeat('aa',32),'hex'),issued,issued + interval '30 minutes');
    BEGIN
        INSERT INTO identity.verification_tokens(user_id,email_revision,token_digest,issued_at,expires_at)
        VALUES (learner,0,decode(repeat('bb',32),'hex'),issued,issued + interval '30 minutes');
        RAISE EXCEPTION 'Expected outstanding-token uniqueness rejection';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    IF has_table_privilege('base_ielts_runtime','identity.users','UPDATE') THEN
        RAISE EXCEPTION 'Unexpected direct users UPDATE grant to runtime';
    END IF;
END;
$test$;
ROLLBACK;
SELECT 'PASS: identity schema constraints and seeds verified on PostgreSQL';
