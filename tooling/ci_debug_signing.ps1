#Requires -Version 7.2
param([Parameter(Mandatory=$true)][ValidateSet('Restore','Cleanup')][string]$Mode)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -or $env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_ENVIRONMENT -ne 'github-hosted' -or
    $env:GITHUB_REPOSITORY -ne 'chatgptopenaiagi/Field-Intelligence-Console') {
    throw 'Signing restoration is restricted to this repository on an owned GitHub-hosted Windows runner.'
}
$userRoot = [IO.Path]::GetFullPath([Environment]::GetFolderPath('UserProfile'))
$keyPath = [IO.Path]::GetFullPath((Join-Path $userRoot '.android/debug.keystore'))
$receiptPath = Join-Path $env:RUNNER_TEMP 'fic-owned-debug-signing.json'
if (-not $keyPath.StartsWith($userRoot.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Signing path escaped runner profile.' }
if ($Mode -eq 'Cleanup') {
    if (Test-Path -LiteralPath $receiptPath) {
        $receipt = Get-Content -Raw -LiteralPath $receiptPath | ConvertFrom-Json
        if ($receipt.path -ne $keyPath) { throw 'Owned signing receipt mismatch.' }
        if (Test-Path -LiteralPath $keyPath) {
            if ((Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash -ne $receipt.hash) { throw 'Signing file changed; refuse unrelated deletion.' }
            Remove-Item -LiteralPath $keyPath
        }
        Remove-Item -LiteralPath $receiptPath
    }
    Write-Output 'Owned runner signing material cleaned; local development key was not accessed.'
    exit 0
}
if ($env:CANDIDATE_TAG -notmatch '^v\d+\.\d+\.\d+-beta\.\d+$') { throw 'Trusted experimental tag required.' }
if ($env:GITHUB_EVENT_NAME -notin @('push','workflow_dispatch') -or $env:GITHUB_REF -ne "refs/tags/$env:CANDIDATE_TAG") {
    throw 'Signing requires a trusted tag push or dispatch on the exact candidate tag; pull requests cannot restore it.'
}
$head = (git rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or (git cat-file -t "refs/tags/$env:CANDIDATE_TAG") -ne 'tag' -or
    (git rev-parse "refs/tags/$($env:CANDIDATE_TAG)^{commit}").Trim() -ne $head) { throw 'Checked-out source is not the annotated candidate.' }
$repository = gh repo view $env:GITHUB_REPOSITORY --json nameWithOwner,visibility | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $repository.nameWithOwner -ne $env:GITHUB_REPOSITORY -or $repository.visibility -notin @('PRIVATE','PUBLIC')) { throw 'Expected original repository not verified.' }
if (Test-Path -LiteralPath $keyPath) { throw 'Unexpected existing runner debug key; refusing to replace it.' }
if (-not $env:FIC_DEBUG_KEYSTORE_B64 -or $env:FIC_DEBUG_KEYSTORE_B64.Length -gt 45000) { throw 'Retained testing key is unavailable or oversized; no replacement will be generated.' }
$bytes = $null
try {
    try { $bytes = [Convert]::FromBase64String($env:FIC_DEBUG_KEYSTORE_B64) } catch { throw 'Retained testing key encoding is invalid.' }
    if ($bytes.Length -lt 512 -or $bytes.Length -gt 32768) { throw 'Retained testing key size is invalid.' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $keyPath) | Out-Null
    $stream = [IO.File]::Open($keyPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
    @{path=$keyPath;hash=(Get-FileHash -LiteralPath $keyPath -Algorithm SHA256).Hash} |
        ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding utf8
} finally {
    if ($null -ne $bytes) { [Array]::Clear($bytes,0,$bytes.Length) }
    $env:FIC_DEBUG_KEYSTORE_B64 = $null
}
Write-Output 'Retained testing identity restored; both APK certificate fingerprints must pass the release gate.'
