CREATE SCHEMA IF NOT EXISTS identity;
REVOKE ALL ON SCHEMA identity FROM PUBLIC;

CREATE TYPE identity.account_status AS ENUM ('PENDING', 'ACTIVE', 'DISABLED');
CREATE TYPE identity.account_kind AS ENUM ('PUBLIC_LEARNER', 'DEDICATED_OPERATOR');
CREATE TYPE identity.verification_method AS ENUM ('MAILBOX_TOKEN', 'OPERATOR_ATTESTED');
CREATE TYPE identity.recovery_action AS ENUM ('RESET_DEDICATED', 'REPLACE_DEDICATED');
CREATE TYPE identity.operator_scope AS ENUM ('DEDICATED_ADMIN');
CREATE TYPE identity.audit_outcome AS ENUM ('SUCCESS', 'DENIED');
CREATE TYPE identity.audit_event AS ENUM (
    'BOOTSTRAP_SUCCESS', 'APPROVAL_ISSUED', 'APPROVAL_CANCELLED', 'RECOVERY_SUCCESS',
    'ACCOUNT_DISABLED', 'BOOTSTRAP_DENIED', 'APPROVAL_ISSUE_DENIED',
    'APPROVAL_CANCEL_DENIED', 'RECOVERY_DENIED'
);

CREATE TABLE identity.users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_email text NOT NULL UNIQUE CHECK (length(canonical_email) BETWEEN 3 AND 254),
    display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
    status identity.account_status NOT NULL DEFAULT 'PENDING',
    account_kind identity.account_kind NOT NULL DEFAULT 'PUBLIC_LEARNER',
    password_hash text,
    verified_at timestamptz,
    verification_method identity.verification_method,
    verification_evidence_ref text,
    security_revision bigint NOT NULL DEFAULT 0 CHECK (security_revision >= 0),
    email_revision bigint NOT NULL DEFAULT 0 CHECK (email_revision >= 0),
    fence_guard boolean NOT NULL DEFAULT false CONSTRAINT users_fence_guard_fixed CHECK (fence_guard IS FALSE),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT users_credential_state CHECK (
        (status = 'PENDING' AND password_hash IS NULL AND verified_at IS NULL AND verification_method IS NULL)
        OR (status = 'ACTIVE' AND password_hash IS NOT NULL AND verified_at IS NOT NULL AND verification_method IS NOT NULL)
        OR (status = 'DISABLED' AND (
            (password_hash IS NULL AND verified_at IS NULL AND verification_method IS NULL)
            OR (password_hash IS NOT NULL AND verified_at IS NOT NULL AND verification_method IS NOT NULL)
        ))
    ),
    CONSTRAINT users_verification_evidence CHECK (
        (verification_method IS DISTINCT FROM 'OPERATOR_ATTESTED' AND verification_evidence_ref IS NULL)
        OR (verification_method = 'OPERATOR_ATTESTED' AND account_kind = 'DEDICATED_OPERATOR'
            AND nullif(btrim(verification_evidence_ref), '') IS NOT NULL)
    ),
    CONSTRAINT users_kind_verification CHECK (
        (account_kind = 'PUBLIC_LEARNER' AND verification_method IS DISTINCT FROM 'OPERATOR_ATTESTED')
        OR (account_kind = 'DEDICATED_OPERATOR' AND verification_method IS NOT DISTINCT FROM 'OPERATOR_ATTESTED')
    )
);

CREATE FUNCTION identity.keep_account_kind() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF NEW.account_kind IS DISTINCT FROM OLD.account_kind THEN
        RAISE EXCEPTION 'account kind is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE EXECUTE ON FUNCTION identity.keep_account_kind() FROM PUBLIC;
CREATE TRIGGER users_account_kind_immutable BEFORE UPDATE OF account_kind ON identity.users
FOR EACH ROW EXECUTE FUNCTION identity.keep_account_kind();

CREATE TABLE identity.roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE CHECK (code IN ('LEARNER', 'TEACHER', 'ADMIN'))
);
INSERT INTO identity.roles (code) VALUES ('LEARNER'), ('TEACHER'), ('ADMIN');

CREATE TABLE identity.user_roles (
    user_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    role_id uuid NOT NULL REFERENCES identity.roles(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    assigned_by uuid REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    assigned_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX user_roles_role_idx ON identity.user_roles(role_id);
CREATE INDEX user_roles_grantor_idx ON identity.user_roles(assigned_by) WHERE assigned_by IS NOT NULL;

CREATE TABLE identity.verification_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    email_revision bigint NOT NULL CHECK (email_revision >= 0),
    token_digest bytea NOT NULL UNIQUE CHECK (octet_length(token_digest) = 32),
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > issued_at),
    consumed_at timestamptz CHECK (consumed_at >= issued_at AND consumed_at < expires_at),
    invalidated_at timestamptz CHECK (invalidated_at >= issued_at),
    CONSTRAINT token_single_terminal_action CHECK (num_nonnulls(consumed_at, invalidated_at) <= 1)
);
CREATE UNIQUE INDEX verification_tokens_one_outstanding_idx
    ON identity.verification_tokens(user_id) WHERE consumed_at IS NULL AND invalidated_at IS NULL;
CREATE INDEX verification_tokens_user_idx ON identity.verification_tokens(user_id);
CREATE INDEX verification_tokens_retention_idx
    ON identity.verification_tokens((coalesce(greatest(consumed_at, invalidated_at), expires_at)), id);

CREATE TABLE identity.admin_governance (
    id smallint PRIMARY KEY CHECK (id = 1),
    bootstrap_completed_at timestamptz,
    bootstrap_event_id uuid,
    CONSTRAINT bootstrap_marker_pair CHECK ((bootstrap_completed_at IS NULL) = (bootstrap_event_id IS NULL))
);
INSERT INTO identity.admin_governance(id) VALUES (1);

CREATE TABLE identity.recovery_approvals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    governance_id smallint NOT NULL REFERENCES identity.admin_governance(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    target_user_id uuid NOT NULL REFERENCES identity.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    expected_security_revision bigint NOT NULL CHECK (expected_security_revision >= 0),
    action identity.recovery_action NOT NULL,
    replacement_email_canonical text,
    approver_principal_id text NOT NULL CHECK (length(btrim(approver_principal_id)) > 0),
    designated_operator_principal_id text NOT NULL CHECK (length(btrim(designated_operator_principal_id)) > 0),
    operator_scope identity.operator_scope NOT NULL,
    evidence_ref text NOT NULL CHECK (length(btrim(evidence_ref)) > 0),
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > issued_at),
    consumed_at timestamptz CHECK (consumed_at >= issued_at AND consumed_at < expires_at),
    cancelled_at timestamptz CHECK (cancelled_at >= issued_at AND cancelled_at < expires_at),
    CHECK (num_nonnulls(consumed_at, cancelled_at) <= 1),
    CHECK (approver_principal_id <> designated_operator_principal_id),
    CHECK ((action = 'REPLACE_DEDICATED') = (replacement_email_canonical IS NOT NULL)),
    CHECK (replacement_email_canonical IS NULL OR length(btrim(replacement_email_canonical)) BETWEEN 3 AND 254)
);
CREATE INDEX recovery_approvals_target_idx ON identity.recovery_approvals(target_user_id);
CREATE INDEX recovery_approvals_governance_idx ON identity.recovery_approvals(governance_id);

CREATE TABLE identity.admin_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    governance_id smallint NOT NULL REFERENCES identity.admin_governance(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    recovery_approval_id uuid REFERENCES identity.recovery_approvals(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    actor_principal_id text NOT NULL CHECK (length(btrim(actor_principal_id)) > 0),
    approver_principal_id text,
    correlation_id uuid NOT NULL,
    target_user_id uuid,
    resulting_user_id uuid,
    event identity.audit_event NOT NULL,
    outcome identity.audit_outcome NOT NULL,
    reason text,
    verification_evidence_ref text,
    occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CHECK ((outcome = 'DENIED') = (event IN ('BOOTSTRAP_DENIED', 'APPROVAL_ISSUE_DENIED', 'APPROVAL_CANCEL_DENIED', 'RECOVERY_DENIED'))),
    CHECK (event <> 'RECOVERY_SUCCESS' OR recovery_approval_id IS NOT NULL),
    UNIQUE (actor_principal_id, correlation_id, event)
);
ALTER TABLE identity.admin_governance ADD CONSTRAINT bootstrap_event_fk
    FOREIGN KEY (bootstrap_event_id) REFERENCES identity.admin_audit(id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE UNIQUE INDEX admin_audit_recovery_success_idx ON identity.admin_audit(recovery_approval_id)
    WHERE event = 'RECOVERY_SUCCESS';
CREATE INDEX admin_audit_recovery_idx ON identity.admin_audit(recovery_approval_id);
CREATE INDEX admin_audit_governance_idx ON identity.admin_audit(governance_id);
REVOKE ALL ON ALL TABLES IN SCHEMA identity FROM PUBLIC;
