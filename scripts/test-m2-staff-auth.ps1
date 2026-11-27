$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$suffix = [Guid]::NewGuid().ToString('N')
$container = "base-ielts-staff-test-$suffix"
$imageLine = (Get-Content -LiteralPath (Join-Path $root 'compose.yaml') |
    Where-Object { $_ -match '^\s+image: postgres:17\.12-' } | Select-Object -First 1)
if (!$imageLine) { throw 'The pinned PostgreSQL fixture image was not found.' }
$image = $imageLine.Trim().Substring('image: '.Length)
$migration = Join-Path $root 'backend/src/main/resources/db/migration'
$adminPassword = [Guid]::NewGuid().ToString('N')
$readerPassword = [Guid]::NewGuid().ToString('N')
$writerPassword = [Guid]::NewGuid().ToString('N')
$tempBackend = Join-Path ([IO.Path]::GetTempPath()) "base-ielts-staff-test-$suffix"
$savedEnvironment = @{}
foreach ($variable in @('POSTGRES_PASSWORD', 'BASE_TEST_STAFF_DB', 'BASE_TEST_STAFF_AUTH',
    'BASE_TEST_STAFF_OWNER_PASSWORD', 'BASE_TEST_STAFF_READER_PASSWORD',
    'BASE_TEST_STAFF_WRITER_PASSWORD', 'MAVEN_BASEDIR')) {
    $savedEnvironment[$variable] = [Environment]::GetEnvironmentVariable($variable, 'Process')
}
$created = $false
try {
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

    $bootstrap = @"
CREATE ROLE account_fence_owner NOLOGIN;
CREATE ROLE base_ielts_runtime NOLOGIN;
CREATE ROLE content_staff_reader LOGIN NOINHERIT PASSWORD '$readerPassword';
CREATE ROLE content_author_writer LOGIN NOINHERIT PASSWORD '$writerPassword';
"@
    $fixtureGrants = @'
GRANT USAGE ON SCHEMA account_fence_routines TO content_staff_reader, content_author_writer;
GRANT EXECUTE ON FUNCTION account_fence_routines."lockAndCheckActor"(uuid,bigint)
TO content_staff_reader, content_author_writer;
'@
    $identity = Get-Content -Raw -LiteralPath (Join-Path $migration 'V1__identity_schema.sql')
    $fence = Get-Content -Raw -LiteralPath (Join-Path $migration 'V2__account_fencing.sql')
    @($bootstrap, $identity, $fence, $fixtureGrants) |
        docker exec -i $container psql -Xq -v ON_ERROR_STOP=1 -U postgres -d postgres 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Isolated staff fixture setup failed.' }
    $address = docker port $container 5432/tcp
    if ($LASTEXITCODE -ne 0 -or $address -notmatch '^127\.0\.0\.1:([0-9]+)$') {
        throw 'Unexpected isolated fixture port binding.'
    }
    $env:BASE_TEST_STAFF_DB = "jdbc:postgresql://127.0.0.1:$($Matches[1])/postgres"
    $env:BASE_TEST_STAFF_AUTH = 'true'
    $env:BASE_TEST_STAFF_OWNER_PASSWORD = $adminPassword
    $env:BASE_TEST_STAFF_READER_PASSWORD = $readerPassword
    $env:BASE_TEST_STAFF_WRITER_PASSWORD = $writerPassword

    New-Item -ItemType Directory -Path $tempBackend | Out-Null
    foreach ($name in @('pom.xml', 'mvnw.cmd', '.mvn', 'src')) {
        Copy-Item -LiteralPath (Join-Path $root "backend/$name") -Destination $tempBackend -Recurse
    }
    $env:MAVEN_BASEDIR = $tempBackend
    & (Join-Path $tempBackend 'mvnw.cmd') -B -f (Join-Path $tempBackend 'pom.xml') '-Dtest=FoundationSecurityTest,AuthSessionsTest,StaffAuthenticationTest,CatalogInputTest,CatalogHttpInputTest' test
    if ($LASTEXITCODE -ne 0) { throw "Staff integration or regression tests failed with exit code $LASTEXITCODE." }
} finally {
    if ($created) { docker stop $container | Out-Null }
    foreach ($variable in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($variable, $savedEnvironment[$variable], 'Process')
    }
}
