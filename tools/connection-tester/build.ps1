param(
    [switch]$FrameworkDependent
)

# Publish script for ConnectionTester.
# Default: self-contained single-file exe (target machine needs no .NET runtime).
# -FrameworkDependent: small package (~2MB), requires .NET 8 Desktop Runtime on target.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 without BOM
# as ANSI, which corrupts non-ASCII text and breaks quoting.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root

try {
    if ($FrameworkDependent) {
        Write-Host ">>> mode: framework-dependent (small, needs .NET 8 Desktop Runtime)" -ForegroundColor Cyan
        dotnet publish -c Release -r win-x64 --self-contained false `
            -p:PublishSingleFile=true -p:DebugType=none -o dist
    }
    else {
        Write-Host ">>> mode: self-contained single file (~63MB, double-click to run)" -ForegroundColor Cyan
        dotnet publish -c Release -r win-x64 --self-contained true `
            -p:PublishSingleFile=true `
            -p:IncludeNativeLibrariesForSelfExtract=true `
            -p:EnableCompressionInSingleFile=true `
            -p:DebugType=none -o dist
    }

    $exe = Join-Path $root "dist\ConnectionTester.exe"
    if (Test-Path $exe) {
        $sizeMb = [math]::Round((Get-Item $exe).Length / 1MB, 1)
        Write-Host ""
        Write-Host ("OK -> " + $exe + " (" + $sizeMb + " MB)") -ForegroundColor Green
    }
    else {
        Write-Host "Publish finished but exe not found. Check the output above." -ForegroundColor Yellow
    }
}
finally {
    Pop-Location
}
