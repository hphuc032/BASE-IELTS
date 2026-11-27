param([switch]$Migrate,[ValidatePattern('^base_ielts_m1test_[0-9]+$')][string]$FixtureDatabase)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env')) {
    if ($line -match '^(BASE_DB_(?:JDBC_URL|RUNTIME_USER|RUNTIME_PASSWORD|MIGRATION_USER|MIGRATION_PASSWORD))=(.+)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
if ($FixtureDatabase) {
    $env:BASE_DB_JDBC_URL = "jdbc:postgresql://127.0.0.1:5432/$FixtureDatabase"
}
$env:BASE_DB_MIGRATE = if ($Migrate) { 'true' } else { 'false' }
$env:BASE_BACKEND_PORT = '18080'
$jar = Join-Path $root 'backend/target/identity-foundation-0.1.0-SNAPSHOT.jar'
$log = Join-Path $root 'backend/target/live-db-smoke.log'
$errors = Join-Path $root 'backend/target/live-db-smoke-errors.log'
if (!(Test-Path $jar)) { throw 'Build the backend before running the PostgreSQL smoke check.' }
if (netstat -ano -p tcp | Select-String ':18080\s+.*LISTENING') { throw 'Smoke-test port 18080 is already in use.' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java.exe' }
$process = Start-Process $java -ArgumentList @('-jar', $jar, '--spring.profiles.active=dev') -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $errors
try {
    for ($attempt = 0; $attempt -lt 60 -and !$process.HasExited; $attempt++) {
        $reply = (curl.exe --silent --max-time 1 -i 'http://127.0.0.1:18080/api/v1/health') -join [Environment]::NewLine
        if ($reply -match 'HTTP/1\.[01] 200' -and $reply -match '"status"\s*:\s*"UP"' -and $reply -notmatch '(?im)^Set-Cookie:') {
            if (!(Select-String -Quiet -Path $log -Pattern 'Added connection org.postgresql.jdbc.PgConnection')) { throw 'No PostgreSQL pool connection confirmed.' }
            if ($Migrate) {
                $migrationResult = Select-String -Path $log -Pattern 'Successfully applied|No migration necessary'
                if (!$migrationResult) { throw 'Flyway did not report migration or validation.' }
                $migrationResult | ForEach-Object { $_.Line }
            }
            Write-Output 'PASS: PostgreSQL JDBC connected using the runtime role; public health 200 UP without a cookie.'
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw 'Database-backed health failed. Inspect backend/target/live-db-smoke.log locally.'
} finally {
    if (!$process.HasExited) { Stop-Process -Id $process.Id }
}
