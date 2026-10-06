# 장애 원인 분석: a-connection-pool

대상 로그: `incident-logs/a-connection-pool/` (2026-09-16 하루치)

## 요약

- **언제**: 2026-09-16 14:37:42 ~ 14:51:49 KST(약 14분) 이상 구간. 사용자 영향은 14:40:52 ~ 14:51:47(약 11분)이며 5xx 186건(500 49 · 502 82 · 504 55)이 났다. 학급 리포트뿐 아니라 문항 · 단원 조회 API 도 실패했다.
- **원인**: 학급 리포트 API(`ReportService.buildClassReport`)가 DB 조회를 마친 뒤에도 트랜잭션(커넥션)을 쥔 채 SHA-256 20만 회 서명을 계산했다. 리포트 요청이 분당 0 ~ 2건에서 10 ~ 19건으로 몰리자 크기 5 인 커넥션 풀이 고갈됐다.
- **전파**: 커넥션을 3초 기다린 다른 요청이 500 으로 실패했다. 응답이 늦어진 인스턴스는 nginx 에서 제외됐고, 나머지 1대는 이미 연결을 거부하고 있어 504 · 502 로 번졌다.
- **회복**: 재기동 기록은 없다. 리포트 요청이 줄어들면서 저절로 회복됐다.
- **확신 수준: 중간**. 누수 스택 134건이 모두 리포트 경로였고 코드 구조도 일치한다. 다만 점유 시간이 늘어난 이유와 2번 인스턴스가 내려간 시점은 로그로 확인하지 못했다(9절).

## 1. 수집 범위

### 1.1 파일별 기록 기간 · 형식

| 파일 | 줄 수 | 기록 기간 (KST) | 시각 형식 | 타임존 |
|---|---:|---|---|---|
| `app.log` | 5,371 (타임스탬프 줄 1,927 + 스택 트레이스 등 연속 줄 3,444) | 09-16 06:43:56 ~ 15:29:51 | ISO-8601 `2026-09-16T06:43:56.753+09:00` | KST (`+09:00` 명시, 전 줄 동일) |
| `nginx-access.log` | 21,086 | 09-16 06:43:56 ~ 15:29:59 | `[16/Sep/2026:06:43:56 +0900]` | KST (`+0900` 명시, 전 줄 동일) |
| `nginx-error.log` | 168 | 09-16 09:12:03 ~ 14:51:47 | `2026/09/16 09:12:03` | 표기 없음 → **KST 로 판단** (아래 근거) |
| `mariadb-slow.log` | 21 (항목 3건) | 09-16 09:14:07 ~ 14:37:52 | `# Time: 260916  0:14:09` (YYMMDD H:MM:SS) + `SET timestamp=<epoch>` | **UTC** |

**KST 변환 규칙**

- `app.log` · `nginx-access.log`: 이미 KST. 변환하지 않는다.
- `nginx-error.log`: 표기 그대로 KST 로 읽는다. 근거
  - 같은 요청이 같은 초에 찍힌다. `nginx-error.log:6` 14:41:06 upstream timed out (client 교사단말A, `/api/classes/3/report`) ↔ `nginx-access.log` 의 첫 504 도 14:41:06 · 같은 클라이언트 · 같은 경로. 마지막 건도 `nginx-error.log:168` ↔ `nginx-access.log:19381` 14:51:47 로 일치한다.
  - 건수도 맞는다. access 504 55건 = error `upstream timed out` 55건, access 502 82건 = error `no live upstreams` 68건 + `connect() failed (111: Connection refused)` 14건, access 500 49건 = app `unhandled exception` ERROR 49건.
- `mariadb-slow.log`: 두 값을 따로 변환한다.
  - `# Time:` 은 UTC 의 **쿼리 종료 시각**이다. +9시간 하면 KST 종료 시각.
  - `SET timestamp=` 는 epoch 의 **쿼리 시작 시각**이다. `TZ=Asia/Seoul date -d @<epoch>` 로 KST 시작 시각을 구한다.
  - 검증: 1789537062 → 05:37:42 UTC + Query_time 9.81초 = 05:37:52 = `# Time: 260916  5:37:52` (`mariadb-slow.log:15,18,20`).

| slow log 항목 | `# Time` (UTC) | KST 시작 ~ 종료 | Query_time |
|---|---|---|---:|
| `mariadb-slow.log:1` | 00:14:09 | 09:14:07 ~ 09:14:09 | 2.32초 |
| `mariadb-slow.log:8` | 02:02:42 | 11:02:40 ~ 11:02:42 | 2.04초 |
| `mariadb-slow.log:15` | 05:37:52 | 14:37:42 ~ 14:37:52 | 9.81초 |

### 1.2 분 단위 이상 신호 건수

세는 대상

- `app.log`: 타임스탬프가 있는 `WARN` · `ERROR` 줄. 스택 트레이스 연속 줄은 세지 않는다.
- `nginx-access.log`: 상태 코드 5xx.
- `nginx-error.log`: `[error]` · `[warn]` 줄.
- `mariadb-slow.log`: 항목 수.

**평소 수준 (14:37 이전 · 14:52 이후)**

| 파일 | 평소 수준 |
|---|---|
| `app.log` | `WARN` 은 `GlobalExceptionHandler` 의 `state conflict`(409) 만 하루 20건, 08:41 ~ 14:52 에 분당 0 ~ 1건. `ERROR` 0건. 그 밖의 `WARN` 0건. |
| `nginx-access.log` | 5xx 0건. 요청은 평균 분당 40건(최대 68). 4xx(404 · 409 · 400)는 시간당 100 ~ 170건으로 하루 내내 비슷하다. |
| `nginx-error.log` | 14:41 이전 5줄뿐: `[warn] ... buffered to a temporary file` 2건(09:12:03, 13:05:51), `[info]` 3건(10:42, 11:47, 13:18). |
| `mariadb-slow.log` | 09:14, 11:02 에 각 1건(2초대). |

**이상 구간 분 단위 건수 (KST)**

app.log 의 열: `Leak` = `ProxyLeakTask` WARN "Connection leak detection triggered", `Pool` = `SqlExceptionHelper` ERROR "Connection is not available, request timed out after 3000ms", `SQL` = `SqlExceptionHelper` WARN "SQL Error: 0, SQLState: null", `Unh` = `GlobalExceptionHandler` ERROR "unhandled exception". nginx-error.log 의 열: `TO` = upstream timed out, `NoLive` = no live upstreams, `Ref` = connect() failed (Connection refused), `Dis` = upstream server temporarily disabled.

| 분 | app Leak | app Pool | app SQL | app Unh | access 500 | access 502 | access 504 | access 요청 수 | err TO | err NoLive | err Ref | err Dis | slow |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 14:36 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 41 | 0 | 0 | 0 | 0 | 0 |
| 14:37 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 50 | 0 | 0 | 0 | 0 | 1 |
| 14:38 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 41 | 0 | 0 | 0 | 0 | 0 |
| 14:39 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 42 | 0 | 0 | 0 | 0 | 0 |
| 14:40 | 14 | 1 | 1 | 1 | 1 | 0 | 0 | 42 | 0 | 0 | 0 | 0 | 0 |
| 14:41 | 13 | 6 | 6 | 6 | 6 | 0 | 3 | 42 | 3 | 0 | 0 | 0 | 0 |
| 14:42 | 10 | 2 | 2 | 2 | 2 | 13 | 5 | 41 | 5 | 11 | 2 | 2 | 0 |
| 14:43 | 14 | 10 | 10 | 10 | 10 | 2 | 5 | 58 | 5 | 1 | 1 | 1 | 0 |
| 14:44 | 11 | 2 | 2 | 2 | 2 | 5 | 6 | 34 | 6 | 4 | 1 | 4 | 0 |
| 14:45 | 9 | 8 | 8 | 8 | 8 | 14 | 5 | 39 | 5 | 12 | 2 | 3 | 0 |
| 14:46 | 12 | 4 | 4 | 4 | 4 | 16 | 6 | 51 | 6 | 14 | 2 | 3 | 0 |
| 14:47 | 9 | 5 | 5 | 5 | 5 | 6 | 3 | 38 | 3 | 5 | 1 | 1 | 0 |
| 14:48 | 10 | 4 | 4 | 4 | 4 | 6 | 5 | 42 | 5 | 5 | 1 | 4 | 0 |
| 14:49 | 12 | 7 | 7 | 7 | 7 | 8 | 8 | 42 | 8 | 6 | 2 | 4 | 0 |
| 14:50 | 11 | 0 | 0 | 0 | 0 | 11 | 7 | 34 | 7 | 9 | 2 | 4 | 0 |
| 14:51 | 3 | 0 | 0 | 0 | 0 | 1 | 2 | 46 | 2 | 1 | 0 | 0 | 0 |
| 14:52 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 47 | 0 | 0 | 0 | 0 | 0 |
| **합계** | 134 | 49 | 49 | 49 | 49 | 82 | 55 | | 55 | 68 | 14 | 26 | |

- 표의 신호 열은 14:36 이전 · 14:52 이후 모두 0 이다(평소 수준 표의 항목 제외).
- `app.log` 의 분당 타임스탬프 줄 수: 14:30 ~ 14:37 은 1 ~ 6줄, 14:40 ~ 14:50 은 33 ~ 80줄, 14:52 이후 다시 1 ~ 6줄.
- 같은 구간의 `state conflict`(409) WARN 은 14:42, 14:50, 14:51, 14:52 에 각 1건으로 평소와 같은 빈도라 이상 신호에서 뺐다.
- 요청 수는 이상 구간에도 분당 34 ~ 58건으로 평소(평균 40)와 비슷하다.

**처음 나타난 순서 (근거 줄)**

| KST | 신호 | 근거 |
|---|---|---|
| 14:37:42 ~ 14:37:52 | slow query 9.81초 (`submission`, Rows_examined 1,284,310) | `mariadb-slow.log:15-21` |
| 14:37:52 | 첫 `ProxyLeakTask` WARN | `app.log:1243` |
| 14:40:52 | 첫 `Connection is not available` ERROR · 첫 `unhandled exception` · 첫 access 500 | `app.log:1522`, `app.log:1523`, `nginx-access.log:18918` |
| 14:41:06 | 첫 504 / upstream timed out | `nginx-error.log:6` |
| 14:42:01 | 첫 502 / connect() failed (Connection refused) | `nginx-error.log:11` |
| 14:49:51 | 마지막 `Connection is not available` ERROR · 마지막 `unhandled exception` | `app.log:4980`, `app.log:4981` |
| 14:51:37 | 마지막 `ProxyLeakTask` WARN | `app.log:5251` |
| 14:51:47 | 마지막 5xx(504) / 마지막 upstream timed out | `nginx-access.log:19381`, `nginx-error.log:168` |
| 14:51:49 | 마지막 `ProxyLeakTask` INFO "Previously reported leaked connection ..." | `app.log:5267` |

**제안: 이상 구간 = 14:37:42 ~ 14:51:49 KST**

- 시작 14:37:42: 평소와 다른 첫 기록(9.81초 slow query 시작). 이어서 14:37:52 부터 `ProxyLeakTask` WARN 이 나온다. 사용자에게 보이는 오류(5xx)는 14:40:52 부터다.
- 끝 14:51:49: 이상 신호 계열의 마지막 줄. 14:52 이후 `app.log` · `nginx-access.log` 의 신호는 0 이고 줄 수도 평소로 돌아간다.
- 주의: `nginx-error.log`(14:51:47 에 끝남) 와 `mariadb-slow.log`(14:37:52 에 끝남) 는 파일 자체가 구간 안 · 경계에서 끝난다. 정상 복귀는 이 두 파일로는 확인할 수 없고 `app.log` · `nginx-access.log` 로만 확인된다.

### 1.3 분석 범위 (여유 포함)

**제안: 14:07 ~ 15:02 KST (앞 30분 · 뒤 10분)**

| 구분 | 시각 | 이유 |
|---|---|---|
| 앞 여유 30분 | 14:07 ~ 14:37 | 첫 5xx 보다 약 3분 앞서 전조 신호(slow query, leak WARN)가 있었다. 이 네 가지 신호에 드러나지 않는 더 이른 변화(요청 패턴 · 배포 · 배치 등)를 잡을 수 있게 넉넉히 둔다. |
| 이상 구간 | 14:37:42 ~ 14:51:49 | 1.2 절 |
| 뒤 여유 10분 | 14:52 ~ 15:02 | 신호가 0 으로 돌아간 뒤 재발이 없는지와 평소 수준 복귀를 확인한다. |

- 범위 밖이지만 참고로 볼 기록: `mariadb-slow.log` 의 앞선 두 항목(09:14, 11:02), `nginx-error.log` 의 13:05:51 `buffered` warn · 13:18 info. `mariadb-slow.log` 는 21줄뿐이라 범위와 관계없이 전체를 본다.
- 14:07:00 ~ 15:02:59 에 해당하는 줄 번호: `app.log` 1135 ~ 5288, `nginx-access.log` 17405 ~ 19842, `nginx-error.log` 6 ~ 168, `mariadb-slow.log` 15 ~ 21(전체 1 ~ 21).

## 2. 타임라인

대상: 2026-09-16 14:07:00 ~ 15:02:59 KST (1.3 절 범위). 모든 시각은 KST 다(1.1 절 변환 규칙 적용).

- 출처의 파일명은 줄여 쓴다: `app` = `app.log`, `access` = `nginx-access.log`, `error` = `nginx-error.log`, `slow` = `mariadb-slow.log`.
- 시각 정밀도가 파일마다 다르다. `app` 은 밀리초, `access` · `error` · `slow` 는 초 단위다. 그래서 같은 초 안의 `app` 줄과 다른 파일 줄은 선후를 정할 수 없다. 그런 곳에 **(선후 불확실)** 을 붙였다.
- 원문 발췌의 학생 식별자는 등장 순서대로 학생A, 학생B … 로 바꿨다.
- 범위 안에서 설정 재적재 · 기동 · 종료 기록은 찾지 못했다. 찾아본 파일은 `app`(Started · Shutting · HikariPool Start/Shutdown 없음)과 `error`(reload · signal 없음)다.
- 가장 많이 나온 오류는 `error` 의 `no live upstreams` 68건이다. 같은 요청이 `access` 에는 502 로 찍혔다(502 82건 = no live 68 + refused 14). 첫 발생은 14:42:04 다. 그보다 앞선 줄 중에서 평소와 다른 것은 아래 표의 14:37:42 ~ 14:42:01 에 모두 넣었다.

### 2.1 앞 여유 구간 (14:07 ~ 14:37:41)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:10:05 ~ 14:18:13 | `app:1148`, `app:1161`, `app:1167`, `app:1174`, `app:1175` | 학급 리포트 생성 5건(9분간). 그 뒤 14:19 ~ 14:37 에는 리포트 요청이 `access` 에 0건이다 | `built class report for class 3: 3 distributions, 10 submissions` |
| 14:11:38 | `app:1154` | 재배포 거절 WARN(409). 14:14:03 `app:1165`, 14:33:45 `app:1229` 도 같은 메시지다. 하루 내내 분당 0 ~ 1건 나오는 평소 빈도와 같다 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| 14:19:38 | `app:1182` | 재배포 1건 | `redistributed distribution 7 (assignment 5, class 3) reason=학생A 결석으로 재배포` |
| 14:07 ~ 14:37 | `access` | 요청 수 분당 32 ~ 56건으로 평소(평균 40)와 비슷하다. 5xx 는 0건이다 | — |
| 14:07 ~ 14:37 | `error`, `slow` | 기록 없음 | — |

### 2.2 이상 구간 (14:37:42 ~ 14:51:49)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:37:42 | `slow:20` | 느린 쿼리 시작(epoch 1789537062 를 KST 로 바꾼 값) | `SET timestamp=1789537062;` |
| 14:37:52 | `slow:15`, `slow:18`, `slow:21` | 느린 쿼리 종료. 9.81초 걸렸고 130만 행을 읽었다. `submission` 을 `distribution_id=5` 로 조회한 쿼리다. **(선후 불확실: 바로 아래 `app:1243` 과 같은 초)** | `Query_time: 9.812417  Lock_time: 0.000071  Rows_sent: 4  Rows_examined: 1284310` |
| 14:37:52.199 | `app:1243`, `app:1253-1254` | 첫 커넥션 누수 감지 WARN(커넥션 `6e8f14d3`, 스레드 exec-6). 스택에 `ReportService.buildClassReport` 와 `ReportController.classReport` 가 찍혀 있다 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3` |
| 14:38:01 | `access:18801` | 14:19 이후 첫 학급 리포트 요청(클라이언트 교사단말A, 200). **(선후 불확실: 아래 `app:1258-1259` 과 같은 초)** | `"GET /api/classes/3/report HTTP/1.1" 200 266` |
| 14:38:01.900 ~ .902 | `app:1258`, `app:1259` | exec-6 의 리포트 생성이 끝났다. 커넥션 `6e8f14d3` 이 풀에 반환됐다는 INFO 가 이어진다 | `Previously reported leaked connection ...@6e8f14d3 ... was returned` |
| 14:38:01 ~ 14:39:55 | `access:18801`, `18815`, `18839`, `18862`, `18882` | 교사단말A 가 `/api/classes/3/report` 를 약 30초 간격으로 5번 요청했다(모두 200) | `"GET /api/classes/3/report HTTP/1.1" 200` |
| 14:38:29 | `access:18817`, `app:1278` | 배포 5 재배포 요청(교사단말A)과 처리 로그. **(선후 불확실: 두 파일이 같은 초)** | `POST /api/distributions/5/redistribute` / `redistributed distribution 5 (assignment 3, class 3) reason=null` |
| 14:38:51.641 | `app:1280` | 두 번째 커넥션(`2ac19f07`)의 누수 감지 WARN | `Connection leak detection triggered for org.mariadb.jdbc.Connection@2ac19f07` |
| 14:39:50.132 | `app:1331` | 세 번째 커넥션(`71d4b8e2`)의 누수 감지 WARN | `Connection leak detection triggered for org.mariadb.jdbc.Connection@71d4b8e2` |
| 14:39:54 | `access:18881` | 두 번째 클라이언트(교사단말B)의 리포트 요청. 14:40:27 에 교사단말C(`access:18904`), 14:40:43 에 교사단말D(`access:18912`)이 이어진다 | `"GET /api/classes/1/report HTTP/1.1" 200 271` |
| 14:40:04.730 | `app:1351` | 네 번째 커넥션(`3f6c2a91`)의 누수 감지 WARN | `Connection leak detection triggered for org.mariadb.jdbc.Connection@3f6c2a91` |
| 14:40:12.901 | `app:1370` | 다섯 번째 커넥션(`5b0e9e0c`)의 누수 감지 WARN. 이 뒤로 누수 감지 WARN 이 분당 9 ~ 14건 이어진다(합계 134건, 대상 커넥션 5개) | `Connection leak detection triggered for org.mariadb.jdbc.Connection@5b0e9e0c` |
| 14:40 ~ 14:50 | `access` | 리포트 요청이 분당 10 ~ 19건이다. 평소에는 시간당 2 ~ 11건이다. 전체 요청 수는 분당 34 ~ 58건으로 평소와 비슷하다 | — |
| 14:40:52.319 | `app:1522` | 첫 커넥션 획득 타임아웃 ERROR. 이후 49건, 분당 1 ~ 10건이다. 메시지의 waiting 값은 1 ~ 7 사이이고 최댓값 7 은 14:45:06 `app:3249` 에 처음 나온다 | `request timed out after 3000ms (total=5, active=5, idle=0, waiting=4)` |
| 14:40:52 | `app:1523`, `access:18918` | 첫 unhandled exception ERROR 와 첫 500. 이후 각 49건이다. **(선후 불확실: 두 파일이 같은 초)** | `unhandled exception on /api/units/M6-2/items` / `"GET /api/units/M6-2/items HTTP/1.1" 500` |
| 14:41:06 | `error:6`, `access:18933` | 첫 upstream timed out(upstream API-1:8080)과 첫 504(`/api/classes/3/report`). 이후 각 55건, 분당 2 ~ 8건이다. **(선후 불확실: 두 파일이 같은 초)** | `upstream timed out (110: Connection timed out) while reading response header` |
| 14:41:27.252 | `app:1864` | 리포트 경로의 첫 unhandled exception. 이 경로는 합계 5건이다(`app:2433`, `2527`, `2836`, `4361`) | `unhandled exception on /api/classes/N/report` |
| 14:42:01 | `error:10` | API-1:8080 이 일시 제외됐다는 첫 기록. 이후 26건, 분당 1 ~ 4건이다. **(선후 불확실: 아래 `error:11` · `access:18970` 과 같은 초)** | `upstream server temporarily disabled while reading response header from upstream` |
| 14:42:01 | `error:11`, `access:18970` | API-2:8080 연결 거부와 첫 502. 연결 거부는 이후 14건이다. **(선후 불확실)** | `connect() failed (111: Connection refused) while connecting to upstream` |
| 14:42:04 | `error:12` | 첫 `no live upstreams`(upstream 그룹 `item_bank_api`). 이후 68건, 분당 1 ~ 14건이다 | `no live upstreams while connecting to upstream` |
| 14:49:51.552 ~ .553 | `app:4980`, `app:4981` | 마지막 커넥션 획득 타임아웃과 마지막 unhandled exception. 14:50 ~ 14:51 에는 `app` ERROR 가 0건이고, `access` 의 502 · 504 는 14:50 11건 · 7건, 14:51 1건 · 2건이다 | `Connection is not available ... (total=5, active=5, idle=0, waiting=3)` |
| 14:51:37.674 | `app:5251` | 마지막 누수 감지 WARN(커넥션 `6e8f14d3`, 스레드 exec-10) | `leak detection triggered for ...@6e8f14d3 on thread http-nio-8080-exec-10` |
| 14:51:47 | `error:168`, `access:19381` | 마지막 upstream timed out 과 마지막 504(교사단말C, `/api/classes/3/report`). `error` 파일은 이 줄에서 끝난다. **(선후 불확실: 두 파일이 같은 초)** | `"GET /api/classes/3/report HTTP/1.1" 504 167` |
| 14:51:49.148 | `app:5267` | 커넥션 `6e8f14d3` 반환 INFO. 누수 관련 계열의 마지막 줄이다 | `Previously reported leaked connection ...@6e8f14d3 ... was returned to the pool` |

### 2.3 뒤 여유 구간 (14:51:50 ~ 15:02:59)

| 시각 | 출처 | 사건 요약 | 원문 발췌 |
|---|---|---|---|
| 14:51:52, 14:52:09 | `app:5268`, `app:5269` | 재배포 거절 WARN(409) 2건. 평소 빈도와 같은 메시지다 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=2` |
| 14:52 ~ 15:02 | `app`, `access` | 누수 감지 · 타임아웃 · 5xx 모두 0건. `app` 은 분당 0 ~ 3줄, `access` 는 분당 35 ~ 47건으로 평소 수준이다 | — |
| 14:59:15 ~ 15:01:20 | `access:19701`, `19724`, `19781` | 리포트 요청 3건(모두 200) | `"GET /api/classes/3/report HTTP/1.1" 200` |
| 14:52 ~ 15:02 | `error`, `slow` | 기록 없음. 두 파일 모두 이 구간 전에 끝났다(1.2 절) | — |

## 3. 가설과 검증

- 판정은 `incident-logs/a-connection-pool/` 의 로그만으로 확인한 결과다. 로그 밖(운영 DB · nginx 설정 · 배포 이력 · 다른 날짜 로그)의 확인 방법은 실행하지 않았다. 실행한 명령과 출력은 3.1 절에 있다.
- "유지"는 이 로그에서 반증 조건이 나오지 않았다는 뜻이다. 원인으로 확정됐다는 뜻이 아니다.
- 증상과 원인을 구분한다. 가장 많이 나온 `no live upstreams`(68건) · `Connection is not available ... timed out after 3000ms`(49건) · 502 · 504 는 **증상**이다. 각 가설은 이 증상이 **왜** 생겼는지를 설명해야 한다.
- 근거 줄은 이 문서(`a-connection-pool.md`)의 줄 번호다. 로그 경로 `incident-logs/a-connection-pool/` 은 `$L` 로 줄여 쓴다.
- `nginx-access.log` 에는 upstream 주소와 응답 시간(`$upstream_addr` · `$request_time`) 필드가 없다(`nginx-access.log:18801`). 그래서 요청별 처리 시간과 처리 인스턴스는 `app.log` · `nginx-error.log` 로만 추정할 수 있다.

| # | 계층 | 가설 | 지지 근거 | 반증 조건 | 확인 방법 | 로그 확인 결과 (3.1 절) | 판정 |
|---|---|---|---|---|---|---|---|
| H1 | 애플리케이션 코드 | 학급 리포트 생성이 DB 작업을 마친 뒤에도 트랜잭션(커넥션)을 쥔 채 CPU 작업(서명 계산)을 해서, 리포트 요청이 몇 건만 겹쳐도 크기 5 의 풀이 고갈됐다. | `a-connection-pool.md:148`(누수 감지 스택이 `ReportService.buildClassReport`), `:150`(약 20초 뒤 반환), `:157`(누수 대상 커넥션 5개 = 풀 크기), `:159`(`total=5, active=5, idle=0`), `:162`(리포트 경로도 실패) | ① 누수 감지 134건의 스택에서 `ReportService` 가 아닌 경로가 대부분이다. ② 서명 계산(`sign`)이 수십 ms 수준이라 커넥션 점유 시간에 의미 있게 기여하지 않는다. ③ 14:10 ~ 14:18 리포트 5건(`:136`)과 이상 구간 리포트의 처리 시간이 같다(즉 "오래 쥐는 코드"만으로는 14:37 부터의 변화가 설명되지 않는다). | ① `grep -n -A12 'leak detection triggered' $L/app.log \| grep -oE 'com\.example\.[a-z]+\.[A-Za-z]+(Controller\|Service)[^(]*' \| sort \| uniq -c` ② `modern/api/src/main/java/com/example/assignment/ReportService.java:41-71` 에서 `sign()` 이 `@Transactional` 안에 있는지 보고, `jshell` 로 SHA-256 200,000회(`SIGNATURE_ROUNDS`, `:23`) 반복 시간을 잰다. ③ `app.log` 에서 같은 스레드의 리포트 요청 시작(누수 WARN 시각 − 10초, `leak-detection-threshold: 10000`)과 `built class report` 시각의 차이를 이상 구간 전후로 비교한다. | | 3.1-a: 누수 감지 134건의 스택이 134건 모두 `ReportService.buildClassReport` ← `ReportController.classReport` 다. 3.1-b: 이상 구간 커넥션 점유 시간(반환 시각 − (WARN 시각 − 10초))은 134쌍, 최소 15.5초 · 중앙값 18.9초 · 최대 21.5초다. 이상 구간 이전 리포트 50건 · 이후 6건에서는 누수 WARN 이 0건이다(점유 10초 미만). ② `sign()` 시간 측정은 로그가 아니라 실행하지 않았다. | **유지** — 반증 ①이 성립하지 않는다(다른 경로 0건). 반증 ③도 성립하지 않는다(점유 시간이 평소 10초 미만에서 15.5 ~ 21.5초로 바뀌었다). 다만 같은 코드에서 점유 시간이 왜 늘었는지는 H1 만으로 설명되지 않는다. |
| H2 | DB · 쿼리 | `submission` 을 `distribution_id` 로 읽는 쿼리가 인덱스를 타지 않고 130만 행을 훑어(9.81초) 리포트 트랜잭션의 커넥션 점유 시간을 크게 늘렸다. | `a-connection-pool.md:146-147`(14:37:42 시작, 9.81초, Rows_examined 1,284,310, Rows_sent 4), `:93`(첫 전조 신호), `:39`(같은 날 앞선 slow 항목은 2초대) | ① 운영 DB 에서 `EXPLAIN` 결과가 `idx_submission_distribution` 을 쓰고 rows 가 수백 행 이하다. ② `long_query_time` 이 2초 이하인데 이상 구간(14:40 ~ 14:50) 동안 slow 항목이 더 없다(리포트 요청이 분당 10 ~ 19건 있었는데도). 단, slow log 파일이 14:37:52 에서 끝나므로(`:107`) 파일이 잘렸는지 먼저 봐야 한다. ③ 9.81초 쿼리의 원인이 행 수가 아니라 락 대기다(`Lock_time` 이 크다). | ① 운영 DB(읽기 전용 계정)에서 `EXPLAIN select ... from submission where distribution_id=5 order by student_id;` · `SHOW INDEX FROM submission;` · `SELECT COUNT(*) FROM submission;`. 로컬 스키마는 `db/mariadb/init/01-schema.sql:112` 에 인덱스가 있으니 운영과 다른지 비교한다. ② `SHOW VARIABLES LIKE 'long_query_time';`, slow log 원본 파일의 회전 · 크기 확인. ③ `sed -n 15,21p $L/mariadb-slow.log` 의 `Lock_time`. | | 3.1-c: 9.81초 쿼리의 `Lock_time` 은 0.000071초다. slow log 는 하루 3건이고 이상 구간 안에는 이 1건뿐이다. 같은 날 2.04초 쿼리가 기록됐으므로 기록 임계값은 2.04초 이하로 보인다. 그런데 3.1-b 의 점유 시간 15.5 ~ 21.5초인 리포트 133건은 slow log 에 남지 않았다. | **판단 불가(로그 부족)** — 반증 ③은 성립하지 않는다(락 대기 아님). 반증 ②는 "slow log 가 끝까지 정상 기록됐다"는 전제가 확인되지 않아 판정할 수 없다. 필요한 로그: 같은 시간대의 slow log 회전 · 설정(`long_query_time`, `log_slow_rate_limit`) 기록, 또는 앱의 쿼리별 실행 시간 로그(Hibernate statistics · `p6spy` 등). |
| H3 | 트래픽 | 전체 요청 수는 평소와 같지만, 무거운 리포트 요청의 비중이 평소(시간당 2 ~ 11건)의 수십 배(분당 10 ~ 19건)로 몰려 동시 실행 수가 풀 크기를 넘었다. | `a-connection-pool.md:151`(교사단말A 가 약 30초 간격으로 반복 요청), `:155`(14:39:54 ~ 14:40:43 에 클라이언트 3개 합류), `:158`(리포트 분당 10 ~ 19건), `:87`(전체 요청 수는 평소 수준) | ① 리포트 요청 급증이 첫 5xx(14:40:52) **이후**에만 나타난다(장애 때문에 생긴 재시도라 원인이 아니라 결과다). ② 다른 날 · 다른 시간대에 같은 비율(분당 10건 이상)의 리포트 요청이 있었는데 장애가 없었다. ③ 누수 감지 · 풀 고갈이 리포트 요청 증가보다 먼저 시작돼 이미 진행 중이었다. | ① `grep '/report' $L/nginx-access.log \| awk '{print substr($4,14,5), $1, $9}' \| sort \| uniq -c` 로 분 · 클라이언트 · 상태별 건수를 뽑아 첫 5xx 시각과 비교한다. ② 다른 날짜의 access log(이번 수집 범위 밖, 확인 필요). ③ 위 결과를 `a-connection-pool.md:148-159` 의 누수 WARN 시각과 나란히 놓는다. 교사단말A 의 30초 주기가 화면 자동 새로고침인지는 `modern/web/src` 와 화면 배포 이력에서 확인한다. | | 3.1-d: 리포트 요청은 14시 이전에 시간당 2 ~ 11건, 분당 최대 2건이다. 14:38 · 14:39 에 각 3건, 14:40 에 10건, 14:41 ~ 14:50 에 분당 10 ~ 19건(14시 합계 179건)이다. 첫 5xx(14:40:52) 이전 14:37:42 ~ 14:40:51 에 리포트 요청은 15건이고 모두 200 이다. 클라이언트는 4개(교사단말B · .12 · .17 · .23)다. | **유지** — 반증 ①이 성립하지 않는다(증가가 첫 5xx 보다 먼저 시작). 반증 ③도 성립하지 않는다(첫 풀 고갈 ERROR 14:40:52 는 분당 10건으로 늘어난 뒤). 반증 ②는 다른 날짜 로그가 없어 확인하지 못했다. |
| H4 | 인프라 (로드밸런서 · 인스턴스) | API 인스턴스 2대 중 API-2 가 이미 내려가 있어(연결 거부) API-1 한 대가 모든 요청을 받았고, nginx 가 그 한 대마저 응답 지연으로 제외하자 `no live upstreams` 로 전체 요청이 실패했다. | `a-connection-pool.md:161`(504 upstream 은 모두 API-1), `:163`(14:42:01 API-1 일시 제외), `:164`(같은 초 API-2 연결 거부), `:165`(14:42:04 `no live upstreams`), `:29`(502 82건 = no live 68 + refused 14) | ① API-2 가 장애 전에도 정상 응답하고 있었다(14:42 직전에 내려갔다면 원인이 아니라 같은 원인의 다른 결과일 수 있다). ② API-2 가 nginx 설정에서 `backup` 이거나 원래 운영에서 빠진 서버라 평소에도 한 대 구성이었다. ③ `no live upstreams` 가 나오기 전에 이미 앱 쪽 풀 고갈(14:40:52)이 시작돼 있었다는 점은 확인됐으므로(`:159`), 한 대가 빠졌다는 사실이 고갈 시작 시각에 영향을 주지 않았다면 이 가설은 증상 증폭 요인에 그친다. | ① `grep -n 'API-2' $L/nginx-error.log`(현재 첫 기록은 `nginx-error.log:11`, 14:42:01), API-2 의 `app.log` · 프로세스 기동 기록(이번 수집 범위 밖, 확인 필요). ② nginx `upstream item_bank_api` 블록의 `server` · `max_fails` · `fail_timeout` · `backup` 과 `proxy_read_timeout`(설정 파일은 저장소에 없음, 확인 필요). ③ 14:40 ~ 14:42 의 `app:1522` 이후 Pool ERROR 와 `error:10-12` 순서 비교. | | 3.1-e: `nginx-error.log` 에서 API-2 가 나오는 줄은 14건이고 모두 `connect() failed (Connection refused)` 다. 첫 줄은 14:42:01(`nginx-error.log:11`)이다. API-1 은 83줄(timed out 55 · disabled 26 · 09:12 · 13:05 buffered 2)이다. 첫 풀 고갈 ERROR 는 14:40:52(`app.log:1522`)이고, 첫 `no live upstreams` 는 14:42:04 다. 14:40:52 이전 5xx 는 0건이다. | **판단 불가(로그 부족)** — API-2 가 14:42 이전에 살아 있었는지, 평소 요청을 받았는지 알 수 없다. API-2 에 대해서는 실패 기록만 남기 때문이다. 필요한 로그: `$upstream_addr` 가 들어간 access log, API-2 의 `app.log` · 프로세스 기동/종료 기록, nginx upstream 설정(`backup` · `max_fails`). 단, 풀 고갈이 `no live upstreams` 보다 72초 먼저 시작됐다. |
| H5 | 배포 · 설정 변경 | 장애 이전 배포에서 바뀐 설정이나 코드(풀 크기 · `connection-timeout` 축소, `SIGNATURE_ROUNDS` 증가, 리포트 화면 자동 새로고침 추가 등)가 리포트 요청을 견디지 못하게 만들었다. | `a-connection-pool.md:159`(풀 `total=5`, 대기 3000ms), `:151`(사람 손으로 보기 어려운 일정한 30초 주기 요청), `:136`(같은 날 오전 리포트는 문제가 없었다) | ① 09-16 이전 수일간 `application.yml` 의 `hikari` 값, `ReportService`, 화면 코드에 변경 · 배포가 없다. ② 같은 설정 · 같은 30초 주기 요청이 이전 날짜에도 있었는데 장애가 없었다. ③ 범위 안에 재기동 · 설정 재적재가 없었다는 기록(`:129`)대로라면 변경 시점은 09-16 06:43 이전이어야 하며, 그 이후 변경 흔적이 나오면 이 가설의 시점 설명이 틀린다. | ① `git log --since=2026-09-01 --stat -- modern/api/src/main/resources/application.yml modern/api/src/main/java/com/example/assignment/ modern/web/src/` 와 운영 배포 이력(확인 필요). ② 이전 날짜 access log 에서 교사단말A 의 `/report` 요청 간격(확인 필요). ③ `grep -n -E 'Started\|Tomcat started\|Shutting' $L/app.log`(현재 0건). | | 3.1-f: 이번 `app.log` 에는 기동 · 종료 줄(`Started` · `Tomcat started` · `Shutting down` · `HikariPool ... Start completed`)이 0건이다. 파일은 06:43:56 에 이미 요청 처리 중인 상태로 시작한다. Hikari 설정 값이 찍힌 줄도 0건이다. 풀 크기는 ERROR 메시지의 `total=5` 로만 알 수 있다. 그 밖의 정보는 3.1-d 의 리포트 요청 분포뿐이다. | **판단 불가(로그 부족)** — 로그가 09-16 하루치이고 기동 이후 부분만 있어서 변경 시점을 볼 수 없다. 필요한 로그: 09-16 이전 배포 · 재기동 기록과 그때의 기동 로그(Hikari 설정 출력 포함), 이전 날짜 `nginx-access.log`(교사단말A 의 리포트 요청 주기 비교). |

### 3.1 로그 확인 실행 기록

저장소 루트에서 `L=incident-logs/a-connection-pool` 로 두고 실행했다. 출력은 줄여 쓰지 않고 숫자를 그대로 옮겼다(긴 목록만 범위로 줄였다).

**3.1-a 누수 감지 스택의 호출 경로 (H1 ①)**

```bash
grep -n -A12 'leak detection triggered' $L/app.log \
  | grep -oE 'com\.example\.[a-z]+\.[A-Za-z]+(Controller|Service)[^(]*' | sort | uniq -c
```
```
    134 com.example.assignment.ReportController.classReport
    134 com.example.assignment.ReportService$$SpringCGLIB$$0.buildClassReport
```

**3.1-b 커넥션 점유 시간과 이상 구간 전후 비교 (H1 ③)**

```bash
grep 'leak detection triggered' $L/app.log | awk '$1<"2026-09-16T14:37"' | wc -l        # 0
grep -c 'was returned to the pool' $L/app.log                                           # 134
grep -oE 'triggered for \S+' $L/app.log | sort | uniq -c                                # 커넥션 5개
grep 'built class report' $L/app.log | awk '{t=substr($1,12,8); if(t<"14:37:42")a++; else if(t<="14:51:49")b++; else c++}
  END{print "before",a,"in",b,"after",c}'                                               # before 50 in 134 after 6
# 같은 (커넥션, 스레드)의 WARN 과 "was returned" 를 짝지어 점유 시간 = 반환 − (WARN − 10초, leak-detection-threshold)
grep -E 'leak detection triggered|was returned to the pool' $L/app.log | python3 -I -c '<짝짓기 스크립트>'
```
```
누수 WARN (14:37 이전): 0
반환 INFO: 134
커넥션별 누수 WARN: 2ac19f07 29, 3f6c2a91 26, 5b0e9e0c 26, 6e8f14d3 29, 71d4b8e2 24
리포트 생성 완료: before 50, in 134, after 6
pairs 134 unpaired 0
min 15.5 p50 18.9 p90 21.0 max 21.5   (초)
```

풀 고갈 ERROR 49건의 대기 수(`grep 'ERROR' $L/app.log | grep -oE 'waiting=[0-9]+' | sort | uniq -c`): waiting=1 9건, 2 12건, 3 15건, 4 7건, 5 3건, 6 2건, 7 1건. 모두 `total=5, active=5, idle=0` 이다.

**3.1-c slow log (H2 ②③)**

```bash
grep -E 'Time|Query_time' $L/mariadb-slow.log
```
```
# Time: 260916  0:14:09   Query_time: 2.318446  Lock_time: 0.000071  Rows_examined: 41822
# Time: 260916  2:02:42   Query_time: 2.041903  Lock_time: 0.912004  Rows_examined: 5
# Time: 260916  5:37:52   Query_time: 9.812417  Lock_time: 0.000071  Rows_examined: 1284310
```

**3.1-d 리포트 요청 분포 (H3 ①②③)**

```bash
grep '/report' $L/nginx-access.log | awk '{print substr($4,14,2)}' | sort | uniq -c                  # 시간대별
grep '/report' $L/nginx-access.log | awk '{m=substr($4,14,5); if(m<"14:37") print m}' | sort | uniq -c | sort -rn | head -3
grep '/report' $L/nginx-access.log | awk '{m=substr($4,14,5); if(m>="14:30"&&m<="14:53") print m}' | sort | uniq -c
grep '/report' $L/nginx-access.log | awk '{t=substr($4,14,8); if(t>="14:37:42"&&t<"14:40:52") print t,$1,$9}'
grep '/report' $L/nginx-access.log | awk '{t=substr($4,14,5); if(t>="14:37"&&t<="14:52") print $1,$9}' | sort | uniq -c
```
```
시간대별: 07시 2, 08시 5, 09시 2, 10시 11, 11시 7, 12시 11, 13시 8, 14시 179, 15시 4
14:37 이전 분당 최대: 12:20 2건 (그 밖은 1건 이하)
분당: 14:38 3, 14:39 3, 14:40 10, 14:41 16, 14:42 15, 14:43 19, 14:44 14, 14:45 15, 14:46 19,
      14:47 12, 14:48 14, 14:49 16, 14:50 10, 14:51 5
첫 5xx 이전(14:37:42 ~ 14:40:51): 15건, 모두 200
  교사단말A 7건(14:38:01 · 14:38:27 · 14:38:57 · 14:39:24 · 14:39:55 · 14:40:19 · 14:40:44)
  교사단말B 6건(14:39:54 · 14:40:11 · 14:40:21 · 14:40:32 · 14:40:36 · 14:40:47)
  교사단말C 1건(14:40:27), 교사단말D 1건(14:40:43)
14:37 ~ 14:52 클라이언트 × 상태:
  교사단말B  200 27 / 500 3 / 502 14 / 504 23
  교사단말A  200 22 / 500 1 / 502 8  / 504 9
  교사단말C  200 19 / 502 8 / 504 8
  교사단말D  200 11 / 500 1 / 502 2  / 504 15
```

**3.1-e upstream 별 nginx 오류 (H4 ①③)**

```bash
grep -c 'API-2' $L/nginx-error.log                                                  # 14
grep -n 'API-2' $L/nginx-error.log | head -1                                        # 11: 14:42:01 connect() failed
grep -oE 'upstream: "http://[^/"]+' $L/nginx-error.log | sort | uniq -c
grep -n 'Connection is not available' $L/app.log | head -1                               # 1522: 14:40:52.319
grep -n 'no live upstreams' $L/nginx-error.log | head -1                                 # 12: 14:42:04
awk '$9~/^5/ && substr($4,14,8)<"14:40:52"' $L/nginx-access.log | wc -l                  # 0
```
```
     83 upstream: "http://API-1:8080
     14 upstream: "http://API-2:8080
     68 upstream: "http://item_bank_api
오류 종류: upstream timed out 55, temporarily disabled 26, connect() failed 14, no live upstreams 68, buffered(warn) 2
```

**3.1-f 기동 · 설정 기록 (H5 ③)**

```bash
grep -c -E 'Started |Tomcat started|Shutting down|HikariPool.*Start completed' $L/app.log   # 0
grep -c -iE 'maximumPoolSize|connectionTimeout|leakDetection' $L/app.log                    # 0
head -1 $L/app.log | cut -c1-30                                                             # 2026-09-16T06:43:56.753+09:00 (요청 처리 로그)
```

### 3.2 판정

로그 확인에서 유지된 H1 · H3 을 `modern/api` 의 코드 · 설정과 대조했다. 단서는 `app.log` 에 찍힌 클래스(`ReportService`, `ReportController.java:22`, `ProxyLeakTask`, `SqlExceptionHelper`, `GlobalExceptionHandler`)와 풀 이름 `itembank-pool`, 메시지 속 값(`total=5`, `3000ms`)이다. 벤치마크 · 테스트는 실행하지 않았다.

**코드 · 설정 대조**

| 가설 | 판정 | 근거 (파일:줄번호) |
|---|---|---|
| H1 | **코드 · 설정이 가설을 지지한다** | ① 로그의 풀 이름 `itembank-pool` 이 `modern/api/src/main/resources/application.yml:10` 과 같다. 같은 파일 `:11` 에 "운영 값과 같게 맞춘다"는 주석이 있고, `:14` `maximum-pool-size: 5` · `:15` `connection-timeout: 3000` · `:18` `leak-detection-threshold: 10000` 은 로그의 `total=5` · `timed out after 3000ms` · 누수 감지 10초 기준과 일치한다. ② 로그 스택의 `ReportController.java:22` 는 `modern/api/src/main/java/com/example/assignment/ReportController.java:22` `return reportService.buildClassReport(id);` 이다. ③ `ReportService.java:41` 의 `@Transactional` 이 메서드 전체에 걸려 있다. DB 조회는 `:43` · `:46` · `:53-54` 에서 끝나지만, 그 뒤 `:71` `sign(payload.toString())` 이 같은 트랜잭션 안에서 실행된다. `sign` 은 `:88-91` 에서 SHA-256 을 `SIGNATURE_ROUNDS` = `:23` `200_000` 회 반복하는 CPU 작업이다. `:69-70` 주석도 "DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다"고 적고 있다. ④ 같은 서버의 다른 서비스는 `@Transactional(readOnly = true)` 조회뿐이다(`UnitService.java:10`, `ItemService.java:14`, `ItemSearchService.java:19`, `DistributionService.java:30,36`). 그 안에 반복 계산은 없고, 누수 스택에도 한 번도 나오지 않는다(3.1-a). ⑤ 풀 고갈 ERROR 49건 중 44건은 리포트가 아닌 경로(`ItemController.java:25,31`, `UnitController.java:22`, `DistributionController.java:26`)에서 났다. 예: `app.log:1523-1547` 은 `ItemService.listActiveItemsByUnit` 이 `waiting=4` 로 실패한 기록이다. 이 경로들은 `GlobalExceptionHandler.java:53-55` 의 `Exception` 처리기를 거쳐 500 이 됐다. 리포트가 커넥션을 쥐고 있는 동안 다른 요청이 피해를 본 모양과 맞는다. |
| H3 | **관련 코드를 찾지 못했다** | 리포트 요청이 몰린 이유(교사단말A 의 약 30초 주기, 3.1-d)는 클라이언트 쪽 일이다. `modern/api` 에는 이를 설명하거나 반박할 코드가 없다. 참고로 `modern/api` 에는 `@Cacheable` · `@EnableCaching` · 요청 제한 코드가 없다(`grep -rn 'Cacheable\|EnableCaching\|RateLimit\|@Async' modern/api/src/main/java` 결과 0건). 그래서 같은 학급 리포트를 다시 요청해도 매번 `ReportService.java:41-81` 전체가 새로 실행된다. 이는 요청 집중이 풀 점유로 바로 이어지는 조건이지만, 요청이 왜 늘었는지에 대한 근거는 아니다. |

**채택: H1 — 학급 리포트 생성이 DB 작업을 끝낸 뒤에도 트랜잭션(커넥션)을 쥔 채 서명 계산을 해서, 리포트 요청이 겹치자 크기 5 의 풀이 고갈됐다.**

- 증상과의 연결: 리포트 요청마다 커넥션을 15.5 ~ 21.5초 쥐었다(3.1-b). 이 시간이 커넥션 대기 한도(`application.yml:15`, 3초)보다 훨씬 길다. 그래서 풀 5개(`application.yml:14`)가 모두 리포트에 묶이자 다른 API 의 커넥션 요청이 3초 뒤 실패했다(500, 49건). 응답하지 못한 요청은 nginx 에서 504(55건)가 됐고, 이어 upstream 제외 → `no live upstreams`(502)로 번졌다.
- H3(리포트 요청 집중)은 이 결함이 드러난 **계기**로 본다. 평소처럼 분당 0 ~ 2건이면 동시 점유가 풀 크기에 닿지 않는다. 분당 10 ~ 19건이 18초 안팎씩 커넥션을 쥐면 평균 동시 점유가 5개 근처가 된다(3.1-b, 3.1-d).

**기각한 가설과 사유**

| 가설 | 기각 사유 |
|---|---|
| H2 DB · 쿼리 | 9.81초 쿼리는 이상 구간 첫 리포트 1건(`ReportService.java:53-54` → `SubmissionRepository.java:8`)뿐이다(3.1-c). 이 1건으로는 그 뒤 13분간 커넥션 134번 · 5개가 매번 15초 넘게 묶인 현상을 설명할 수 없다. 첫 고갈을 앞당긴 요인일 수는 있다. 다만 slow log 가 끝까지 정상 기록됐는지 확인되지 않아 완전히 배제하지는 못했다(3절 표 H2). |
| H3 트래픽 | 원인이 아니라 계기로 보아 단독 원인으로 채택하지 않는다. 전체 요청 수는 평소와 같았다(분당 34 ~ 58, 1.2 절). 무거운 리포트 요청이 늘어도 커넥션을 쥐지 않고 계산했다면 풀이 고갈되지 않았을 것이다. 고갈 메커니즘은 H1 의 코드에 있다. |
| H4 인프라 | 앱의 풀 고갈(14:40:52, `app.log:1522`)과 첫 5xx 가 API-2 의 첫 연결 거부(14:42:01) · `no live upstreams`(14:42:04)보다 먼저다(3.1-e). 따라서 502 를 키운 증폭 요인일 수는 있어도 발단은 아니다. API-2 가 평소 살아 있었는지는 여전히 확인 필요다. |
| H5 배포 · 설정 변경 | 근거가 없다. 저장소에서 `application.yml` 과 `ReportService.java` 는 초기 커밋(`1260542`) 이후 바뀐 적이 없다(`git log`). 로그에도 기동 · 재적재 기록이 없다(3.1-f). 단, 운영 배포 이력은 저장소 밖이라 확인하지 못했다. |

**확신 수준: 중간**

- 높일 근거
  - 누수 스택 134건이 모두 `ReportService.buildClassReport` 였다(3.1-a).
  - 커넥션을 쥔 채 CPU 루프를 도는 구조가 코드에 그대로 있다(`ReportService.java:41`, `:71`, `:88-91`).
  - 풀 설정값이 로그 메시지와 정확히 맞는다(`application.yml:10,14,15,18`).
- 낮출 근거
  - 같은 코드인데 이상 구간 전 리포트 50건은 점유가 10초 미만이었고, 이상 구간에서는 15.5 ~ 21.5초로 늘었다(3.1-b). 동시 실행된 `sign` 끼리 CPU 를 나눠 써서 느려졌다는 설명이 자연스럽다. 하지만 CPU 사용률 로그가 없고 벤치마크도 하지 않아 확인하지 못했다.
  - H2 · H4 의 일부 반증 조건이 로그 부족으로 열려 있다.
- 높이려면: 장애 시각의 호스트 CPU 사용률, 스레드 덤프(동시에 `sign` 안에 있는 스레드 수), `sign()` 단독 소요 시간 측정.

## 4. 원인

3.2 절에서 채택한 H1 과 계기 H3 을 인과 순서로 적는다. 문장 번호(B · T · D · P)는 5 절의 근거 표와 짝을 이룬다.

**배경 원인: 결함이 장애로 이어질 수 있게 한 조건**

- **B1** `ReportService.buildClassReport` 는 메서드 전체가 `@Transactional` 이다. 그래서 DB 조회가 끝난 뒤 SHA-256 200,000회 서명 계산(`sign`)을 하는 동안에도 커넥션을 쥐고 있다.
- **B2** 커넥션 풀은 최대 5개이고 획득 대기 한도는 3초다. 커넥션을 오래 쥐는 요청이 5건만 겹쳐도 다른 요청은 3초 뒤 실패한다.
- **B3** 리포트 결과를 캐시하거나 리포트의 동시 실행 수를 제한하는 장치가 없다. 같은 학급 리포트를 다시 요청해도 매번 계산을 처음부터 한다.
- **B4** (증폭 요인) upstream 의 다른 인스턴스 API-2 는 연결을 거부하는 상태였다. 그래서 API-1 이 빠지자 대신 받을 서버가 없었다. 언제부터 거부했는지는 확인 필요다.

**계기**

- **T1** 14:37:42 에 이상 구간의 첫 리포트 요청이 9.81초 걸린 `submission` 조회를 포함해 커넥션을 10초 넘게 쥐었다. 이것이 첫 누수 감지다.
- **T2** 14:38 부터 교사단말A 가 약 30초 간격으로 리포트를 반복 요청했다. 14:39:54 ~ 14:40:43 에 클라이언트 3개가 더 붙으면서 리포트 요청이 분당 0 ~ 2건에서 10 ~ 19건이 됐다.

**직접 원인**

- **D1** 동시에 실행된 리포트들이 커넥션을 각각 15.5 ~ 21.5초 쥐었다. 이상 구간 이전 리포트 50건은 10초 미만이었다. 늘어난 이유는 동시에 돈 `sign` 끼리의 CPU 경합으로 추정하지만 확인하지 못했다(9 절).
- **D2** 14:40:52 에 풀 5개가 모두 사용 중(`active=5, idle=0`)이 됐다. 커넥션을 기다리던 다른 API 요청은 3초 뒤 실패해 500 이 됐다(49건).

**전파 · 회복**

- **P1** 리포트 처리에 묶여 응답하지 못한 요청은 nginx 에서 504 가 됐다(14:41:06 부터 55건).
- **P2** 14:42:01 에 nginx 가 API-1 을 일시 제외했고, 같은 초에 API-2 는 연결을 거부했다. 그래서 14:42:04 부터 모든 경로가 `no live upstreams` 502 로 실패했다.
- **P3** 14:49:51 에 마지막 풀 고갈 ERROR 가 났다. 리포트 요청이 14:51 에 5건으로 줄었고, 14:51:49 에 마지막 누수 커넥션이 반환되며 정상화됐다. 재기동 기록은 없다.

## 5. 근거 로그 줄

로그 경로는 `incident-logs/a-connection-pool/` 이고 코드 경로는 `modern/api/src/main/` 이다. 둘 다 앞부분을 생략했다. 학생 식별자는 발췌에 없다.

| 문장 | 근거 | 발췌 |
|---|---|---|
| B1 | `java/com/example/assignment/ReportService.java:41`, `:71`, `:23` | `@Transactional` / `String signature = sign(payload.toString());` / `SIGNATURE_ROUNDS = 200_000` |
| B1 | `app.log:1243`, `app.log:1253` | `Connection leak detection triggered ... on thread http-nio-8080-exec-6` / `at ...ReportService$$SpringCGLIB$$0.buildClassReport` |
| B2 | `resources/application.yml:14`, `:15` | `maximum-pool-size: 5` / `connection-timeout: 3000` |
| B3 | 근거 줄 없음(없다는 것을 확인함) | `modern/api` 에서 `Cacheable` · `EnableCaching` · `RateLimit` 를 grep 한 결과 0건(3.2 절 H3) |
| B4 | `nginx-error.log:11` | `connect() failed (111: Connection refused) ... upstream: "http://API-2:8080/...` |
| T1 | `mariadb-slow.log:20`, `:18` | `SET timestamp=1789537062;`(14:37:42 KST) / `Query_time: 9.812417 ... Rows_examined: 1284310` |
| T1 | `app.log:1243` | `14:37:52.199 ... Connection leak detection triggered ...@6e8f14d3`(누수 감지 기준 10초를 빼면 14:37:42) |
| T2 | `nginx-access.log:18801`, `:18815` | `14:38:01 ... 교사단말A "GET /api/classes/3/report" 200` / `14:38:27 ... 같은 경로 200` |
| T2 | `nginx-access.log:18881`, `:18904`, `:18912` | `14:39:54 교사단말B` · `14:40:27 교사단말C` · `14:40:43 교사단말D` 리포트 요청 |
| T2 | 집계(3.1-d 절) | 분당 리포트 요청 14:38 3 → 14:40 10 → 14:43 19. 14:37 이전은 분당 최대 2 |
| D1 | `app.log:1243`, `app.log:1259` | WARN 14:37:52.199 → `14:38:01.902 ... was returned to the pool` (점유 약 19.7초) |
| D1 | 집계(3.1-b 절) | 점유 시간 134쌍: 최소 15.5초 · 중앙값 18.9초 · 최대 21.5초. 이상 구간 이전 누수 WARN 0건 |
| D2 | `app.log:1522` | `14:40:52.319 ... Connection is not available, request timed out after 3000ms (total=5, active=5, idle=0, waiting=4)` |
| D2 | `app.log:1523`, `nginx-access.log:18918` | `unhandled exception on /api/units/M6-2/items` / `"GET /api/units/M6-2/items HTTP/1.1" 500` |
| P1 | `nginx-error.log:6`, `nginx-access.log:18933` | `14:41:06 upstream timed out (110: Connection timed out)` / `"GET /api/classes/3/report" 504` |
| P2 | `nginx-error.log:10`, `:11`, `:12` | `14:42:01 upstream server temporarily disabled` / `14:42:01 connect() failed (111: Connection refused)` / `14:42:04 no live upstreams` |
| P3 | `app.log:4980`, `app.log:5267`, `nginx-access.log:19381` | `14:49:51.552 ... Connection is not available` / `14:51:49.148 ... was returned to the pool` / `14:51:47 ... 504`(마지막 5xx) |

## 6. 수정안 (지금 적용)

코드는 고치지 않았다. 아래는 제안이다.

| # | 파일 | 무엇을 | 어떻게 |
|---|---|---|---|
| 6-1 | `modern/api/src/main/java/com/example/assignment/ReportService.java:41-82` | 서명 계산을 트랜잭션 밖으로 뺀다 | `buildClassReport` 에서 `@Transactional` 을 뗀다. DB 조회와 집계(`:43-67`)는 같은 패키지의 새 빈(예: `ReportDataReader`)으로 옮기고 그 메서드에 `@Transactional(readOnly = true)` 를 붙인다. 이 빈은 엔티티가 아니라 record(학급 id · 이름 · 배포 수 · 제출 수 · 평균 · payload)를 반환한다. `sign()`(`:71`)은 그 결과를 받아 트랜잭션 밖에서 호출한다. 같은 클래스 안에서 자기 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않으므로, 반드시 별도 빈으로 나눈다. `:69-70` TODO 주석은 지운다. |
| 6-2 | `modern/api/src/test/java/com/example/assignment/` | 회귀 테스트 | 새 빈에 대한 Mockito 단위 테스트를 추가한다. 서명 계산 시점에 `TransactionSynchronizationManager.isActualTransactionActive()` 가 `false` 인지 검증하는 테스트도 추가한다(CLAUDE.md: 새 public 메서드마다 테스트). |
| 6-3 | `application.yml:14-15` | **바꾸지 않는다** | 풀 크기를 늘리는 것은 해결책이 아니다. 결함(B1)이 남아 있으면 경계값만 밀린다. 또 CLAUDE.md 금지 사항(커넥션 풀 설정 변경)에도 해당한다. |
| 6-4 | 저장소 밖(운영) | API-2 복구 | 인스턴스가 떠 있는지 확인하고 다시 띄운다. nginx `upstream item_bank_api` 에 두 대가 모두 활성인지 확인한다. |

## 7. 재발 방지 (구조적 장치)

- **트랜잭션 안 장시간 작업 금지를 자동 점검**: `@Transactional` 메서드에서 서명 · 해시 반복, 외부 HTTP 호출, 건수 제한 없는 루프를 부르면 실패하는 아키텍처 테스트를 둔다. ArchUnit 처럼 의존성을 추가해야 하면 먼저 승인을 받는다. 그전까지는 같은 규칙을 `convention-check` Skill 의 점검 항목에 넣는다(CLAUDE.md 3절 규칙을 기계 점검으로 만드는 것).
- **무거운 엔드포인트를 격리(bulkhead)**: 리포트 생성의 동시 실행 수를 풀 크기보다 작게 제한한다(예: 세마포어 2). 그러면 리포트가 몰려도 다른 API 가 쓸 커넥션이 남는다.
- **같은 리포트를 다시 계산하지 않기**: 학급 리포트를 짧은 TTL 로 캐시하거나 미리 계산해 두는 방식으로 바꾼다. 요청 수가 늘어도 DB 와 CPU 부하가 비례해서 늘지 않게 한다.
- **동시성 부하 테스트를 배포 게이트로**: 리포트를 풀 크기 이상 동시에 요청하는 동안 문항 조회 API 가 3초 안에 성공하는지 확인하는 시나리오를 CI 나 사전 배포 단계에 둔다.
- **이중화 보장**: upstream 2대 중 1대가 빠진 상태를 정상으로 두지 않는다. 헬스체크로 자동 복구하거나 교체하는 절차를 운영 runbook 에 넣는다.
- **클라이언트 주기 요청 관리**: 교사단말A 의 30초 주기가 화면 자동 새로고침이라면, 주기 요청에는 백오프 · 최소 간격 기준을 둔다(원인 확인 필요, 9 절).

## 8. 모니터링 항목

이번 타임라인에 적용하면 언제 울렸을지를 함께 적는다. 첫 사용자 오류는 14:40:52 다. 평가 주기는 1분 고정 창으로 가정한다.

| 지표 | 임계값 (등급) | 이번 경보 시각 | 근거 |
|---|---|---|---|
| Hikari 누수 감지 WARN 건수 | 5분 동안 1건 이상 (경고) | **14:37:52** (3분 앞섬) | `app.log:1243` |
| `hikaricp_connections_usage` 최댓값 | 5초 초과 (경고) | **14:38:01** (반환 시점에 약 19.7초로 기록) | `app.log:1259` |
| slow query (5초 이상) | 1건 이상 (경고) | **14:37:52** | `mariadb-slow.log:15-18` |
| 리포트 엔드포인트 요청 수 | 분당 5건 이상, 평소 최대 2건 (경고) | **14:41:00** (14:40 분 10건) | 3.1-d 절 |
| `hikaricp_connections_pending` | 30초 동안 0 초과 (긴급) | 로그로 알 수 있는 가장 이른 시각은 14:40:52(`waiting=4`). 그 이전은 판단 불가 | `app.log:1522` |
| `hikaricp_connections_timeout` 증가량 | 분당 1건 이상 (긴급) | **14:40:52** | `app.log:1522` |
| 5xx 비율 | 1분 동안 5% 초과 (긴급) | **14:42:00** (14:41 분 9/42 ≈ 21%. 14:40 분은 1/42 ≈ 2.4% 로 임계값 미만) | 1.2 절 표 |
| nginx 정상 upstream 수 | 2 미만 (긴급) | 로그 기준 14:42:01. 실제로는 더 이전일 수 있음 | `nginx-error.log:11` |

- 누수 WARN · 커넥션 점유 시간 · slow query 를 경고로 걸었다면 사용자 오류보다 약 3분 먼저 알았을 것이다.
- 풀 타임아웃 · 5xx 비율 · upstream 수는 사람을 호출하는 긴급 경보로 둔다.
- 호스트 CPU 사용률(예: 1분 동안 80% 초과)도 수집 대상으로 넣는다. 이번에는 데이터가 없어 언제 울렸을지 계산하지 못했다.

## 9. 확인하지 못한 것

| 판단하지 못한 것 | 이유 | 다음을 위해 남길 로그 |
|---|---|---|
| 커넥션 점유 시간이 10초 미만에서 15.5 ~ 21.5초로 늘어난 이유(동시 `sign` 의 CPU 경합으로 추정) | CPU 사용률 · 스레드 덤프 · `sign()` 소요 시간 기록이 없다. 벤치마크도 실행하지 않았다 | 호스트 CPU 지표, 장애 시 자동 스레드 덤프, 리포트 단계별 소요 시간(조회 · 서명) INFO 로그 |
| 9.81초 `submission` 조회의 원인과 그 뒤 slow log 공백 | slow log 파일이 14:37:52 에서 끝난다. 운영 `EXPLAIN` · `long_query_time` 을 확인하지 않았다 | slow log 설정과 회전 기록, 앱의 쿼리별 실행 시간(Hibernate statistics 등) |
| API-2 가 언제부터 연결을 거부했는지 | access log 에 `$upstream_addr` 가 없고, API-2 의 앱 로그도 없다 | nginx access log 에 `$upstream_addr` · `$request_time` · `$upstream_response_time` 추가, 인스턴스별 기동 · 종료 로그 |
| 리포트 요청이 급증한 이유(30초 주기 클라이언트와 나머지 3개) | 화면 · 클라이언트 배포 이력과 이전 날짜 로그가 없다 | 화면 배포 이력, 요청에 화면 버전 · 자동 새로고침 여부 헤더, 며칠 치 access log 보관 |
| 회복 원인(리포트 요청이 14:51 이후 줄어든 이유) | 사용자 행동인지 운영자 조치인지 기록이 없다 | 운영 조치 기록(누가 · 언제 · 무엇을) |
| 장애 이전 배포 · 설정 변경 여부(H5) | 로그가 09-16 기동 이후 부분뿐이다 | 기동 로그의 Hikari 설정 출력, 배포 이력 |
| 풀 사용 추이(14:40:52 이전 `active` · `pending`) | 풀 상태는 ERROR 메시지에만 남는다 | Hikari 지표(active · idle · pending · usage)를 주기적으로 수집 |

마스킹 적용: IP 주소 59건 · 이메일 0건 · 학생 식별자 1건 · 토큰 0건 · 비밀번호/접속 문자열 0건
