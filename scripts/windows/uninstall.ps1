<#
.SYNOPSIS
    자동 실행을 해제하고 서버를 멈춘다. 기본적으로 데이터(data 폴더)와 로그는 남겨둔다.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\windows\uninstall.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\windows\uninstall.ps1 -RemoveData   # 출결 데이터까지 모두 삭제
#>
param(
    [string]$InstallDir = 'C:\SeatSystem',
    [int]$Port = 8080,
    [switch]$RemoveData,
    [string]$ShortcutDir = [Environment]::GetFolderPath('Startup')
)

$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'stop.ps1') -Port $Port

$shortcutPath = Join-Path $ShortcutDir '좌석관리 시스템.lnk'
if (Test-Path $shortcutPath) {
    Remove-Item $shortcutPath -Force
    Write-Host "자동 실행 해제: $shortcutPath"
}

if (Test-Path $InstallDir) {
    if ($RemoveData) {
        Remove-Item $InstallDir -Recurse -Force
        Write-Host "설치 폴더를 데이터와 함께 삭제했습니다: $InstallDir"
    } else {
        Remove-Item (Join-Path $InstallDir '*') -Include 'seat-management.jar', 'start.ps1', 'stop.ps1' -Force
        Write-Host "프로그램 파일을 삭제했습니다. 데이터와 로그는 남아 있습니다: $InstallDir"
    }
}
