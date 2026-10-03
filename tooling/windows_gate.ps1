param(
    [string]$Python = 'python',
    [string]$Wheel,
    [string]$OutputRoot = "$PSScriptRoot/../.local/evidence/windows-gate",
    [switch]$AllowDirtyDevelopment
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location -LiteralPath $projectRoot
New-Item -ItemType Directory -Force $OutputRoot | Out-Null
$OutputRoot = (Resolve-Path -LiteralPath $OutputRoot).Path
foreach ($receiptName in @('gate-summary.json','pe-metadata.json','focused-tests.json','packaged-ui.json','native/native-ui.json','native/native-restart.json')) {
    $receiptPath = Join-Path $OutputRoot $receiptName
    New-Item -ItemType Directory -Force (Split-Path -Parent $receiptPath) | Out-Null
    @{status='NOT_COMPLETED'} | ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding utf8
}
$sourceSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceSha -notmatch '^[0-9a-f]{40}$') { throw 'Cannot verify source commit.' }
$sourceStatus = @(& git status --porcelain --untracked-files=normal)
if ($LASTEXITCODE -ne 0) { throw 'Cannot verify source status.' }
$sourceDirty = $sourceStatus.Count -gt 0
if ($sourceDirty -and -not $AllowDirtyDevelopment) { throw 'Release/CI gate requires a clean source candidate. Use -AllowDirtyDevelopment only for explicitly preliminary builds.' }
$environment = Join-Path $projectRoot '.venv-windows'
$runtime = Join-Path $environment 'Scripts/python.exe'
if (-not (Test-Path -LiteralPath $runtime)) {
    & $Python -m venv $environment
    if ($LASTEXITCODE -ne 0) { throw 'Windows virtual environment creation failed.' }
}
& $runtime -m pip install --disable-pip-version-check -r windows/requirements-build.txt
if ($LASTEXITCODE -ne 0) { throw 'Windows build dependencies failed.' }
if (-not $Wheel) {
    & "$PSScriptRoot/python_gate.ps1" -Python $Python -OutputRoot "$OutputRoot/python" -AllowDirtyDevelopment:$AllowDirtyDevelopment
    $Wheel = "$OutputRoot/python/packages/field_intelligence_console-4.0.0b1-py3-none-any.whl"
}
$Wheel = (Resolve-Path -LiteralPath $Wheel).Path
& $runtime -m pip install --disable-pip-version-check --force-reinstall --constraint windows/constraints.txt $Wheel
if ($LASTEXITCODE -ne 0) { throw 'Windows core wheel installation failed.' }
& $runtime windows/run_focused.py --receipt "$OutputRoot/focused-tests.json" *> "$OutputRoot/focused-tests.log"
if ($LASTEXITCODE -ne 0) { throw 'Windows focused UI tests failed; inspect local log.' }
$focusedReceipt = Get-Content -Raw "$OutputRoot/focused-tests.json" | ConvertFrom-Json
if ($focusedReceipt.tests -lt 7 -or $focusedReceipt.skipped -ne 0 -or $focusedReceipt.status -ne 'PASS') { throw 'Focused test evidence is incomplete.' }
& "$projectRoot/windows/build.ps1" -Wheel $Wheel -Python $runtime -Output "$OutputRoot/artifacts" *> "$OutputRoot/package.log"
$exe = Join-Path $OutputRoot 'artifacts/FieldIntelligenceConsole-4.0.0-beta.1-windows-x64.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw 'Windows EXE missing.' }
$profile = Join-Path ([IO.Path]::GetTempPath()) ('FIC-synthetic-gate-' + [guid]::NewGuid().ToString('N'))
$receipt = Join-Path $OutputRoot 'packaged-ui.json'
$network = Join-Path $projectRoot 'shared/fixtures/network-v1.json'
$report = Join-Path $projectRoot 'shared/fixtures/field-report-v1.json'
$arguments = @('--ui-test', '--profile', ('"' + $profile + '"'), '--evidence', ('"' + $receipt + '"'),
    '--interop-input', ('"' + $network + '"'), '--interop-output', ('"' + "$OutputRoot/network-return.json" + '"'),
    '--interop-report-input', ('"' + $report + '"'), '--interop-report-output', ('"' + "$OutputRoot/report-return.json" + '"'))
$process = Start-Process -FilePath $exe -ArgumentList $arguments -WindowStyle Hidden -PassThru
if (-not $process.WaitForExit(120000)) {
    $process.Kill($true)
    throw 'Owned Windows package test exceeded two minutes.'
}
if ($process.ExitCode -ne 0) { throw 'Packaged Windows UI checks failed.' }
$uiReceipt = Get-Content -Raw -LiteralPath $receipt | ConvertFrom-Json
if ($uiReceipt.status -ne 'PASS' -or $uiReceipt.check_count -lt 28 -or -not $uiReceipt.frozen) {
    throw 'Packaged Windows UI evidence is incomplete.'
}
& $runtime windows/native_ui_check.py $exe --evidence "$OutputRoot/native" *> "$OutputRoot/native-driver.log"
if ($LASTEXITCODE -ne 0) { throw 'Native Windows mouse/keyboard gate failed; it was not skipped.' }
$nativeReceipt = Get-Content -Raw "$OutputRoot/native/native-ui.json" | ConvertFrom-Json
if ($nativeReceipt.status -ne 'PASS' -or $nativeReceipt.check_count -lt 7) { throw 'Native Windows evidence is incomplete.' }
$metadataScript = @'
import hashlib,importlib.metadata,json,pathlib,pefile,sys
p=pathlib.Path(sys.argv[1]);pe=pefile.PE(str(p))
assert pe.FILE_HEADER.Machine==0x8664 and pe.OPTIONAL_HEADER.Subsystem==2
strings={k.decode():v.decode() for group in pe.FileInfo for block in group if block.Key==b'StringFileInfo' for table in block.StringTable for k,v in table.entries.items()}
assert strings['FileVersion']=='4.0.0.1' and strings['ProductVersion']=='4.0.0-beta.1'
fixed=pe.VS_FIXEDFILEINFO[0]
assert (fixed.FileVersionMS,fixed.FileVersionLS)==(4<<16,1)
print(json.dumps({"machine":"AMD64","subsystem":"Windows GUI","file_version":strings['FileVersion'],"product_version":strings['ProductVersion'],"python":sys.version.split()[0],"packager":"PyInstaller "+importlib.metadata.version('pyinstaller'),"bytes":p.stat().st_size,"sha256":hashlib.sha256(p.read_bytes()).hexdigest()}))
'@
$metadata = $metadataScript | & $runtime - $exe
if ($LASTEXITCODE -ne 0) { throw 'PE architecture validation failed.' }
$metadata | Set-Content -Encoding utf8 "$OutputRoot/pe-metadata.json"
$signature = (Get-AuthenticodeSignature -LiteralPath $exe).Status.ToString()
$endingSha = (& git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $endingSha -ne $sourceSha) { throw 'Source commit changed during Windows gate; rebuild candidate.' }
$endingStatus = @(& git status --porcelain --untracked-files=normal)
if ($LASTEXITCODE -ne 0) { throw 'Cannot recheck source status.' }
$sourceDirty = $sourceDirty -or $endingStatus.Count -gt 0
if ($sourceDirty -and -not $AllowDirtyDevelopment) { throw 'Source changed during Windows gate; rebuild clean candidate.' }
$summary = @{
    status = 'PASS'
    focused_tests = $focusedReceipt.tests
    packaged_widget_checks = $uiReceipt.check_count
    native_checks = $nativeReceipt.check_count
    signing = $signature
    source_sha = $sourceSha
    worktree_dirty = $sourceDirty
    artifact = (Split-Path -Leaf $exe)
    binary_metadata = ($metadata | ConvertFrom-Json)
    limitations = @('Synthetic profiles only', 'No live provider key used', 'Unsigned experimental Windows application')
}
$summary | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 "$OutputRoot/gate-summary.json"
Write-Output 'Windows gate PASS: focused tests, packaged GUI workflow, native interaction, restart and PE checks executed.'
