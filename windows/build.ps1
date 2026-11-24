param(
    [Parameter(Mandatory=$true)][string]$Wheel,
    [string]$Python = "$PSScriptRoot/../.venv-windows/Scripts/python.exe",
    [string]$Output = "$PSScriptRoot/../.local/artifacts/windows"
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot/..").Path
$wheelPath = (Resolve-Path -LiteralPath $Wheel).Path
if (-not $wheelPath.EndsWith('.whl')) { throw 'Provide the verified FIC wheel.' }
& $Python -m pip install --disable-pip-version-check --force-reinstall --constraint "$PSScriptRoot/constraints.txt" $wheelPath
if ($LASTEXITCODE -ne 0) { throw 'Wheel installation failed.' }
$packArgs = @('-m', 'PyInstaller', '--noconfirm', '--onefile', '--windowed',
    '--name', 'FieldIntelligenceConsole-4.0.0-beta.7-windows-x64',
    '--version-file', "$PSScriptRoot/version-info.txt",
    '--collect-all', 'field_intelligence_console', '--collect-data', 'jsonschema_specifications',
    '--add-data', "$PSScriptRoot/notices:notices",
    '--distpath', $Output, '--workpath', "$projectRoot/.local/build/windows",
    '--specpath', "$projectRoot/.local/build/windows", "$PSScriptRoot/fic_windows.py")
& $Python @packArgs
if ($LASTEXITCODE -ne 0) { throw 'Windows packaging failed.' }
