GRANT USAGE ON SCHEMA identity, identity_api TO identity_login_owner, identity_profile_owner;
GRANT CREATE ON SCHEMA identity_api TO identity_login_owner, identity_profile_owner;
GRANT SELECT (id, canonical_email, password_hash, status, security_revision) ON identity.users TO identity_login_owner;
GRANT SELECT (id, canonical_email, display_name, status, security_revision) ON identity.users TO identity_profile_owner;
GRANT UPDATE (fence_guard, display_name, updated_at) ON identity.users TO identity_profile_owner;
GRANT SELECT (id, code) ON identity.roles TO identity_profile_owner;
GRANT SELECT (user_id, role_id) ON identity.user_roles TO identity_profile_owner;

CREATE FUNCTION identity_api.login_snapshot(email text)
RETURNS TABLE(user_id uuid, password_hash text, status identity.account_status, security_revision bigint)
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
    SELECT u.id,u.password_hash,u.status,u.security_revision FROM identity.users u WHERE u.canonical_email=$1
$$;

CREATE FUNCTION identity_api.profile(account_id uuid, expected_revision bigint, new_display_name text)
RETURNS TABLE(user_id uuid, email text, display_name text, roles text[])
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF $1 IS NULL OR $2 IS NULL OR $2<0 OR ($3 IS NOT NULL AND length(btrim($3)) NOT BETWEEN 1 AND 120) THEN
        RAISE EXCEPTION 'Invalid profile request' USING ERRCODE='22023';
    END IF;
    PERFORM u.id FROM identity.users u WHERE u.id=$1 AND u.status='ACTIVE' AND u.security_revision=$2
        FOR NO KEY UPDATE OF u;
    IF NOT FOUND THEN RETURN; END IF;
    IF $3 IS NOT NULL THEN
        UPDATE identity.users u SET display_name=btrim($3), updated_at=clock_timestamp() WHERE u.id=$1;
    END IF;
    RETURN QUERY SELECT u.id,u.canonical_email,u.display_name,
        ARRAY(SELECT r.code FROM identity.user_roles ur JOIN identity.roles r ON r.id=ur.role_id
            WHERE ur.user_id=u.id ORDER BY r.code)
        FROM identity.users u WHERE u.id=$1;
END;
$$;

REVOKE EXECUTE ON FUNCTION identity_api.login_snapshot(text) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION identity_api.profile(uuid,bigint,text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity_api.login_snapshot(text) TO base_ielts_runtime;
GRANT EXECUTE ON FUNCTION identity_api.profile(uuid,bigint,text) TO base_ielts_runtime;
ALTER FUNCTION identity_api.login_snapshot(text) OWNER TO identity_login_owner;
ALTER FUNCTION identity_api.profile(uuid,bigint,text) OWNER TO identity_profile_owner;
REVOKE CREATE ON SCHEMA identity_api FROM identity_login_owner, identity_profile_owner;
