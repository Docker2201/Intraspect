# Build portable Windows app-image (folder with .exe) into this directory.
# Uses JavaFX *jmods* with jpackage so Windows natives load correctly (SDK jars alone often fail to start).
# Needs nothing installed: a JDK on PATH or in JAVA_HOME is used if there is one,
# otherwise a portable Temurin 21 is downloaded into launch/cache and used instead.
param([string]$OutputDirectory = "")
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Staging = Join-Path $PSScriptRoot "staging"
$Cache = Join-Path $PSScriptRoot "cache"
$JavaFxVersion = "21.0.2"
# Версия сборки: одна на exe и README. Номер не меняется на каждое обновление.
$AppVersion = "1.0"

$JavaFxSdkZip = "openjfx-$JavaFxVersion" + "_windows-x64_bin-sdk.zip"
$JavaFxSdkUrl = "https://download2.gluonhq.com/openjfx/$JavaFxVersion/$JavaFxSdkZip"
$JavaFxJmodsZip = "openjfx-$JavaFxVersion" + "_windows-x64_bin-jmods.zip"
$JavaFxJmodsUrl = "https://download2.gluonhq.com/openjfx/$JavaFxVersion/$JavaFxJmodsZip"

function Get-PortableJdk {
    # Temurin 21 with jmods, unpacked into the cache. jpackage needs the jmods,
    # and on a machine with no Java at all this is also what compiles the sources.
    $root = Join-Path $Cache "jdk-21.0.6+7-jpackage"
    if (Test-Path (Join-Path $root "jmods")) { return $root }
    New-Item -ItemType Directory -Force -Path $Cache | Out-Null
    $zip = Join-Path $Cache "temurin-jdk-21-windows-x64.zip"
    if (-not (Test-Path $zip)) {
        Write-Host "Downloading Temurin JDK 21 (full, with jmods) ..."
        $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
        Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    }
    Write-Host "Extracting portable JDK 21 ..."
    $extract = Join-Path $Cache "jdk-21-extract"
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $extract
    Expand-Archive -Path $zip -DestinationPath $extract -Force
    $inside = Get-ChildItem $extract -Directory | Select-Object -First 1
    if (-not $inside) { throw "JDK 21 archive did not contain a root folder." }
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $root
    Move-Item -LiteralPath $inside.FullName -Destination $root
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $extract
    return $root
}

function Find-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }
    $cacheJdk = Get-ChildItem -Path (Join-Path $PSScriptRoot "cache") -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\javac.exe") } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    $candidates = @(
        $(if ($cacheJdk) { $cacheJdk.FullName }),
        "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot",
        "C:\Program Files\Java\latest",
        "C:\Program Files (x86)\Android\openjdk\jdk-17.0.14"
    ) | Where-Object { $_ }
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c "bin\javac.exe")) { return $c }
    }
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if ($javac) {
        $bin = Split-Path $javac.Source
        return Split-Path $bin
    }
    # No Java on this machine. The build downloads a JDK anyway for jpackage,
    # so it compiles with that one instead of refusing to start.
    Write-Host "No JDK found on this machine, taking the portable one ..."
    return Get-PortableJdk
}

$JdkHome = Find-JdkHome
$env:JAVA_HOME = $JdkHome
$javac = Join-Path $JdkHome "bin\javac.exe"
$jar = Join-Path $JdkHome "bin\jar.exe"
$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
$jdkJmods = Join-Path $JdkHome "jmods"

Write-Host "Using JDK: $JdkHome"
& $javac -version

New-Item -ItemType Directory -Force -Path $Cache, $Staging | Out-Null

# --- JavaFX SDK (compile only) ---
$javafxRoot = Join-Path $Cache "javafx-sdk-$JavaFxVersion"
$javafxLib = Join-Path $javafxRoot "lib"
if (-not (Test-Path $javafxLib)) {
    $zipPath = Join-Path $Cache $JavaFxSdkZip
    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading JavaFX SDK $JavaFxVersion (for javac) ..."
        Invoke-WebRequest -Uri $JavaFxSdkUrl -OutFile $zipPath -UseBasicParsing
    }
    Write-Host "Extracting JavaFX SDK ..."
    Expand-Archive -Path $zipPath -DestinationPath $Cache -Force
}
if (-not (Test-Path $javafxLib)) {
    throw "JavaFX lib folder not found: $javafxLib"
}

$javafxJars = @(Get-ChildItem $javafxLib -Filter "javafx*.jar" | ForEach-Object { $_.FullName })
if ($javafxJars.Count -eq 0) {
    throw "No javafx*.jar in $javafxLib"
}
$cp = ($javafxJars -join ";")

# --- RichTextFX (program editor) ---
$RtxLibDir = Join-Path $Cache "richtext-lib"
New-Item -ItemType Directory -Force -Path $RtxLibDir | Out-Null
$rtxArtifacts = @(
    "org/fxmisc/richtext/richtextfx/0.11.2/richtextfx-0.11.2.jar",
    "org/fxmisc/flowless/flowless/0.7.2/flowless-0.7.2.jar",
    "org/reactfx/reactfx/2.0-M5/reactfx-2.0-M5.jar",
    "org/fxmisc/wellbehaved/wellbehavedfx/0.3.3/wellbehavedfx-0.3.3.jar",
    "org/fxmisc/undo/undofx/2.1.1/undofx-2.1.1.jar"
)
foreach ($artifact in $rtxArtifacts) {
    $jarName = Split-Path $artifact -Leaf
    $dest = Join-Path $RtxLibDir $jarName
    if (-not (Test-Path $dest)) {
        $url = "https://repo1.maven.org/maven2/$artifact"
        Write-Host "Downloading $jarName ..."
        Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
    }
}
$rtxCp = (Get-ChildItem $RtxLibDir -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
$cp = "$cp;$rtxCp"

# --- JavaFX jmods (jpackage / runtime) ---
$javafxJmodsRoot = Join-Path $Cache "javafx-jmods-$JavaFxVersion"
$javafxBaseJmod = Join-Path $javafxJmodsRoot "javafx.base.jmod"
if (-not (Test-Path $javafxBaseJmod)) {
    $jmodsZipPath = Join-Path $Cache $JavaFxJmodsZip
    if (-not (Test-Path $jmodsZipPath)) {
        Write-Host "Downloading JavaFX jmods $JavaFxVersion (for jpackage runtime) ..."
        Invoke-WebRequest -Uri $JavaFxJmodsUrl -OutFile $jmodsZipPath -UseBasicParsing
    }
    Write-Host "Extracting JavaFX jmods ..."
    Expand-Archive -Path $jmodsZipPath -DestinationPath $Cache -Force
}
if (-not (Test-Path $javafxBaseJmod)) {
    throw "JavaFX jmods not found under $javafxJmodsRoot (expected javafx.base.jmod)"
}

$JpackageJdkHome = $JdkHome
$portableJdkRoot = Get-PortableJdk
$portableJdkJmods = Join-Path $portableJdkRoot "jmods"
if (Test-Path $portableJdkJmods) {
  $JpackageJdkHome = $portableJdkRoot
  $jpackage = Join-Path $JpackageJdkHome "bin\jpackage.exe"
  $jdkJmods = $portableJdkJmods
  Write-Host "jpackage JDK: $JpackageJdkHome"
}
if (Test-Path $jdkJmods) {
    $modulePath = "$jdkJmods;$javafxJmodsRoot"
} else {
    throw "JDK jmods not found. Install a full JDK or allow build.ps1 to download Temurin JDK 21."
}
# Only these modules end up in the runtime. The MCP server needs nothing extra: it runs on
# plain sockets from java.base, because on the shop-floor machine Selector.open() fails and
# that takes both jdk.httpserver and java.net.http down with it (measured, see HttpLoop).
$addModules = "java.base,java.logging,java.desktop,javafx.base,javafx.graphics,javafx.controls,javafx.fxml"

# --- Compile ---
$classes = Join-Path $Staging "classes"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $classes
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$srcRoot = Join-Path $Root "src\main\java"
$srcFiles = Get-ChildItem -Path $srcRoot -Recurse -Filter "*.java" |
    Sort-Object FullName |
    ForEach-Object { $_.FullName }
if ($srcFiles.Count -eq 0) {
    throw "No Java source files found under $srcRoot"
}

Write-Host "Compiling ..."
& $javac -encoding UTF-8 --release 17 -classpath $cp -d $classes @srcFiles
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Copy-Item -Recurse -Force "$Root\src\main\resources\*" $classes

# Base language file = English. A key missing from a translation then falls back to
# readable English instead of showing the raw key. Copied at build time, not stored
# in git, so it can never drift away from messages_en.properties.
$i18nDir = Join-Path $classes "i18n"
$i18nEnglish = Join-Path $i18nDir "messages_en.properties"
if (Test-Path $i18nEnglish) {
    Copy-Item -Force $i18nEnglish (Join-Path $i18nDir "messages.properties")
} else {
    throw "missing English language file: $i18nEnglish"
}

$historyIconsDir = Join-Path $classes "history-icons"
New-Item -ItemType Directory -Force -Path $historyIconsDir | Out-Null
$deleteCandidates = @(
    (Join-Path $Root "delete_icon.png"),
    (Join-Path $Root "delete_icon.PNG"),
    (Join-Path $Root "delete.png"),
    (Join-Path $Root "delete.ico"),
    (Join-Path $Root "Delete.ico")
)
$backCandidates = @(
    (Join-Path $Root "back_icon.png"),
    (Join-Path $Root "back_icon.PNG"),
    (Join-Path $Root "back.png"),
    (Join-Path $Root "back.ico"),
    (Join-Path $Root "Back.ico"),
    (Join-Path $Root "undo icon.ico"),
    (Join-Path $Root "undo.ico")
)
foreach ($candidate in $deleteCandidates) {
    if (Test-Path $candidate) {
        $ext = [System.IO.Path]::GetExtension($candidate)
        if ($ext -match "png") {
            Copy-Item -Force $candidate (Join-Path $historyIconsDir "delete.png")
        } else {
            Copy-Item -Force $candidate (Join-Path $historyIconsDir "delete.ico")
        }
        break
    }
}
foreach ($candidate in $backCandidates) {
    if (Test-Path $candidate) {
        $ext = [System.IO.Path]::GetExtension($candidate)
        if ($ext -match "png") {
            Copy-Item -Force $candidate (Join-Path $historyIconsDir "back.png")
        } else {
            Copy-Item -Force $candidate (Join-Path $historyIconsDir "back.ico")
        }
        break
    }
}

# --- Main JAR + RichTextFX libs in staging ---
$stagingLib = Join-Path $Staging "lib"
New-Item -ItemType Directory -Force -Path $stagingLib | Out-Null
Get-ChildItem $RtxLibDir -Filter "*.jar" | ForEach-Object {
    $destination = Join-Path $stagingLib $_.Name
    if (Test-Path $destination) {
        $existing = Get-Item $destination
        if ($existing.Length -eq $_.Length) {
            return
        }
    }
    Copy-Item -Force $_.FullName $destination
}
$mainJar = Join-Path $Staging "CNC_Modeling.jar"
Remove-Item -Force -ErrorAction SilentlyContinue $mainJar
$metaInfDir = Join-Path $classes "META-INF"
New-Item -ItemType Directory -Force -Path $metaInfDir | Out-Null
$manifestPath = Join-Path $metaInfDir "MANIFEST.MF"
$cpLines = Get-ChildItem $stagingLib -Filter "*.jar" | ForEach-Object { "lib/$($_.Name)" }
$cpManifest = ($cpLines -join " ")
@"
Manifest-Version: 1.0
Main-Class: com.sergey.pisarev.Launcher
Class-Path: $cpManifest

"@ | Set-Content -Path $manifestPath -Encoding ASCII
Push-Location $classes
try {
    & $jar --create --file $mainJar --manifest $manifestPath .
}
finally {
    Pop-Location
}

$outName = "Intraspect"
$existingImageDir = Join-Path $PSScriptRoot "Intraspect_Extended_3.96"
$defaultImageDir = if (Test-Path -LiteralPath $existingImageDir) { $existingImageDir } else { Join-Path $PSScriptRoot "Intraspect" }
$imageDir = if ($OutputDirectory) { [IO.Path]::GetFullPath($OutputDirectory) } else { $defaultImageDir }
if (-not $imageDir.StartsWith([IO.Path]::GetFullPath($Root).TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Build output must stay inside the project: $imageDir"
}
$tempBuildRoot = Join-Path $env:TEMP ("cnc_jpackage_" + [Guid]::NewGuid().ToString("N"))
$resolvedTempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
if (-not [IO.Path]::GetFullPath($tempBuildRoot).StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Build temp path is outside TEMP: $tempBuildRoot"
}
New-Item -ItemType Directory -Force -Path $tempBuildRoot | Out-Null

# jpackage copies the whole staging folder into the app, so anything forgotten
# there ends up shipped. A dead lwjgl-natives folder rode along like that for
# releases, 6 MB of DLLs no source mentions. Only these entries belong here.
$expectedInStaging = @("CNC_Modeling.jar", "classes", "lib", "occt")
foreach ($leftover in Get-ChildItem $Staging) {
    if ($expectedInStaging -notcontains $leftover.Name) {
        Write-Host "Staging leftover dropped (not shipped): $($leftover.Name)"
        Remove-Item -Recurse -Force $leftover.FullName
    }
}

Write-Host "Running jpackage with JavaFX jmods (may take a minute) ..."
& $jpackage `
    --type app-image `
    --name $outName `
    --input $Staging `
    --dest $tempBuildRoot `
    --main-jar "CNC_Modeling.jar" `
    --main-class "com.sergey.pisarev.Launcher" `
    --module-path "$modulePath" `
    --add-modules $addModules `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Dchekator.occt.mesh=true" `
    --java-options "-Dprism.order=d3d,sw" `
    --java-options "--add-opens=javafx.base/com.sun.javafx.runtime=ALL-UNNAMED" `
    --java-options "--add-opens=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED" `
    --java-options "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED" `
    --icon "$Root\icon.ico" `
    --app-version $AppVersion
if ($LASTEXITCODE -ne 0) {
    Remove-Item -LiteralPath $tempBuildRoot -Recurse -Force -ErrorAction SilentlyContinue
    throw "jpackage failed with exit code $LASTEXITCODE"
}

$builtImage = Join-Path $tempBuildRoot $outName
if (-not (Test-Path -LiteralPath $builtImage)) {
    Remove-Item -LiteralPath $tempBuildRoot -Recurse -Force -ErrorAction SilentlyContinue
    throw "jpackage output missing: $builtImage"
}

Write-Host "Installing into $imageDir ..."
foreach ($appProcess in @(Get-Process -Name "Intraspect" -ErrorAction SilentlyContinue)) {
    $targetExe = Join-Path $imageDir 'Intraspect.exe'
    if ($appProcess.Path -and [IO.Path]::GetFullPath($appProcess.Path) -eq $targetExe) {
        # Let the close handler save editor buffers before replacing this app image.
        $null = $appProcess.CloseMainWindow()
        if (-not $appProcess.WaitForExit(8000)) {
            Stop-Process -Id $appProcess.Id -Force -ErrorAction Stop
            $appProcess.WaitForExit()
        }
    }
}

# --- User data ---
# The data folder (settings, tools, history, last program) lives INSIDE the app
# folder that gets replaced below: it would go into .bak and be deleted with it.
# So move it aside before the swap and put it back after installing.
# Without this every rebuild silently wiped the user's data.
$dataDir = Join-Path $imageDir "data"
$dataStash = Join-Path $PSScriptRoot ("data-preserve-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
$dataPreserved = $false
$dataFileCount = 0
if (Test-Path -LiteralPath $dataDir) {
    $dataFileCount = @(Get-ChildItem -LiteralPath $dataDir -Recurse -File -ErrorAction SilentlyContinue).Count
    Move-Item -LiteralPath $dataDir -Destination $dataStash -Force
    if (-not (Test-Path -LiteralPath $dataStash)) {
        throw "Could not move user data aside from $dataDir. Install aborted so nothing is lost."
    }
    $dataPreserved = $true
    Write-Host "User data set aside ($dataFileCount files): $dataStash"
}

$backupDir = "${imageDir}.bak"
if (Test-Path -LiteralPath $backupDir) {
    Remove-Item -LiteralPath $backupDir -Recurse -Force -ErrorAction SilentlyContinue
}
if (Test-Path -LiteralPath $imageDir) {
    Move-Item -LiteralPath $imageDir -Destination $backupDir -Force -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $imageDir) {
        Remove-Item -LiteralPath $imageDir -Recurse -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
}
Move-Item -LiteralPath $builtImage -Destination $imageDir -Force

# Put the data back into the new build. On failure keep the copy and say where it is.
if ($dataPreserved) {
    $restoredDataDir = Join-Path $imageDir "data"
    if (Test-Path -LiteralPath $restoredDataDir) {
        Write-Warning "New build already has $restoredDataDir. Your data stayed in $dataStash - move it back by hand."
    } else {
        Move-Item -LiteralPath $dataStash -Destination $restoredDataDir -Force
        if (-not (Test-Path -LiteralPath $restoredDataDir)) {
            throw "User data was not restored. It is intact in $dataStash - copy that folder to $restoredDataDir."
        }
        $restoredCount = @(Get-ChildItem -LiteralPath $restoredDataDir -Recurse -File -ErrorAction SilentlyContinue).Count
        if ($restoredCount -ne $dataFileCount) {
            throw "After restore there are $restoredCount files instead of $dataFileCount. Check $restoredDataDir."
        }
        Write-Host "User data restored ($restoredCount files): $restoredDataDir"
    }
}

Remove-Item -LiteralPath $backupDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $tempBuildRoot -Recurse -Force -ErrorAction SilentlyContinue

$requiredLibJars = @(
    "flowless-0.7.2.jar",
    "reactfx-2.0-M5.jar",
    "richtextfx-0.11.2.jar",
    "undofx-2.1.1.jar",
    "wellbehavedfx-0.3.3.jar"
)
foreach ($libJar in $requiredLibJars) {
    $libPath = Join-Path $imageDir "app\lib\$libJar"
    if (-not (Test-Path -LiteralPath $libPath)) {
        throw "Deploy incomplete: missing $libPath"
    }
}

$exe = Join-Path $imageDir "$outName.exe"
$runtimeJvm = Join-Path $imageDir "runtime\bin\server\jvm.dll"
if (-not (Test-Path $exe)) {
    throw "Expected exe not found: $exe"
}
if (-not (Test-Path -LiteralPath $runtimeJvm)) {
    throw "Bundled runtime incomplete (missing $runtimeJvm). Re-run build.ps1."
}
if (Test-Path "$Root\icon.ico") {
    Copy-Item -Force "$Root\icon.ico" (Join-Path $imageDir "icon.ico")
    Copy-Item -Force "$Root\icon.ico" (Join-Path $PSScriptRoot "icon.ico")
    Copy-Item -Force "$Root\icon.ico" (Join-Path $imageDir "app\icon.ico")
    Copy-Item -Force "$Root\icon.ico" (Join-Path $imageDir "app\classes\icon.ico")
}

$occtStaging = Join-Path $Staging "occt\win64"
$occtApp = Join-Path $imageDir "app\occt\win64"
if (Test-Path (Join-Path $occtStaging "chekator_occt.dll")) {
    Write-Host "Installing OCCT native runtime into app ..."
    New-Item -ItemType Directory -Force -Path $occtApp | Out-Null
    Copy-Item -Force (Join-Path $occtStaging "*.dll") $occtApp
} else {
    Write-Host "OCCT runtime not in staging (optional). Build with: .\build-occt-bridge.ps1"
}

# The folder is remembered before the cd: cmd resolves %~dp0 of a relatively
# called script against the current directory, so reading it after the cd gives
# launch\launch\ and the launcher only works by double-click.
if ($OutputDirectory) {
    Write-Host "Done. Run: $exe"
    Write-Host "Portable output: $imageDir (copy this whole folder)"
    return
}
$runner = Join-Path $PSScriptRoot "Run_Intraspect.cmd"
$imageFolder = Split-Path -Leaf $imageDir
Set-Content -Path $runner -Encoding ASCII -Value @(
    '@echo off',
    'set "HERE=%~dp0"',
    'cd /d "%HERE%"',
    ('set "PATH=%HERE%{0}\app\occt\win64;%PATH%"' -f $imageFolder),
    ('"%HERE%{0}\Intraspect.exe" %*' -f $imageFolder)
)

$debugBat = Join-Path $PSScriptRoot "Run_Intraspect_with_console.cmd"
Set-Content -Path $debugBat -Encoding ASCII -Value @(
    '@echo off',
    'cd /d "%~dp0"',
    'echo Starting Intraspect...',
    ('"{0}\Intraspect.exe"' -f $imageFolder),
    'echo Exit code: %ERRORLEVEL%',
    'pause'
)

$readme = Join-Path $PSScriptRoot "README.txt"
Set-Content -Path $readme -Encoding UTF8 -Value @(
    "Intraspect $AppVersion (portable app-image)",
    '',
    '  Start:',
    ('    {0}\Intraspect.exe' -f $imageFolder),
    '    or: Run_Intraspect.cmd',
    '',
    '  If the window does not appear, run once:',
    '    Run_Intraspect_with_console.cmd',
    '',
    ('  Keep the whole {0} folder (it includes the Java runtime).' -f $imageFolder),
    ('  All user data stays in {0}\data. Old PC files and registry settings are ignored.' -f $imageFolder),
    '  A read-only drive does not redirect data to the PC profile.',
    '',
    '  Language: menu Language (Russian / English / Ukrainian, or as in Windows).',
    '  Applies after a restart.',
    '',
    '  AI over MCP: menu MCP -> press Connect. The program starts its own server and',
    '  writes the address into the Claude Code and Codex settings itself; then restart',
    '  the client. The server listens on 127.0.0.1 only and needs the key from that',
    '  address. Access starts as read-only; the checkbox lets the model change the',
    '  program, the settings and the tools.',
    '',
    'Rebuild:',
    "  cd to this launch folder, then:  .\build.ps1"
)

Write-Host ""
Write-Host "Done. Run: $exe"
Write-Host "Or: $runner"
Write-Host "Debug console: $debugBat"
Write-Host "Full app folder: $imageDir"
