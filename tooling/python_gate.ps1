param(
    [string]$Python = 'python',
    [string]$OutputRoot = "$PSScriptRoot/../.local/evidence/python-ci",
    [switch]$AllowDirtyDevelopment
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath "$PSScriptRoot/..").Path
$outputPath = [System.IO.Path]::GetFullPath($OutputRoot)
$environmentPath = Join-Path $projectRoot '.venv-ci-python'
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
foreach ($stepName in @('unit-tests','clean-wheel')) {
    Set-Content -LiteralPath (Join-Path $outputPath "$stepName.sanitized.log") -Value 'NOT_EXECUTED in this invocation' -Encoding utf8
}
$receiptPath = Join-Path $outputPath 'gate-receipt.json'
@{status='NOT_COMPLETED'; source_sha=$null; source_dirty=$null} |
    ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding utf8
$sourceSha = (& git -C $projectRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch '^[0-9a-f]{40}$') { throw 'Cannot verify the source commit.' }
$sourceStatus = @(& git -C $projectRoot status --porcelain --untracked-files=normal)
if ($LASTEXITCODE -ne 0) { throw 'Cannot verify the source working tree.' }
$sourceDirty = $sourceStatus.Count -gt 0
@{status='NOT_COMPLETED'; source_sha=$sourceSha; source_dirty=$sourceDirty} |
    ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding utf8
if ($sourceDirty -and -not $AllowDirtyDevelopment) { throw 'Source changes are present; commit the candidate or explicitly use -AllowDirtyDevelopment for preliminary evidence.' }

& $Python -m venv $environmentPath
if ($LASTEXITCODE -ne 0) { throw 'Project-local Python environment creation failed.' }
$venvPython = if ($IsWindows) { Join-Path $environmentPath 'Scripts/python.exe' } else { Join-Path $environmentPath 'bin/python' }

function Invoke-PythonGate {
    param([string]$Step, [string[]]$Arguments)
    $log = Join-Path $outputPath "$Step.log"
    & $venvPython @Arguments *> $log
    $stepExit = $LASTEXITCODE
    # Export only sanitized diagnostics; raw local logs are never CI artifacts.
    $safeLog = Get-Content -LiteralPath $log -Raw
    foreach ($privateRoot in @($projectRoot,$outputPath,$env:USERPROFILE,$env:HOME,$env:TEMP,$env:TMPDIR)) {
        if ($privateRoot) {
            $safeLog = $safeLog.Replace($privateRoot,'[local-path]').Replace($privateRoot.Replace('\','/'),'[local-path]')
        }
    }
    $safeLog = $safeLog -replace '(?i)[A-Z]:[\\/][^\r\n"<>]*','[local-path]'
    $safeLog = $safeLog -replace '/(?:home|Users|tmp|var/folders)/[^\s"<>]+','[local-path]'
    if ($safeLog.Length -gt 1000000) { $safeLog = $safeLog.Substring($safeLog.Length - 1000000) }
    $safePath = Join-Path $outputPath "$Step.sanitized.log"
    Set-Content -LiteralPath $safePath -Value $safeLog -Encoding utf8
    if ($stepExit -ne 0) {
        Get-Content -LiteralPath $safePath -Tail 30
        throw "Python gate failed: $Step"
    }
    Write-Output "$Step PASS"
}

Push-Location $projectRoot
try {
    Invoke-PythonGate -Step 'dependencies' -Arguments @('-m','pip','install','--disable-pip-version-check',
        'build==1.4.0','setuptools==82.0.1','jsonschema==4.26.0','-e','./python')
    Invoke-PythonGate -Step 'dependency-check' -Arguments @('-m','pip','check')
    Invoke-PythonGate -Step 'unit-tests' -Arguments @('-m','unittest','discover','-s','python/tests','-v')
    $testLog = Get-Content -LiteralPath (Join-Path $outputPath 'unit-tests.log') -Raw
    if ($testLog -notmatch 'Ran (\d+) tests?' -or [int]$Matches[1] -le 0) { throw 'No executed unit-test count was found.' }
    $executedTests = [int]$Matches[1]
    if ($testLog -match '(?i)\bskipped\s*[=:]\s*[1-9]' -or $testLog -match '(?m)\.\.\. skipped ') { throw 'Skipped Python tests do not satisfy the acceptance gate.' }
    $packagePath = Join-Path $outputPath 'packages'
    Invoke-PythonGate -Step 'package-build' -Arguments @('-m','build','python','--outdir',$packagePath)
    $wheelFiles = @(Get-ChildItem -LiteralPath $packagePath -Filter 'field_intelligence_console-*-py3-none-any.whl')
    $sourceFiles = @(Get-ChildItem -LiteralPath $packagePath -Filter 'field_intelligence_console-*.tar.gz')
    if ($wheelFiles.Count -ne 1 -or $sourceFiles.Count -ne 1) {
        throw 'Expected one current wheel and one sdist; use an empty gate output directory when changing versions.'
    }
    Invoke-PythonGate -Step 'clean-wheel' -Arguments @('python/verify_wheel.py',$wheelFiles[0].FullName)
    $runtime = (& $venvPython --version).Trim()
    $artifacts = @($wheelFiles[0],$sourceFiles[0]) | ForEach-Object {
        @{name=$_.Name; bytes=$_.Length; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    }
    $endingSha = (& git -C $projectRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $endingSha -ne $sourceSha) { throw 'Source commit changed during the gate; rebuild the candidate.' }
    $endingStatus = @(& git -C $projectRoot status --porcelain --untracked-files=normal)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot recheck the source working tree.' }
    $sourceDirty = $sourceDirty -or $endingStatus.Count -gt 0
    if ($sourceDirty -and -not $AllowDirtyDevelopment) { throw 'Source changed during the gate; rebuild the clean candidate.' }
    @{
        status='PASS'; source_sha=$sourceSha; source_dirty=$sourceDirty;
        development_override=[bool]$AllowDirtyDevelopment;
        runtime=$runtime; unit_tests=$executedTests; unit_failures=0; unit_skipped=0; packaging='wheel and sdist built';
        isolated_wheel='five checks passed outside checkout, including shared invalid-contract vectors';
        artifacts=$artifacts;
        limitations='This receipt describes local gate execution; it is not a GitHub CI or final release publication claim.'
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $receiptPath -Encoding utf8
    Write-Output 'PYTHON GATE PASS'
} finally {
    Pop-Location
}
