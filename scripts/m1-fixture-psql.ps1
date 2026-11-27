# Test-only psql selector for an explicitly prepared fixture.
function Invoke-M1FixturePsql {
    if ($env:BASE_TEST_WSL_FIXTURE_ROOT) {
        $fixtureRoot = $env:BASE_TEST_WSL_FIXTURE_ROOT
        $fixturePort = $env:BASE_TEST_WSL_FIXTURE_PORT
        if ($fixtureRoot -cnotmatch '^/tmp/base-ielts-pg1712-[A-Za-z0-9]{8}$' -or $fixturePort -notmatch '^[1-9][0-9]{3,4}$') {
            throw 'Expected a dedicated WSL PostgreSQL fixture.'
        }
        $input | wsl.exe -d Ubuntu -- "$fixtureRoot/runtime/bin/psql" -h "$fixtureRoot/socket" -p $fixturePort @args
    } else {
        $input | docker compose exec -T postgres psql @args
    }
}
