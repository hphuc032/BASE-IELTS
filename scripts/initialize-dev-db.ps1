param([string]$SettingsFile, [string]$FixtureContainer, [string]$WslFixtureRoot, [int]$WslPort = 55481)

# Uses the local Compose database by default. Does not rotate existing roles.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
if (!$SettingsFile) { $SettingsFile = Join-Path $root '.env' }
$settings = @{}
foreach ($line in Get-Content -LiteralPath $SettingsFile) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$Matches[1]] = $Matches[2] }
}
$bootstrap = $settings['BASE_DB_BOOTSTRAP_USER']
$database = $settings['BASE_DB_NAME']
if (!$database) { $database = 'base_ielts' }
$runtime = $settings['BASE_DB_RUNTIME_USER']
$migration = $settings['BASE_DB_MIGRATION_USER']
if ($runtime -cne 'base_ielts_runtime' -or $migration -cne 'base_ielts_migration') {
    throw 'Use the fixed development runtime and migration roles expected by the Flyway grants.'
}
foreach ($identifier in @($bootstrap, $database, $runtime, $migration)) {
    if ($identifier -cnotmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'Invalid development database identifier.' }
}
if (@(@($bootstrap, $runtime, $migration) | Sort-Object -Unique).Count -ne 3) {
    throw 'Bootstrap, migration and runtime must be separate identities.'
}
$runtimePassword = $settings['BASE_DB_RUNTIME_PASSWORD']
$migrationPassword = $settings['BASE_DB_MIGRATION_PASSWORD']
foreach ($password in @($runtimePassword, $migrationPassword)) {
    if ($password -cnotmatch '^[a-zA-Z0-9_-]{32,128}$') { throw 'Expected generated local database passwords.' }
}
if ($runtimePassword -ceq $migrationPassword) { throw 'Development database role passwords must be distinct.' }
$commands = @"
BEGIN;
DO `$provision`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$runtime') THEN
        CREATE ROLE $runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '$runtimePassword';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '$migration') THEN
        CREATE ROLE $migration LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '$migrationPassword';
    END IF;
END
`$provision`$;
GRANT CONNECT ON DATABASE $database TO $runtime, $migration;
DO `$owners`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_migration_owner') THEN
        CREATE ROLE identity_migration_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'account_fence_owner') THEN
        CREATE ROLE account_fence_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$owners`$;
GRANT CREATE ON DATABASE $database TO identity_migration_owner;
REVOKE CREATE ON DATABASE $database FROM $migration;
GRANT identity_migration_owner TO $migration WITH INHERIT FALSE, SET TRUE;
GRANT account_fence_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
DO `$registration`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_signup_owner') THEN
        CREATE ROLE identity_signup_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$registration`$;
GRANT identity_signup_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
DO `$profile`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_login_owner') THEN
        CREATE ROLE identity_login_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_profile_owner') THEN
        CREATE ROLE identity_profile_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$profile`$;
GRANT identity_login_owner, identity_profile_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
DO `$maintenance`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'token_cleanup_owner') THEN
        CREATE ROLE token_cleanup_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'maintenance_cleanup') THEN
        CREATE ROLE maintenance_cleanup LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$maintenance`$;
GRANT token_cleanup_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
DO `$governance`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'governance_denial_owner') THEN
        CREATE ROLE governance_denial_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'deployment_operator') THEN
        CREATE ROLE deployment_operator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'operator_approver') THEN
        CREATE ROLE operator_approver LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$governance`$;
GRANT governance_denial_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
DO `$second_approver`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'operator_approver_secondary') THEN
        CREATE ROLE operator_approver_secondary LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$second_approver`$;
DO `$admin`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'deployment_admin_owner') THEN
        CREATE ROLE deployment_admin_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'operator_approval_owner') THEN
        CREATE ROLE operator_approval_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$admin`$;
GRANT deployment_admin_owner, operator_approval_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
DO `$lifecycle`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_lifecycle_owner') THEN
        CREATE ROLE identity_lifecycle_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'identity_lifecycle_operator') THEN
        CREATE ROLE identity_lifecycle_operator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
`$lifecycle`$;
GRANT identity_lifecycle_owner TO identity_migration_owner WITH INHERIT FALSE, SET TRUE;
COMMIT;
"@
Push-Location $root
try {
    # Keep SQL containing local credentials off the command line and out of output.
    if ($WslFixtureRoot) {
        if ($WslFixtureRoot -cnotmatch '^/tmp/base-ielts-pg1712-[A-Za-z0-9]{8}$' -or !$SettingsFile -or $FixtureContainer) {
            throw 'Expected a dedicated WSL PostgreSQL fixture and explicit fixture settings.'
        }
        $commands | wsl.exe -d Ubuntu -- "$WslFixtureRoot/runtime/bin/psql" -h "$WslFixtureRoot/socket" -p $WslPort -Xq -v ON_ERROR_STOP=1 -U $bootstrap -d $database 2>$null
    } elseif ($FixtureContainer) {
        if ($FixtureContainer -cnotmatch '^base-ielts-staff-test-[a-f0-9]{32}$') {
            throw 'Expected a generated staff fixture container name.'
        }
        $commands | docker exec -i $FixtureContainer psql -X -q -v ON_ERROR_STOP=1 -U $bootstrap -d $database 2>$null
    } else {
        $commands | docker compose exec -T postgres psql -X -q -v ON_ERROR_STOP=1 -U $bootstrap -d $database 2>$null
    }
    if ($LASTEXITCODE -ne 0) { throw 'Development role provisioning failed. Check local Compose health and bootstrap role configuration.' }
} finally {
    Pop-Location
}
