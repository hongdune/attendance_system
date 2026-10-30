<#
.SYNOPSIS
    좌석관리 시스템 서버를 종료한다. 해당 포트를 Java 가 쓰고 있을 때만 종료한다.
#>
param(
    [int]$Port = 8080
)

$conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $conn) {
    Write-Host "포트 $Port 에서 실행 중인 서버가 없습니다."
    return
}

$proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
if (-not $proc -or $proc.ProcessName -notin @('java', 'javaw')) {
    Write-Warning "포트 $Port 를 다른 프로그램($($proc.ProcessName))이 사용 중이라 종료하지 않습니다."
    return
}

Stop-Process -Id $proc.Id -Force -Confirm:$false
for ($i = 0; $i -lt 20 -and (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue); $i++) {
    Start-Sleep -Milliseconds 500
}
Write-Host "서버를 종료했습니다. (PID $($proc.Id))"
