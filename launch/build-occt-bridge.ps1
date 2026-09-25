# Build chekator_occt.dll JNI bridge for Open CASCADE (Windows x64).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$CacheOcct = Join-Path $PSScriptRoot "cache\occt"

function Find-OcctPackageRoot {
    if ($env:OCCT_ROOT -and (Test-Path (Join-Path $env:OCCT_ROOT "inc"))) {
        return (Resolve-Path $env:OCCT_ROOT).Path
    }
    if (-not (Test-Path $CacheOcct)) { return $null }
    $dirs = Get-ChildItem -Path $CacheOcct -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'opencascade|occt-vc' -and $_.Name -notmatch '^_' }
    foreach ($d in $dirs) {
        if ((Test-Path (Join-Path $d.FullName "win64")) -or (Test-Path (Join-Path $d.FullName "inc"))) {
            return $d.FullName
        }
    }
    return $null
}

function Find-OcctBinLibDir {
    param([string]$PackageRoot)
    foreach ($rel in @("win64\vc14", "bin", "win64")) {
        $dir = Join-Path $PackageRoot $rel
        if ((Test-Path (Join-Path $dir "bin")) -and (Test-Path (Join-Path $dir "lib"))) {
            return (Resolve-Path $dir).Path
        }
        if ((Test-Path (Join-Path $dir "TKernel.lib")) -and (Test-Path (Join-Path $dir "TKernel.dll"))) {
            return (Resolve-Path $dir).Path
        }
    }
    $nested = Join-Path $PackageRoot "win64\vc14"
    if ((Test-Path (Join-Path $nested "bin")) -and (Test-Path (Join-Path $nested "lib"))) {
        return (Resolve-Path $nested).Path
    }
    return $null
}

function Ensure-OcctIncPresent {
    param([string]$PackageRoot)
    if (Test-Path (Join-Path $PackageRoot "inc")) { return $true }
    $zipCandidates = @(
        (Join-Path $CacheOcct "opencascade-8.0.0-vc14-64.zip"),
        "D:\Завантаження\opencascade-release-no-pch\opencascade-8.0.0-vc14-64.zip"
    )
    foreach ($zip in $zipCandidates) {
        if (-not (Test-Path $zip)) { continue }
        Write-Host "Extracting missing inc/ from zip: $zip"
        $temp = Join-Path $CacheOcct "_zip_extract_inc"
        if (Test-Path $temp) { Remove-Item -Recurse -Force $temp }
        Expand-Archive -Path $zip -DestinationPath $temp -Force
        $inner = Get-ChildItem -Path $temp -Directory | Where-Object { $_.Name -match 'opencascade' } | Select-Object -First 1
        if (-not $inner) { continue }
        $srcInc = Join-Path $inner.FullName "inc"
        if (-not (Test-Path $srcInc)) { continue }
        Copy-Item -Recurse -Force $srcInc (Join-Path $PackageRoot "inc")
        Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
        return $true
    }
    return $false
}

$OcctRoot = Find-OcctPackageRoot
if (-not $OcctRoot) {
    Write-Host "OCCT package not found. Put opencascade-* folder under launch\cache\occt\"
    exit 1
}
if (-not (Ensure-OcctIncPresent $OcctRoot)) {
    Write-Host "OCCT inc/ missing. Re-extract full opencascade-8.0.0-vc14-64.zip (not binaries-only)."
    exit 1
}

$OcctBinLib = Find-OcctBinLibDir $OcctRoot
if (-not $OcctBinLib) {
    Write-Host "OCCT bin/lib not found under $OcctRoot"
    exit 1
}

Write-Host "OCCT_ROOT     = $OcctRoot"
Write-Host "OCCT_BINLIB   = $OcctBinLib"

function Find-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "include\jni.h"))) {
        return (Resolve-Path $env:JAVA_HOME).Path
    }
    $cacheJdk = Get-ChildItem -Path (Join-Path $PSScriptRoot "cache") -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "include\jni.h") } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    $candidates = @(
        $(if ($cacheJdk) { $cacheJdk.FullName }),
        "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot",
        "C:\Program Files\Java\latest"
    ) | Where-Object { $_ }
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "include\jni.h")) { return $c }
    }
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if ($javac) {
        $bin = Split-Path $javac.Source
        $home = Split-Path $bin
        if (Test-Path (Join-Path $home "include\jni.h")) { return $home }
    }
    throw "JDK not found (need include\jni.h). Install JDK 17+ or set JAVA_HOME."
}

$BridgeDir = Join-Path $Root "native\occt-bridge"
$BuildDir = Join-Path $BridgeDir "build"
$OutStaging = Join-Path $PSScriptRoot "staging\occt\win64"
$OutApp = Join-Path $PSScriptRoot "CNC_Modeling\app\occt\win64"

function Find-CmakeExe {
    $cmd = Get-Command cmake -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        "${env:ProgramFiles}\CMake\bin\cmake.exe",
        "${env:ProgramFiles(x86)}\CMake\bin\cmake.exe",
        "${env:ProgramFiles}\Microsoft Visual Studio\2022\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        "${env:ProgramFiles}\Microsoft Visual Studio\2022\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        "${env:ProgramFiles}\Microsoft Visual Studio\2022\BuildTools\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe",
        "${env:ProgramFiles}\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
    )
    foreach ($path in $candidates) {
        if (Test-Path $path) { return $path }
    }
    return $null
}

function Find-MsvcClPath {
    param([string]$VsInstallRoot)
    if (-not $VsInstallRoot) { return $null }
    $pattern = Join-Path $VsInstallRoot "VC\Tools\MSVC\*\bin\Hostx64\x64\cl.exe"
    $found = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1
    if ($found) { return $found.FullName }
    return $null
}

function Resolve-VsCmakeGenerator {
    $vsWhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vsWhere)) {
        return @{ Generator = "Visual Studio 17 2022"; VsPath = $null; ClPath = $null }
    }
    $vsPath = & $vsWhere -latest -products * -property installationPath 2>$null
    if (-not $vsPath) {
        return @{ Generator = $null; VsPath = $null; ClPath = $null }
    }
    $ver = & $vsWhere -latest -products * -property installationVersion 2>$null
    $major = 17
    if ($ver -match '^(\d+)\.') { $major = [int]$Matches[1] }
    $generator = switch ($major) {
        18 { "Visual Studio 18 2026" }
        17 { "Visual Studio 17 2022" }
        16 { "Visual Studio 16 2019" }
        default { "Visual Studio 17 2022" }
    }
    $cl = Find-MsvcClPath $vsPath
    return @{ Generator = $generator; VsPath = $vsPath; ClPath = $cl; Version = $ver }
}

$cmakeExe = Find-CmakeExe
if (-not $cmakeExe) {
    throw "cmake not found. Install CMake 3.20+ (https://cmake.org/download/)"
}
$cmakeDir = Split-Path $cmakeExe
$env:PATH = "$cmakeDir;$env:PATH"
Write-Host "cmake = $cmakeExe"

$vs = Resolve-VsCmakeGenerator
if (-not $vs.Generator) {
    throw "Visual Studio not found. Install VS 2019+ with C++ (Desktop development with C++)."
}
Write-Host "Visual Studio = $($vs.VsPath) ($($vs.Version))"
Write-Host "CMake generator = $($vs.Generator)"
if (-not $vs.ClPath) {
    Write-Host ""
    Write-Host "ERROR: MSVC compiler (cl.exe) is not installed."
    Write-Host "Open 'Visual Studio Installer' -> Modify your VS -> check:"
    Write-Host "  'Desktop development with C++' (or 'MSVC v143/v144 build tools')"
    Write-Host "Then run this script again."
    Write-Host ""
    exit 1
}
Write-Host "MSVC cl.exe = $($vs.ClPath)"
$generator = $vs.Generator

$JdkHome = Find-JdkHome
Write-Host "JAVA_HOME     = $JdkHome"

if (Test-Path $BuildDir) { Remove-Item -Recurse -Force $BuildDir }
New-Item -ItemType Directory -Force -Path $BuildDir, $OutStaging | Out-Null

Push-Location $BuildDir
cmake -G $generator -A x64 `
    "-DOCCT_ROOT=$OcctRoot" `
    "-DOCCT_BINLIB=$OcctBinLib" `
    "-DJAVA_HOME=$JdkHome" `
    ..
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    throw "cmake configure failed"
}
cmake --build . --config Release
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    throw "cmake build failed"
}
Pop-Location

function Install-OcctRuntime {
    param([string]$TargetDir)
    New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
    Copy-Item -Force (Join-Path $OcctBinLib "bin\*.dll") $TargetDir
    $thirdParty = Join-Path $CacheOcct "3rdparty-vc14-64"
    if (Test-Path $thirdParty) {
        Get-ChildItem -Path $thirdParty -Directory | ForEach-Object {
            $bin = Join-Path $_.FullName "bin"
            if (Test-Path $bin) {
                Copy-Item -Force (Join-Path $bin "*.dll") $TargetDir -ErrorAction SilentlyContinue
            }
        }
    }
    $built = @(
        Get-ChildItem -Path (Join-Path $BuildDir "bin\Release\chekator_occt.dll") -ErrorAction SilentlyContinue
        Get-ChildItem -Path $BuildDir -Recurse -Filter "chekator_occt.dll" -ErrorAction SilentlyContinue
        Get-ChildItem -Path (Join-Path $OcctBinLib "bin\chekator_occt.dll") -ErrorAction SilentlyContinue
    ) | Select-Object -First 1
    if (-not $built) {
        throw "chekator_occt.dll not found after build"
    }
    Copy-Item -Force $built.FullName $TargetDir
    $winpath = @(
        Get-ChildItem -Path (Join-Path $BuildDir "bin\Release\chekator_occt_winpath.dll") -ErrorAction SilentlyContinue
        Get-ChildItem -Path $BuildDir -Recurse -Filter "chekator_occt_winpath.dll" -ErrorAction SilentlyContinue
    ) | Select-Object -First 1
    if ($winpath) {
        Copy-Item -Force $winpath.FullName $TargetDir
    }
}

Write-Host "Installing OCCT runtime to $OutStaging ..."
Install-OcctRuntime $OutStaging
Write-Host "OK: chekator_occt.dll -> $OutStaging"

if (Test-Path (Join-Path $PSScriptRoot "CNC_Modeling\app")) {
    Write-Host "Updating live app folder: $OutApp ..."
    Install-OcctRuntime $OutApp
}

Write-Host "Done. Run: .\build.ps1  (OCCT DLLs are kept in staging\occt\win64)"
