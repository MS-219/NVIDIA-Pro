param(
    [string]$OutputDir = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'

<#
Read-only backup helper for the CX3588-A/RK3588S board.
It deliberately backs up only boot-critical and vendor partitions. It never
calls rkdeveloptool, fastboot flash, dd with an output file on the device, or
any other write operation against eMMC.
#>

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw 'adb.exe not found. Install Android platform-tools and add it to PATH.'
}

$device = (& adb get-serialno).Trim()
if ([string]::IsNullOrWhiteSpace($device) -or $device -eq 'unknown') {
    throw 'No ADB device found. Check USB debugging and run adb devices first.'
}

$rootCheck = (& adb -s $device shell id).Trim()
if ($rootCheck -notmatch 'uid=0\(root\)') {
    throw "ADB shell is not root: $rootCheck"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outDir = Join-Path $OutputDir "rk3588-backup-$stamp-$device"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

function Read-AdbBinary([string]$deviceSerial, [string]$devicePath, [string]$targetPath, [Int64]$expectedBytes) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'adb.exe'
    # Keep the command BusyBox-compatible. Some vendor builds append dd
    # statistics to exec-out; the size check below removes those bytes safely.
    $psi.Arguments = "-s `"$deviceSerial`" exec-out `"dd if=$devicePath bs=4M 2>/dev/null`""
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    if (-not $process.Start()) {
        throw "Unable to start adb for $devicePath"
    }
    $file = [System.IO.File]::Open($targetPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        # Copy the native stdout BaseStream so PowerShell cannot transcode the
        # binary partition into UTF-16/UTF-32 text.
        $process.StandardOutput.BaseStream.CopyTo($file)
    } finally {
        $file.Dispose()
    }
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "adb failed for ${devicePath}: $stderr"
    }
    $actualBytes = (Get-Item $targetPath).Length
    if ($actualBytes -lt $expectedBytes) {
        throw "Short read for ${devicePath}: got $actualBytes bytes, expected $expectedBytes"
    }
    if ($actualBytes -gt $expectedBytes) {
        # Some vendor BusyBox builds still append dd diagnostics to exec-out.
        # Remove only trailing bytes after proving the raw payload is complete.
        $stream = [System.IO.File]::Open($targetPath, [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
        try { $stream.SetLength($expectedBytes) } finally { $stream.Dispose() }
    }
}

& adb -s $device shell cat /proc/device-tree/model | Out-File (Join-Path $outDir 'model.txt') -Encoding utf8
& adb -s $device shell cat /proc/cmdline | Out-File (Join-Path $outDir 'cmdline.txt') -Encoding utf8
& adb -s $device shell cat /proc/partitions | Out-File (Join-Path $outDir 'partitions.txt') -Encoding utf8
& adb -s $device shell "ls -l /dev/block/by-name" | Out-File (Join-Path $outDir 'by-name.txt') -Encoding utf8

$partitions = @('uboot', 'misc', 'boot', 'recovery', 'backup', 'oem')
foreach ($name in $partitions) {
    $devicePath = "/dev/block/by-name/$name"
    $exists = (& adb -s $device shell "test -e $devicePath; echo `$?").Trim()
    if ($exists -ne '0') {
        Write-Warning "Skipping missing partition: $name"
        continue
    }

    $target = Join-Path $outDir "$name.img"
    # blockdev is not included in some CX3588-A Buildroot images. Try it
    # first, then BusyBox stat (both return bytes) without calling Trim() on a
    # null PowerShell result.
    $sizeText = ((& adb -s $device shell "blockdev --getsize64 $devicePath 2>/dev/null" 2>$null) -join '').Trim()
    if ([string]::IsNullOrWhiteSpace($sizeText)) {
        $sizeText = ((& adb -s $device shell "stat -c %s $devicePath 2>/dev/null" 2>$null) -join '').Trim()
    }
    [Int64]$expectedBytes = 0
    if (-not [Int64]::TryParse($sizeText, [ref]$expectedBytes) -or $expectedBytes -le 0) {
        throw "Unable to determine partition size for $name ($devicePath)"
    }
    Write-Host "Reading $name -> $target"
    Read-AdbBinary $device $devicePath $target $expectedBytes
    if (-not (Test-Path $target) -or (Get-Item $target).Length -eq 0) {
        throw "Backup failed or empty: $name"
    }
    Get-FileHash $target -Algorithm SHA256 | Format-Table -AutoSize
}

& adb -s $device shell "sed -n '1,260p' /opt/scripts/startup.sh 2>/dev/null" |
    Out-File (Join-Path $outDir 'startup.sh.txt') -Encoding utf8
& adb -s $device shell "find /etc/init.d /opt/scripts /oem -maxdepth 3 -type f 2>/dev/null" |
    Out-File (Join-Path $outDir 'vendor-files.txt') -Encoding utf8

Write-Host "Read-only backup complete: $outDir"
Write-Host 'Keep this directory private. Do not upload token files or userdata.'
