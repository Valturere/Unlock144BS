param(
    [string]$DistinguishedName = "CN=Unlock144BS, OU=Release, O=Unlock144BS, L=Samara, C=RU"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot "signing"
$keystorePath = Join-Path $signingDirectory "unlock144bs-release.jks"
$propertiesPath = Join-Path $projectRoot "keystore.properties"
$keytoolCandidates = @()
if ($env:JAVA_HOME) {
    $keytoolCandidates += Join-Path $env:JAVA_HOME "bin\keytool.exe"
}
$keytoolCandidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
$keytoolOnPath = Get-Command keytool.exe -ErrorAction SilentlyContinue
if ($keytoolOnPath) {
    $keytoolCandidates += $keytoolOnPath.Source
}
$keytoolPath = $keytoolCandidates |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1

if (Test-Path -LiteralPath $keystorePath) {
    throw "Release keystore already exists: $keystorePath"
}
if (Test-Path -LiteralPath $propertiesPath) {
    throw "Signing properties already exist: $propertiesPath"
}
if (-not $keytoolPath) {
    throw "keytool was not found in JAVA_HOME, Android Studio, or PATH"
}

New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null

$randomBytes = New-Object byte[] 36
[System.Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$password = [Convert]::ToBase64String($randomBytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
$alias = "unlock144bs"

$env:UNLOCK144BS_KEYSTORE_PASSWORD = $password
try {
    & $keytoolPath -genkeypair -v `
        -keystore $keystorePath `
        -storetype PKCS12 `
        -storepass:env UNLOCK144BS_KEYSTORE_PASSWORD `
        -keypass:env UNLOCK144BS_KEYSTORE_PASSWORD `
        -alias $alias `
        -keyalg RSA `
        -keysize 4096 `
        -validity 36500 `
        -dname $DistinguishedName
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }
} finally {
    Remove-Item Env:UNLOCK144BS_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
}

$relativeKeystorePath = "signing/unlock144bs-release.jks"
$propertyLines = @(
    "storeFile=$relativeKeystorePath",
    "storePassword=$password",
    "keyAlias=$alias",
    "keyPassword=$password"
)
[System.IO.File]::WriteAllLines($propertiesPath, $propertyLines, [System.Text.UTF8Encoding]::new($false))

Write-Host "Release keystore created. Secrets were written only to ignored local files."
Write-Host "Keystore: $keystorePath"
Write-Host "Properties: $propertiesPath"
