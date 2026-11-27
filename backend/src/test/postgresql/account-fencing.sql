BEGIN;
INSERT INTO identity.users(id,canonical_email,display_name,status,password_hash,verified_at,verification_method)
VALUES ('10000000-0000-4000-8000-000000000001','fence@identity-test.invalid','Fixture','ACTIVE','nonfunctional-fixture',clock_timestamp(),'MAILBOX_TOKEN');
DO $test$
BEGIN
    IF has_column_privilege('base_ielts_runtime','identity.users','fence_guard','UPDATE')
       OR has_column_privilege('account_fence_owner','identity.users','security_revision','UPDATE')
       OR has_column_privilege('account_fence_owner','identity.users','password_hash','UPDATE')
       OR NOT has_column_privilege('account_fence_owner','identity.users','fence_guard','UPDATE') THEN
        RAISE EXCEPTION 'Unexpected effective column privileges';
    END IF;
    IF pg_has_role('base_ielts_runtime','account_fence_owner','SET')
       OR has_schema_privilege('base_ielts_runtime','account_fence_routines','CREATE')
       OR has_schema_privilege('account_fence_owner','account_fence_routines','CREATE') THEN
        RAISE EXCEPTION 'Unexpected role assumption or schema CREATE privilege';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
        CROSS JOIN LATERAL aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
        WHERE n.nspname='account_fence_routines' AND a.grantee=0 AND a.privilege_type='EXECUTE'
    ) THEN
        RAISE EXCEPTION 'Public can execute a fence routine';
    END IF;
END;
$test$;
SET LOCAL ROLE base_ielts_runtime;
DO $test$
DECLARE
    matched uuid;
    account uuid := '10000000-0000-4000-8000-000000000001';
BEGIN
    SELECT user_id INTO matched FROM account_fence_routines."lockForLogin"(account);
    IF matched IS DISTINCT FROM account
       OR NOT account_fence_routines."lockAndCheckActor"(account,0)
       OR account_fence_routines."lockAndCheckActor"(account,1) THEN
        RAISE EXCEPTION 'Fence tuple/revision predicate invalid';
    END IF;
    BEGIN
        UPDATE identity.users SET security_revision=99 WHERE id=account;
        RAISE EXCEPTION 'Unexpected direct account write';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END;
$test$;
ROLLBACK;
SELECT 'PASS: restricted fence execution, effective grants and revision checks verified';
