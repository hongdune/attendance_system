<#
.SYNOPSIS
    좌석관리 시스템을 빌드해 설치 폴더에 배치하고, 로그인할 때 자동으로 켜지도록 등록한다.

.DESCRIPTION
    1. JDK 17 이상을 찾는다 (-InstallJdk 를 주면 없을 때 winget 으로 설치)
    2. 저장소에서 jar 를 빌드한다 (mvnw.cmd package)
    3. 실행 중인 서버를 멈추고 jar 와 실행 스크립트를 설치 폴더로 복사한다 (data 폴더는 유지)
    4. 시작프로그램 폴더에 바로가기를 만든다 (관리자 권한 불필요)
    5. 바로 한 번 실행해서 브라우저를 연다 (-NoStart 로 생략)

    코드를 업데이트한 뒤(git pull) 다시 실행하면 같은 설정으로 새 버전이 설치된다.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1 -InstallJdk -NoSeed -Kiosk
#>
param(
    [string]$InstallDir = 'C:\SeatSystem',
    [int]$Port = 8080,
    [switch]$AllowLan,
    [switch]$NoSeed,
    [switch]$Kiosk,
    [switch]$InstallJdk,
    [switch]$NoStart,
    [string]$ShortcutDir = [Environment]::GetFolderPath('Startup')
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$shortcutName = '좌석관리 시스템.lnk'

function Step([string]$message) { Write-Host "`n==> $message" -ForegroundColor Cyan }

function Get-JavaMajor([string]$javaExe) {
    # java -version 은 stderr 로 출력하므로 cmd 를 거쳐 문자열로 받는다
    $out = cmd /c "`"$javaExe`" -version 2>&1"
    $m = [regex]::Match(($out -join ' '), 'version "(\d+)')
    if ($m.Success) { [int]$m.Groups[1].Value } else { 0 }
}

function Find-Jdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $candidates += (Split-Path (Split-Path $cmd.Source)) }
    $candidates += Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft' `
        -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName

    foreach ($jdkHome in $candidates | Select-Object -Unique) {
        $java = Join-Path $jdkHome 'bin\java.exe'
        $javaw = Join-Path $jdkHome 'bin\javaw.exe'
        if ((Test-Path $java) -and (Test-Path $javaw) -and (Get-JavaMajor $java) -ge 17) { return $jdkHome }
    }
    return $null
}

# ---------- 1. JDK ----------
Step 'JDK 17 이상 확인'
$jdk = Find-Jdk
if (-not $jdk -and $InstallJdk) {
    Write-Host 'JDK 가 없어 winget 으로 Temurin 17 을 설치합니다...'
    winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-source-agreements --accept-package-agreements
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
    $jdk = Find-Jdk
}
if (-not $jdk) {
    throw @'
JDK 17 이상을 찾지 못했습니다. 아래 중 하나로 설치한 뒤 다시 실행해주세요.
  winget install EclipseAdoptium.Temurin.17.JDK
  또는 이 스크립트를 -InstallJdk 옵션과 함께 실행
'@
}
$javaw = Join-Path $jdk 'bin\javaw.exe'
Write-Host "사용할 JDK: $jdk"

# ---------- 2. 빌드 ----------
Step 'jar 빌드 (처음에는 Maven·라이브러리를 내려받느라 몇 분 걸릴 수 있습니다)'
$env:JAVA_HOME = $jdk
Push-Location $repo
try {
    & .\mvnw.cmd -q -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "빌드에 실패했습니다. (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
}
$jar = Get-ChildItem (Join-Path $repo 'target') -Filter 'seat-management-*.jar' |
        Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw 'target 폴더에서 jar 를 찾지 못했습니다.' }

# ---------- 3. 설치 폴더 ----------
Step "설치 폴더에 배치: $InstallDir"
& (Join-Path $PSScriptRoot 'stop.ps1') -Port $Port
New-Item -ItemType Directory -Force $InstallDir | Out-Null
Copy-Item $jar.FullName (Join-Path $InstallDir 'seat-management.jar') -Force
Copy-Item (Join-Path $PSScriptRoot 'start.ps1'), (Join-Path $PSScriptRoot 'stop.ps1') $InstallDir -Force
Write-Host '데이터(data 폴더)는 그대로 유지됩니다.'

# ---------- 4. 시작프로그램 바로가기 ----------
Step '로그인 시 자동 실행 등록 (시작프로그램 바로가기)'
$startArgs = @('-JavaPath', "`"$javaw`"", '-Port', $Port)
if ($AllowLan) { $startArgs += '-AllowLan' }
if ($NoSeed) { $startArgs += '-NoSeed' }
if ($Kiosk) { $startArgs += '-Kiosk' }

New-Item -ItemType Directory -Force $ShortcutDir | Out-Null
$shortcutPath = Join-Path $ShortcutDir $shortcutName
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = (Get-Command powershell.exe).Source
$shortcut.Arguments = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$(Join-Path $InstallDir 'start.ps1')`" $($startArgs -join ' ')"
$shortcut.WorkingDirectory = $InstallDir
$shortcut.WindowStyle = 7   # 최소화 상태로 시작 (창 깜박임 최소화)
$shortcut.Description = '좌석관리 시스템 서버를 켜고 브라우저로 엽니다'
$shortcut.Save()
Write-Host "바로가기: $shortcutPath"

# ---------- 5. 지금 실행 ----------
if (-not $NoStart) {
    Step '서버 시작'
    & (Join-Path $InstallDir 'start.ps1') -JavaPath $javaw -Port $Port -AllowLan:$AllowLan -NoSeed:$NoSeed -Kiosk:$Kiosk
}

Write-Host "`n설치 완료. 다음 로그인부터 자동으로 http://localhost:$Port 가 열립니다." -ForegroundColor Green
Write-Host "  지금 끄기 : powershell -ExecutionPolicy Bypass -File `"$InstallDir\stop.ps1`" -Port $Port"
Write-Host "  지금 켜기 : powershell -ExecutionPolicy Bypass -File `"$InstallDir\start.ps1`" -Port $Port"
Write-Host "  자동 실행 해제 : scripts\windows\uninstall.ps1"
