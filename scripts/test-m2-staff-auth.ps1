param([switch]$IncludeCatalog, [switch]$Combined, [string]$WslFixtureRoot, [int]$WslPort = 55481, [int]$WslJdbcPort = $WslPort)
if ($Combined) { $IncludeCatalog = $true }

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$suffix = [Guid]::NewGuid().ToString('N')
$container = "base-ielts-staff-test-$suffix"
$imageLine = (Get-Content -LiteralPath (Join-Path $root 'compose.yaml') |
    Where-Object { $_ -match '^\s+image: postgres:17\.12-' } | Select-Object -First 1)
if (!$imageLine) { throw 'The pinned PostgreSQL fixture image was not found.' }
$image = $imageLine.Trim().Substring('image: '.Length)
$migration = Join-Path $root 'backend/src/main/resources/db/migration'
if ($WslFixtureRoot -and $WslFixtureRoot -cnotmatch '^/tmp/base-ielts-pg1712-[A-Za-z0-9]{8}$') {
    throw 'Expected a dedicated WSL PostgreSQL fixture.'
}
$adminPassword = if ($WslFixtureRoot) { $env:BASE_TEST_WSL_BOOTSTRAP_PASSWORD } else { [Guid]::NewGuid().ToString('N') }
if ($adminPassword -cnotmatch '^[a-f0-9]{32}$') { throw 'Expected a generated fixture password.' }
function Invoke-StaffFixtureSql([string]$Database, [string]$Sql) {
    if ($WslFixtureRoot) {
        $Sql | wsl.exe -d Ubuntu -- "$WslFixtureRoot/runtime/bin/psql" -h "$WslFixtureRoot/socket" -p $WslPort -Xq -v ON_ERROR_STOP=1 -U postgres -d $Database
    } else {
        $Sql | docker exec -i $container psql -Xq -v ON_ERROR_STOP=1 -U postgres -d $Database
    }
    if ($LASTEXITCODE) { throw "Staff fixture SQL failed in $Database with exit code $LASTEXITCODE." }
}
$migrationPassword = [Guid]::NewGuid().ToString('N')
$readerPassword = [Guid]::NewGuid().ToString('N')
$writerPassword = [Guid]::NewGuid().ToString('N')
$tempBackend = Join-Path ([IO.Path]::GetTempPath()) "base-ielts-staff-test-$suffix"
$savedEnvironment = @{}
foreach ($variable in @('POSTGRES_PASSWORD', 'BASE_TEST_STAFF_DB', 'BASE_TEST_STAFF_AUTH',
    'BASE_TEST_STAFF_MIGRATION', 'BASE_TEST_STAFF_MIGRATION_PASSWORD', 'BASE_TEST_STAFF_OWNER_PASSWORD', 'BASE_TEST_STAFF_READER_PASSWORD',
    'BASE_TEST_STAFF_WRITER_PASSWORD', 'BASE_TEST_CATALOG_STAFF_API', 'MAVEN_BASEDIR',
    'BASE_TEST_CATALOG_G03', 'BASE_TEST_CATALOG_G03_WIRE', 'BASE_TEST_CATALOG_ENVELOPE', 'BASE_TEST_CATALOG_ENVELOPE_READERS', 'BASE_TEST_READING_COMBINED',
    'BASE_TEST_READING_V15_FRESH_URL', 'BASE_TEST_READING_V15_UPGRADE_URL',
    'BASE_TEST_READING_STAFF_READER_PASSWORD', 'BASE_TEST_READING_STAFF_WRITER_PASSWORD',
    'BASE_DB_BOOTSTRAP_USER', 'BASE_DB_BOOTSTRAP_PASSWORD', 'BASE_DB_MIGRATION_PASSWORD')) {
    $savedEnvironment[$variable] = [Environment]::GetEnvironmentVariable($variable, 'Process')
}
$created = $false
try {
    if ($WslFixtureRoot) {
        $server = wsl.exe -d Ubuntu -- "$WslFixtureRoot/runtime/bin/psql" -h "$WslFixtureRoot/socket" -p $WslPort -XAtq -U postgres -d postgres -c "select current_setting('server_version_num')"
        if ($LASTEXITCODE -or $server -ne '170012') { throw 'WSL fixture is unavailable or is not exactly PostgreSQL 17.12.' }
    } else {
        # The test process is isolated from the project's Compose database and its roles.
        $env:POSTGRES_PASSWORD = $adminPassword
        docker run -d --name $container --publish 127.0.0.1::5432 --env POSTGRES_PASSWORD $image | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to start the isolated PostgreSQL fixture.' }
        $created = $true
        $ready = $false
        for ($i = 0; $i -lt 40; $i++) {
            docker exec $container pg_isready -h 127.0.0.1 -U postgres -d postgres | Out-Null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Milliseconds 500
        }
        if (!$ready) { throw 'Isolated PostgreSQL did not become ready.' }
    }

    $bootstrap = @"
CREATE ROLE content_catalog_reader_owner NOLOGIN;
CREATE ROLE content_catalog_writer_owner NOLOGIN;
CREATE ROLE content_reading_reader_owner NOLOGIN;
CREATE ROLE content_reading_writer_owner NOLOGIN;
CREATE ROLE content_staff_reader LOGIN NOINHERIT PASSWORD '$readerPassword';
CREATE ROLE content_author_writer LOGIN NOINHERIT PASSWORD '$writerPassword';
"@
    $fixtureGrants = @'
GRANT USAGE ON SCHEMA account_fence_routines TO content_staff_reader, content_author_writer;
GRANT EXECUTE ON FUNCTION account_fence_routines."lockAndCheckActor"(uuid,bigint)
TO content_staff_reader, content_author_writer;
'@
    Invoke-StaffFixtureSql postgres $bootstrap
    $v16Path = Join-Path $migration 'V16__staff_catalog_routines.sql'
    if ((Get-FileHash -LiteralPath $v16Path).Hash -cne '2E488FC22ABA5DF1AF34FB73DE0118D46E155D0FD4BD90A85F0F4B0A12810C81') {
        throw 'V16 bytes do not match the applied Catalog migration.'
    }
    if ($Combined) {
        $v173Path = Join-Path $migration 'V17_3__reading_staff_authoring.sql'
        if ((Get-FileHash -LiteralPath $v173Path).Hash -cne '2D32EAFAF4A7889C8D1ADD753E9F24224296295606BE0B8DCF056667575C31E1') {
            throw 'V17.3 bytes do not match the reviewed Reading migration.'
        }
    }
    if ($WslFixtureRoot) { $port = $WslJdbcPort } else {
        $address = docker port $container 5432/tcp
        if ($LASTEXITCODE -ne 0 -or $address -notmatch '^127\.0\.0\.1:([0-9]+)$') {
            throw 'Unexpected isolated fixture port binding.'
        }
        $port = $Matches[1]
    }
    $env:BASE_TEST_STAFF_AUTH = 'true'
    $env:BASE_TEST_STAFF_OWNER_PASSWORD = $adminPassword
    $env:BASE_TEST_STAFF_READER_PASSWORD = $readerPassword
    $env:BASE_TEST_STAFF_WRITER_PASSWORD = $writerPassword

    New-Item -ItemType Directory -Path $tempBackend | Out-Null
    foreach ($name in @('pom.xml', 'mvnw.cmd', '.mvn', 'src')) {
        Copy-Item -LiteralPath (Join-Path $root "backend/$name") -Destination $tempBackend -Recurse
    }
    $env:MAVEN_BASEDIR = $tempBackend
    $tests = 'FoundationSecurityTest,AuthSessionsTest,StaffAuthenticationTest,CatalogInputTest,CatalogHttpInputTest'
    if ($IncludeCatalog) { $tests += ',CatalogStaffApiPostgresTest,CatalogConnectionAdmissionTest,CatalogRouteBoundaryProofTest' }
    $settingsFile = Join-Path $tempBackend 'fixture.env'
    $env:BASE_TEST_STAFF_MIGRATION_PASSWORD = $migrationPassword
    $env:BASE_TEST_CATALOG_STAFF_API = if ($IncludeCatalog) { 'true' } else { 'false' }
    $modes = @('fresh')
    if ($IncludeCatalog) { $modes += 'upgrade' }
    $runs = $modes
    if ($Combined) { $runs += @('placement', 'wire-envelope-fresh', 'wire-envelope-upgrade', 'wire-fresh', 'wire-upgrade') }
    foreach ($run in $runs) {
        $mode = $run -replace '^wire-(envelope-)?', ''
        $wire = $run.StartsWith('wire-')
        $dbSuffix = if ($wire) { [Guid]::NewGuid().ToString('N') } else { $suffix }
        $database = "staff_${mode}_$dbSuffix"
        Invoke-StaffFixtureSql postgres "CREATE DATABASE $database"
        @"
BASE_DB_BOOTSTRAP_USER=postgres
BASE_DB_NAME=$database
BASE_DB_RUNTIME_USER=base_ielts_runtime
BASE_DB_RUNTIME_PASSWORD=$([Guid]::NewGuid().ToString('N'))
BASE_DB_MIGRATION_USER=base_ielts_migration
BASE_DB_MIGRATION_PASSWORD=$migrationPassword
"@ | Set-Content -LiteralPath $settingsFile
        try {
            if ($WslFixtureRoot) {
                & (Join-Path $root 'scripts/initialize-dev-db.ps1') -SettingsFile $settingsFile -WslFixtureRoot $WslFixtureRoot -WslPort $WslPort
            } else {
                & (Join-Path $root 'scripts/initialize-dev-db.ps1') -SettingsFile $settingsFile -FixtureContainer $container
            }
        } finally {
            Remove-Item -LiteralPath $settingsFile
        }
        $fixture = if ($WslFixtureRoot) { $WslFixtureRoot } else { $container }
        Write-Output "Staff fixture: $fixture/$database ($mode)"
        $env:BASE_TEST_STAFF_DB = "jdbc:postgresql://127.0.0.1:$port/$database"
        $env:BASE_TEST_STAFF_MIGRATION = $mode
        & (Join-Path $tempBackend 'mvnw.cmd') -B -f (Join-Path $tempBackend 'pom.xml') '-Dtest=StaffFixtureMigrationTest' test
        if ($LASTEXITCODE -ne 0) { throw "Staff fixture Flyway failed with exit code $LASTEXITCODE." }

        $grants = $fixtureGrants
        if ($IncludeCatalog) {
            $grants += "`n" + (Get-Content -Raw -LiteralPath (Join-Path $root 'backend/src/test/postgresql/catalog-staff.sql'))
        }
        if ($Combined -and !$wire) {
            $grants += "`n" + (Get-Content -Raw -LiteralPath (Join-Path $root 'backend/src/test/postgresql/reading-staff.sql'))
        }
        Invoke-StaffFixtureSql $database $grants

        if ($Combined -and $mode -eq 'placement') {
            $env:BASE_TEST_READING_V15_UPGRADE_URL = $env:BASE_TEST_STAFF_DB
            continue
        }
        if ($Combined -and $mode -eq 'fresh' -and !$wire) {
            $env:BASE_TEST_READING_V15_FRESH_URL = $env:BASE_TEST_STAFF_DB
        }
        $selected = $tests
        if ($wire) {
            $env:BASE_TEST_CATALOG_G03 = 'true'
            $env:BASE_TEST_CATALOG_G03_WIRE = 'true'
            $env:BASE_TEST_CATALOG_ENVELOPE_READERS = '3'
            $env:BASE_TEST_CATALOG_ENVELOPE = if ($run.StartsWith('wire-envelope-')) { 'true' } else { 'false' }
            $selected = 'CatalogG03PostgresTest#verifiesPo09AndPo10OverAnEmbeddedHttpServer'
        }
        & (Join-Path $tempBackend 'mvnw.cmd') -B -f (Join-Path $tempBackend 'pom.xml') "-Dtest=$selected" test
        if ($LASTEXITCODE -ne 0) { throw "Staff integration or regression tests failed with exit code $LASTEXITCODE." }
    }
    if ($Combined) {
        $env:BASE_DB_BOOTSTRAP_USER = 'postgres'
        $env:BASE_DB_BOOTSTRAP_PASSWORD = $adminPassword
        $env:BASE_DB_MIGRATION_PASSWORD = $migrationPassword
        $env:BASE_TEST_READING_STAFF_READER_PASSWORD = $readerPassword
        $env:BASE_TEST_READING_STAFF_WRITER_PASSWORD = $writerPassword
        $env:BASE_TEST_READING_COMBINED = 'true'
        & (Join-Path $tempBackend 'mvnw.cmd') -B -f (Join-Path $tempBackend 'pom.xml') '-Dtest=ReadingStaffPersistencePostgresTest' test
        if ($LASTEXITCODE -ne 0) { throw "Combined Reading/Catalog verification failed with exit code $LASTEXITCODE." }
    }
} finally {
    if ($created) { docker stop $container | Out-Null }
    foreach ($variable in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($variable, $savedEnvironment[$variable], 'Process')
    }
}
