# 좌석관리 시스템 — 클론 개발용 기능 명세서

> 원본: seat.pyeonch.com (부트캠프 강의실 좌석/출결 실시간 관리 도구)
> 범위: **학생(수강생) 등록 + 출결/상태 관리**. 로그인·인스턴스 관리는 제외(추후).
> 이 문서는 원본 화면/동작을 관찰해 정리한 것입니다. 내부 소스 일부는 확인하지 못해 API 요청 형식 등은 "추정"으로 표시했습니다.

---

## 1. 개요

- 강사/매니저가 한 화면에서 **기수(batch)별 강의실 좌석 배치도**를 보고, 각 수강생의 현재 상태(입실·화장실·흡연·외출 등)를 클릭 한 번으로 바꾸는 도구.
- 여러 명이 동시에 보더라도 **WebSocket으로 실시간 동기화**.
- 모든 상태 변경은 **출입 히스토리**로 기록되고 필터·페이징·엑셀 내보내기가 가능.
- 원본 기술 스택(관찰): 서버 렌더링 HTML + Vanilla JS, SockJS + STOMP(`/ws`, 토픽 `/topic/batch/{batchName}`) → Spring Boot 계열로 추정. Font Awesome 6.4 아이콘.

## 2. 도메인 모델

### Batch (기수)
| 필드 | 타입 | 비고 |
|---|---|---|
| id | long | |
| name | string | 고유, 2~50자 (예: `AINLP5`, `BESP5`) |

### Student / Seat (수강생 = 좌석 1개)
원본은 학생과 좌석을 하나의 엔티티로 다룸 (`seatId` = 학생 id).

| 필드 | 타입 | 비고 |
|---|---|---|
| id | long | seatId로도 사용 |
| name | string | |
| batch | Batch | |
| rowIndex | int | 1~11 (배치도 행). 중도포기 시 0 |
| colIndex | int | 1~12 (배치도 열). 중도포기 시 0 |
| status | enum | 아래 표 |
| statusChangedAt | datetime | 타이머/위험 판정 기준 |
| meetingRoom | int? | null 또는 1~3 |

- 같은 기수 안에서 (rowIndex, colIndex)는 중복 불가.
- 참고: 원본 수강생 추가 폼은 행 최대값이 15, 배치도 그리드는 11행 → **상수로 분리**(`GRID_ROWS=11`, `GRID_COLS=12`)하고 일관되게 검증할 것.

### SeatStatus (enum)
| 코드 | 라벨 | 색상(배경) | 아이콘(FA) | 타이머 | 위험(깜박임) 기준 |
|---|---|---|---|---|---|
| IN | 입실 | #10b981 (green) | sign-in-alt | ✕ | – |
| OUT | 퇴실 | #64748b (slate) | sign-out-alt | ✕ | – |
| WC | 화장실 | #0ea5e9 (sky) | restroom | ✓ | 10분 |
| SMOKE | 흡연 | #f59e0b (amber) | smoking | ✓ | 10분 |
| STORE | 편의점 | #C2410C (brown-orange) | shopping-cart | ✓ | 10분 |
| ABSENT | 결석 | #ef4444 (red) | user-times | ✕ | – |
| LATE | 지각 | #06b6d4 (cyan) | clock | ✕ | – |
| OUT_OFFICE | 외출 | #6366f1 (indigo) | building | ✓ | 2시간 (1시간 30분부터 경고) |
| LUNCH | 점심시간 | #f97316 (orange) | utensils | ✕ | – |
| GIVEUP | 중도포기 | (배치도에서 숨김) | – | ✕ | – |

- 위험 상태: 테두리 3px + 빨강↔주황 펄스 애니메이션(`dangerPulse 1.5s infinite`) + (권한 있으면) 브라우저 알림.

### History (출입 기록)
| 필드 | 타입 |
|---|---|
| id | long |
| studentName | string (스냅샷) |
| batchName | string (스냅샷) |
| toStatus | SeatStatus |
| changedAt | datetime |

→ 학생 상태가 바뀔 때마다(개별/일괄/점심 포함) 1건씩 insert.

## 3. 화면 1 — 좌석관리 메인 (`/`)

### 헤더
- 좌측: 모니터 아이콘(주황) + "좌석관리 시스템"
- 우측: `실시간 업데이트 ●`(WebSocket 연결 시 초록, 끊기면 빨강) · [수강생 관리] 버튼 · (인스턴스 관리/로그아웃은 이번 범위 제외)

### 툴바
- **기수 선택** 드롭다운 — 선택 시 해당 기수 배치도로 전환 (`?batchName=` 또는 상태로 관리)
- 버튼 5개:
  1. **가나다순 보기 / 숨기기** (토글)
  2. **회의실 현황** — 버튼에 현재 회의실 총 인원 카운트 표시
  3. **출입 히스토리**
  4. **점심시간** — 확인 모달 → 기수 전체를 LUNCH로
  5. **전체 퇴실** — 확인 모달 → 기수 전체를 OUT으로
- **범례(legend)**: 상태별 색 점 + 라벨 (모바일에선 접기/펼치기 토글)

### 좌석 배치도
- 상단 중앙에 **"강의실 스크린"** 표시 블록(다크 배경, 모니터 아이콘) — 교실 앞쪽 표시용.
- 흰 카드 안에 CSS Grid: `grid-template-columns: repeat(12, 1fr); grid-template-rows: repeat(11, 55px); gap: .7rem; max-width: 1200px`.
- 각 좌석은 `grid-row: r; grid-column: c` 인라인 스타일로 배치. 빈 칸은 그대로 비어 통로처럼 보임 (원본은 2인 1조 책상 형태: 2,3 / 5,6 / 8,9 / 11 열 사용).
- **좌석 카드**: 둥근 사각형, 상태색 그라데이션 배경, 흰 글씨 이름 + 우측 반투명 상태 아이콘, 타이머 대상 상태면 경과시간(mm:ss) 표시. 회의실에 있으면 회의실 뱃지.
- **호버**: 작은 툴팁 `이름 - 상태`.
- **클릭**: 화면 중앙 모달(배경 블러) 메뉴
  - 헤더: 학생 이름 + 닫기(X)
  - 2열 버튼 8개: 입실 / 퇴실 / 화장실 / 흡연 / 편의점 / 결석 / 지각 / 외출 (각 상태색 아웃라인 버튼)
  - 하단 "회의실" 섹션: 회의실에 없으면 [입장하기] → 회의실 1/2/3 선택 모달, 있으면 [퇴장] 버튼
  - ESC, 바깥 클릭, 스페이스바로 닫힘
- 특수 규칙
  - GIVEUP 좌석은 숨김, 메뉴 열지 않음
  - LUNCH 좌석 클릭 시 메뉴 없이 **즉시 IN으로 복귀**
- 상태 변경 시: 해당 카드 로딩 표시 → 성공하면 즉시 로컬 UI 반영(+펄스 애니메이션) → WebSocket으로 다른 클라이언트도 반영.

### 드래그 앤 드롭 자리 이동
- 좌석을 드래그하면 11×12 전체 빈 칸에 점선 드롭 가이드 표시, 마우스 위치 칸 하이라이트.
- 빈 칸에 드롭 → 이동 (`PUT /students/{id}` with name,rowIndex,colIndex)
- 다른 좌석 위에 드롭 → **"자리 교환" 확인 모달** → 확인 시 swap (`POST /students/swap` with seatId1, seatId2)
- 메뉴가 열려 있으면 드래그 금지.

### 가나다순 보기
- 배치도 아래에 "가나다순 정렬 보기 (N명)" 카드 섹션 추가 + 부드럽게 스크롤.
- 5열 카드 그리드: 이름 + 상태 뱃지(상태색). 결석은 빨간 테두리, 중도포기는 회색 처리(목록에는 표시).
- 카드 클릭 → 해당 좌석으로 스크롤 + 3초간 하이라이트 펄스.
- 실시간 업데이트 시 이 목록도 갱신.

### 회의실 현황 모달
- 회의실 1·2·3 카드(고정 3개). 각 카드: 이름, 인원 뱃지(`N명`), 학생 목록(각 학생 옆 [퇴장]) 또는 "비어있음".
- 하단 [전체 회의실 퇴장] → confirm → 기수 전체 meetingRoom=null.
- 회의실 입장은 좌석 status와 별개(상태는 유지, meetingRoom 필드만 변경).

### 출입 히스토리 모달
- 필터: 기수(전체/각 기수), 수강생명(텍스트), 시작일시·종료일시(`datetime-local`)
- 버튼: [검색] [초기화] [엑셀 내보내기] / 입력창에서 Enter = 검색
- 테이블: 번호 · 수강생명 · 기수 · 상태변경(상태 뱃지) · 변경시간(`2026. 09. 02. 오후 12:06:44` 형식), 최신순
- 페이징: 페이지당 10건, `이전 | 1 / N 페이지 (총 M건) | 다음`
- 엑셀: 현재 필터를 적용해 `.xlsx` 다운로드

### 하단 바
- `마지막 업데이트: 오전 9:11:54` (30초마다 갱신) · `자동 새로고침 활성화` 표시
- (문의 링크 등은 생략 가능)

### 클라이언트 주기 작업
- 1초마다 타이머 갱신, 10초마다 위험 상태 체크, 30초마다 마지막 업데이트 표시 갱신
- 탭이 다시 보이거나(visibilitychange) focus 시 `GET /api/seats/batch/{batchName}`로 전체 동기화. 위치가 바뀐 좌석이 있으면 새로고침.
- WebSocket 끊기면 자동 재연결.
- 토스트 메시지: 우측 상단, 로딩이 아니면 3초 후 자동 제거.

## 4. 화면 2 — 수강생 관리 (`/students`)

### 헤더
- 사람 아이콘 + "수강생 관리" · [← 좌석관리 시스템으로 돌아가기]

### 툴바
- **관리할 기수 선택** 드롭다운 + 빨간 휴지통 버튼(기수 삭제)
- 우측 버튼: [+ 기수 추가] (주황) · [수강생 추가] (초록) · [일괄 등록] (갈색)

### 수강생 목록
- 제목: `{기수명} 수강생 목록` + `총 N명` 뱃지
- 테이블(헤더 주황 그라데이션): 이름 · 위치(`1행 9열` 뱃지) · 현재 상태(셀렉트 — 상태 즉시 변경, LUNCH 제외 9개) · 상태 변경 시간(`MM/DD HH:mm`) · 관리([수정] [삭제] 아이콘 버튼)
- 이름 가나다순 정렬. WebSocket으로 실시간 반영.
- 상태를 **중도포기**로 바꾸면 confirm("좌석 위치가 자동으로 제거됩니다") → 위치 0,0으로. 취소 시 원래 값 복원.

### 모달
1. **수강생 추가**: 이름, 행(Row), 열(Column) → 추가
   - 행/열 1 이상, 최대값 이하. 입력 300ms 디바운스로 **실시간 중복 체크**("○○이(가) 배치되어 있습니다." 경고, 필드 빨강/초록 스타일)
2. **수강생 정보 수정**: 이름, 행, 열 (중도포기 학생은 위치 입력 비활성화 + "중도포기 (자동 설정)" 안내)
3. **수강생 삭제**: `confirm("○○ 수강생을 정말 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다.")`
4. **일괄 등록**: textarea
   - 방법 1 (자동 배치): `홍길동, 김철수, 이영희` 또는 줄바꿈 구분 → 1행 1열부터 빈자리에 자동 배치
   - 방법 2 (직접 지정): 줄마다 `홍길동, 1, 1`
   - 첫 줄이 `이름, 숫자, 숫자` 패턴이면 방법 2로 자동 감지
   - 최대 50명, 이미 사용 중인 위치는 건너뜀, 형식/범위 오류는 줄 번호와 함께 안내
5. **새 기수 추가**: 기수명(placeholder `예: 5기, 백엔드 1기, 프론트엔드 2기`), 2~50자, 중복 불가 → 성공 시 해당 기수로 이동
6. **기수 삭제 확인**: "정말로 'X' 기수를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다." — 수강생이 남아 있으면 삭제 버튼 비활성화 + "먼저 모든 수강생을 삭제해주세요."

## 5. API (원본 경로 기준, 요청 바디는 일부 추정)

| 메서드 | 경로 | 용도 | 바디/파라미터 |
|---|---|---|---|
| GET | `/` `?batchName=` | 메인 화면 | |
| GET | `/students?batchName=` | 수강생 관리 화면 | |
| GET | `/api/seats/batch/{batchName}` | 기수 좌석 전체 JSON | → `[{id,name,rowIndex,colIndex,status,statusChangedAt,meetingRoom}]` |
| POST | `/seats/status` | 상태 변경 | `{seatId, status}` (추정) |
| POST | `/seats/bulk-exit` | 전체 퇴실 | `{batchName}` |
| POST | `/seats/lunch-time` | 점심시간 일괄 | `{batchName}` |
| POST | `/students` | 수강생 추가 | `{name,batchName,rowIndex,colIndex}` |
| PUT | `/students/{id}` | 수정/자리 이동 | `{name,rowIndex,colIndex}` |
| DELETE | `/students/{id}` | 삭제 | |
| POST | `/students/swap` | 자리 교환 | `{seatId1, seatId2}` |
| POST | `/students/bulk` | 일괄 등록 | `{batchName, students:[{name,rowIndex,colIndex}]}` (추정) |
| POST | `/batches` | 기수 추가 | `{name}` |
| DELETE | `/batches/{id}` | 기수 삭제 | |
| GET | `/api/history` | 히스토리 | `batchName, studentName, startDate, endDate, page, size` → `{histories,totalPages,currentPage,totalElements,hasPrevious,hasNext}` |
| GET | `/api/history/export` | 엑셀 | 동일 필터 → xlsx |
| POST | `/api/meeting-room/enter` | 회의실 입장 | `{seatId, meetingRoom}` |
| POST | `/api/meeting-room/exit?seatId=` | 회의실 퇴장 | |
| POST | `/api/meeting-room/exit-all?batchName=` | 전체 회의실 퇴장 | |

### WebSocket
- 엔드포인트 `/ws` (SockJS) / STOMP 구독 `/topic/batch/{batchName}`
- 메시지: `{type?, seatId, seatName, rowIndex, colIndex, status, statusChangedAt, meetingRoom}`; 삭제 시 `DELETED` 표시
- 클라이언트 처리: 삭제 → 카드 제거 / 신규 좌석 → 새로고침 / 위치 변경 → 그리드 위치 이동 애니메이션 / 상태·이름·회의실 갱신

## 6. 디자인 토큰
- Primary `#FF6B00` (light `#FF8A3D`, dark `#E65C00`), secondary `#64748b`, success `#10b981`, warning `#f59e0b`, danger `#ef4444`, info `#06b6d4`
- 배경 화이트 카드 + 연회색 페이지, 큰 라운드(radius-xl), 부드러운 그림자, 버튼은 그라데이션 + 그림자
- 한글 UI, 아이콘은 Font Awesome 6