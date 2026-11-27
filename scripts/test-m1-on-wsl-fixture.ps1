param(
    [Parameter(Mandatory)][ValidatePattern('^/tmp/base-ielts-pg1712-[A-Za-z0-9]{8}$')][string]$FixtureRoot,
    [ValidateRange(1024,65535)][int]$Port = 55481,
    [ValidateRange(1024,65535)][int]$JdbcPort = $Port
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)
$env:BASE_TEST_WSL_FIXTURE_ROOT = $FixtureRoot
$env:BASE_TEST_WSL_FIXTURE_PORT = [string]$Port
$env:BASE_TEST_WSL_JDBC_PORT = [string]$JdbcPort
. (Join-Path $PSScriptRoot 'm1-fixture-psql.ps1')

function Invoke-FixtureSetup([string]$Database, [string]$Sql) {
    $Sql | Invoke-M1FixturePsql -Xq -v ON_ERROR_STOP=1 -U postgres -d $Database
    if ($LASTEXITCODE) { throw "Setup failed for isolated database $Database." }
}

$server = Invoke-M1FixturePsql -XAtq -v ON_ERROR_STOP=1 -U postgres -d postgres -c "select current_setting('server_version_num') || '|' || current_setting('data_directory')"
if ($LASTEXITCODE -or $server -ne "170012|$FixtureRoot/data") {
    throw 'Expected a dedicated PostgreSQL 17.12 WSL instance.'
}
$env:BASE_DB_RUNTIME_USER = 'base_ielts_runtime'
$env:BASE_DB_RUNTIME_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:BASE_DB_MIGRATION_USER = 'base_ielts_migration'
$env:BASE_DB_MIGRATION_PASSWORD = [Guid]::NewGuid().ToString('N')
$env:SPRING_FLYWAY_TARGET = '12'
$env:MAVEN_BASEDIR = (Resolve-Path backend).Path
$env:BASE_TEST_STAFF_MIGRATION = 'identity'
$env:BASE_TEST_STAFF_MIGRATION_PASSWORD = $env:BASE_DB_MIGRATION_PASSWORD

# This observer can only authenticate over the protected fixture socket.
Invoke-FixtureSetup postgres @'
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'base_ielts_bootstrap') THEN
        CREATE ROLE base_ielts_bootstrap SUPERUSER LOGIN;
    END IF;
END $$;
'@
$suffix = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
$m1 = "base_ielts_m1test_${suffix}01"
$edge = "base_ielts_m1test_${suffix}02"
$settings = Join-Path ([IO.Path]::GetTempPath()) ("m1-wsl-settings-" + [Guid]::NewGuid().ToString('N'))
try {
    foreach ($database in @($m1, $edge)) {
        Invoke-FixtureSetup postgres "CREATE DATABASE $database"
        @"
BASE_DB_BOOTSTRAP_USER=postgres
BASE_DB_NAME=$database
BASE_DB_RUNTIME_USER=$env:BASE_DB_RUNTIME_USER
BASE_DB_RUNTIME_PASSWORD=$env:BASE_DB_RUNTIME_PASSWORD
BASE_DB_MIGRATION_USER=$env:BASE_DB_MIGRATION_USER
BASE_DB_MIGRATION_PASSWORD=$env:BASE_DB_MIGRATION_PASSWORD
"@ | Set-Content -LiteralPath $settings
        & scripts/initialize-dev-db.ps1 -SettingsFile $settings -WslFixtureRoot $FixtureRoot -WslPort $Port
        Invoke-FixtureSetup postgres @"
ALTER ROLE base_ielts_runtime PASSWORD '$env:BASE_DB_RUNTIME_PASSWORD';
ALTER ROLE base_ielts_migration PASSWORD '$env:BASE_DB_MIGRATION_PASSWORD';
"@
        $env:BASE_TEST_STAFF_DB = "jdbc:postgresql://127.0.0.1:${JdbcPort}/$database"
        & backend/mvnw.cmd -B -f backend/pom.xml '-Dtest=StaffFixtureMigrationTest' test *> "backend/m2-wsl-$database.log"
        if ($LASTEXITCODE) { throw "V12 migration verification failed for $database." }
        Write-Output "Verified V12 migrations in $database."
    }
} finally {
    if (Test-Path -LiteralPath $settings) { Remove-Item -LiteralPath $settings }
}
Remove-Item Env:BASE_TEST_STAFF_MIGRATION,Env:BASE_TEST_STAFF_DB,Env:BASE_TEST_STAFF_MIGRATION_PASSWORD
Invoke-FixtureSetup $m1 @'
SET SESSION AUTHORIZATION deployment_operator;
SELECT deployment_operator.bootstrap('cli-admin-wsl@identity-test.invalid',
    '$argon2id$v=19$m=19456,t=2,p=1$ZmFrZS1maXh0dXJlLXNhbHQ$ZmFrZS1maXh0dXJlLWhhc2g', 'wsl-fixture');
RESET SESSION AUTHORIZATION;
'@
pwsh -NoProfile -File scripts/test-admin-recovery.ps1 -FixtureDatabase $m1 -Full *> backend/m2-wsl-m1.log
$m1Exit = $LASTEXITCODE
Write-Output "M1 clean verify in $m1 exit: $m1Exit"
Get-Content backend/m2-wsl-m1.log -Tail 10

npm --prefix frontend run lint *> backend/m2-wsl-frontend.log
$frontendExit = $LASTEXITCODE
if (!$frontendExit) {
    $env:BASE_BACKEND_ORIGIN = 'http://127.0.0.1:18083'
    npm --prefix frontend run build *>> backend/m2-wsl-frontend.log
    $frontendExit = $LASTEXITCODE
}
Write-Output "Frontend exit: $frontendExit"
if ($frontendExit -or $m1Exit) { exit 1 }

$edgeExit = 0
foreach ($mode in @('standard', 'forced', 'graceful')) {
    $flags = switch ($mode) {
        'standard' { @() }
        'forced' { @('-Https', '-ActiveShutdown') }
        'graceful' { @('-Https', '-ActiveShutdown', '-GracefulShutdown') }
    }
    pwsh -NoProfile -File scripts/test-auth-browser.ps1 -FixtureDatabase $edge @flags *> "backend/m2-wsl-edge-$mode.log"
    $code = $LASTEXITCODE
    Write-Output "Edge $mode in $edge exit: $code"
    Get-Content "backend/m2-wsl-edge-$mode.log" -Tail 7
    if ($code) { $edgeExit = $code }
}
exit $edgeExit
