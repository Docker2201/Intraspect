param([switch]$WithOcct)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $projectRoot 'out/geometry-tests'
$classes = Join-Path $output 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$jdk = Get-ChildItem (Join-Path $PSScriptRoot 'cache') -Directory -Filter 'jdk-*' |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } | Select-Object -First 1
if (-not $jdk) { throw 'Build once with launch/build.ps1 to prepare the local JDK and JavaFX cache.' }
$dependencies = @((Join-Path $PSScriptRoot 'cache/javafx-sdk-21.0.2/lib/*.jar'),
    (Join-Path $PSScriptRoot 'cache/richtext-lib/*.jar'))
$classPath = (@(Get-ChildItem -Path $dependencies -File | ForEach-Object FullName) -join ';')
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src/main/java'),(Join-Path $projectRoot 'src/test/java') -Recurse -Filter '*.java' |
    ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
$sourceList = Join-Path $output 'sources.txt'
[IO.File]::WriteAllLines($sourceList, $sources, [Text.UTF8Encoding]::new($false))
& (Join-Path $jdk.FullName 'bin/javac.exe') -encoding UTF-8 --release 17 -cp $classPath -d $classes ('@' + $sourceList)
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
$runtimeCp = $classes + ';' + (Join-Path $projectRoot 'src/main/resources') + ';' + (Join-Path $projectRoot 'src/test/resources') + ';' + $classPath
$java = Join-Path $jdk.FullName 'bin/java.exe'
& $java -cp $runtimeCp com.sergey.pisarev.service.WorkpieceRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'WORKPIECE regressions failed' }
foreach ($scenario in @('empty', 'populated', 'blocked')) {
    & $java -cp $runtimeCp com.sergey.pisarev.util.PortableStorageRegressionTest $output $scenario
    if ($LASTEXITCODE -ne 0) { throw "Portable storage regression failed: $scenario" }
}
& $java -cp $runtimeCp com.sergey.pisarev.service.LatheSurfaceRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Surface regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.service.ProgramToolTableRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Program tool table regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.GraphOrientationRegressionTest
if ($LASTEXITCODE -ne 0) { throw '2D graph orientation regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.VerticalCycleRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Vertical cycle regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.MeasuredToolContactRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Measured tool contact regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.CrankedToolRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Cranked tool surface regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.ToolAssetNormalsRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Tool OBJ normal regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.ToolRadiusExpressionRegressionTest $output
if ($LASTEXITCODE -ne 0) { throw 'Tool radius expression regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.SimulationMcpRegressionTest $output
if ($LASTEXITCODE -ne 0) { throw 'Simulation MCP wire regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.MachineToolLibraryRegressionTest $output
if ($LASTEXITCODE -ne 0) { throw 'Machine tool library regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.service.AxialCycleStockRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Axial cycle stock regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.service.CyclePreviewMeshCacheRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Cycle preview cache regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.CycleMeshBuffersRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Cycle mesh buffer regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.service.ApproximatePreformRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Approximate preform regressions failed' }
& $java -cp $runtimeCp com.sergey.pisarev.controller.SimulationGeometryRegressionTest $output
if ($LASTEXITCODE -ne 0) { throw 'Controller regressions failed' }
if ($WithOcct) {
    & $java '-Dchekator.occt.mesh=true' -cp $runtimeCp com.sergey.pisarev.service.LatheNativeRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'Native regressions failed (rebuild the OCCT bridge)' }
    & $java '-Dchekator.occt.mesh=true' -cp $runtimeCp com.sergey.pisarev.service.AxialSectionRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'Axial section regressions failed' }
    & $java '-Dchekator.occt.mesh=true' -cp $runtimeCp com.sergey.pisarev.service.BoringRegressionTest
    if ($LASTEXITCODE -ne 0) { throw 'Boring regressions failed' }
    & $java '-Dchekator.occt.mesh=true' -cp $runtimeCp com.sergey.pisarev.controller.TwoSetupCycleRegressionTest $output
    if ($LASTEXITCODE -ne 0) { throw 'Two-setup cycle regressions failed' }
}
Write-Host 'All geometry regressions passed.'
