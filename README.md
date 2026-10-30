# 좌석관리 시스템

부트캠프 강의실의 기수별 좌석 배치도를 보면서 수강생 상태(입실·화장실·흡연·외출 등)를 클릭 한 번으로 바꾸는 웹앱입니다.
여러 명이 동시에 열어도 WebSocket으로 실시간 동기화되고, 모든 상태 변경은 출입 히스토리로 남습니다.

- 기능 명세: [docs/Seat-sytem-spec.md](docs/Seat-sytem-spec.md)
- 개발 규칙·구조: [CLAUDE.md](CLAUDE.md)
- 범위 밖: 로그인/인증, 인스턴스 관리 (인증을 붙일 자리는 [CLAUDE.md](CLAUDE.md#인증-도입-시-현재-범위-밖) 참고)

## 기술 스택

| 구분 | 사용 |
|---|---|
| 백엔드 | Spring Boot 3.5 (Java 17), Spring Data JPA, Spring WebSocket (STOMP + SockJS), Apache POI |
| DB | 개발: H2 file 모드 / 운영: MySQL 또는 PostgreSQL (프로필 전환) |
| 프론트 | Thymeleaf + Vanilla JS + CSS, Font Awesome 6, Pretendard (CDN) |
| 빌드 | Maven Wrapper (`mvnw`, Maven 별도 설치 불필요) |

## 실행 방법

> 강의실 PC에 설치해서 로그인할 때 자동으로 켜지게 하려면 아래 [다른 컴퓨터에 설치하고 로그인 시 자동 실행하기](#다른-컴퓨터에-설치하고-로그인-시-자동-실행하기-windows)를 보세요.
> 이 절은 개발용 실행 방법입니다.

### 준비
- **JDK 17 이상**이 필요합니다. `java -version`으로 확인하고, 없으면 [Temurin 17](https://adoptium.net/temurin/releases/?version=17)을 설치하세요.
- 브라우저에서 CDN(cdnjs, jsdelivr)에 접속할 수 있어야 아이콘·폰트·WebSocket 라이브러리가 로드됩니다.

### 개발 모드 (H2 + 샘플 데이터)
```bash
# macOS / Linux / Git Bash
./mvnw spring-boot:run

# Windows PowerShell / cmd
mvnw.cmd spring-boot:run
```
- 좌석관리 메인: http://localhost:8080
- 수강생 관리: http://localhost:8080/students
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/seatdb`, 사용자 `sa`, 비밀번호 없음)

처음 실행하면 `./data/seatdb.mv.db`가 만들어지고, 기수가 하나도 없을 때만 샘플 데이터가 들어갑니다.

| 기수 | 인원 | 비고 |
|---|---|---|
| AINLP5 | 32명 | 2인 1조 책상(열 2·3 / 5·6 / 8·9 / 11), 화장실·흡연(위험)·편의점·외출(경고)·결석·지각·중도포기·회의실 인원 포함 |
| BESP5 | 24명 | 같은 배치 |

샘플 데이터를 처음 상태로 되돌리려면 서버를 끄고 `data/` 폴더를 지운 뒤 다시 실행하세요.

### 운영 DB (MySQL / PostgreSQL)
```bash
# MySQL
DB_URL="jdbc:mysql://db-host:3306/seat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" \
DB_USERNAME=seat DB_PASSWORD=secret \
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql

# PostgreSQL
DB_URL="jdbc:postgresql://db-host:5432/seat" DB_USERNAME=seat DB_PASSWORD=secret \
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres

# 배포용 jar
./mvnw clean package
java -jar target/seat-management-0.1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```
- 운영 프로필은 샘플 데이터를 넣지 않습니다.
- 스키마는 `ddl-auto=update`로 만들어집니다. 첫 배포 뒤에는 `DDL_AUTO=validate`로 바꾸고 Flyway 같은 마이그레이션 도구를 도입하는 것을 권장합니다.
- 서버 기준 시간대는 `seat.time-zone`(기본 `Asia/Seoul`)입니다.

### 테스트
```bash
./mvnw test
```
핵심 서비스 단위 테스트 25개(상태 변경 + 히스토리 기록, 좌석 중복, 스왑, 일괄 등록 파싱 등)가 실행됩니다.

## 다른 컴퓨터에 설치하고 로그인 시 자동 실행하기 (Windows)

강의실 PC처럼 **컴퓨터를 켜고 로그인하면 콘솔 창 없이 서버가 뜨고 브라우저가 http://localhost:8080 을 여는** 구성입니다.
`scripts/windows/install.ps1` 하나로 빌드·설치·자동 실행 등록까지 끝납니다.

### 1. 준비물
- Git
- JDK 17 이상. 없으면 아래 명령으로 설치하거나, 3단계에서 `-InstallJdk` 옵션을 주면 스크립트가 winget으로 설치합니다.
  ```powershell
  winget install EclipseAdoptium.Temurin.17.JDK
  ```
- 관리자 권한은 필요 없습니다.

### 2. 소스 받기
```powershell
git clone https://github.com/hongdune/attendance_system.git
cd attendance_system
```

### 3. 설치 + 자동 실행 등록
```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1
```
스크립트는 다음을 순서대로 합니다.
1. JDK 17 이상을 찾습니다(`JAVA_HOME` → `PATH` → `C:\Program Files\Eclipse Adoptium` 등).
2. jar를 빌드합니다. 처음에는 Maven과 라이브러리를 내려받느라 몇 분 걸립니다.
3. 실행 중인 서버를 멈추고 `C:\SeatSystem`에 jar와 실행 스크립트를 복사합니다.
4. **시작프로그램**에 `좌석관리 시스템` 바로가기를 만듭니다(`Win+R` → `shell:startup` 폴더).
5. 바로 한 번 실행해 브라우저를 엽니다.

다음 로그인부터는 서버가 자동으로 뜹니다. 보통 20초 안팎이 걸리고, 서버가 응답한 뒤에 브라우저가 열립니다.

**설치 옵션** (여러 개 함께 사용 가능)

| 옵션 | 설명 |
|---|---|
| `-InstallDir D:\Seat` | 설치 폴더 (기본 `C:\SeatSystem`). 출결 데이터도 이 폴더의 `data\`에 저장됩니다. |
| `-Port 9090` | 사용할 포트 (기본 8080) |
| `-NoSeed` | 샘플 데이터(AINLP5·BESP5)를 넣지 않음. **실제 운영용이면 권장**합니다. |
| `-Kiosk` | 브라우저를 Edge 전체 화면 키오스크 모드로 엽니다(종료: `Alt+F4`). |
| `-AllowLan` | 같은 네트워크의 다른 PC에서도 접속할 수 있게 엽니다. 이 앱에는 **로그인 기능이 없으므로** 신뢰할 수 있는 네트워크에서만 사용하세요. 처음 실행 때 Windows 방화벽 허용 창이 뜰 수 있습니다. |
| `-InstallJdk` | JDK가 없으면 winget으로 Temurin 17을 설치합니다. |
| `-NoStart` | 설치만 하고 지금 실행하지는 않습니다. |

예: 운영용, 키오스크 모드
```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1 -NoSeed -Kiosk
```

### 4. 평소 사용
```powershell
# 지금 끄기 / 켜기 (설치 폴더에 복사된 스크립트)
powershell -ExecutionPolicy Bypass -File C:\SeatSystem\stop.ps1
powershell -ExecutionPolicy Bypass -File C:\SeatSystem\start.ps1
```
- 끄기는 해당 포트를 쓰는 Java 프로세스만 종료합니다. IDE 같은 다른 Java 프로그램은 건드리지 않습니다.
- 이미 켜져 있을 때 켜기를 실행하면 서버를 하나 더 띄우지 않고 브라우저만 엽니다.
- 로그: `C:\SeatSystem\logs\seat.log`(서버), `C:\SeatSystem\logs\launcher.log`(자동 실행 스크립트)

### 5. 업데이트
```powershell
cd attendance_system
git pull
powershell -ExecutionPolicy Bypass -File scripts\windows\install.ps1 -NoSeed   # 처음 설치 때와 같은 옵션으로
```
서버를 멈추고 새 jar로 교체한 뒤 다시 켭니다. `data\` 폴더(출결 데이터)는 그대로 유지됩니다.

### 6. 자동 실행 해제
```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\uninstall.ps1              # 프로그램만 삭제, 데이터·로그 유지
powershell -ExecutionPolicy Bypass -File scripts\windows\uninstall.ps1 -RemoveData  # 출결 데이터까지 모두 삭제
```
설치 때 `-InstallDir`이나 `-Port`를 바꿨다면 여기에도 같은 값을 주세요.

### 참고
- **백업**: 출결 기록은 `C:\SeatSystem\data\seatdb.mv.db` 파일 하나에 들어 있습니다. 서버를 끄고 이 파일을 복사해 두면 됩니다.
- **로그인 없이 바로 켜지게**: 전원만 켜면 열리게 하려면 Windows 자동 로그인(`Win+R` → `netplwiz`)을 설정해야 합니다. PC를 누구나 쓸 수 있게 되므로 강의실 전용 PC에서만 권장합니다.
- **창 깜박임**: 로그인 직후 PowerShell 창이 잠깐(최소화 상태로) 나타났다가 사라질 수 있습니다. 서버 자체는 콘솔 창 없이(`javaw`) 실행됩니다.
- **포트 충돌**: 다른 프로그램이 이미 8080을 쓰고 있으면 `-Port`로 다른 포트를 지정하세요.
- **macOS/Linux**는 자동 실행 스크립트를 제공하지 않습니다. 위의 "운영 DB"처럼 jar를 빌드해 `java -jar`로 실행하세요.

## API 요약

명세서 5장의 경로를 그대로 사용합니다. 오류는 HTTP 400/404/409와 함께 `{"message": "...", "details": ["..."]}` 형태로 응답합니다(`details`는 있을 때만).

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/` `?batchName=` | | 메인 화면 |
| GET | `/students?batchName=` | | 수강생 관리 화면 |
| GET | `/api/seats/batch/{batchName}` | | `[{id,name,rowIndex,colIndex,status,statusChangedAt,meetingRoom}]` (중도포기 포함, 이름순) |
| POST | `/seats/status` | `{seatId, status}` | 변경된 좌석 |
| POST | `/seats/bulk-exit` | `{batchName}` | `{changed}` |
| POST | `/seats/lunch-time` | `{batchName}` | `{changed}` |
| POST | `/students` | `{name, batchName, rowIndex, colIndex}` | 생성된 좌석 (201) |
| PUT | `/students/{id}` | `{name, rowIndex, colIndex}` | 수정된 좌석 |
| DELETE | `/students/{id}` | | 204 |
| POST | `/students/swap` | `{seatId1, seatId2}` | 바뀐 두 좌석 |
| POST | `/students/bulk` | `{batchName, text}` 또는 `{batchName, students:[{name, rowIndex?, colIndex?}]}` | `{createdCount, created, skipped:[{line,name,reason}]}` |
| POST | `/batches` | `{name}` | `{id, name}` (201) |
| DELETE | `/batches/{id}` | | 204 (수강생이 있으면 409) |
| GET | `/api/history` | `batchName, studentName, startDate, endDate, page, size` | `{histories, totalPages, currentPage, totalElements, hasPrevious, hasNext}` |
| GET | `/api/history/export` | 같은 필터 | `.xlsx` |
| POST | `/api/meeting-room/enter` | `{seatId, meetingRoom}` | 변경된 좌석 |
| POST | `/api/meeting-room/exit?seatId=` | | 변경된 좌석 |
| POST | `/api/meeting-room/exit-all?batchName=` | | `{changed}` |

**WebSocket**: SockJS 엔드포인트 `/ws`, STOMP 구독 `/topic/batch/{batchName}`. 메시지 형식은 아래와 같습니다.
```json
{"type":"UPDATED","seatId":12,"seatName":"홍길동","rowIndex":1,"colIndex":2,
 "status":"WC","statusChangedAt":"2026-09-02T12:06:44+09:00","meetingRoom":null}
```
`type`은 `CREATED` / `UPDATED` / `DELETED` 중 하나이고, 트랜잭션이 커밋된 뒤에만 전송됩니다.

## 결정 사항 (명세가 모호했던 부분)

### API 형식
1. **상태 변경 바디**(명세 "추정"): `{seatId, status}`. 응답은 바뀐 좌석 JSON입니다.
2. **일괄 등록 바디**(명세 "추정"): 화면은 textarea 원문을 `{batchName, text}`로 보내고 서버가 파싱합니다. 파싱 규칙을 한 곳(`BulkRegistrationParser`)에 두고 테스트하기 위해서입니다. 명세에서 추정한 `{batchName, students:[...]}` 형식도 받으며, 같은 규칙으로 검증하고 행·열을 비우면 자동 배치합니다.
3. **일괄 등록 오류 처리**: 형식·범위 오류가 **한 줄이라도 있으면 아무것도 저장하지 않고** 줄 번호별 오류 목록(400 `details`)을 돌려줍니다. 이미 사용 중인 위치(입력 안에서 겹친 경우 포함)는 그 줄만 건너뛰고 `skipped`로 알려줍니다. 줄 번호는 빈 줄을 포함한 원문 기준입니다.
4. **일괄 작업 응답**: 전체 퇴실·점심시간·전체 회의실 퇴장은 `{changed: 바뀐 인원 수}`를 돌려줍니다.
5. **히스토리 조회**: `page`는 0부터 시작하고, `size`는 기본 10·최대 100입니다. `startDate`/`endDate`는 `datetime-local` 형식(`yyyy-MM-ddTHH:mm`)이며 서버 시간대로 해석합니다. 종료일시가 분 단위로 들어오면 **그 분의 마지막 초까지 포함**합니다(12:06까지 검색하면 12:06:44 기록도 나옴). 수강생명은 부분 일치입니다. 화면의 "번호"는 최신 기록이 가장 큰 번호인 역순 번호입니다.
6. **시간 표현**: JSON 시각은 오프셋을 포함한 ISO 문자열(`+09:00`)로 내보냅니다. 화면은 서버 시각(`serverNow`)과 브라우저 시계의 차이를 보정해서 타이머를 계산합니다.

### 상태 규칙
7. **같은 상태로 다시 변경하면 아무것도 하지 않습니다.** 타이머가 유지되고 히스토리도 남기지 않습니다(명세의 "상태가 바뀔 때마다").
8. **점심시간 대상**: 중도포기·결석·퇴실(아직 안 왔거나 이미 간 사람)·이미 점심인 수강생은 **제외**하고, 나머지(입실·지각·화장실·흡연·편의점·외출)를 점심시간으로 바꿉니다.
9. **전체 퇴실 대상**: 중도포기·결석(결석 기록 보존)·이미 퇴실한 수강생은 제외합니다. 그리고 **모든 수강생을 회의실에서도 퇴장**시킵니다.
10. **회의실 입·퇴장은 상태 변경이 아니므로** 히스토리를 남기지 않고 브로드캐스트만 합니다(명세: "회의실 입장은 좌석 status와 별개").
11. **중도포기 → 다른 상태로 복귀**하면 1행 1열부터 행 우선으로 찾은 첫 빈 칸에 자동 배치합니다. 빈 칸이 없으면 409입니다. 중도포기로 바꾸면 위치 (0,0)과 함께 회의실도 비웁니다.
12. **신규 수강생의 초기 상태는 퇴실(OUT)**이고, 등록 자체는 상태 변경이 아니므로 히스토리를 남기지 않습니다.
13. **메뉴 구성**: 좌석 메뉴는 명세대로 8개 상태(입실~외출)를 보여줍니다. 중도포기는 수강생 관리 화면의 셀렉트에서만 지정할 수 있고, 그 셀렉트에는 점심시간을 뺀 9개 상태가 있습니다. 점심시간 중인 학생은 셀렉트에 "점심시간"이 선택 불가 항목으로 표시됩니다.

### 좌석·검증
14. **일괄 등록 자동 배치**는 명세 문구대로 1행 1열부터 **모든 열**의 빈 칸을 행 우선으로 채웁니다(책상 열만 쓰는 방식이 아님). 책상 배치에 맞추려면 방법 2(직접 지정)를 쓰세요.
15. **행·열 범위**는 원본 폼의 행 최대값 15 대신 그리드 상수 `GRID_ROWS=11`, `GRID_COLS=12`로 통일했습니다. 서버(`SeatGrid`)와 화면(`SEAT_CONFIG`)이 같은 값을 씁니다.
16. **좌석 중복**: 중도포기 학생은 모두 (0,0)이라 DB 유니크 제약을 걸 수 없어서 서비스에서 검증합니다. 동시에 두 명이 같은 칸으로 옮기는 경합은 기수 행 비관적 잠금(`SELECT ... FOR UPDATE`)으로 막습니다.
17. **이름 제한**: 수강생 이름은 1~30자이고, 일괄 등록에서 숫자로만 된 이름은 오류로 처리합니다. 이름만 쓰다가 `이름, 행, 열`을 섞어 쓴 실수를 잡기 위해서입니다.
18. **기수명**은 2~50자이고, URL 경로와 WebSocket 토픽에 그대로 쓰이므로 `/ \ ? # %` 문자를 금지합니다.

### 화면 동작
19. **실시간 반영 방식**: 명세는 신규 좌석이나 위치 변경이 있으면 새로고침한다고 되어 있지만, 새로고침하지 않고 **제자리에서 반영**합니다. 카드 추가·제거는 즉시, 위치 이동은 이동 애니메이션으로 보여줍니다. 탭 복귀·창 포커스·WebSocket 재연결 때는 `GET /api/seats/batch/{batchName}`로 전체를 다시 받아 맞춥니다.
20. **위험/경고 표시**: WC·SMOKE·STORE는 10분, OUT_OFFICE는 2시간이 지나면 빨강↔주황 펄스(위험)가 되고, OUT_OFFICE는 1시간 30분부터 주황 테두리(경고)가 됩니다. 브라우저 알림은 위험 상태가 될 때 좌석당 한 번 보냅니다. 권한은 첫 클릭 때 요청합니다.
21. **"마지막 업데이트"**는 실시간 메시지를 받거나 동기화할 때 즉시, 그 밖에는 30초마다 현재 시각으로 갱신됩니다.
22. **히스토리 필터의 기수 기본값**은 현재 보고 있는 기수입니다. [초기화]를 누르면 이 상태로 돌아갑니다. "전체"를 고르면 모든 기수를 조회합니다.
23. **엑셀 내보내기**는 현재 필터를 적용해 최대 100,000행까지 내보냅니다. 변경시간은 엑셀 날짜 셀(`yyyy-mm-dd hh:mm:ss`)로 저장됩니다.
24. **모바일**: 페이지 자체에는 가로 스크롤이 없고, 배치도만 카드 안에서 가로로 스크롤됩니다. 드래그 앤 드롭은 HTML5 DnD라 데스크톱 브라우저에서만 동작합니다.

## 실시간 동기화 확인 체크리스트

브라우저 창 두 개(A, B)를 나란히 띄우고 둘 다 http://localhost:8080/?batchName=AINLP5 를 엽니다. 하나는 시크릿 창이어도 됩니다.

**연결**
- [ ] A·B 모두 헤더의 `실시간 업데이트 ●`가 초록색이다.

**좌석 상태**
- [ ] A에서 입실 좌석을 클릭해 [화장실]을 누르면, B의 같은 좌석이 하늘색·화장실 아이콘으로 바뀌고 `00:0x` 타이머가 돈다.
- [ ] A에서 회의실 [입장하기] → 회의실 3을 고르면, B 좌석에 `회의실 3` 뱃지가 생기고 [회의실 현황] 숫자가 1 늘어난다.
- [ ] B에서 [회의실 현황]을 열어 둔 채 A에서 해당 학생을 퇴장시키면, B 모달 목록에서 바로 사라진다.

**일괄 작업**
- [ ] A에서 [점심시간] → 확인을 누르면, B의 좌석들이 주황색 점심 아이콘으로 바뀐다(결석·퇴실·중도포기는 그대로).
- [ ] B에서 점심 좌석 하나를 클릭하면 메뉴 없이 바로 입실이 되고, A에도 반영된다.
- [ ] A에서 [전체 퇴실] → 확인을 누르면, B 좌석들이 회색 퇴실로 바뀌고 회의실 인원이 0이 된다.

**자리 이동**
- [ ] A에서 좌석을 빈 칸으로 드래그하면, B에서 같은 좌석이 미끄러지듯 새 칸으로 이동한다.
- [ ] A에서 좌석을 다른 좌석 위에 드롭하고 [교환]을 누르면, B에서 두 좌석의 자리가 바뀐다.

**수강생 관리 화면과의 연동**
- [ ] B를 http://localhost:8080/students?batchName=AINLP5 로 바꾸고 A에서 상태를 바꾸면, B 표의 "현재 상태" 셀렉트와 "상태 변경 시간"이 바뀌고 행이 잠깐 강조된다.
- [ ] B에서 [수강생 추가]로 빈 칸(예: 10행 1열)에 학생을 추가하면, A 배치도에 새 카드가 바로 나타난다.
- [ ] B에서 어떤 학생을 "중도포기"로 바꾸고 확인하면, A 배치도에서 그 카드가 사라진다. [가나다순 보기]에는 회색으로 남는다.
- [ ] B에서 학생을 삭제하면 A 배치도와 가나다순 목록에서 사라진다.

**히스토리**
- [ ] A에서 [출입 히스토리]를 열면, 방금 한 변경들이 최신순으로 `2026. 09. 02. 오후 12:06:44` 형식과 함께 보인다. [엑셀 내보내기]로 `.xlsx`를 받을 수 있다.

**재연결**
- [ ] 서버를 껐다 켜면, 두 창의 표시가 빨강으로 바뀌었다가 자동으로 초록으로 돌아온다(3초 간격 재연결). 그 사이 바뀐 상태도 맞춰진다.

> 참고: 이 체크리스트의 주요 항목(연결, 상태·회의실·점심·전체 퇴실 동기화, 드래그 이동·교환, 수강생 추가/중도포기/삭제 연동,
> 히스토리 페이징·형식, 모바일 폭, 서버 재시작 후 재연결)은 개발 중 헤드리스 Chrome으로 자동 확인했습니다.
> 실제 마우스 드래그와 브라우저 알림 권한 팝업은 직접 확인해 주세요.
