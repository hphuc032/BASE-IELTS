param([Parameter(Mandatory)][ValidatePattern('^base_ielts_m1test_[0-9]+$')][string]$FixtureDatabase,[switch]$Https,[switch]$ActiveShutdown,[switch]$GracefulShutdown)
. (Join-Path $PSScriptRoot 'm1-fixture-psql.ps1')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
Push-Location $root
$triggerInstalled = $false
try {
    if ($ActiveShutdown -and !$Https) { throw 'Active shutdown requires the local HTTPS ingress fixture.' }
    if ($GracefulShutdown -and !$ActiveShutdown) { throw 'Graceful shutdown requires the active transaction fixture.' }
    if ($GracefulShutdown) {
        $env:BASE_ACCEPTANCE_GRACEFUL = 'true'
        $env:SPRING_APPLICATION_ADMIN_ENABLED = 'true'
        $env:SERVER_SHUTDOWN = 'graceful'
    }
    foreach ($line in $(if (!$env:BASE_TEST_WSL_FIXTURE_ROOT) { Get-Content .env })) {
        if ($line -match '^(BASE_DB_(?:RUNTIME_USER|RUNTIME_PASSWORD))=(.+)$') {
            [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
        }
    }
    $fixturePort = if ($env:BASE_TEST_WSL_FIXTURE_ROOT) { $env:BASE_TEST_WSL_JDBC_PORT } else { 5432 }
    $env:BASE_DB_JDBC_URL="jdbc:postgresql://127.0.0.1:${fixturePort}/$FixtureDatabase"
    $env:BASE_BACKEND_PORT='18083'
    $env:BASE_BACKEND_ORIGIN='http://127.0.0.1:18083'
    $env:BASE_IDENTITY_ENABLED='true'
    $env:BASE_IDENTITY_VERIFICATION_CAPTURE='true'
    $id=[guid]::NewGuid().ToString('N')
    $raw=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).TrimEnd('=').Replace('+','-').Replace('/','_')
    $digest=[Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($raw)))
    $secondRaw=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).TrimEnd('=').Replace('+','-').Replace('/','_')
    $secondDigest=[Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($secondRaw)))
    $env:BASE_BROWSER_FIXTURE_EMAIL="browser-$id@identity-test.invalid"
    if ($ActiveShutdown) {
        $env:BASE_ACCEPTANCE_DATABASE=$FixtureDatabase
        $env:BASE_ACCEPTANCE_APPLICATION_NAME="m1-cutover-$id"
        $fixturePort = if ($env:BASE_TEST_WSL_FIXTURE_ROOT) { $env:BASE_TEST_WSL_JDBC_PORT } else { 5432 }
    $env:BASE_DB_JDBC_URL += "?ApplicationName=$env:BASE_ACCEPTANCE_APPLICATION_NAME"
    }
    $seed=@"
WITH pending AS (
    INSERT INTO identity.users(canonical_email,display_name)
    VALUES ('$env:BASE_BROWSER_FIXTURE_EMAIL','Browser fixture'), ('load-$env:BASE_BROWSER_FIXTURE_EMAIL','Independent login fixture')
    RETURNING id,canonical_email
), membership AS (
    INSERT INTO identity.user_roles(user_id,role_id) SELECT pending.id,r.id FROM pending CROSS JOIN identity.roles r WHERE r.code='LEARNER'
)
INSERT INTO identity.verification_tokens(user_id,email_revision,token_digest,issued_at,expires_at)
SELECT id,0,decode(CASE WHEN canonical_email='$env:BASE_BROWSER_FIXTURE_EMAIL' THEN '$digest' ELSE '$secondDigest' END,'hex'),
    clock_timestamp(),clock_timestamp()+interval '3 minutes' FROM pending;
"@
    $seed | Invoke-M1FixturePsql -Xq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
    if ($LASTEXITCODE) { throw 'Unable to seed isolated browser fixture.' }
    if ($ActiveShutdown) {
        @"
BEGIN;
CREATE FUNCTION identity.fixture_pause_$id() RETURNS trigger LANGUAGE plpgsql AS `$`$
BEGIN
    IF OLD.canonical_email = '$env:BASE_BROWSER_FIXTURE_EMAIL' AND NEW.display_name = 'Interrupted fixture write' THEN
        PERFORM pg_catalog.pg_sleep($(if ($GracefulShutdown) { '0.65' } else { '8' }));
    END IF;
    RETURN NEW;
END;
`$`$;
CREATE TRIGGER fixture_pause_$id BEFORE UPDATE OF display_name ON identity.users
FOR EACH ROW EXECUTE FUNCTION identity.fixture_pause_$id();
COMMIT;
"@ | Invoke-M1FixturePsql -Xq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
        if ($LASTEXITCODE) { throw 'Unable to install the isolated transaction barrier.' }
        $triggerInstalled = $true
    }

    foreach ($port in @(3301,18083,3443,3080)) {
        if (netstat -ano -p tcp | Select-String ":${port}\s+.*LISTENING") { throw "Test port $port is occupied." }
    }
    $java=Join-Path $env:JAVA_HOME 'bin/java.exe'
    if (!(Test-Path $java)) { throw 'Set JAVA_HOME to the approved Java 21 JDK before launching the browser fixture.' }
    $backend=Start-Process $java -ArgumentList @('-jar','backend/target/identity-foundation-0.1.0-SNAPSHOT.jar','--spring.profiles.active=dev') -WindowStyle Hidden -PassThru -RedirectStandardOutput backend/target/browser-backend.log -RedirectStandardError backend/target/browser-backend-error.log
    if ($Https) {
        $env:BASE_ACCEPTANCE_BACKEND_PID=[string]$backend.Id
        $env:BASE_ACCEPTANCE_TLS_DIR=(New-Item -ItemType Directory "backend/target/acceptance-tls-$id").FullName
        "[req]`ndistinguished_name=dn`n[dn]" | Set-Content "$env:BASE_ACCEPTANCE_TLS_DIR/openssl.cnf"
        openssl req -config "$env:BASE_ACCEPTANCE_TLS_DIR/openssl.cnf" -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=BASE-IELTS-Fixture -addext subjectAltName=IP:127.0.0.1 -keyout "$env:BASE_ACCEPTANCE_TLS_DIR/private.key" -out "$env:BASE_ACCEPTANCE_TLS_DIR/certificate.pem" 2> "$env:BASE_ACCEPTANCE_TLS_DIR/openssl.log"
        if ($LASTEXITCODE) { throw 'Unable to create the ephemeral fixture certificate.' }
        $ingress=Start-Process node.exe -ArgumentList 'frontend/tests/acceptance-ingress.mjs' -WindowStyle Hidden -PassThru
    }
    $frontend=Start-Process node.exe -ArgumentList @('node_modules/next/dist/bin/next','start','-H','127.0.0.1','-p','3301') -WorkingDirectory frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput backend/target/browser-frontend.log -RedirectStandardError backend/target/browser-frontend-error.log
    for ($i=0; $i -lt 35; $i++) {
        try {
            $ready=curl.exe -s --max-time 2 'http://127.0.0.1:3301/api/v1/health' | ConvertFrom-Json
            if ($ready.status -eq 'UP') { break }
        } catch {}
        if ($i -eq 34) { throw 'The browser fixture servers did not become ready.' }
        Start-Sleep -Seconds 1
    }
    $env:BASE_BROWSER_FIXTURE_TOKEN=$raw
    $env:BASE_BROWSER_SECOND_TOKEN=$secondRaw
    Push-Location frontend
    try {
        node node_modules/@playwright/test/cli.js test
        if ($LASTEXITCODE) { throw 'Playwright browser acceptance failed.' }
    } finally { Pop-Location }
} finally {
    foreach ($process in @($backend,$frontend,$ingress)) {
        if ($process -and !$process.HasExited) { Stop-Process -Id $process.Id }
    }
    if ($triggerInstalled) {
        "DROP TRIGGER fixture_pause_$id ON identity.users; DROP FUNCTION identity.fixture_pause_$id();" |
            Invoke-M1FixturePsql -Xq -v ON_ERROR_STOP=1 -U base_ielts_bootstrap -d $FixtureDatabase
        if ($LASTEXITCODE) { throw 'Unable to remove the isolated transaction barrier.' }
    }
    if ($Https -and $env:BASE_ACCEPTANCE_TLS_DIR) {
        foreach ($name in @('private.key','certificate.pem')) {
            Remove-Item -LiteralPath (Join-Path $env:BASE_ACCEPTANCE_TLS_DIR $name) -ErrorAction SilentlyContinue
        }
    }
    Remove-Item Env:BASE_BROWSER_FIXTURE_TOKEN,Env:BASE_BROWSER_SECOND_TOKEN -ErrorAction SilentlyContinue
    Remove-Item Env:BASE_ACCEPTANCE_TLS_DIR,Env:BASE_ACCEPTANCE_BACKEND_PID -ErrorAction SilentlyContinue
    Remove-Item Env:BASE_ACCEPTANCE_DATABASE,Env:BASE_ACCEPTANCE_APPLICATION_NAME -ErrorAction SilentlyContinue
    if ($GracefulShutdown) {
        Remove-Item Env:BASE_ACCEPTANCE_GRACEFUL,Env:SPRING_APPLICATION_ADMIN_ENABLED,Env:SERVER_SHUTDOWN -ErrorAction SilentlyContinue
    }
    Pop-Location
}
