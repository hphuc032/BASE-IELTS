param([Parameter(Mandatory)][ValidatePattern('^base_ielts_m1test_[0-9]+$')][string]$FixtureDatabase,[switch]$Full)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot)
$roleVariables = @{
    deployment_operator = 'BASE_TEST_OPERATOR_PASSWORD'
    operator_approver = 'BASE_TEST_APPROVER_PASSWORD'
    operator_approver_secondary = 'BASE_TEST_SECONDARY_APPROVER_PASSWORD'
    identity_lifecycle_operator = 'BASE_TEST_LIFECYCLE_PASSWORD'
}
$credentialsInstalled = $false
function Get-RecoveryEvidence([string]$Target) {
    if ($Target -notmatch '^[0-9a-f-]{36}$') { throw 'Invalid synthetic target identifier.' }
    # Read with the existing local fixture observer. Application and operator
    # grants remain untouched.
    @"
SELECT count(*) FILTER (WHERE a.event='RECOVERY_SUCCESS'),
       count(*) FILTER (WHERE a.event='RECOVERY_SUCCESS' AND
            (a.actor_principal_id <> 'deployment_operator' OR
             a.approver_principal_id IS DISTINCT FROM 'operator_approver' OR
             p.consumed_at IS NULL OR p.cancelled_at IS NOT NULL)),
       count(DISTINCT a.recovery_approval_id) FILTER (WHERE a.event='RECOVERY_SUCCESS')
FROM identity.admin_audit a
LEFT JOIN identity.recovery_approvals p ON p.id=a.recovery_approval_id
WHERE a.target_user_id='$Target';
"@ | docker compose exec -T postgres psql -XAtq -F ',' -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
    if ($LASTEXITCODE) { throw 'Unable to read synthetic recovery evidence.' }
}
try {
    $setup = foreach ($entry in $roleVariables.GetEnumerator()) {
        $fixturePassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
        [Environment]::SetEnvironmentVariable($entry.Value, $fixturePassword, 'Process')
        $role = $entry.Key
        "IF EXISTS (SELECT FROM pg_catalog.pg_authid WHERE rolname='$role' AND rolpassword IS NOT NULL) THEN RAISE EXCEPTION 'Existing credential'; END IF; ALTER ROLE $role PASSWORD '$fixturePassword';"
    }
    ('DO $$ BEGIN ' + ($setup -join ' ') + ' END $$;') |
        docker compose exec -T postgres psql -Xq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
    if ($LASTEXITCODE) { throw 'Temporary test credentials could not be installed.' }
    $credentialsInstalled = $true
    $fixture = 'select id,canonical_email,security_revision from identity.users where canonical_email like ''cli-admin-%@identity-test.invalid'' and status=''ACTIVE'';' |
        docker compose exec -T postgres psql -XAtq -F ',' -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
    if ($LASTEXITCODE -or @($fixture).Count -ne 1 -or $fixture -notmatch '^[0-9a-f-]{36},cli-admin-[a-z0-9-]+@identity-test\.invalid,[0-9]+$') {
        throw 'Exactly one active synthetic Admin fixture is required.'
    }
    $env:BASE_TEST_GOVERNANCE_TARGET,$env:BASE_TEST_GOVERNANCE_EMAIL,$env:BASE_TEST_GOVERNANCE_REVISION = $fixture -split ','
    $before = (Get-RecoveryEvidence $env:BASE_TEST_GOVERNANCE_TARGET) -split ','
    $env:BASE_TEST_ADMIN_HTTP = 'true'
    $env:BASE_TEST_GOVERNANCE = 'true'
    $env:BASE_TEST_LIFECYCLE = 'true'
    $env:BASE_TEST_POSTGRES = 'true'
    $env:MAVEN_BASEDIR = (Resolve-Path backend).Path
    $env:BASE_DB_JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/$FixtureDatabase"
    foreach ($line in Get-Content .env) {
        if ($line -match '^(BASE_DB_(?:RUNTIME_USER|RUNTIME_PASSWORD|MIGRATION_USER|MIGRATION_PASSWORD))=(.+)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
    if ($Full) {
        $env:BASE_TEST_VERIFICATION_LOAD = 'true'
        $env:BASE_TEST_VERIFICATION_COMMIT_FAULT = 'true'
        ./backend/mvnw.cmd -B -f backend/pom.xml clean verify
    } else {
        ./backend/mvnw.cmd -B -f backend/pom.xml '-Dtest=AdminRacePostgresTest,VerificationPostgresTest#resetRollbackAndCommitFenceRealLoginAndProfile' test
    }
    if ($LASTEXITCODE) { throw 'Focused recovery verification failed.' }
    $after = (Get-RecoveryEvidence $env:BASE_TEST_GOVERNANCE_TARGET) -split ','
    $expectedRecoveries = if ($Full) { 4 } else { 2 }
    if ($before.Count -ne 3 -or $after.Count -ne 3 -or
        ([int]$after[0] - [int]$before[0]) -ne $expectedRecoveries -or
        [int]$after[1] -ne 0 -or [int]$after[0] -ne [int]$after[2]) {
        throw 'Recovery audit and consumed approval evidence do not match the committed operations.'
    }
    Write-Output "PASS: $expectedRecoveries committed recoveries, distinct consumed approvals and correct audit attribution."
} finally {
    try {
        if ($credentialsInstalled) {
            ($roleVariables.Keys | ForEach-Object { "ALTER ROLE $_ PASSWORD NULL;" }) |
                docker compose exec -T postgres psql -Xq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
            if ($LASTEXITCODE) { throw 'Temporary credential cleanup failed.' }
        }
    } finally {
        foreach ($name in $roleVariables.Values) { [Environment]::SetEnvironmentVariable($name, $null, 'Process') }
        Pop-Location
    }
}
