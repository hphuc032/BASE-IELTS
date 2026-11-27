\set ON_ERROR_STOP 1
BEGIN;
CREATE TEMP TABLE governance_fixture AS SELECT
    'admin-fixture-'||gen_random_uuid()::text||'@test.invalid' AS email,
    gen_random_uuid() AS attempt,
    '$argon2id$v=19$m=19456,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'::text AS fixture_hash;
ALTER TABLE governance_fixture ADD COLUMN account uuid, ADD COLUMN approval uuid;
GRANT SELECT,UPDATE ON governance_fixture TO deployment_operator,operator_approver;
GRANT SELECT ON governance_fixture TO identity_lifecycle_operator;
SET SESSION AUTHORIZATION deployment_operator;
UPDATE governance_fixture SET account=deployment_operator.bootstrap(email,fixture_hash,'independent-development-fixture');
DO $$ BEGIN
    IF (SELECT account IS NULL FROM governance_fixture) THEN RAISE EXCEPTION 'Bootstrap failed'; END IF;
    IF (SELECT deployment_operator.bootstrap(email,fixture_hash,'independent-development-fixture') IS NOT NULL
        FROM governance_fixture) THEN RAISE EXCEPTION 'Bootstrap replay did not return no-op'; END IF;
END $$;
SELECT governance_audit."appendDenied"(attempt,'RECOVERY_DENIED','INVALID_REQUEST',account,NULL)
FROM governance_fixture;
DO $$ BEGIN
    BEGIN
        PERFORM governance_audit."appendDenied"(gen_random_uuid(),'APPROVAL_ISSUE_DENIED','INVALID_REQUEST',NULL,NULL);
        RAISE EXCEPTION 'Role could impersonate approver event';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET SESSION AUTHORIZATION;
SET SESSION AUTHORIZATION identity_lifecycle_operator;
DO $$ BEGIN
    BEGIN
        PERFORM identity_lifecycle.mutate(account,0,'DISABLE',NULL,NULL) FROM governance_fixture;
        RAISE EXCEPTION 'Last Admin could be disabled';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        PERFORM identity_lifecycle.mutate(account,0,'GRANT_ROLE',NULL,'ADMIN') FROM governance_fixture;
        RAISE EXCEPTION 'Generic lifecycle unexpectedly granted ADMIN';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
    BEGIN
        PERFORM identity_lifecycle.mutate(account,0,'SET_CREDENTIAL',fixture_hash,NULL) FROM governance_fixture;
        RAISE EXCEPTION 'Generic lifecycle unexpectedly reset an Admin credential';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET SESSION AUTHORIZATION;
SET SESSION AUTHORIZATION operator_approver;
UPDATE governance_fixture SET approval=operator_approvals.approve(account,0,'RESET_DEDICATED',NULL,
    'deployment_operator','independent-development-fixture',clock_timestamp()+interval '1 hour');
RESET SESSION AUTHORIZATION;
SAVEPOINT before_expiry;
UPDATE identity.recovery_approvals
SET issued_at=clock_timestamp()-interval '2 hours', expires_at=clock_timestamp()-interval '1 hour'
WHERE id=(SELECT approval FROM governance_fixture);
SET SESSION AUTHORIZATION deployment_operator;
DO $$ BEGIN
    IF (SELECT r.outcome FROM governance_fixture f CROSS JOIN LATERAL deployment_operator.recovery_outcome(f.approval) r)
        IS DISTINCT FROM 'UNKNOWN' THEN
        RAISE EXCEPTION 'Elapsed approval expiry alone does not prove rollback';
    END IF;
END $$;
RESET SESSION AUTHORIZATION;
ROLLBACK TO before_expiry;
SET SESSION AUTHORIZATION operator_approver;
DO $$ BEGIN
    IF (SELECT operator_approvals."cancelApproval"(approval,'ISSUER_WITHDRAWAL') FROM governance_fixture)<>'CANCELLED'
        THEN RAISE EXCEPTION 'Cancellation failed'; END IF;
    IF (SELECT operator_approvals."cancelApproval"(approval,'ISSUER_WITHDRAWAL') FROM governance_fixture)<>'ALREADY_CANCELLED'
        THEN RAISE EXCEPTION 'Cancellation retry failed'; END IF;
END $$;
RESET SESSION AUTHORIZATION;
SET SESSION AUTHORIZATION deployment_operator;
DO $$ BEGIN
    BEGIN
        PERFORM deployment_operator.recover(approval,account,0,'RESET_DEDICATED',NULL,fixture_hash) FROM governance_fixture;
        RAISE EXCEPTION 'Cancelled approval was accepted';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET SESSION AUTHORIZATION;
SET SESSION AUTHORIZATION operator_approver;
UPDATE governance_fixture SET approval=operator_approvals.approve(account,0,'RESET_DEDICATED',NULL,
    'deployment_operator','independent-development-fixture',clock_timestamp()+interval '1 hour');
RESET SESSION AUTHORIZATION;
SET SESSION AUTHORIZATION deployment_operator;
SELECT deployment_operator.recover(approval,account,0,'RESET_DEDICATED',NULL,fixture_hash) IS NOT NULL
FROM governance_fixture;
RESET SESSION AUTHORIZATION;
SET SESSION AUTHORIZATION operator_approver;
DO $$ BEGIN
    BEGIN
        PERFORM operator_approvals."cancelApproval"(approval,'ISSUER_WITHDRAWAL') FROM governance_fixture;
        RAISE EXCEPTION 'Consumed approval could be cancelled';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;
RESET SESSION AUTHORIZATION;
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM identity.admin_audit a JOIN governance_fixture f ON a.correlation_id=f.attempt
        WHERE a.actor_principal_id='deployment_operator' AND a.event='RECOVERY_DENIED' AND a.outcome='DENIED')
       OR NOT EXISTS (SELECT FROM identity.users u JOIN governance_fixture f ON u.id=f.account
        WHERE u.status='ACTIVE' AND u.security_revision=1)
        THEN RAISE EXCEPTION 'Missing denial attribution or committed recovery revision'; END IF;
END $$;
ROLLBACK;
