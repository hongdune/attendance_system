<#
.SYNOPSIS
    좌석관리 시스템 서버를 콘솔 창 없이 띄우고, 응답하면 브라우저로 연다.

.DESCRIPTION
    install.ps1 이 설치 폴더에 복사하고 시작프로그램 바로가기로 등록한다.
    이미 서버가 떠 있으면 새로 띄우지 않고 브라우저만 연다.
    서버 로그: <설치 폴더>\logs\seat.log / 이 스크립트 로그: <설치 폴더>\logs\launcher.log
#>
param(
    [string]$InstallDir = $PSScriptRoot,
    [string]$JavaPath,
    [int]$Port = 8080,
    [switch]$AllowLan,
    [switch]$NoSeed,
    [switch]$Kiosk,
    [switch]$NoBrowser,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = 'Stop'
$logDir = Join-Path $InstallDir 'logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$launcherLog = Join-Path $logDir 'launcher.log'

function Write-Log([string]$message) {
    $line = '{0:yyyy-MM-dd HH:mm:ss}  {1}' -f (Get-Date), $message
    Add-Content -Path $launcherLog -Value $line -Encoding UTF8
    Write-Host $message
}

function Test-Listening {
    [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Resolve-Javaw {
    if ($JavaPath -and (Test-Path $JavaPath)) { return $JavaPath }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javaw.exe")) { return "$env:JAVA_HOME\bin\javaw.exe" }
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'javaw.exe 를 찾을 수 없습니다. JDK 17 을 설치하고 install.ps1 을 다시 실행해주세요.'
}

try {
    $url = "http://localhost:$Port"
    $jar = Join-Path $InstallDir 'seat-management.jar'
    if (-not (Test-Path $jar)) { throw "실행 파일이 없습니다: $jar (install.ps1 을 먼저 실행해주세요)" }

    if (Test-Listening) {
        Write-Log "포트 $Port 에서 이미 실행 중입니다."
    } else {
        $javaw = Resolve-Javaw
        # PowerShell 5.1 의 Start-Process 는 인자를 따옴표 없이 이어 붙이므로 경로는 직접 감싼다
        $javaArgs = @(
            '-jar', "`"$jar`"",
            "--server.port=$Port",
            "--logging.file.name=`"$(Join-Path $logDir 'seat.log')`""
        )
        if (-not $AllowLan) { $javaArgs += '--server.address=127.0.0.1' }
        if ($NoSeed) { $javaArgs += '--seat.seed.enabled=false' }

        Write-Log "서버 시작: $javaw $($javaArgs -join ' ')"
        # 작업 폴더가 설치 폴더라서 DB 는 <설치 폴더>\data 에 저장된다
        Start-Process -FilePath $javaw -ArgumentList $javaArgs -WorkingDirectory $InstallDir
    }

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $ready = $false
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest "http://127.0.0.1:$Port/" -UseBasicParsing -TimeoutSec 3 | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if ($ready) { Write-Log "서버 응답 확인: $url" }
    else { Write-Log "경고: ${TimeoutSeconds}초 안에 서버가 응답하지 않았습니다. logs\seat.log 를 확인해주세요." }

    if (-not $NoBrowser) {
        if ($Kiosk) {
            Start-Process msedge -ArgumentList "--kiosk $url --edge-kiosk-type=fullscreen --no-first-run"
        } else {
            Start-Process $url
        }
    }
} catch {
    Write-Log "오류: $($_.Exception.Message)"
    exit 1
}
