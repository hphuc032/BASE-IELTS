CREATE SCHEMA identity_api;
REVOKE ALL ON SCHEMA identity_api FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA identity_api TO identity_signup_owner;
GRANT USAGE ON SCHEMA identity TO identity_signup_owner;
GRANT SELECT (id,canonical_email,status,email_revision,account_kind) ON identity.users TO identity_signup_owner;
GRANT INSERT (canonical_email,display_name) ON identity.users TO identity_signup_owner;
GRANT UPDATE (fence_guard,status,password_hash,verified_at,verification_method,security_revision,updated_at) ON identity.users TO identity_signup_owner;
GRANT SELECT (security_revision) ON identity.users TO identity_signup_owner;
GRANT SELECT (id,code) ON identity.roles TO identity_signup_owner;
GRANT INSERT (user_id,role_id) ON identity.user_roles TO identity_signup_owner;
GRANT SELECT (id,user_id,email_revision,token_digest,issued_at,expires_at,consumed_at,invalidated_at) ON identity.verification_tokens TO identity_signup_owner;
GRANT INSERT (user_id,email_revision,token_digest,issued_at,expires_at) ON identity.verification_tokens TO identity_signup_owner;
GRANT UPDATE (consumed_at,invalidated_at) ON identity.verification_tokens TO identity_signup_owner;

CREATE FUNCTION identity_api.register(canonical_email text, display_name text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    account uuid;
BEGIN
    IF canonical_email IS NULL OR canonical_email <> lower(btrim(canonical_email))
       OR display_name IS NULL OR length(btrim(display_name)) NOT BETWEEN 1 AND 120 THEN
        RAISE EXCEPTION 'Invalid registration fields' USING ERRCODE = '22023';
    END IF;
    INSERT INTO identity.users AS u(canonical_email,display_name)
    VALUES ($1,btrim($2)) ON CONFLICT ON CONSTRAINT users_canonical_email_key DO NOTHING
    RETURNING u.id INTO account;
    IF account IS NOT NULL THEN
        INSERT INTO identity.user_roles(user_id,role_id)
        VALUES (account, (SELECT id FROM identity.roles WHERE code='LEARNER'));
    END IF;
    RETURN account;
END;
$$;

CREATE FUNCTION identity_api.issue(canonical_email text, token_digest bytea, expected_email_revision bigint, expiry timestamptz) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    account uuid;
    revision bigint;
    decision_at timestamptz;
BEGIN
    IF $1 IS NULL OR $2 IS NULL OR octet_length($2) <> 32 OR $3 < 0 THEN
        RAISE EXCEPTION 'Invalid token issuance fields' USING ERRCODE = '22023';
    END IF;
    SELECT u.id,u.email_revision INTO account,revision FROM identity.users u
    WHERE u.canonical_email=$1 AND u.status='PENDING' AND u.account_kind='PUBLIC_LEARNER'
    FOR NO KEY UPDATE OF u;
    IF NOT FOUND OR ($3 IS NOT NULL AND revision<>$3) THEN RETURN false; END IF;
    decision_at := clock_timestamp();
    IF $4 IS NOT NULL AND (NOT isfinite($4) OR $4 <= decision_at OR $4 > decision_at+interval '30 minutes') THEN
        RAISE EXCEPTION 'Invalid verification expiry' USING ERRCODE = '22023';
    END IF;
    IF EXISTS (
       SELECT FROM identity.verification_tokens t WHERE t.user_id=account
       AND t.issued_at > decision_at-interval '60 seconds'
    ) OR (SELECT count(*) FROM identity.verification_tokens t WHERE t.user_id=account
       AND t.issued_at > decision_at-interval '1 hour') >= 5 THEN
       RETURN false;
    END IF;
    UPDATE identity.verification_tokens t SET invalidated_at=decision_at
    WHERE t.user_id=account AND t.consumed_at IS NULL AND t.invalidated_at IS NULL;
    INSERT INTO identity.verification_tokens(user_id,email_revision,token_digest,issued_at,expires_at)
    VALUES (account,revision,$2,decision_at,coalesce($4,decision_at+interval '30 minutes'));
    RETURN true;
END;
$$;

CREATE FUNCTION identity_api.consume(token_digest bytea, new_hash text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    account uuid;
    revision bigint;
    token_id uuid;
    token_revision bigint;
    token_expiry timestamptz;
    decision_at timestamptz;
BEGIN
    IF $1 IS NULL OR octet_length($1) <> 32 OR $2 IS NULL OR $2 NOT LIKE '$argon2id$%' OR length($2) > 255 THEN
        RAISE EXCEPTION 'Invalid verification fields' USING ERRCODE = '22023';
    END IF;
    SELECT t.user_id INTO account FROM identity.verification_tokens t WHERE t.token_digest=$1;
    IF NOT FOUND THEN RETURN false; END IF;

    SELECT u.email_revision INTO revision FROM identity.users u
    WHERE u.id=account AND u.status='PENDING' AND u.account_kind='PUBLIC_LEARNER'
    FOR NO KEY UPDATE OF u;
    IF NOT FOUND THEN RETURN false; END IF;
    SELECT t.id,t.email_revision,t.expires_at INTO token_id,token_revision,token_expiry
    FROM identity.verification_tokens t WHERE t.user_id=account AND t.token_digest=$1
      AND t.consumed_at IS NULL AND t.invalidated_at IS NULL FOR UPDATE OF t;
    IF NOT FOUND THEN RETURN false; END IF;
    decision_at := clock_timestamp();
    IF token_revision<>revision OR token_expiry<=decision_at THEN RETURN false; END IF;
    UPDATE identity.users SET status='ACTIVE',password_hash=$2,verified_at=decision_at,
        verification_method='MAILBOX_TOKEN',security_revision=security_revision+1,updated_at=decision_at
    WHERE id=account;
    UPDATE identity.verification_tokens SET consumed_at=decision_at WHERE id=token_id;
    RETURN true;
END;
$$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA identity_api FROM PUBLIC;
GRANT USAGE ON SCHEMA identity_api TO base_ielts_runtime;
GRANT EXECUTE ON FUNCTION identity_api.register(text,text) TO base_ielts_runtime;
GRANT EXECUTE ON FUNCTION identity_api.issue(text,bytea,bigint,timestamptz) TO base_ielts_runtime;
GRANT EXECUTE ON FUNCTION identity_api.consume(bytea,text) TO base_ielts_runtime;
ALTER FUNCTION identity_api.register(text,text) OWNER TO identity_signup_owner;
ALTER FUNCTION identity_api.issue(text,bytea,bigint,timestamptz) OWNER TO identity_signup_owner;
ALTER FUNCTION identity_api.consume(bytea,text) OWNER TO identity_signup_owner;
REVOKE CREATE ON SCHEMA identity_api FROM identity_signup_owner;
