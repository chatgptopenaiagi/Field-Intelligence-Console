#Requires -Version 7.2
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+-[A-Za-z0-9.]+$')][string]$Tag,
    [ValidateSet('Build','Finalize','All')][string]$Stage = 'All',
    [string]$Python = 'python',
    [string]$OutputRoot = "$PSScriptRoot/../.local/release-candidate",
    [ValidatePattern('^emulator-[0-9]+$')][string]$DeviceSerial,
    [string]$RuntimeReceipt,
    [switch]$RequireExistingTag
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath "$PSScriptRoot/..").Path
Set-Location -LiteralPath $projectRoot
& $Python tooling/check_versions.py
if ($LASTEXITCODE -ne 0) { throw 'Release version consistency check failed.' }
if (@(git status --porcelain).Count -ne 0) { throw 'Release candidate requires a clean source tree; no dirty bypass exists.' }
$sourceSha = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve source commit.' }
git show-ref --verify --quiet "refs/tags/$Tag"
$tagExists = $LASTEXITCODE -eq 0
if ($tagExists) {
    if ((git cat-file -t "refs/tags/$Tag") -ne 'tag') { throw 'Existing release tag must be annotated.' }
    $tagCommit = (git rev-parse "refs/tags/$Tag^{commit}").Trim()
    if ($LASTEXITCODE -ne 0 -or $tagCommit -ne $sourceSha) { throw 'Annotated tag does not point to the checked-out candidate.' }
} elseif ($RequireExistingTag -or $Stage -ne 'All') {
    throw 'CI Build/Finalize requires an existing annotated tag; local All may use a planned tag without creating it.'
}
$version = $Tag.Substring(1)
$buildText = Get-Content -Raw -LiteralPath app/build.gradle.kts
if ($buildText -notmatch ('versionName\s*=\s*"' + [regex]::Escape($version) + '"')) { throw 'Tag and Android application version do not match.' }
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$assets = Join-Path $OutputRoot 'assets'
$receipts = Join-Path $OutputRoot 'receipts'
$testAssets = Join-Path $OutputRoot 'android-test'

function Invoke-Bounded {
    param([string]$Program, [string[]]$Arguments, [string]$Name, [int]$Seconds=1800)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Program
    $info.WorkingDirectory = $projectRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw "Could not start $Name." }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($Seconds * 1000)) {
            $process.Kill($true)
            $process.WaitForExit()
            throw "Owned release step timed out: $Name."
        }
        $log = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
        if ($log.Length -gt 20000000) { $log = $log.Substring(0,20000000) }
        Set-Content -LiteralPath (Join-Path $OutputRoot "$Name.local.log") -Value $log -Encoding utf8
        if ($Name -eq 'inventory') {
            # The inventory emits only bounded, path-free structured diagnostics.
            # Never forward a raw traceback or captured build log to CI artifacts.
            $safeInventory = @($log -split "`n" | Where-Object {
                $_.Length -le 4096 -and $_ -match '^\{"(?:inventory_inputs_present|status|android_components)":'
            })
            $safeInventory | Set-Content -LiteralPath (Join-Path $OutputRoot 'inventory.sanitized.log') -Encoding utf8
            $safeInventory | ForEach-Object { Write-Host $_ }
        }
        if ($process.ExitCode -ne 0) { throw "Release step failed: $Name. Inspect its local-only log." }
    } finally { $process.Dispose() }
}

function Assert-Unchanged {
    if ((git rev-parse HEAD).Trim() -ne $sourceSha -or @(git status --porcelain).Count -ne 0) {
        throw 'Candidate changed during verification; regenerate from a new clean commit.'
    }
}

if ($Stage -in @('Build','All')) {
    if (-not $IsWindows) { throw 'The combined artifact build must run on Windows x64.' }
    if ($Stage -eq 'All' -and -not $DeviceSerial) { throw 'Local All requires an explicitly owned isolated emulator serial; no Android runtime skip is allowed.' }
    if (Test-Path -LiteralPath (Join-Path $OutputRoot 'build-receipt.json')) { throw 'Use a new candidate output directory; do not overwrite prior candidate evidence.' }
    foreach ($directory in @($assets,$receipts,$testAssets)) { New-Item -ItemType Directory -Force -Path $directory | Out-Null }
    $pwsh = (Get-Command pwsh -ErrorAction Stop).Source
    Invoke-Bounded -Program (Get-Command $Python).Source -Arguments @('tooling/evidence_budget.py') -Name 'evidence-budget' -Seconds 120
    $androidOutput = Join-Path $OutputRoot 'android-gate'
    $androidArguments = @('-NoProfile','-File',"$PSScriptRoot/android_gate.ps1",'-OutputRoot',$androidOutput)
    if ($DeviceSerial) { $androidArguments += @('-DeviceSerial',$DeviceSerial) }
    Invoke-Bounded -Program $pwsh -Arguments $androidArguments -Name 'android' -Seconds 3600
    $androidAttestation = Get-Content -Raw -LiteralPath "$androidOutput/gate-receipt.json" | ConvertFrom-Json
    $signing = Get-Content -Raw -LiteralPath shared/release-signing.json | ConvertFrom-Json
    if ($androidAttestation.artifacts.Count -ne 2 -or @($androidAttestation.artifacts | Where-Object {
        $_.certificate_sha256 -ne $signing.android_testing_certificate_sha256 -or -not $_.signature_verified
    }).Count -ne 0) { throw 'Release APKs do not retain the approved testing certificate; no replacement signing identity is allowed.' }
    $pythonOutput = Join-Path $OutputRoot 'python-gate'
    Invoke-Bounded -Program $pwsh -Arguments @('-NoProfile','-File',"$PSScriptRoot/python_gate.ps1",'-Python',$Python,'-OutputRoot',$pythonOutput) -Name 'python' -Seconds 1200
    $wheel = @(Get-ChildItem -LiteralPath "$pythonOutput/packages" -Filter 'field_intelligence_console-*-py3-none-any.whl')
    $sdist = @(Get-ChildItem -LiteralPath "$pythonOutput/packages" -Filter 'field_intelligence_console-*.tar.gz')
    if ($wheel.Count -ne 1 -or $sdist.Count -ne 1) { throw 'Expected one tested wheel and one sdist.' }
    $windowsOutput = Join-Path $OutputRoot 'windows-gate'
    Invoke-Bounded -Program $pwsh -Arguments @('-NoProfile','-File',"$PSScriptRoot/windows_gate.ps1",'-Python',$Python,'-Wheel',$wheel[0].FullName,'-OutputRoot',$windowsOutput) -Name 'windows' -Seconds 1800
    $apkName = 'FieldIntelligenceConsole-BETA-Android.apk'
    $exeName = 'FieldIntelligenceConsole-BETA-Windows-x64.exe'
    Copy-Item -LiteralPath app/build/outputs/apk/debug/app-debug.apk -Destination (Join-Path $assets $apkName)
    Copy-Item -LiteralPath app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk -Destination "$testAssets/app-debug-androidTest.apk"
    Copy-Item -LiteralPath "$windowsOutput/artifacts/FieldIntelligenceConsole-$version-windows-x64.exe" -Destination (Join-Path $assets $exeName)
    Copy-Item -LiteralPath $wheel[0].FullName -Destination $assets
    Copy-Item -LiteralPath $sdist[0].FullName -Destination $assets
    $sourceZip = Join-Path $assets "FieldIntelligenceConsole-$version-source.zip"
    Invoke-Bounded -Program (Get-Command git).Source -Arguments @('archive','--format=zip',"--prefix=FieldIntelligenceConsole-$version/","--output=$sourceZip",$sourceSha) -Name 'source-archive' -Seconds 120
    Copy-Item -LiteralPath "$androidOutput/gate-receipt.json" -Destination "$receipts/android.json"
    Copy-Item -LiteralPath "$windowsOutput/gate-summary.json" -Destination "$receipts/windows.json"
    Copy-Item -LiteralPath "$windowsOutput/pe-metadata.json" -Destination "$receipts/pe-metadata.json"
    $pythonReceipt = Get-Content -Raw -LiteralPath "$pythonOutput/gate-receipt.json" | ConvertFrom-Json -AsHashtable
    if ($pythonReceipt.status -ne 'PASS' -or $pythonReceipt.source_sha -ne $sourceSha -or $pythonReceipt.source_dirty -ne $false) {
        throw 'Python receipt must independently attest the same clean candidate.'
    }
    $pythonReceipt | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath "$receipts/python.json" -Encoding utf8
    Copy-Item -LiteralPath "$windowsOutput/packaged-ui.json" -Destination "$receipts/windows-ui.json"
    Copy-Item -LiteralPath "$windowsOutput/native/native-ui.json" -Destination "$receipts/windows-native.json"
    $runtime = Join-Path $projectRoot '.venv-windows/Scripts/python.exe'
    $gradleRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $inventoryArgs = @('tooling/release_inventory.py',
        '--android-dependencies','app/build/intermediates/lint_report_lint_model/debug/generateDebugLintReportModel/debug-artifact-dependencies.xml',
        '--gradle-cache',"$gradleRoot/caches/modules-2/files-2.1",
        '--python-executable',$runtime,
        '--windows-toc',".local/build/windows/FieldIntelligenceConsole-$version-windows-x64/Analysis-00.toc",
        '--windows-notices','windows/notices','--source-sha',$sourceSha,'--version',$version,'--output',$assets)
    foreach ($file in @($apkName,$exeName,$wheel[0].Name,$sdist[0].Name,[IO.Path]::GetFileName($sourceZip))) {
        $inventoryArgs += @('--artifact',(Join-Path $assets $file))
    }
    Invoke-Bounded -Program $runtime -Arguments $inventoryArgs -Name 'inventory' -Seconds 300
    $auditArgs = @('tooling/audit_artifacts.py','--source-sha',$sourceSha,'--output',"$receipts/artifact-scan.json")
    foreach ($file in @($apkName,$exeName,$wheel[0].Name,$sdist[0].Name,[IO.Path]::GetFileName($sourceZip))) {
        $auditArgs += @('--artifact',(Join-Path $assets $file))
    }
    Invoke-Bounded -Program $runtime -Arguments $auditArgs -Name 'artifact-scan' -Seconds 300
    $scan = Get-Content -Raw -LiteralPath "$receipts/artifact-scan.json" | ConvertFrom-Json
    if ($scan.status -ne 'REQUIRES_REVIEW' -or $scan.findings.Count -ne 0 -or $scan.incomplete.Count -ne 0) {
        throw 'Artifact scan is incomplete or has findings; no candidate upload is permitted.'
    }
    # Contextual coordinator review remains mandatory before publication.
    Assert-Unchanged
    $artifactEntries = @(Get-ChildItem -LiteralPath $assets -File | ForEach-Object {
        @{name=$_.Name;bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
    $buildReceipt = @{status='BUILD_PASS';source_sha=$sourceSha;tag=$Tag;tag_state=$(if($tagExists){'verified annotated tag'}else{'planned; not created'});version=$version;source_dirty=$false;
        artifacts=$artifactEntries;
        instrumentation_apk_sha256=(Get-FileHash -LiteralPath "$testAssets/app-debug-androidTest.apk" -Algorithm SHA256).Hash.ToLowerInvariant();
        finalization='Requires PASS Android runtime bound to this exact APK, then final manifest/checksums'}
    $buildReceipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$OutputRoot/build-receipt.json" -Encoding utf8
    Write-Output 'All three actual artifacts built and tested; candidate awaits final runtime/manifest verification.'
}

if ($Stage -in @('Finalize','All')) {
    $build = Get-Content -Raw -LiteralPath "$OutputRoot/build-receipt.json" | ConvertFrom-Json
    if ($build.status -ne 'BUILD_PASS' -or $build.source_sha -ne $sourceSha -or $build.tag -ne $Tag -or $build.source_dirty) { throw 'Build receipt does not match the clean annotated candidate.' }
    foreach ($artifact in $build.artifacts) {
        if ([IO.Path]::GetFileName($artifact.name) -ne $artifact.name) { throw 'Unsafe artifact name in receipt.' }
        $path = Join-Path $assets $artifact.name
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256 -or (Get-Item -LiteralPath $path).Length -ne $artifact.bytes) { throw 'Artifact differs from the verified build receipt.' }
    }
    $android = Get-Content -Raw -LiteralPath "$receipts/android.json" | ConvertFrom-Json -AsHashtable
    if ($RuntimeReceipt) {
        $runtimeProof = Get-Content -Raw -LiteralPath $RuntimeReceipt | ConvertFrom-Json
        $debugArtifact = @($android.artifacts | Where-Object variant -eq 'debug')[0]
        if ($runtimeProof.status -ne 'PASS' -or $runtimeProof.source_sha -ne $sourceSha -or
            $runtimeProof.apk_sha256 -ne $debugArtifact.sha256 -or
            $runtimeProof.instrumentation_apk_sha256 -ne $build.instrumentation_apk_sha256 -or $runtimeProof.tests -lt 6) {
            throw 'Runtime receipt is not PASS for these exact tested APKs and source commit.'
        }
        $android.runtime_validation = @{status='PASS';tests=$runtimeProof.tests;kind='isolated emulator';acceleration=$runtimeProof.acceleration;ui_wait_millis=$runtimeProof.ui_wait_millis;instrumentation_timeout_seconds=$runtimeProof.instrumentation_timeout_seconds;physical_device='NOT_EXECUTED';evidence='Hash-bound CI runtime receipt'}
        $android | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath "$receipts/android.json" -Encoding utf8
    }
    if ($android.status -ne 'PASS' -or $android.source_dirty -or $android.source_sha -ne $sourceSha -or $android.runtime_validation.status -ne 'PASS') { throw 'Android runtime gate has not passed for this candidate.' }
    if (-not (Test-Path -LiteralPath tooling/finalize_release.py)) { throw 'Final manifest generator is unavailable; candidate stays incomplete.' }
    $guardEnvironment = Join-Path $OutputRoot 'guard-environment'
    Invoke-Bounded -Program (Get-Command $Python).Source -Arguments @('-m','venv',$guardEnvironment) -Name 'release-guard-environment' -Seconds 120
    $guardPython = Join-Path $guardEnvironment $(if ($IsWindows) { 'Scripts/python.exe' } else { 'bin/python' })
    $guardWheel = @(Get-ChildItem -LiteralPath $assets -Filter 'field_intelligence_console-*-py3-none-any.whl')
    if ($guardWheel.Count -ne 1) { throw 'Exactly one candidate wheel required for release guard dependencies.' }
    Invoke-Bounded -Program $guardPython -Arguments @('-m','pip','install','--disable-pip-version-check',$guardWheel[0].FullName) -Name 'release-guard-install' -Seconds 180
    Invoke-Bounded -Program $guardPython -Arguments @('-m','unittest','discover','-s','tooling/tests','-v') -Name 'release-guard-tests' -Seconds 120
    Invoke-Bounded -Program (Get-Command $Python).Source -Arguments @('tooling/finalize_release.py',
        '--candidate-dir',$assets,'--tag',$Tag,'--source-sha',$sourceSha,
        '--android-receipt',"$receipts/android.json",'--windows-receipt',"$receipts/windows.json",'--python-receipt',"$receipts/python.json",
        '--sbom',"$assets/SBOM.cdx.json",'--notices-dir',$assets) -Name 'finalize' -Seconds 300
    foreach ($name in @('release-manifest.json','SHA256SUMS.txt','RELEASE_NOTES.md','SBOM.cdx.json','THIRD_PARTY_NOTICES.txt')) {
        if (-not (Test-Path -LiteralPath (Join-Path $assets $name))) { throw "Required release metadata missing: $name" }
    }
    Assert-Unchanged
    Write-Output 'Candidate assets and final verification data are ready. No Git push or release publication was performed.'
}
