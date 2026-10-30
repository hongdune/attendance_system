# CLAUDE.md

부트캠프 강의실 좌석/출결 실시간 관리 웹앱. 기능 명세: [docs/Seat-sytem-spec.md](docs/Seat-sytem-spec.md) — 작업 전 반드시 읽을 것.

## 스택
- Spring Boot 3.5 (Java 17), Maven Wrapper(`mvnw`), Spring Data JPA, Spring WebSocket(STOMP + SockJS), Apache POI
- DB: `dev` 프로필 = H2 file(`./data/seatdb`), `mysql` / `postgres` 프로필로 교체
- 프론트: Thymeleaf 템플릿 + Vanilla JS + CSS, Font Awesome 6 (CDN), SockJS·stomp.js (CDN). 빌드 도구 없음.

## 명령
```bash
./mvnw spring-boot:run                 # dev 프로필(H2 + 샘플 데이터), http://localhost:8080
./mvnw test                            # 단위 테스트
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql   # 운영 DB (환경변수 DB_URL/DB_USERNAME/DB_PASSWORD)
```

## 구조 (`src/main/java/com/bootcamp/seat`)
- `domain/` — `Batch`, `Student`(학생 = 좌석 1개, `seatId` = 학생 id), `History`, `SeatStatus`, `SeatGrid`
- `repository/` — Spring Data 리포지토리, 히스토리 필터는 `HistorySpecs`
- `service/` — 비즈니스 규칙 전부 여기. 웹 계층(HTTP, DTO 매핑 외)에 의존하지 않음
- `web/` — `PageController`(Thymeleaf 화면), REST 컨트롤러들, `ApiExceptionHandler`, `dto/`
- `config/` — WebSocket, Clock, 샘플 데이터 시더
- `src/main/resources/templates` — `index.html`(좌석 메인), `students.html`(수강생 관리), `fragments/`
- `src/main/resources/static/js` — `common.js`(api 래퍼·토스트·모달·WS), `seat.js`, `students.js`
- `scripts/windows/` — 설치·자동 실행(시작프로그램 바로가기)·시작·종료·제거 스크립트. Windows PowerShell 5.1 호환을 위해 **UTF-8 BOM + CRLF**로 저장할 것(BOM 이 없으면 한글이 깨져 파싱 오류)

## 반드시 지킬 규칙
- **상태 enum**: `IN, OUT, WC, SMOKE, STORE, ABSENT, LATE, OUT_OFFICE, LUNCH, GIVEUP`. 라벨·색·아이콘·타이머/위험 기준은 `SeatStatus`에만 정의하고, 템플릿이 `window.SEAT_CONFIG`로 JS에 내려준다. JS에 상태 메타데이터를 하드코딩하지 말 것.
- **그리드 크기**: `SeatGrid.ROWS=11`, `SeatGrid.COLS=12`. 프론트도 `SEAT_CONFIG.gridRows/gridCols`만 사용.
- **상태 변경 = History 1건 + 브로드캐스트**. 개별·점심시간·전체퇴실 모두 `SeatStatusService`를 거친다. 상태를 바꾸는 코드를 다른 곳에 새로 만들지 말 것.
- 브로드캐스트는 `SeatChangedEvent`를 발행 → `SeatEventBroadcaster`가 **커밋 후** `/topic/batch/{batchName}`로 전송. `SimpMessagingTemplate`를 서비스에서 직접 호출하지 말 것.
- 타이머/위험 판정 기준은 `statusChangedAt`. WC/SMOKE/STORE 10분, OUT_OFFICE 2시간(1시간 30분부터 경고).
- GIVEUP: 위치 (0,0), 회의실 해제, 배치도에서 숨김(가나다순 목록에는 회색 표시). LUNCH 좌석 클릭 = 즉시 IN.
- 같은 기수 안 (row, col) 중복 금지 — 서버에서 검증(`StudentService`). 위치를 바꾸는 작업은 `BatchRepository.findByIdForUpdate`로 기수 행을 잠근 뒤 검증한다.
- 수강생이 있는 기수는 삭제 불가.
- API 경로와 WebSocket 토픽은 명세 5장과 동일하게 유지.
- UI 문구는 전부 한국어. 디자인 토큰은 `static/css/common.css`의 `:root` 변수(primary `#FF6B00`)를 사용.
- 시간: 서버는 `Clock` 빈(`seat.time-zone`, 기본 Asia/Seoul)으로 `LocalDateTime`을 만든다. JSON에는 오프셋 포함 ISO 문자열로 내보낸다(`TimeFormats`).

## 에러 처리
- 서비스는 `SeatException`(HTTP 상태 + 한국어 메시지)을 던진다. `ApiExceptionHandler`가 `{ "message": "..." }`로 변환. 프론트는 `api()`가 실패 시 이 메시지로 토스트를 띄운다.

## 인증 도입 시 (현재 범위 밖)
- 화면은 GET, 변경은 JSON API(POST/PUT/DELETE)로 분리돼 있음 → Spring Security `SecurityFilterChain`으로 경로별 보호.
- 프론트의 모든 요청은 `common.js`의 `api()` 하나로 나간다 → CSRF 헤더는 여기에만 추가.
- WebSocket은 `WebSocketConfig.configureClientInboundChannel`에 `ChannelInterceptor`를 붙이면 됨.

## 테스트
- 서비스 테스트는 `@DataJpaTest` + `@Import(서비스)` + `@RecordApplicationEvents`로 히스토리/이벤트를 검증. 시간은 `MutableClock`으로 고정.
- 파서(`BulkRegistrationParser`)는 순수 단위 테스트.

## 커밋
- 기능 단위로 커밋. 명세가 모호해 결정한 사항은 README "결정 사항"에 추가.
