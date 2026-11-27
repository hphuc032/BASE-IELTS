CREATE SCHEMA identity_lifecycle;
REVOKE ALL ON SCHEMA identity_lifecycle FROM PUBLIC;
CREATE TYPE identity_lifecycle.action AS ENUM ('DISABLE','SET_CREDENTIAL','GRANT_ROLE','REVOKE_ROLE');
CREATE TYPE identity_lifecycle.role_code AS ENUM ('LEARNER','TEACHER','ADMIN');
REVOKE ALL ON TYPE identity_lifecycle.action,identity_lifecycle.role_code FROM PUBLIC;
GRANT USAGE ON SCHEMA identity_lifecycle TO identity_lifecycle_operator;
GRANT USAGE ON TYPE identity_lifecycle.action,identity_lifecycle.role_code TO identity_lifecycle_operator,identity_lifecycle_owner;
GRANT USAGE,CREATE ON SCHEMA identity_lifecycle TO identity_lifecycle_owner;
GRANT USAGE ON SCHEMA identity TO identity_lifecycle_owner;
GRANT SELECT(id,bootstrap_completed_at), UPDATE(bootstrap_completed_at) ON identity.admin_governance TO identity_lifecycle_owner;
GRANT SELECT(id,status,account_kind,security_revision,password_hash,verified_at,verification_evidence_ref),
    UPDATE(status,password_hash,security_revision,updated_at) ON identity.users TO identity_lifecycle_owner;
GRANT SELECT(id,code) ON identity.roles TO identity_lifecycle_owner;
GRANT SELECT(user_id,role_id),INSERT(user_id,role_id),DELETE ON identity.user_roles TO identity_lifecycle_owner;
GRANT SELECT(target_user_id,consumed_at,cancelled_at,expires_at),UPDATE(cancelled_at) ON identity.recovery_approvals TO identity_lifecycle_owner;
GRANT INSERT(governance_id,actor_principal_id,correlation_id,target_user_id,event,outcome,occurred_at)
    ON identity.admin_audit TO identity_lifecycle_owner;

CREATE FUNCTION identity_lifecycle.mutate(target uuid, revision bigint, change identity_lifecycle.action,
    credential_hash text, membership identity_lifecycle.role_code) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp SET lock_timeout='250ms' AS $$
DECLARE
    target_kind identity.account_kind;
    target_status identity.account_status;
    member_role uuid;
    observed timestamptz;
BEGIN
    IF SESSION_USER <> 'identity_lifecycle_operator' THEN
        RAISE EXCEPTION 'Lifecycle operation denied' USING ERRCODE='42501';
    END IF;
    IF target IS NULL OR revision IS NULL OR change IS NULL
      OR ((change='SET_CREDENTIAL')<>(credential_hash IS NOT NULL))
      OR ((change IN ('GRANT_ROLE','REVOKE_ROLE'))<>(membership IS NOT NULL))
      OR (change='GRANT_ROLE' AND membership='ADMIN') THEN
        RAISE EXCEPTION 'Invalid lifecycle operation' USING ERRCODE='22023';
    END IF;
    PERFORM g.id FROM identity.admin_governance g WHERE g.id=1 FOR NO KEY UPDATE OF g;
    SELECT u.account_kind,u.status INTO target_kind,target_status FROM identity.users u
        WHERE u.id=target AND u.security_revision=revision FOR NO KEY UPDATE OF u;
    IF NOT FOUND THEN RETURN false; END IF;
    observed := clock_timestamp();
    IF change='DISABLE' THEN
        IF target_status='DISABLED' THEN RETURN false; END IF;
        UPDATE identity.users SET status='DISABLED' WHERE id=target;
        UPDATE identity.recovery_approvals SET cancelled_at=observed
            WHERE target_user_id=target AND consumed_at IS NULL AND cancelled_at IS NULL AND expires_at>observed;
        INSERT INTO identity.admin_audit(governance_id,actor_principal_id,correlation_id,target_user_id,event,outcome,occurred_at)
            VALUES(1,SESSION_USER,gen_random_uuid(),target,'ACCOUNT_DISABLED','SUCCESS',observed);
    ELSIF change='SET_CREDENTIAL' THEN
        IF target_status<>'ACTIVE' OR target_kind='DEDICATED_OPERATOR'
           OR credential_hash !~ '^\$argon2id\$v=19\$m=19456,t=2,p=1\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$' THEN
            RAISE EXCEPTION 'Credential change denied' USING ERRCODE='42501';
        END IF;
        UPDATE identity.users SET password_hash=credential_hash WHERE id=target;
    ELSE
        SELECT r.id INTO member_role FROM identity.roles r WHERE r.code=membership::text;
        IF change='GRANT_ROLE' THEN
            INSERT INTO identity.user_roles(user_id,role_id) VALUES(target,member_role) ON CONFLICT DO NOTHING;
        ELSE
            DELETE FROM identity.user_roles ur WHERE ur.user_id=target AND ur.role_id=member_role;
        END IF;
        IF NOT FOUND THEN RETURN false; END IF;
    END IF;
    IF EXISTS (SELECT FROM identity.admin_governance g WHERE g.id=1 AND g.bootstrap_completed_at IS NOT NULL)
       AND NOT EXISTS (
        SELECT FROM identity.users u JOIN identity.user_roles ur ON ur.user_id=u.id JOIN identity.roles r ON r.id=ur.role_id
        WHERE u.status='ACTIVE' AND u.account_kind='DEDICATED_OPERATOR' AND u.password_hash IS NOT NULL
          AND u.verified_at IS NOT NULL AND u.verification_evidence_ref IS NOT NULL AND r.code='ADMIN'
    ) THEN
        RAISE EXCEPTION 'Usable Admin required' USING ERRCODE='23514';
    END IF;
    UPDATE identity.users SET security_revision=security_revision+1,updated_at=observed WHERE id=target;
    RETURN true;
END;
$$;
REVOKE EXECUTE ON FUNCTION identity_lifecycle.mutate(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity_lifecycle.mutate(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code)
    TO identity_lifecycle_operator;
ALTER FUNCTION identity_lifecycle.mutate(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code) OWNER TO identity_lifecycle_owner;
REVOKE CREATE ON SCHEMA identity_lifecycle FROM identity_lifecycle_owner;
