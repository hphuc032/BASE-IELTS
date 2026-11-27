CREATE SCHEMA governance_audit;
REVOKE ALL ON SCHEMA governance_audit FROM PUBLIC;
CREATE TYPE governance_audit.denial_event AS ENUM (
    'BOOTSTRAP_DENIED', 'APPROVAL_ISSUE_DENIED', 'APPROVAL_CANCEL_DENIED', 'RECOVERY_DENIED'
);
CREATE TYPE governance_audit.denial_reason AS ENUM (
    'NOT_AUTHORIZED', 'INVALID_REQUEST', 'STATE_CONFLICT', 'EXPIRED', 'STALE_REVISION',
    'IDENTITY_COLLISION', 'TRANSACTION_FAILED'
);
REVOKE ALL ON TYPE governance_audit.denial_event, governance_audit.denial_reason FROM PUBLIC;
GRANT USAGE ON SCHEMA governance_audit TO deployment_operator, operator_approver;
GRANT USAGE ON TYPE governance_audit.denial_event, governance_audit.denial_reason
    TO governance_denial_owner, deployment_operator, operator_approver;
GRANT USAGE, CREATE ON SCHEMA governance_audit TO governance_denial_owner;
GRANT USAGE ON SCHEMA identity TO governance_denial_owner;
GRANT SELECT (id) ON identity.recovery_approvals TO governance_denial_owner;
GRANT INSERT (governance_id,recovery_approval_id,actor_principal_id,correlation_id,target_user_id,event,outcome,reason)
    ON identity.admin_audit TO governance_denial_owner;

CREATE FUNCTION governance_audit."appendDenied"(
    correlation_id uuid, event governance_audit.denial_event, reason governance_audit.denial_reason,
    target_user_id uuid, recovery_approval_id uuid
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    attributed_approval uuid;
BEGIN
    IF $1 IS NULL OR $2 IS NULL OR $3 IS NULL THEN
        RAISE EXCEPTION 'Invalid audit request' USING ERRCODE='22023';
    END IF;
    IF NOT (
        (SESSION_USER = 'deployment_operator' AND $2 IN ('BOOTSTRAP_DENIED','RECOVERY_DENIED'))
        OR (SESSION_USER = 'operator_approver' AND $2 IN ('APPROVAL_ISSUE_DENIED','APPROVAL_CANCEL_DENIED'))
    ) THEN
        RAISE EXCEPTION 'Audit operation denied' USING ERRCODE='42501';
    END IF;
    SELECT a.id INTO attributed_approval FROM identity.recovery_approvals a WHERE a.id=$5;
    INSERT INTO identity.admin_audit
        (governance_id,recovery_approval_id,actor_principal_id,correlation_id,target_user_id,event,outcome,reason)
        VALUES (1,attributed_approval,SESSION_USER,$1,$4,$2::text::identity.audit_event,'DENIED',$3::text);
END;
$$;
REVOKE EXECUTE ON FUNCTION governance_audit."appendDenied"(
    uuid,governance_audit.denial_event,governance_audit.denial_reason,uuid,uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION governance_audit."appendDenied"(
    uuid,governance_audit.denial_event,governance_audit.denial_reason,uuid,uuid)
    TO deployment_operator,operator_approver;
ALTER FUNCTION governance_audit."appendDenied"(
    uuid,governance_audit.denial_event,governance_audit.denial_reason,uuid,uuid) OWNER TO governance_denial_owner;
REVOKE CREATE ON SCHEMA governance_audit FROM governance_denial_owner;
