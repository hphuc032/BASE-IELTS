-- Only a committed cancellation proves that a pending recovery cannot later commit.
GRANT CREATE ON SCHEMA deployment_operator TO deployment_admin_owner;
SET LOCAL ROLE deployment_admin_owner;
CREATE OR REPLACE FUNCTION deployment_operator.recovery_outcome(approval_id uuid)
RETURNS TABLE(outcome text, revision bigint)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
    SELECT CASE WHEN a.consumed_at IS NOT NULL AND success.found THEN 'COMMITTED'
                WHEN a.consumed_at IS NULL AND a.cancelled_at IS NOT NULL
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
RESET ROLE;
SET LOCAL ROLE identity_migration_owner;
REVOKE CREATE ON SCHEMA deployment_operator FROM deployment_admin_owner;
