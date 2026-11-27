-- Retrying an already committed profile intent must not move updated_at.
GRANT CREATE ON SCHEMA identity_api TO identity_profile_owner;
SET LOCAL ROLE identity_profile_owner;
CREATE OR REPLACE FUNCTION identity_api.profile(account_id uuid, expected_revision bigint, new_display_name text)
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
        UPDATE identity.users u SET display_name=btrim($3), updated_at=clock_timestamp() WHERE u.id=$1 AND u.display_name IS DISTINCT FROM btrim($3);
    END IF;
    RETURN QUERY SELECT u.id,u.canonical_email,u.display_name,
        ARRAY(SELECT r.code FROM identity.user_roles ur JOIN identity.roles r ON r.id=ur.role_id
            WHERE ur.user_id=u.id ORDER BY r.code)
        FROM identity.users u WHERE u.id=$1;
END;
$$;
RESET ROLE;
SET LOCAL ROLE identity_migration_owner;
REVOKE CREATE ON SCHEMA identity_api FROM identity_profile_owner;
