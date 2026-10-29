$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
function New-LabSecret {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
}
if (-not $env:LAB_JWT_SECRET) { $env:LAB_JWT_SECRET = New-LabSecret }
if (-not $env:LAB_ALICE_PASSWORD) { $env:LAB_ALICE_PASSWORD = (New-LabSecret).Substring(0, 24) }
if (-not $env:LAB_BOB_PASSWORD) { $env:LAB_BOB_PASSWORD = (New-LabSecret).Substring(0, 24) }
Write-Host 'Local demo: http://127.0.0.1:8080 (data is reset on restart)'
Write-Host "alice password: $env:LAB_ALICE_PASSWORD"
Write-Host "bob password:   $env:LAB_BOB_PASSWORD"
& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
