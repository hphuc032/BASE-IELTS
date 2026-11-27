BEGIN;
SET LOCAL ROLE base_ielts_runtime;
DO $test$
DECLARE
    account uuid;
    digest bytea := decode(repeat('12',32),'hex');
BEGIN
    account := identity_api.register('verification@identity-test.invalid','Fixture');
    IF account IS NULL OR identity_api.register('verification@identity-test.invalid','Do not overwrite') IS NOT NULL THEN
        RAISE EXCEPTION 'Registration duplicate behavior failed';
    END IF;
    IF NOT identity_api.issue('verification@identity-test.invalid',digest,0,NULL)
       OR identity_api.issue('verification@identity-test.invalid',decode(repeat('34',32),'hex'),0,NULL) THEN
        RAISE EXCEPTION 'Issue or cooldown behavior failed';
    END IF;
    IF NOT identity_api.consume(digest,'$argon2id$nonfunctional-fixture')
       OR identity_api.consume(digest,'$argon2id$nonfunctional-fixture')
       OR identity_api.issue('verification@identity-test.invalid',digest,0,NULL) THEN
        RAISE EXCEPTION 'Atomic activation or token reuse behavior failed';
    END IF;
    IF NOT account_fence_routines."lockAndCheckActor"(account,1) THEN
        RAISE EXCEPTION 'Activation did not advance revision or status';
    END IF;
END;
$test$;
RESET ROLE;
DO $test$
BEGIN
    IF NOT EXISTS (
        SELECT FROM identity.users u JOIN identity.user_roles ur ON ur.user_id=u.id
        JOIN identity.roles r ON r.id=ur.role_id
        WHERE u.canonical_email='verification@identity-test.invalid' AND u.display_name='Fixture'
          AND u.status='ACTIVE' AND u.verification_method='MAILBOX_TOKEN' AND r.code='LEARNER'
    ) THEN RAISE EXCEPTION 'Role or immutable duplicate profile assertion failed'; END IF;
END;
$test$;
ROLLBACK;
SELECT 'PASS: registration/token routines and least-privilege runtime path verified';
