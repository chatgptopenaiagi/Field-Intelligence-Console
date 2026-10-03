#Requires -Version 7.2
[CmdletBinding()]
param(
    [string]$OutputRoot = "$PSScriptRoot/../.local/evidence/android-gate",
    [ValidatePattern('^emulator-[0-9]+$')][string]$DeviceSerial,
    [switch]$AllowDirty,
    [ValidateRange(5,90)][int]$TimeoutMinutes = 40
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath "$PSScriptRoot/..").Path
$outputPath = [IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$runPath = Join-Path $outputPath ('run-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Force -Path $runPath | Out-Null
# A failed later invocation must never leave an earlier PASS as the current receipt.
@{status='NOT_COMPLETED';utc=[DateTime]::UtcNow.ToString('o');reason='Gate running or failed; inspect this invocation logs.'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'gate-receipt.json') -Encoding utf8
$reportPath = Join-Path $runPath 'reports'
New-Item -ItemType Directory -Force -Path $reportPath | Out-Null
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $sdk -or -not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 and ANDROID_HOME to the existing Android SDK.' }
$java = Join-Path $env:JAVA_HOME $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })
$aapt = Join-Path $sdk $(if ($IsWindows) { 'build-tools/34.0.0/aapt2.exe' } else { 'build-tools/34.0.0/aapt2' })
$signer = Join-Path $sdk 'build-tools/34.0.0/lib/apksigner.jar'
foreach ($required in @($java,$aapt,$signer,(Join-Path $sdk 'platforms/android-34/android.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw 'Required JDK17 / Android34 / Build Tools34 installation is incomplete. This gate never modifies the local SDK.' }
}
function Protect-Diagnostic([string]$Text) {
    foreach ($privateRoot in @($projectRoot,$sdk,$env:JAVA_HOME,$env:USERPROFILE,$env:HOME)) {
        if ($privateRoot) {
            $Text = $Text.Replace($privateRoot,'[local-path]').Replace($privateRoot.Replace('\','/'),'[local-path]')
        }
    }
    return $Text
}
function Invoke-Bounded {
    param([string]$Step,[string]$Program,[string[]]$Arguments,[int]$Seconds=300)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$Program; $info.WorkingDirectory=$projectRoot
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    foreach($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process=[Diagnostics.Process]::new(); $process.StartInfo=$info
    try {
        if(-not $process.Start()) { throw "Could not start gate step: $Step" }
        $stdout=$process.StandardOutput.ReadToEndAsync(); $stderr=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit($Seconds*1000)) {
            # Only this gate's process tree. Gradle uses --no-daemon to avoid a shared daemon.
            $process.Kill($true); $process.WaitForExit()
            throw "Gate step timed out: $Step"
        }
        $text=$stdout.GetAwaiter().GetResult()+$stderr.GetAwaiter().GetResult()
        if($text.Length -gt 20000000) { $text=$text.Substring(0,20000000)+"`n[log truncated at 20 million characters]" }
        $safe=Protect-Diagnostic $text
        Set-Content -LiteralPath (Join-Path $runPath "$Step.log") -Value $safe -Encoding utf8
        if($process.ExitCode -ne 0) {
            $safe -split "`n" | Select-Object -Last 25 | Write-Output
            throw "Gate step failed: $Step (exit $($process.ExitCode))"
        }
        return $text
    } finally { $process.Dispose() }
}
Push-Location $projectRoot
try {
    $source=(& git rev-parse HEAD).Trim()
    if($LASTEXITCODE -ne 0) { throw 'Not a Git source checkout.' }
    $dirty=@(& git status --porcelain)
    if(-not $AllowDirty -and $dirty.Count -ne 0) { throw 'Candidate must be clean. Use -AllowDirty only for development gates, never release packaging.' }
    $runtime=Invoke-Bounded -Step 'java-version' -Program $java -Arguments @('-version')
    if($runtime -notmatch '(?:version|openjdk) "?17[.]') { throw 'Android gate requires JDK 17.' }
    $buildText=Get-Content -LiteralPath 'app/build.gradle.kts' -Raw
    $version=[regex]::Match($buildText,'versionName\s*=\s*"([^"]+)"').Groups[1].Value
    $versionCode=[regex]::Match($buildText,'versionCode\s*=\s*(\d+)').Groups[1].Value
    if(-not $version -or -not $versionCode) { throw 'Cannot verify literal Android version mapping.' }
    $arguments=@('-classpath',(Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar'),
        'org.gradle.wrapper.GradleWrapperMain','--no-daemon','--console=plain','--continue',
        'testDebugUnitTest','--rerun','testReleaseUnitTest','--rerun',
        'assembleDebug','assembleRelease','assembleDebugAndroidTest','lintDebug','lintRelease')
    $null=Invoke-Bounded -Step 'gradle-gate' -Program $java -Arguments $arguments -Seconds ($TimeoutMinutes*60)
    $unit=@(); $lint=@()
    foreach($variant in @('Debug','Release')) {
        $tests=0; $failures=0; $errors=0; $skips=0
        $files=@(Get-ChildItem -LiteralPath "app/build/test-results/test${variant}UnitTest" -Filter 'TEST-*.xml')
        if($files.Count -eq 0) { throw "Missing $variant unit-test results." }
        $variantPath=Join-Path $reportPath $variant
        New-Item -ItemType Directory -Force -Path $variantPath | Out-Null
        foreach($file in $files) {
            [xml]$xml=Get-Content -LiteralPath $file.FullName -Raw
            $tests += [int]$xml.testsuite.tests; $failures += [int]$xml.testsuite.failures
            $errors += [int]$xml.testsuite.errors; $skips += [int]$xml.testsuite.skipped
            $xml.testsuite.RemoveAttribute('hostname')
            foreach($node in @($xml.SelectNodes('//system-out|//system-err'))) { $null=$node.ParentNode.RemoveChild($node) }
            Set-Content -LiteralPath (Join-Path $variantPath $file.Name) -Value (Protect-Diagnostic $xml.OuterXml) -Encoding utf8
        }
        if($tests -eq 0 -or $failures -gt 0 -or $errors -gt 0 -or $skips -gt 0) { throw "Unit-test count/failure/skip gate failed for $variant." }
        $unit += @{variant=$variant;tests=$tests;failures=$failures;errors=$errors;skipped=$skips;status='PASS'}
        $lintFile="app/build/reports/lint-results-$($variant.ToLowerInvariant()).xml"
        [xml]$lintXml=Get-Content -LiteralPath $lintFile -Raw
        $issues=@($lintXml.issues.issue | Where-Object { $null -ne $_ })
        $lintErrors=@($issues | Where-Object { $_.severity -in @('Error','Fatal') })
        if($lintErrors.Count -gt 0) { throw "Lint errors in $variant." }
        $lint += @{variant=$variant;errors=$lintErrors.Count;warnings=@($issues | Where-Object severity -eq 'Warning').Count;
            warning_ids=@($issues | Where-Object severity -eq 'Warning' | ForEach-Object id | Sort-Object -Unique);status='PASS'}
        Set-Content -LiteralPath (Join-Path $variantPath 'lint.xml') -Value (Protect-Diagnostic $lintXml.OuterXml) -Encoding utf8
    }
    $artifacts=@()
    foreach($variant in @('debug','release')) {
        $apk=Join-Path $projectRoot "app/build/outputs/apk/$variant/app-$variant.apk"
        $badging=Invoke-Bounded -Step "$variant-metadata" -Program $aapt -Arguments @('dump','badging',$apk)
        $expected=if($variant -eq 'debug') {'com.fieldconsole.debug'} else {'com.fieldconsole'}
        if($badging -notmatch "package: name='$([regex]::Escape($expected))' versionCode='$versionCode' versionName='$([regex]::Escape($version))'") { throw 'APK package/version mismatch.' }
        if($badging -notmatch "sdkVersion:'29'" -or $badging -notmatch "targetSdkVersion:'34'") { throw 'APK SDK mapping changed unexpectedly.' }
        $permissions=@([regex]::Matches($badging,"uses-permission: name='([^']+)'")) | ForEach-Object {$_.Groups[1].Value}
        $allowed=@('android.permission.ACCESS_FINE_LOCATION','android.permission.ACCESS_COARSE_LOCATION',
            'android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.READ_CONTACTS',
            "$expected.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        if(@($permissions | Where-Object {$_ -notin $allowed}).Count -gt 0) { throw 'Unexpected APK permission; review is required.' }
        $signature=Invoke-Bounded -Step "$variant-signature" -Program $java -Arguments @('-jar',$signer,'verify','--verbose','--print-certs',$apk)
        if($signature -notmatch 'Verifies') { throw 'APK signature verification did not succeed.' }
        if($signature -notmatch 'CN=Android Debug') { throw 'APK signing identity changed; review and classify it before distribution.' }
        $certificate=[regex]::Match($signature,'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)').Groups[1].Value.ToLowerInvariant()
        if($certificate.Length -ne 64) {throw 'APK certificate fingerprint missing.'}
        $abis=[regex]::Match($badging,'(?m)^native-code: (.+)$').Groups[1].Value.Trim()
        if($abis -notmatch "'arm64-v8a'" -or $abis -notmatch "'x86_64'") {throw 'Expected native ABI payloads missing; static Android compatibility gate failed.'}
        $artifacts += @{variant=$variant;name=[IO.Path]::GetFileName($apk);package=$expected;version=$version;version_code=[int]$versionCode;
            bytes=(Get-Item -LiteralPath $apk).Length;sha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant();
            signature_verified=$true;certificate_sha256=$certificate;signing='Android debug/testing identity; not production signing';static_abis=$abis}
    }
    $device=@{status='NOT_EXECUTED';reason='No explicit isolated emulator serial supplied.';physical_device='NOT_EXECUTED'}
    if($DeviceSerial) {
        $adb=Join-Path $sdk $(if($IsWindows){'platform-tools/adb.exe'}else{'platform-tools/adb'})
        $null=Invoke-Bounded -Step 'emulator-state' -Program $adb -Arguments @('-s',$DeviceSerial,'get-state')
        foreach($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
            $install=Invoke-Bounded -Step ('install-'+[IO.Path]::GetFileNameWithoutExtension($apk)) -Program $adb -Arguments @('-s',$DeviceSerial,'install','-r',$apk)
            if($install -notmatch 'Success') {throw 'Non-destructive emulator APK update failed; no uninstall is attempted.'}
        }
        $uiWaitMillis=10000
        $emulatorArguments=@()
        if($env:FIC_EMULATOR_ACCELERATION -eq 'off') {
            $uiWaitMillis=60000
            $emulatorArguments=@('-e','ficEmulatorMode','software','-e','ficUiWaitMillis','60000')
        }
        $result=Invoke-Bounded -Step 'instrumentation' -Program $adb -Arguments (@('-s',$DeviceSerial,'shell','am','instrument','-w') + $emulatorArguments + @(
            '-e','class','com.fieldconsole.DatabaseMigrationDeviceTest,com.fieldconsole.NetworkUiDeviceTest',
            'com.fieldconsole.debug.test/androidx.test.runner.AndroidJUnitRunner')) -Seconds 600
        if($result -notmatch 'OK \((\d+) tests?\)' -or $result -match 'FAILURES|INSTRUMENTATION_FAILED|Process crashed') {throw 'Emulator instrumentation did not pass.'}
        $deviceCount=[int]([regex]::Match($result,'OK \((\d+) tests?\)').Groups[1].Value)
        if($deviceCount -lt 6) {throw 'Expected migration and UI instrumentation tests were not all executed.'}
        $device=@{status='PASS';tests=$deviceCount;kind='isolated emulator';physical_device='NOT_EXECUTED';ui_wait_millis=$uiWaitMillis;instrumentation_timeout_seconds=600;acceleration=$(if($env:FIC_EMULATOR_ACCELERATION){$env:FIC_EMULATOR_ACCELERATION}else{'existing local emulator; mode not asserted'})}
    }
    if((& git rev-parse HEAD).Trim() -ne $source) {throw 'Source commit changed during the gate.'}
    if(-not $AllowDirty -and @(& git status --porcelain).Count -ne 0) {throw 'Tracked candidate changed during build; regenerate and recommit before release.'}
    $receipt=@{status='PASS';source_sha=$source;source_dirty=($dirty.Count -ne 0);utc=[DateTime]::UtcNow.ToString('o');
        runtime=($runtime -split "`n" | Select-Object -First 1).Trim();gradle='8.7';android_sdk=34;build_tools='34.0.0';
        unit=$unit;lint=$lint;artifacts=$artifacts;runtime_validation=$device;
        limitations='Static ABI/signature checks are not ARM64 physical-device proof. CI status requires actual workflow readback. Release artifact selection remains explicit.'}
    $receipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $outputPath 'gate-receipt.json') -Encoding utf8
    $receipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $runPath 'gate-receipt.json') -Encoding utf8
    Write-Output "ANDROID GATE PASS: debug/release unit tests, APKs, lint; emulator $($device.status)"
} finally { Pop-Location }
