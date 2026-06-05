# Remove build output; detects Cursor lock on app.asar and prints fix steps.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'out'

if (-not (Test-Path $outDir)) {
  Write-Host '[clean-out] out/ does not exist.'
  exit 0
}

$asar = Join-Path $outDir 'win-unpacked\resources\app.asar'
$handleExe = Join-Path $env:TEMP 'handle.exe'

function Get-LockingProcesses([string]$path) {
  if (-not (Test-Path $handleExe)) {
    try {
      Invoke-WebRequest -Uri 'https://live.sysinternals.com/handle.exe' -OutFile $handleExe -UseBasicParsing
    } catch {
      return @()
    }
  }

  $output = & $handleExe -accepteula $path 2>&1 | Out-String
  $pids = [regex]::Matches($output, 'pid:\s*(\d+)') | ForEach-Object { [int]$_.Groups[1].Value } | Select-Object -Unique
  foreach ($pid in $pids) {
    $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
    if ($proc) {
      [PSCustomObject]@{ PID = $pid; Name = $proc.ProcessName }
    }
  }
}

if (Test-Path $asar) {
  $lockers = Get-LockingProcesses $asar
  if ($lockers) {
    Write-Host '[clean-out] app.asar is locked by:'
    $lockers | Format-Table -AutoSize
    Write-Host 'Close the file tab in Cursor (or quit the locking app), then run this script again.'
    exit 1
  }
}

Remove-Item -Recurse -Force $outDir
if (Test-Path $outDir) {
  Write-Host '[clean-out] Failed to remove out/ completely.'
  exit 1
}

Write-Host "[clean-out] Removed $outDir"
