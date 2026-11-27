-- Read-only reconciliation uses the authenticated database identity, never a caller-supplied actor.
GRANT SELECT (id, expected_security_revision, designated_operator_principal_id, operator_scope,
    consumed_at, cancelled_at, expires_at) ON identity.recovery_approvals TO deployment_admin_owner;
GRANT SELECT (recovery_approval_id, actor_principal_id, event, outcome)
    ON identity.admin_audit TO deployment_admin_owner;
GRANT CREATE ON SCHEMA deployment_operator TO deployment_admin_owner;
CREATE FUNCTION deployment_operator.recovery_outcome(approval_id uuid)
RETURNS TABLE(outcome text, revision bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
    SELECT CASE WHEN a.consumed_at IS NOT NULL AND success.found THEN 'COMMITTED'
                WHEN a.consumed_at IS NULL AND (a.cancelled_at IS NOT NULL OR a.expires_at <= statement_timestamp())
                    THEN 'NOT_COMMITTED'
                ELSE 'UNKNOWN' END,
           CASE WHEN a.consumed_at IS NOT NULL AND success.found THEN a.expected_security_revision + 1 END
    FROM identity.recovery_approvals a
    CROSS JOIN LATERAL (SELECT EXISTS (
        SELECT 1 FROM identity.admin_audit e WHERE e.recovery_approval_id = a.id
          AND e.actor_principal_id = SESSION_USER AND e.event = 'RECOVERY_SUCCESS' AND e.outcome = 'SUCCESS'
    ) AS found) success
    WHERE SESSION_USER = 'deployment_operator' AND a.id = $1
      AND a.designated_operator_principal_id = SESSION_USER AND a.operator_scope = 'DEDICATED_ADMIN';
$$;
REVOKE ALL ON FUNCTION deployment_operator.recovery_outcome(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION deployment_operator.recovery_outcome(uuid) TO deployment_operator;
ALTER FUNCTION deployment_operator.recovery_outcome(uuid) OWNER TO deployment_admin_owner;
REVOKE CREATE ON SCHEMA deployment_operator FROM deployment_admin_owner;

CREATE TABLE identity.credential_reset_receipts (
    operation_id uuid PRIMARY KEY,
    actor_principal_id text NOT NULL,
    target_user_id uuid NOT NULL REFERENCES identity.users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    resulting_revision bigint NOT NULL CHECK(resulting_revision > 0)
);
REVOKE ALL ON identity.credential_reset_receipts FROM PUBLIC;
GRANT SELECT, INSERT ON identity.credential_reset_receipts TO identity_lifecycle_owner;

GRANT CREATE ON SCHEMA identity_lifecycle TO identity_lifecycle_owner;
SET LOCAL ROLE identity_lifecycle_owner;
ALTER FUNCTION identity_lifecycle.mutate(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code)
    RENAME TO mutate_internal;
REVOKE EXECUTE ON FUNCTION identity_lifecycle.mutate_internal(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code)
    FROM identity_lifecycle_operator;

CREATE FUNCTION identity_lifecycle.mutate(target uuid, revision bigint, change identity_lifecycle.action,
    credential_hash text, membership identity_lifecycle.role_code) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
    IF change = 'SET_CREDENTIAL' THEN
        RAISE EXCEPTION 'Credential reset requires an operation reference' USING ERRCODE='42501';
    END IF;
    RETURN identity_lifecycle.mutate_internal(target,revision,change,credential_hash,membership);
END;
$$;

CREATE FUNCTION identity_lifecycle.reset_credential(operation_id uuid, target uuid, revision bigint, credential_hash text)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp SET lock_timeout='250ms' AS $$
BEGIN
    IF SESSION_USER <> 'identity_lifecycle_operator' THEN
        RAISE EXCEPTION 'Lifecycle operation denied' USING ERRCODE='42501';
    END IF;
    IF operation_id IS NULL THEN
        RAISE EXCEPTION 'Operation reference required' USING ERRCODE='22023';
    END IF;
    -- Match the original lifecycle lock order. The second lock inside mutate_internal is reentrant.
    PERFORM g.id FROM identity.admin_governance g WHERE g.id=1 FOR NO KEY UPDATE OF g;
    IF EXISTS (SELECT 1 FROM identity.credential_reset_receipts r WHERE r.operation_id=$1) THEN
        RETURN false;
    END IF;
    IF NOT identity_lifecycle.mutate_internal(target,revision,'SET_CREDENTIAL',credential_hash,NULL) THEN
        RETURN false;
    END IF;
    INSERT INTO identity.credential_reset_receipts(operation_id,actor_principal_id,target_user_id,resulting_revision)
    VALUES ($1, SESSION_USER, target, revision + 1);
    RETURN true;
END;
$$;

CREATE FUNCTION identity_lifecycle.reset_outcome(operation_id uuid)
RETURNS TABLE(outcome text, revision bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
    SELECT 'COMMITTED'::text, r.resulting_revision
    FROM identity.credential_reset_receipts r
    WHERE SESSION_USER = 'identity_lifecycle_operator'
      AND r.operation_id=$1 AND r.actor_principal_id=SESSION_USER;
$$;

REVOKE ALL ON FUNCTION identity_lifecycle.mutate(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code),
    identity_lifecycle.reset_credential(uuid,uuid,bigint,text), identity_lifecycle.reset_outcome(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity_lifecycle.mutate(uuid,bigint,identity_lifecycle.action,text,identity_lifecycle.role_code),
    identity_lifecycle.reset_credential(uuid,uuid,bigint,text), identity_lifecycle.reset_outcome(uuid)
    TO identity_lifecycle_operator;
RESET ROLE;
SET LOCAL ROLE identity_migration_owner;
REVOKE CREATE ON SCHEMA identity_lifecycle FROM identity_lifecycle_owner;
