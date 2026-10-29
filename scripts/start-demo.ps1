$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
if (-not $env:LAB_JWT_SECRET) {
    $env:LAB_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
}
# Пароли учебных пользователей; в базе будут храниться их BCrypt-хэши.
if (-not $env:LAB_ALICE_PASSWORD) { $env:LAB_ALICE_PASSWORD = 'AliceDemo2026!' }
if (-not $env:LAB_BOB_PASSWORD) { $env:LAB_BOB_PASSWORD = 'BobDemo2026!' }
& .\mvnw.cmd spring-boot:run
exit $LASTEXITCODE
