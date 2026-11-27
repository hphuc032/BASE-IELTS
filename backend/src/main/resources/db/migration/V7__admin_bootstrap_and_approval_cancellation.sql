CREATE SCHEMA deployment_operator;
CREATE SCHEMA operator_approvals;
REVOKE ALL ON SCHEMA deployment_operator, operator_approvals FROM PUBLIC;
GRANT USAGE ON SCHEMA deployment_operator TO deployment_operator;
GRANT USAGE ON SCHEMA operator_approvals TO operator_approver;
GRANT USAGE, CREATE ON SCHEMA deployment_operator TO deployment_admin_owner;
GRANT USAGE, CREATE ON SCHEMA operator_approvals TO operator_approval_owner;
GRANT USAGE ON SCHEMA identity TO deployment_admin_owner, operator_approval_owner;
GRANT USAGE ON TYPE identity.recovery_action TO deployment_operator, operator_approver;

GRANT SELECT (id,bootstrap_completed_at) ON identity.admin_governance TO deployment_admin_owner, operator_approval_owner;
GRANT UPDATE (bootstrap_completed_at,bootstrap_event_id) ON identity.admin_governance TO deployment_admin_owner;
GRANT UPDATE (bootstrap_completed_at) ON identity.admin_governance TO operator_approval_owner;
GRANT SELECT (id,canonical_email,display_name,status,account_kind,security_revision,password_hash,verified_at,verification_method,verification_evidence_ref)
    ON identity.users TO deployment_admin_owner;
GRANT INSERT (id,canonical_email,display_name,status,account_kind,password_hash,verified_at,verification_method,verification_evidence_ref)
    ON identity.users TO deployment_admin_owner;
GRANT UPDATE (password_hash,verification_evidence_ref,verified_at,security_revision,status,updated_at)
    ON identity.users TO deployment_admin_owner;
GRANT SELECT (id,canonical_email,status,account_kind,security_revision) ON identity.users TO operator_approval_owner;
GRANT UPDATE (fence_guard) ON identity.users TO operator_approval_owner;
GRANT SELECT (id,code) ON identity.roles TO deployment_admin_owner, operator_approval_owner;
GRANT SELECT (user_id,role_id) ON identity.user_roles TO deployment_admin_owner, operator_approval_owner;
GRANT INSERT (user_id,role_id) ON identity.user_roles TO deployment_admin_owner;
GRANT SELECT ON identity.recovery_approvals TO deployment_admin_owner, operator_approval_owner;
GRANT UPDATE (consumed_at,cancelled_at) ON identity.recovery_approvals TO deployment_admin_owner;
GRANT UPDATE (cancelled_at) ON identity.recovery_approvals TO operator_approval_owner;
GRANT INSERT (id,governance_id,target_user_id,expected_security_revision,action,replacement_email_canonical,
              approver_principal_id,designated_operator_principal_id,operator_scope,evidence_ref,issued_at,expires_at)
    ON identity.recovery_approvals TO operator_approval_owner;
GRANT INSERT (id,governance_id,recovery_approval_id,actor_principal_id,approver_principal_id,correlation_id,
              target_user_id,resulting_user_id,event,outcome,reason,verification_evidence_ref,occurred_at)
    ON identity.admin_audit TO deployment_admin_owner, operator_approval_owner;

CREATE FUNCTION deployment_operator.bootstrap(email text, managed_hash text, evidence text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    account uuid := gen_random_uuid();
    audit uuid := gen_random_uuid();
    occurred timestamptz;
BEGIN
    IF SESSION_USER <> 'deployment_operator' THEN
        RAISE EXCEPTION 'Bootstrap denied' USING ERRCODE='42501';
    END IF;
    PERFORM g.id FROM identity.admin_governance g WHERE g.id=1 FOR UPDATE OF g;
    IF EXISTS (SELECT FROM identity.admin_governance g WHERE g.id=1 AND g.bootstrap_completed_at IS NOT NULL) THEN
        RETURN NULL;
    END IF;
    IF email IS NULL OR email <> lower(btrim(email)) OR length(email) NOT BETWEEN 3 AND 254
       OR managed_hash IS NULL OR managed_hash !~ '^\$argon2id\$v=19\$m=19456,t=2,p=1\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$'
       OR evidence IS NULL OR length(btrim(evidence)) NOT BETWEEN 1 AND 128
       OR EXISTS (SELECT FROM identity.user_roles ur JOIN identity.roles r ON r.id=ur.role_id WHERE r.code='ADMIN')
       OR EXISTS (SELECT FROM identity.users u WHERE u.canonical_email=email) THEN
        RAISE EXCEPTION 'Bootstrap denied' USING ERRCODE='22023';
    END IF;
    occurred := clock_timestamp();
    INSERT INTO identity.users(id,canonical_email,display_name,status,account_kind,password_hash,verified_at,verification_method,verification_evidence_ref)
        VALUES (account,email,'Administrator','ACTIVE','DEDICATED_OPERATOR',managed_hash,occurred,'OPERATOR_ATTESTED',evidence);
    INSERT INTO identity.user_roles(user_id,role_id)
        SELECT account,r.id FROM identity.roles r WHERE r.code='ADMIN';
    IF NOT FOUND THEN RAISE EXCEPTION 'Admin role missing'; END IF;
    INSERT INTO identity.admin_audit(id,governance_id,actor_principal_id,correlation_id,target_user_id,resulting_user_id,event,outcome,verification_evidence_ref,occurred_at)
        VALUES (audit,1,SESSION_USER,gen_random_uuid(),account,account,'BOOTSTRAP_SUCCESS','SUCCESS',evidence,occurred);
    UPDATE identity.admin_governance SET bootstrap_completed_at=occurred, bootstrap_event_id=audit WHERE id=1;
    RETURN account;
END;
$$;

CREATE TYPE operator_approvals.cancel_reason AS ENUM ('ISSUER_WITHDRAWAL','EVIDENCE_CHANGED','REQUEST_WITHDRAWN');
REVOKE ALL ON TYPE operator_approvals.cancel_reason FROM PUBLIC;
GRANT USAGE ON TYPE operator_approvals.cancel_reason TO operator_approver, operator_approval_owner;
CREATE FUNCTION operator_approvals."cancelApproval"(approval_id uuid, reason operator_approvals.cancel_reason)
RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    target uuid;
    approval identity.recovery_approvals%ROWTYPE;
    observed timestamptz;
BEGIN
    IF SESSION_USER <> 'operator_approver' THEN
        RAISE EXCEPTION 'Cancellation denied' USING ERRCODE='42501';
    END IF;
    IF approval_id IS NULL OR reason IS NULL THEN
        RAISE EXCEPTION 'Invalid cancellation' USING ERRCODE='22023';
    END IF;
    SELECT a.target_user_id INTO target FROM identity.recovery_approvals a WHERE a.id=approval_id;
    PERFORM g.id FROM identity.admin_governance g WHERE g.id=1 FOR NO KEY UPDATE OF g;
    PERFORM u.id FROM identity.users u WHERE u.id=target FOR NO KEY UPDATE OF u;
    SELECT a.* INTO approval FROM identity.recovery_approvals a WHERE a.id=approval_id FOR NO KEY UPDATE OF a;
    IF NOT FOUND OR approval.target_user_id IS DISTINCT FROM target OR approval.approver_principal_id <> SESSION_USER THEN
        RAISE EXCEPTION 'Cancellation denied' USING ERRCODE='42501';
    END IF;
    IF approval.cancelled_at IS NOT NULL THEN RETURN 'ALREADY_CANCELLED'; END IF;
    observed := clock_timestamp();
    IF approval.consumed_at IS NOT NULL OR approval.expires_at <= observed THEN
        RAISE EXCEPTION 'Approval is terminal' USING ERRCODE='23514';
    END IF;
    UPDATE identity.recovery_approvals SET cancelled_at=observed WHERE id=approval_id;
    INSERT INTO identity.admin_audit(governance_id,recovery_approval_id,actor_principal_id,correlation_id,target_user_id,event,outcome,reason,occurred_at)
        VALUES (1,approval_id,SESSION_USER,gen_random_uuid(),target,'APPROVAL_CANCELLED','SUCCESS',reason::text,observed);
    RETURN 'CANCELLED';
END;
$$;

CREATE FUNCTION operator_approvals.approve(
    target uuid, revision bigint, action identity.recovery_action, replacement text,
    designated_operator text, evidence text, expires_utc timestamptz
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    email text;
    observed timestamptz;
    approval uuid := gen_random_uuid();
BEGIN
    IF SESSION_USER <> 'operator_approver' OR designated_operator IS DISTINCT FROM 'deployment_operator' THEN
        RAISE EXCEPTION 'Approval denied' USING ERRCODE='42501';
    END IF;
    PERFORM g.id FROM identity.admin_governance g WHERE g.id=1 FOR NO KEY UPDATE OF g;
    SELECT u.canonical_email INTO email FROM identity.users u
    WHERE u.id=target AND u.security_revision=revision AND u.status='ACTIVE' AND u.account_kind='DEDICATED_OPERATOR'
      AND EXISTS (SELECT FROM identity.user_roles ur JOIN identity.roles r ON r.id=ur.role_id
                  WHERE ur.user_id=u.id AND r.code='ADMIN')
    FOR NO KEY UPDATE OF u;
    observed := clock_timestamp();
    IF NOT FOUND OR action IS NULL OR (action='REPLACE_DEDICATED') <> (replacement IS NOT NULL)
       OR (replacement IS NOT NULL AND (replacement=email OR replacement<>lower(btrim(replacement))
           OR length(replacement) NOT BETWEEN 3 AND 254))
       OR evidence IS NULL OR length(btrim(evidence)) NOT BETWEEN 1 AND 128
       OR expires_utc IS NULL OR NOT isfinite(expires_utc) OR expires_utc <= observed THEN
        RAISE EXCEPTION 'Approval denied' USING ERRCODE='22023';
    END IF;
    INSERT INTO identity.recovery_approvals(id,governance_id,target_user_id,expected_security_revision,action,
        replacement_email_canonical,approver_principal_id,designated_operator_principal_id,operator_scope,evidence_ref,issued_at,expires_at)
        VALUES (approval,1,target,revision,action,replacement,SESSION_USER,designated_operator,'DEDICATED_ADMIN',evidence,observed,expires_utc);
    INSERT INTO identity.admin_audit(governance_id,recovery_approval_id,actor_principal_id,approver_principal_id,correlation_id,
        target_user_id,event,outcome,verification_evidence_ref,occurred_at)
        VALUES (1,approval,SESSION_USER,SESSION_USER,gen_random_uuid(),target,'APPROVAL_ISSUED','SUCCESS',evidence,observed);
    RETURN approval;
END;
$$;

CREATE FUNCTION deployment_operator.recover(
    approval_id uuid, target uuid, revision bigint, action identity.recovery_action, replacement text, managed_hash text
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    grant_row identity.recovery_approvals%ROWTYPE;
    result uuid := target;
    observed timestamptz;
BEGIN
    IF SESSION_USER <> 'deployment_operator' THEN
        RAISE EXCEPTION 'Recovery denied' USING ERRCODE='42501';
    END IF;
    IF managed_hash IS NULL OR managed_hash !~ '^\$argon2id\$v=19\$m=19456,t=2,p=1\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$' THEN
        RAISE EXCEPTION 'Recovery denied' USING ERRCODE='22023';
    END IF;
    PERFORM g.id FROM identity.admin_governance g WHERE g.id=1 AND g.bootstrap_completed_at IS NOT NULL FOR NO KEY UPDATE OF g;
    IF NOT FOUND THEN RAISE EXCEPTION 'Recovery denied' USING ERRCODE='42501'; END IF;
    PERFORM u.id FROM identity.users u
    WHERE u.id=target AND u.security_revision=revision AND u.status='ACTIVE' AND u.account_kind='DEDICATED_OPERATOR'
      AND EXISTS (SELECT FROM identity.user_roles ur JOIN identity.roles r ON r.id=ur.role_id
                  WHERE ur.user_id=u.id AND r.code='ADMIN')
    FOR NO KEY UPDATE OF u;
    IF NOT FOUND THEN RAISE EXCEPTION 'Recovery denied' USING ERRCODE='42501'; END IF;
    SELECT a.* INTO grant_row FROM identity.recovery_approvals a WHERE a.id=approval_id FOR NO KEY UPDATE OF a;
    observed := clock_timestamp();
    IF NOT FOUND OR grant_row.target_user_id IS DISTINCT FROM target OR grant_row.expected_security_revision IS DISTINCT FROM revision
       OR grant_row.action IS DISTINCT FROM action OR grant_row.replacement_email_canonical IS DISTINCT FROM replacement
       OR grant_row.designated_operator_principal_id <> SESSION_USER OR grant_row.operator_scope <> 'DEDICATED_ADMIN'
       OR grant_row.approver_principal_id <> 'operator_approver' OR grant_row.consumed_at IS NOT NULL
       OR grant_row.cancelled_at IS NOT NULL OR grant_row.expires_at <= observed THEN
        RAISE EXCEPTION 'Recovery denied' USING ERRCODE='42501';
    END IF;
    UPDATE identity.recovery_approvals SET consumed_at=observed WHERE id=approval_id;
    IF action='REPLACE_DEDICATED' THEN
        result := gen_random_uuid();
        INSERT INTO identity.users(id,canonical_email,display_name,status,account_kind,password_hash,verified_at,verification_method,verification_evidence_ref)
            VALUES (result,replacement,'Administrator','ACTIVE','DEDICATED_OPERATOR',managed_hash,observed,'OPERATOR_ATTESTED',grant_row.evidence_ref);
        INSERT INTO identity.user_roles(user_id,role_id)
            SELECT result,r.id FROM identity.roles r WHERE r.code='ADMIN';
        IF NOT FOUND THEN RAISE EXCEPTION 'Admin role missing'; END IF;
        UPDATE identity.users SET status='DISABLED',security_revision=security_revision+1,updated_at=observed WHERE id=target;
    ELSE
        UPDATE identity.users SET password_hash=managed_hash, verified_at=observed, verification_evidence_ref=grant_row.evidence_ref,
            security_revision=security_revision+1,updated_at=observed WHERE id=target;
    END IF;
    UPDATE identity.recovery_approvals SET cancelled_at=observed
    WHERE target_user_id=target AND consumed_at IS NULL AND cancelled_at IS NULL AND expires_at>observed;
    IF NOT EXISTS (
        SELECT FROM identity.users u JOIN identity.user_roles ur ON ur.user_id=u.id JOIN identity.roles r ON r.id=ur.role_id
        WHERE u.status='ACTIVE' AND u.account_kind='DEDICATED_OPERATOR' AND u.password_hash IS NOT NULL
          AND u.verified_at IS NOT NULL AND u.verification_evidence_ref IS NOT NULL AND r.code='ADMIN'
    ) THEN
        RAISE EXCEPTION 'Usable Admin required' USING ERRCODE='23514';
    END IF;
    INSERT INTO identity.admin_audit(governance_id,recovery_approval_id,actor_principal_id,approver_principal_id,correlation_id,
        target_user_id,resulting_user_id,event,outcome,verification_evidence_ref,occurred_at)
        VALUES (1,approval_id,SESSION_USER,grant_row.approver_principal_id,gen_random_uuid(),target,result,
            'RECOVERY_SUCCESS','SUCCESS',grant_row.evidence_ref,observed);
    RETURN result;
END;
$$;

REVOKE EXECUTE ON FUNCTION operator_approvals.approve(uuid,bigint,identity.recovery_action,text,text,text,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION operator_approvals.approve(uuid,bigint,identity.recovery_action,text,text,text,timestamptz) TO operator_approver;
ALTER FUNCTION operator_approvals.approve(uuid,bigint,identity.recovery_action,text,text,text,timestamptz) OWNER TO operator_approval_owner;
REVOKE EXECUTE ON FUNCTION deployment_operator.recover(uuid,uuid,bigint,identity.recovery_action,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION deployment_operator.recover(uuid,uuid,bigint,identity.recovery_action,text,text) TO deployment_operator;
ALTER FUNCTION deployment_operator.recover(uuid,uuid,bigint,identity.recovery_action,text,text) OWNER TO deployment_admin_owner;

REVOKE EXECUTE ON FUNCTION deployment_operator.bootstrap(text,text,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION deployment_operator.bootstrap(text,text,text) TO deployment_operator;
ALTER FUNCTION deployment_operator.bootstrap(text,text,text) OWNER TO deployment_admin_owner;
REVOKE CREATE ON SCHEMA deployment_operator FROM deployment_admin_owner;

REVOKE EXECUTE ON FUNCTION operator_approvals."cancelApproval"(uuid,operator_approvals.cancel_reason) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION operator_approvals."cancelApproval"(uuid,operator_approvals.cancel_reason) TO operator_approver;
ALTER FUNCTION operator_approvals."cancelApproval"(uuid,operator_approvals.cancel_reason) OWNER TO operator_approval_owner;
REVOKE CREATE ON SCHEMA operator_approvals FROM operator_approval_owner;
