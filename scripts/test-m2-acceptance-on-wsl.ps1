param(
    [Parameter(Mandatory)][ValidatePattern('^/tmp/base-ielts-pg1712-[A-Za-z0-9]{8}$')][string]$FixtureRoot,
    [Parameter(Mandatory)][string]$PrivateWslAddress,
    [ValidateRange(1024,65535)][int]$Port = 55481
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)
if ($PrivateWslAddress -notmatch '^172\.(1[6-9]|2[0-9]|3[01])\.[0-9]{1,3}\.[0-9]{1,3}$') {
    throw 'Expected a private WSL NAT address.'
}
$bin = "$FixtureRoot/runtime/bin"
$data = "$FixtureRoot/data"
$env:BASE_TEST_WSL_BOOTSTRAP_PASSWORD = [Guid]::NewGuid().ToString('N')
wsl.exe -d Ubuntu -- "$bin/pg_ctl" -D $data -l "$FixtureRoot/server.log" -o "-p $Port -h 127.0.0.1,$PrivateWslAddress -k $FixtureRoot/socket" -w start
if ($LASTEXITCODE) { exit $LASTEXITCODE }
$relay = $null
try {
    $identity = wsl.exe -d Ubuntu -- "$bin/psql" -h "$FixtureRoot/socket" -p $Port -U postgres -d postgres -XAtq -v ON_ERROR_STOP=1 -c "select current_setting('server_version_num') || '|' || current_setting('data_directory')"
    if ($LASTEXITCODE -or $identity -ne "170012|$data") { throw 'Incorrect isolated PostgreSQL identity.' }
    "ALTER ROLE postgres PASSWORD '$env:BASE_TEST_WSL_BOOTSTRAP_PASSWORD';" |
        wsl.exe -d Ubuntu -- "$bin/psql" -h "$FixtureRoot/socket" -p $Port -U postgres -d postgres -Xq -v ON_ERROR_STOP=1
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
    $relayFile = Join-Path ([IO.Path]::GetTempPath()) ('m2-wsl-relay-' + [guid]::NewGuid().ToString('N') + '.cjs')
    @'
const net = require('node:net');
const host = process.argv[2];
const port = Number(process.argv[3]);
if (!net.isIPv4(host) || !Number.isInteger(port)) throw new Error('Invalid fixture relay target');
const server = net.createServer(client => {
  const target = net.connect({ host, port });
  client.on('error', () => target.destroy());
  target.on('error', () => client.destroy());
  client.on('close', () => target.destroy());
  target.on('close', () => client.destroy());
  client.pipe(target).pipe(client);
});
server.listen(0, '127.0.0.1', () => console.log(server.address().port));
'@ | Set-Content -LiteralPath $relayFile
    $relay = Start-Process node.exe -ArgumentList @($relayFile,$PrivateWslAddress,$Port) -WindowStyle Hidden -PassThru -RedirectStandardOutput backend/m2-wsl-relay-port.log -RedirectStandardError backend/m2-wsl-relay-error.log
    for ($n = 0; $n -lt 25; $n++) {
        $jdbcPort = Get-Content backend/m2-wsl-relay-port.log -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jdbcPort -match '^[1-9][0-9]{3,4}$') { break }
        if ($relay.HasExited) { Get-Content backend/m2-wsl-relay-error.log; break }
        Start-Sleep -Milliseconds 200
    }
    if ($jdbcPort -notmatch '^[1-9][0-9]{3,4}$') { throw 'No Windows loopback relay port.' }
    Write-Output "Isolated fixture $FixtureRoot with private WSL port $Port and Windows loopback port $jdbcPort"
    pwsh -NoProfile -File scripts/test-m2-staff-auth.ps1 -Combined -WslFixtureRoot $FixtureRoot -WslPort $Port -WslJdbcPort $jdbcPort *> backend/m2-wsl-combined-final.log
    $combined = $LASTEXITCODE
    Write-Output "Combined exit: $combined"
    Get-Content backend/m2-wsl-combined-final.log -Tail 15
    pwsh -NoProfile -File scripts/test-m1-on-wsl-fixture.ps1 -FixtureRoot $FixtureRoot -Port $Port -JdbcPort $jdbcPort *> backend/m2-wsl-m1-launcher.log
    $m1 = $LASTEXITCODE
    Write-Output "M1 and browser launcher exit: $m1"
    Get-Content backend/m2-wsl-m1-launcher.log -Tail 26
    if ($combined) { exit $combined }
    exit $m1
} finally {
    if ($relay -and !$relay.HasExited) { Stop-Process -Id $relay.Id }
    wsl.exe -d Ubuntu -- "$bin/pg_ctl" -D $data -m fast -w stop
}
