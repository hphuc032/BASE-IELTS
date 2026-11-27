ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
CREATE SCHEMA account_fence_routines;
REVOKE ALL ON SCHEMA account_fence_routines FROM PUBLIC;

GRANT USAGE ON SCHEMA identity TO account_fence_owner;
GRANT SELECT (id, password_hash, status, security_revision) ON identity.users TO account_fence_owner;
GRANT UPDATE (fence_guard) ON identity.users TO account_fence_owner;
GRANT USAGE, CREATE ON SCHEMA account_fence_routines TO account_fence_owner;

CREATE FUNCTION account_fence_routines."lockForLogin"(account_id uuid)
RETURNS TABLE(user_id uuid, password_hash text, status identity.account_status, security_revision bigint)
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF account_id IS NULL THEN
        RAISE EXCEPTION 'Account ID required' USING ERRCODE = '22023';
    END IF;
    RETURN QUERY
        SELECT u.id, u.password_hash, u.status, u.security_revision
        FROM identity.users AS u WHERE u.id = account_id FOR NO KEY UPDATE OF u;
END;
$$;

CREATE FUNCTION account_fence_routines."lockAndCheckActor"(actor_id uuid, expected_security_revision bigint)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER VOLATILE PARALLEL UNSAFE
SET search_path = pg_catalog, pg_temp
AS $$
DECLARE
    actual_status identity.account_status;
    actual_revision bigint;
BEGIN
    IF actor_id IS NULL OR expected_security_revision IS NULL OR expected_security_revision < 0 THEN
        RAISE EXCEPTION 'Invalid account fence arguments' USING ERRCODE = '22023';
    END IF;
    SELECT u.status, u.security_revision INTO actual_status, actual_revision
    FROM identity.users AS u WHERE u.id = actor_id FOR NO KEY UPDATE OF u;
    RETURN coalesce(actual_status = 'ACTIVE' AND actual_revision = expected_security_revision, false);
END;
$$;

REVOKE EXECUTE ON FUNCTION account_fence_routines."lockForLogin"(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION account_fence_routines."lockAndCheckActor"(uuid, bigint) FROM PUBLIC;
GRANT USAGE ON SCHEMA account_fence_routines TO base_ielts_runtime;
GRANT EXECUTE ON FUNCTION account_fence_routines."lockForLogin"(uuid) TO base_ielts_runtime;
GRANT EXECUTE ON FUNCTION account_fence_routines."lockAndCheckActor"(uuid, bigint) TO base_ielts_runtime;
ALTER FUNCTION account_fence_routines."lockForLogin"(uuid) OWNER TO account_fence_owner;
ALTER FUNCTION account_fence_routines."lockAndCheckActor"(uuid,bigint) OWNER TO account_fence_owner;
REVOKE CREATE ON SCHEMA account_fence_routines FROM account_fence_owner;
