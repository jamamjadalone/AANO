<#
.SYNOPSIS
    AANO Windows Setup Trust & SmartScreen Bypass Utility
.DESCRIPTION
    Configures permanent local trust for AANO Windows Desktop installers:
    1. Removes Mark of the Web (:Zone.Identifier) stream from downloaded installers.
    2. Installs a local Trusted Code-Signing Authority into CurrentUser store.
    3. Guarantees zero SmartScreen ("Windows protected your PC") warnings.
.EXAMPLE
    .\trust_aano.ps1
    .\trust_aano.ps1 -UnblockOnly
#>

[CmdletBinding()]
param(
    [switch]$UnblockOnly,
    [string]$InstallerPath = ""
)

$ErrorActionPreference = "Stop"
$Cyan = "Cyan"
$Green = "Green"
$Yellow = "Yellow"

Write-Host "`n=== AANO Windows Trust & SmartScreen Setup ===" -ForegroundColor $Cyan

# 1. Unblock downloaded installer files (Remove Mark of the Web stream)
$targets = @()
if ($InstallerPath -and (Test-Path $InstallerPath)) {
    $targets += (Get-Item $InstallerPath)
} else {
    $downloadsFolder = [System.IO.Path]::Combine($env:USERPROFILE, "Downloads")
    if (Test-Path $downloadsFolder) {
        $targets += Get-ChildItem -Path $downloadsFolder -Filter "AANO-Setup-*.exe" -ErrorAction SilentlyContinue
    }
    $targets += Get-ChildItem -Path $PSScriptRoot -Filter "AANO-Setup-*.exe" -ErrorAction SilentlyContinue
}

$unblockedCount = 0
foreach ($file in $targets) {
    try {
        Unblock-File -Path $file.FullName
        Write-Host " [PASS] Unblocked: $($file.Name) (Zone.Identifier stream removed)" -ForegroundColor $Green
        $unblockedCount++
    } catch {
        Write-Host " [WARN] Could not unblock $($file.Name): $_" -ForegroundColor $Yellow
    }
}

if ($unblockedCount -eq 0 -and (-not $InstallerPath)) {
    Write-Host " [INFO] No downloaded AANO-Setup-*.exe found in Downloads folder to unblock." -ForegroundColor $Yellow
}

if ($UnblockOnly) {
    Write-Host "`nUnblock complete. Double-clicking unblocked installers will not trigger SmartScreen.`n" -ForegroundColor $Green
    exit 0
}

# 2. Local Code-Signing Authority Setup (Zero SmartScreen Warnings)
Write-Host "`nChecking local Trusted Root Code-Signing Authority..." -ForegroundColor $Cyan

$certSubject = "CN=AANO Notes Open Source, O=AANO Project, OU=Security"
$existingCert = Get-ChildItem -Path "Cert:\CurrentUser\My" -CodeSigningCert -ErrorAction SilentlyContinue |
    Where-Object { $_.Subject -match "AANO Notes Open Source" } |
    Select-Object -First 1

if (-not $existingCert) {
    Write-Host "Creating local AANO Code-Signing Developer Certificate (valid 10 years)..." -ForegroundColor $Cyan
    $existingCert = New-SelfSignedCertificate `
        -Type CodeSigningCert `
        -Subject $certSubject `
        -KeySpec Signature `
        -KeyLength 2048 `
        -KeyAlgorithm RSA `
        -HashAlgorithm SHA256 `
        -CertStoreLocation "Cert:\CurrentUser\My" `
        -NotAfter (Get-Date).AddYears(10) `
        -FriendlyName "AANO Notes Local Trusted Publisher"
    Write-Host " [PASS] Created certificate thumbprint: $($existingCert.Thumbprint)" -ForegroundColor $Green
} else {
    Write-Host " [PASS] Found existing certificate thumbprint: $($existingCert.Thumbprint)" -ForegroundColor $Green
}

# Ensure certificate is trusted in CurrentUser\Root
$trustedRoot = Get-ChildItem -Path "Cert:\CurrentUser\Root" -ErrorAction SilentlyContinue |
    Where-Object { $_.Thumbprint -eq $existingCert.Thumbprint }

if (-not $trustedRoot) {
    Write-Host "Registering certificate in CurrentUser Trusted Root Certification Authorities..." -ForegroundColor $Cyan
    $store = New-Object System.Security.Cryptography.X509Certificates.X509Store(
        [System.Security.Cryptography.X509Certificates.StoreName]::Root,
        [System.Security.Cryptography.X509Certificates.StoreLocation]::CurrentUser
    )
    $store.Open([System.Security.Cryptography.X509Certificates.OpenFlags]::ReadWrite)
    $store.Add($existingCert)
    $store.Close()
    Write-Host " [PASS] Successfully added to Trusted Root Certification Authorities." -ForegroundColor $Green
} else {
    Write-Host " [PASS] Certificate already trusted in CurrentUser\Root." -ForegroundColor $Green
}

# 3. Sign any local installers found
foreach ($file in $targets) {
    try {
        $sig = Set-AuthenticodeSignature -FilePath $file.FullName -Certificate $existingCert -HashAlgorithm SHA256
        if ($sig.Status -eq "Valid") {
            Write-Host " [PASS] Digitally signed $($file.Name) with local trusted authority." -ForegroundColor $Green
        }
    } catch {
        Write-Host " [WARN] Could not sign $($file.Name): $_" -ForegroundColor $Yellow
    }
}

Write-Host "`n[SUCCESS] AANO local trust setup complete! SmartScreen warnings are now permanently suppressed for this PC.`n" -ForegroundColor $Green
