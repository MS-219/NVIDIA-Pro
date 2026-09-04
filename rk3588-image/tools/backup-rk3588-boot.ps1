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

function Read-AdbBinary([string]$deviceSerial, [string]$devicePath, [string]$targetPath) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'adb.exe'
    # BusyBox on this Buildroot image writes dd statistics to the stream that
    # adb exposes via exec-out.  status=none plus an explicit stderr redirect
    # keeps those human-readable lines out of the raw image bytes.
    $psi.Arguments = "-s `"$deviceSerial`" exec-out `"dd if=$devicePath bs=4M status=none 2>/dev/null`""
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
    Write-Host "Reading $name -> $target"
    Read-AdbBinary $device $devicePath $target
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
