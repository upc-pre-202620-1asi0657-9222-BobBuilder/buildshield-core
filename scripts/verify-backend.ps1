[CmdletBinding()]
param(
    [string]$PostgresBin,
    [string]$MavenCommand = '.\mvnw.cmd',
    [string]$MavenRepository,
    [switch]$RunLoad,
    [ValidateRange(1, 500)][int]$Users = 50,
    [ValidateRange(1, 600)][int]$Seconds = 60
)
$ErrorActionPreference = 'Stop'
$taskWorkspace = Split-Path -Parent $PSScriptRoot
$taskPreviousLocation = Get-Location
$taskCluster = $null
$taskStarted = $false
try {
    Set-Location -LiteralPath $taskWorkspace
    $taskMavenArgs = @('-B', '-ntp')
    if ($MavenRepository) { $taskMavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    if ($PostgresBin) {
        $taskCluster = Join-Path ([System.IO.Path]::GetFullPath($env:TEMP)) ('buildshield-e2e-' + [guid]::NewGuid().ToString('N'))
        $taskListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
        $taskListener.Start()
        $taskPort = $taskListener.LocalEndpoint.Port
        $taskListener.Stop()
        & (Join-Path $PostgresBin 'initdb.exe') -D $taskCluster -U buildshield_test -A trust --encoding=UTF8 --no-locale
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo inicializar PostgreSQL temporal' }
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $taskCluster -l (Join-Path $taskCluster 'server.log') -o "-h 127.0.0.1 -p $taskPort" -w start
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL temporal' }
        $taskStarted = $true
        foreach ($taskDatabase in @('buildshield_core_it', 'buildshield_kernel_it')) {
            & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $taskPort -U buildshield_test $taskDatabase
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base temporal' }
        }
        $taskMavenArgs += @('-Dbuildshield.test.external-database=true',
            "-Dbuildshield.test.core-url=jdbc:postgresql://127.0.0.1:$taskPort/buildshield_core_it",
            "-Dbuildshield.test.kernel-url=jdbc:postgresql://127.0.0.1:$taskPort/buildshield_kernel_it",
            '-Dbuildshield.test.database-user=buildshield_test')
    }
    & $MavenCommand @taskMavenArgs verify
    if ($LASTEXITCODE -ne 0) { throw 'La verificación completa falló' }
    New-Item -ItemType Directory -Force 'target/verification' | Out-Null
    Copy-Item -LiteralPath 'target/failsafe-reports/failsafe-summary.xml' -Destination 'target/verification/full-suite-summary.xml'
    if ($RunLoad) {
        & $MavenCommand @taskMavenArgs -Pload-tests "-Dbuildshield.load.users=$Users" "-Dbuildshield.load.seconds=$Seconds" test-compile failsafe:integration-test failsafe:verify
        if ($LASTEXITCODE -ne 0) { throw 'La medición de carga falló' }
    }
} finally {
    if ($taskStarted) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $taskCluster -m fast -w stop
        if ($LASTEXITCODE -ne 0) { Write-Warning "No se pudo detener el clúster temporal: $taskCluster" }
    }
    Set-Location -LiteralPath $taskPreviousLocation
}
