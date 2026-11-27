param([Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{40}$')][string]$MigrationCommit)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$suffix = [Guid]::NewGuid().ToString('N')
$workspace = Join-Path ([IO.Path]::GetTempPath()) "base-ielts-reading-v15-$suffix"
$archive = Join-Path $workspace 'source.zip'
$test = 'backend/src/test/java/com/baseielts/platform/reading/ReadingPassagePostgresContractTest.java'
New-Item -ItemType Directory -Path $workspace | Out-Null

# Use committed migration inputs, never working-tree files from the Catalog workstream.
& git -C $root archive --format=zip "--output=$archive" $MigrationCommit backend compose.yaml scripts/initialize-dev-db.ps1
if ($LASTEXITCODE) { throw 'Unable to export the coordinator-verified migration commit.' }
Expand-Archive -LiteralPath $archive -DestinationPath $workspace
if (!(Test-Path -LiteralPath (Join-Path $workspace 'backend/src/main/resources/db/migration/V15__reading_passage_storage.sql'))) {
    throw 'The selected commit does not contain the allocated V15 Passage migration.'
}
Copy-Item -LiteralPath (Join-Path $root $test) -Destination (Join-Path $workspace $test)

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
try {
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
} finally {
    $listener.Stop()
}
$settings = @{
    BASE_DB_PORT = "$port"
    BASE_DB_NAME = 'base_ielts'
    BASE_DB_BOOTSTRAP_USER = 'base_ielts_bootstrap'
    BASE_DB_BOOTSTRAP_PASSWORD = [Guid]::NewGuid().ToString('N')
    BASE_DB_RUNTIME_USER = 'base_ielts_runtime'
    BASE_DB_RUNTIME_PASSWORD = [Guid]::NewGuid().ToString('N')
    BASE_DB_MIGRATION_USER = 'base_ielts_migration'
    BASE_DB_MIGRATION_PASSWORD = [Guid]::NewGuid().ToString('N')
}
$fresh = 'base_ielts_m2test_reading_v15_fresh'
$upgrade = 'base_ielts_m2test_reading_v15_upgrade'
$project = "baseielts-reading-v15-$($suffix.Substring(0, 12))"
$variables = $settings.Clone()
$variables.COMPOSE_PROJECT_NAME = $project
$variables.BASE_TEST_READING_V15 = 'true'
$variables.BASE_TEST_READING_V15_FRESH_URL = "jdbc:postgresql://127.0.0.1:$port/$fresh"
$variables.BASE_TEST_READING_V15_UPGRADE_URL = "jdbc:postgresql://127.0.0.1:$port/$upgrade"
$variables.MAVEN_BASEDIR = Join-Path $workspace 'backend'
$previous = @{}
$fixtureEnv = Join-Path $workspace '.env'
$started = $false

Push-Location $workspace
try {
    foreach ($name in $variables.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $variables[$name], 'Process')
    }
    $settings.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content -LiteralPath $fixtureEnv
    $started = $true
    docker compose up -d --wait postgres
    if ($LASTEXITCODE) { throw 'Unable to start the isolated PostgreSQL project.' }
    pwsh -NoProfile -File scripts/initialize-dev-db.ps1
    if ($LASTEXITCODE) { throw 'Unable to initialize isolated test roles.' }
    foreach ($database in @($fresh, $upgrade)) {
        @"
CREATE DATABASE $database;
GRANT CONNECT ON DATABASE $database TO base_ielts_migration, base_ielts_runtime;
GRANT CREATE ON DATABASE $database TO identity_migration_owner;
"@ | docker compose exec -T postgres psql -Xq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d postgres
        if ($LASTEXITCODE) { throw 'Unable to create an isolated Reading fixture database.' }
    }
    docker compose exec -T postgres psql -XAtq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d postgres -c 'SHOW server_version'
    if ($LASTEXITCODE) { throw 'Unable to verify PostgreSQL version.' }
    & backend/mvnw.cmd -B -f backend/pom.xml -Dtest=ReadingPassagePostgresContractTest test
    if ($LASTEXITCODE) { throw "Reading PostgreSQL verification exited $LASTEXITCODE." }
} finally {
    try {
        if ($started) {
            docker compose stop postgres | Out-Null
            if ($LASTEXITCODE) { throw "Unable to stop isolated fixture project $project." }
        }
    } finally {
        if (Test-Path -LiteralPath $fixtureEnv) { Remove-Item -LiteralPath $fixtureEnv }
        foreach ($name in $previous.Keys) {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
        Pop-Location
    }
}
