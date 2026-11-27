$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
$settings = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env')) {
    if ($line -cmatch '^(BASE_DB_BOOTSTRAP_USER|BASE_DB_MIGRATION_PASSWORD)=(.+)$') {
        $settings[$Matches[1]] = $Matches[2]
    }
}
$bootstrap = $settings['BASE_DB_BOOTSTRAP_USER']
if ($bootstrap -cnotmatch '^[a-z][a-z0-9_]{0,62}$' -or !$settings['BASE_DB_MIGRATION_PASSWORD']) {
    throw 'Use the initialized local Compose PostgreSQL environment before running M2 catalog tests.'
}

$suffix = [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
$fresh = "base_ielts_m2test_fresh_$suffix"
$upgrade = "base_ielts_m2test_upgrade_$suffix"
$environment = @{
    BASE_TEST_CATALOG = 'true'
    BASE_TEST_CATALOG_FRESH_DB = $fresh
    BASE_TEST_CATALOG_UPGRADE_DB = $upgrade
    BASE_DB_MIGRATION_PASSWORD = $settings['BASE_DB_MIGRATION_PASSWORD']
    MAVEN_BASEDIR = (Join-Path $root 'backend')
}
$previous = @{}

Push-Location $root
try {
    foreach ($database in @($fresh, $upgrade)) {
        # Only generated fixture names and existing M1 roles appear in this SQL.
        @"
CREATE DATABASE $database;
GRANT CONNECT ON DATABASE $database TO base_ielts_migration, base_ielts_runtime;
GRANT CREATE ON DATABASE $database TO identity_migration_owner;
"@ | docker compose exec -T postgres psql -Xq -v ON_ERROR_STOP=1 -U $bootstrap -d postgres
        if ($LASTEXITCODE -ne 0) { throw "Unable to prepare local fixture $database." }
        Write-Output "Catalog fixture: $database"
    }
    foreach ($name in $environment.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $environment[$name], 'Process')
    }
    & (Join-Path $root 'backend/mvnw.cmd') -B -f backend/pom.xml '-Dtest=CatalogPostgresTest,CatalogInputTest' test
    if ($LASTEXITCODE -ne 0) { throw "Catalog PostgreSQL tests failed with exit code $LASTEXITCODE." }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}
