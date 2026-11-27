CREATE SCHEMA identity_maintenance;
REVOKE ALL ON SCHEMA identity_maintenance FROM PUBLIC;
GRANT USAGE ON SCHEMA identity_maintenance TO maintenance_cleanup;
GRANT USAGE, CREATE ON SCHEMA identity_maintenance TO token_cleanup_owner;
GRANT USAGE ON SCHEMA identity TO token_cleanup_owner;
GRANT SELECT (id) ON identity.users TO token_cleanup_owner;
GRANT UPDATE (fence_guard) ON identity.users TO token_cleanup_owner;
GRANT SELECT (id, user_id, consumed_at, invalidated_at, expires_at), DELETE
    ON identity.verification_tokens TO token_cleanup_owner;

CREATE FUNCTION identity_maintenance."cleanupExpiredTokens"(cutoff_utc timestamptz, batch_limit integer)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
SET lock_timeout = '250ms' AS $$
DECLARE
    candidates uuid[];
    locked_time timestamptz;
    removed integer := 0;
    candidate uuid;
    deleted integer;
BEGIN
    IF cutoff_utc IS NULL OR NOT isfinite(cutoff_utc) OR batch_limit IS NULL
       OR batch_limit NOT BETWEEN 1 AND 500
       OR cutoff_utc > clock_timestamp() - interval '168 hours' THEN
        RAISE EXCEPTION 'Invalid token cleanup request' USING ERRCODE='22023';
    END IF;

    SELECT ARRAY(
        SELECT t.id FROM identity.verification_tokens t
        WHERE coalesce(greatest(t.consumed_at, t.invalidated_at), t.expires_at) <= cutoff_utc
        ORDER BY coalesce(greatest(t.consumed_at, t.invalidated_at), t.expires_at), t.id
        LIMIT batch_limit
    ) INTO candidates;
    IF cardinality(candidates) = 0 THEN RETURN 0; END IF;

    PERFORM u.id FROM identity.users u WHERE u.id IN (
        SELECT t.user_id FROM identity.verification_tokens t WHERE t.id = ANY(candidates)
    ) ORDER BY u.id FOR NO KEY UPDATE OF u;

    locked_time := clock_timestamp();
    IF cutoff_utc > locked_time - interval '168 hours' THEN
        RAISE EXCEPTION 'Invalid token cleanup request' USING ERRCODE='22023';
    END IF;
    -- All token writers first lock the same user row. DELETE locks selected token rows in UUID order.
    FOR candidate IN SELECT unnest(candidates) ORDER BY 1 LOOP
        DELETE FROM identity.verification_tokens t
        WHERE t.id = candidate
            AND (t.consumed_at IS NOT NULL OR t.invalidated_at IS NOT NULL OR t.expires_at <= locked_time)
            AND coalesce(greatest(t.consumed_at, t.invalidated_at), t.expires_at) <= cutoff_utc;
        GET DIAGNOSTICS deleted = ROW_COUNT;
        removed := removed + deleted;
    END LOOP;
    RETURN removed;
END;
$$;
REVOKE EXECUTE ON FUNCTION identity_maintenance."cleanupExpiredTokens"(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION identity_maintenance."cleanupExpiredTokens"(timestamptz, integer) TO maintenance_cleanup;
ALTER FUNCTION identity_maintenance."cleanupExpiredTokens"(timestamptz, integer) OWNER TO token_cleanup_owner;
REVOKE CREATE ON SCHEMA identity_maintenance FROM token_cleanup_owner;
