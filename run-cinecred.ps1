param(
    [switch]$Detach,
    [switch]$InstallJdkIfMissing
)

$ErrorActionPreference = "Stop"

function Get-JavaVersionOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JavaExe
    )

    return cmd /c """$JavaExe"" -version 2>&1"
}

function Find-Jdk21Home {
    $candidates = @(
        $env:JAVA_HOME,
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Java\latest"
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        Get-ChildItem -Path $candidate -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $javaExe = Join-Path $_.FullName "bin\java.exe"
            if (-not (Test-Path $javaExe)) {
                return
            }

            $versionOutput = Get-JavaVersionOutput -JavaExe $javaExe
            if ($versionOutput -match 'version "21(\.|$)') {
                return $_.FullName
            }
        }

        if (Test-Path (Join-Path $candidate "bin\java.exe")) {
            $versionOutput = Get-JavaVersionOutput -JavaExe (Join-Path $candidate "bin\java.exe")
            if ($versionOutput -match 'version "21(\.|$)') {
                return $candidate
            }
        }
    }

    return $null
}

function Ensure-Jdk21Home {
    $jdkHome = Find-Jdk21Home
    if ($jdkHome) {
        return $jdkHome
    }

    if (-not $InstallJdkIfMissing) {
        throw "JDK 21 was not found. Re-run with -InstallJdkIfMissing or install a local JDK 21 manually."
    }

    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "Installing Temurin JDK 21 with winget..."
        winget install --id EclipseAdoptium.Temurin.21.JDK --accept-source-agreements --accept-package-agreements --silent | Out-Host
        $jdkHome = Find-Jdk21Home
        if ($jdkHome) {
            return $jdkHome
        }
    }

    throw "JDK 21 installation did not complete successfully."
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$jdkHome = Ensure-Jdk21Home
$env:JAVA_HOME = $jdkHome
$env:Path = "$jdkHome\bin;$env:Path"

Write-Host "Using JAVA_HOME=$jdkHome"

if ($Detach) {
    $command = "`$env:JAVA_HOME='$jdkHome'; `$env:Path='$jdkHome\bin;' + `$env:Path; Set-Location '$repoRoot'; .\gradlew.bat runOnWindows"
    Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-Command", $command -WorkingDirectory $repoRoot | Out-Null
    Write-Host "Cinecred launch started in a detached PowerShell process."
    exit 0
}

& ".\gradlew.bat" "runOnWindows"
