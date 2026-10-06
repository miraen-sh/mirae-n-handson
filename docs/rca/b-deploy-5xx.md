# RCA 리포트 — b-deploy-5xx (배포 후 특정 API 5xx 증가)

> 작성 범위: 이번 회차는 요청에 따라 **1. 수집 범위 · 2. 타임라인 · 3. 가설(가설 표까지)** 만 작성했다. 반증 실행 · 코드 대조 · 판정과 요약 · 원인 · 수정안 등 4절 이후는 작성하지 않았다.
>
> - 대상 폴더: `incident-logs/b-deploy-5xx/`
> - 모든 시각은 KST(+09:00) 기준이다.
> - 원문 발췌는 마스킹 규칙(`.claude/skills/rca/masking-rules.md`)을 적용한 뒤의 값이다. 긴 줄은 `…` 로 줄였다.

---

## 1. 수집 범위

### 1.1 파일 목록 · 기록 기간

실행한 명령:

```bash
ls -l incident-logs/b-deploy-5xx/
wc -l incident-logs/b-deploy-5xx/*
head -3 app.log; tail -3 app.log; head -3 nginx-access.log; tail -3 nginx-access.log
```

| 파일 | 줄 수 | 크기(byte) | 첫 줄 시각 | 끝 줄 시각 | 내용 |
|---|---:|---:|---|---|---|
| `app.log` | 2,938 | 394,019 | 2026-09-17 09:00:04.878 (`app.log:1`) | 2026-09-17 14:39:57.905 (`app.log:2938`) | Spring Boot 애플리케이션 로그(`item-bank-api`) |
| `nginx-access.log` | 20,365 | 4,443,493 | 2026-09-17 09:00:00 (`nginx-access.log:1`) | 2026-09-17 14:39:58 (`nginx-access.log:20365`) | nginx combined 접근 로그 |
| `deploy-history.md` | 39 | 2,822 | — | — | 배포 이력 표(v1.3.9 ~ v1.4.2)와 버전별 메모 |

### 1.2 시각 형식 · 타임존

| 파일 | 형식 | 예 | 정밀도 | 타임존 | 근거 |
|---|---|---|---|---|---|
| `app.log` | ISO 8601 + 오프셋 | `2026-09-17T09:00:04.878+09:00` | 밀리초 | KST(오프셋 명시) | `app.log:1` |
| `nginx-access.log` | combined `[dd/Mon/yyyy:HH:MM:SS +0900]` | `[17/Sep/2026:09:00:00 +0900]` | 초 | KST(오프셋 명시, 20,365줄 전부 `+0900`) | `nginx-access.log:1`, 명령 `awk '{print $5}' nginx-access.log \| sort \| uniq -c` → `20365 +0900]` |
| `deploy-history.md` | `yyyy-mm-dd HH:MM:SS` | `2026-09-17 13:40:05` | 초 | KST(문서에 명시) | `deploy-history.md:3` "시각은 모두 KST(한국 표준시)입니다." |

교차 확인: 같은 요청이 두 파일에서 같은 초로 찍힌다. 따라서 세 파일의 시각을 변환 없이 그대로 비교한다.

- `app.log:1274-1275` `2026-09-17T13:46:38.107+09:00 … redistributed distribution 42` / `13:46:38.113 … ERROR … unhandled exception on /api/distributions/42/redistribute`
- `nginx-access.log:17155` `교사단말A - - [17/Sep/2026:13:46:38 +0900] "POST /api/distributions/42/redistribute HTTP/1.1" 500 176`

정밀도 주의: nginx 는 초 단위이고, combined 형식의 시각은 보통 요청 처리 완료 시점이다. app 로그(밀리초)와 같은 초에 찍힌 사건은 타임라인에 "(선후 불확실)"로 표시했다.

### 1.3 이상 신호 집계

시간대별 상태 코드(nginx)와 로그 레벨(app):

```bash
awk '{h=substr($4,14,2); t[h]++; s[h" "$9]++} END{for(k in s) print k, s[k]; for(h in t) print h, "ALL", t[h]}' nginx-access.log | sort
awk '{print substr($1,12,2), $2}' app.log | grep -E ' (INFO|WARN|ERROR)$' | sort | uniq -c
```

| 시(KST) | 전체 요청 | 200 | 400 | 404 | 409 | 500 | 502 | app ERROR | app WARN |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 09 | 3,625 | 3,413 | 6 | 199 | 4 | 3 | 0 | 3 | 4 |
| 10 | 3,621 | 3,437 | 2 | 173 | 8 | 1 | 0 | 1 | 8 |
| 11 | 3,762 | 3,565 | 4 | 182 | 8 | 3 | 0 | 3 | 8 |
| 12 | 3,215 | 3,053 | 8 | 145 | 9 | 0 | 0 | 0 | 9 |
| 13 | 3,678 | 3,509 | 2 | 142 | 3 | **14** | **8** | **14** | 3 |
| 14 (~14:39) | 2,464 | 2,301 | 7 | 128 | 3 | **25** | 0 | **25** | 3 |

- 400 · 404 · 409 는 시간대마다 비슷한 수준이다. 400 은 `GET /api/items/NaN|null|undefined`, 409 · WARN 은 `state conflict: 마감된 과제는 재배포할 수 없습니다`(예: `app.log:24`)이고, 배포 전후로 크게 변하지 않는다. 그래서 평상시 수준(baseline)으로 보고 이상 신호에서 뺐다.
- 5xx 는 합계 54건이다(500 46건 · 502 8건). 경로별로 나누면 다음과 같다.

```bash
awk '$9 ~ /^5/ {p=$7; gsub(/[0-9]+/,"{n}",p); print $9, $6, p}' nginx-access.log | sort | uniq -c
```

```
     45 500 "POST /api/distributions/{n}/redistribute
      1 500 "GET /api/distributions/{n}
      1 502 "GET /
      1 502 "GET /api/distributions/{n}
      1 502 "GET /api/distributions/{n}?student=학생-{n}
      1 502 "GET /api/items/{n}
      1 502 "GET /api/units
      1 502 "GET /api/units/M{n}-{n}/items
      2 502 "GET /api/units/M{n}-{n}/items?student=학생-{n}
```

5xx 분 단위 집계:

```bash
awk '$9 ~ /^5/ {print substr($4,14,5)}' nginx-access.log | uniq -c
```

| 분(KST) | 5xx | 비고 |
|---|---:|---|
| 09:52 | 3 | 배포 전, distribution 41 재배포 500 |
| 10:31 | 1 | 배포 전, distribution 41 단건 조회 500 |
| 11:17 | 1 | 배포 전, distribution 41 재배포 500 |
| 11:18 | 2 | 배포 전, distribution 41 재배포 500 |
| 13:41 | 8 | 전부 502(재기동 구간) |
| 13:46 | 2 | 이후 전부 500, distribution 42 · 43 재배포 |
| 13:47 | 1 | |
| 13:50 | 4 | |
| 13:52 | 1 | |
| 13:53 | 1 | |
| 13:55 | 3 | |
| 13:58 | 2 | |
| 14:02 | 2 | |
| 14:03 | 4 | |
| 14:06 | 1 | |
| 14:11 | 3 | |
| 14:15 | 1 | |
| 14:16 | 2 | |
| 14:22 | 3 | |
| 14:27 | 2 | |
| 14:28 | 1 | |
| 14:31 | 2 | |
| 14:32 | 1 | |
| 14:34 | 3 | 마지막 5xx 14:34:45 (`nginx-access.log:20084`) |

### 1.4 이상 구간과 분석 범위

- **이상 구간**: 2026-09-17 13:41:51(첫 502) ~ 14:34:45(마지막 500). 로그가 끝나는 14:39:58까지 회복 기록은 없다.
- **분석 범위**: 13:10:00 ~ 14:39:58(로그 끝). 앞쪽 여유는 약 30분이다. 뒤쪽 여유는 10분을 잡으려 했지만 로그가 14:39:58에 끝나서 약 5분만 있다.

| 파일 | 분석 범위 줄 | 근거 |
|---|---|---|
| `nginx-access.log` | 14793 ~ 20365 | `nginx-access.log:14793` `[17/Sep/2026:13:10:00 +0900] "GET / HTTP/1.1" 200 40 "-" "kube-probe/1.29"` |
| `app.log` | 1146 ~ 2938 | `app.log:1146` `2026-09-17T13:11:41.157+09:00` (13:10 이후 첫 줄) |
| `deploy-history.md` | 3, 7, 12 ~ 22 | v1.4.2 배포 기록 |

- **범위 밖 참고 구간**: 배포 전에 같은 예외가 나온 줄. 가설 검토에 필요해서 따로 적어 둔다.
  - `nginx-access.log`: 3150, 3162, 3174, 5563, 8442, 8466, 8468
  - `app.log`: 194, 232, 270, 451, 655, 695, 733

### 1.5 기동 · 종료 · 설정 재적재 · 배포 기록 탐색

```bash
grep -nEi 'Started |Starting |Tomcat|shutdown|Shutting|graceful|HikariPool|version|Profile|refresh' app.log
```

| 항목 | 결과 |
|---|---|
| 종료 | 있음. `app.log:1240-1244` graceful shutdown → JPA · Hikari 종료 |
| 기동 | 있음. `app.log:1245` `Starting ItemBankApplication v1.4.2` ~ `app.log:1264` `Started … in 4.921 seconds` |
| 설정 재적재(refresh 등) | 없음. 위 명령에서 `refresh` 일치 0건. `Profile` 일치는 기동 시 `No active profile set, falling back to 1 default profile: "default"`(`app.log:1246`) 1건뿐이다 |
| 배포 기록 | `deploy-history.md:7` v1.4.2 배포 시작 13:40:05 · 완료 13:42:31 |
| 롤백 기록 | `deploy-history.md:22` 에 계획("`v1.4.1` 이미지로 재기동(약 3분)")만 있다. `app.log` 에는 13:42:00 이후 다른 기동 로그가 없다. 로그 끝까지 롤백이 실행된 흔적이 없다 |
| 호스트 구분 | nginx combined 로그에는 upstream 주소가 없어서 어느 API 호스트가 응답했는지 접근 로그로는 구분할 수 없다. 배포 기록상 대상은 API-1 하나다(`deploy-history.md:3`, `deploy-history.md:18`) |

---

## 2. 타임라인

표기: `app` = `app.log`, `nginx` = `nginx-access.log`, `deploy` = `deploy-history.md`.

### 2.1 배포 전 선행 신호 (분석 범위 밖, 참고)

| 시각(KST) | 사건 | 출처 | 원문 발췌 |
|---|---|---|---|
| 09:52:17.335 | distribution 41 재배포 500. 예외는 `Assignment` id 9 없음. 스택 경유 지점은 `DistributionService.loadOrThrow:65` ← `redistribute:51` ← `DistributionController.redistribute:38` | app:194, app:195, app:205-206, app:214 | `ERROR … unhandled exception on /api/distributions/41/redistribute` / `JpaObjectRetrievalFailureException: Entity \`com.example.assignment.Assignment\` with identifier value \`9\` does not exist` / `at com.example.assignment.DistributionService.loadOrThrow(DistributionService.java:65)` / `at com.example.assignment.DistributionController.redistribute(DistributionController.java:38)` |
| 09:52:17 (선후 불확실) | 같은 요청이 nginx 에 500 으로 기록됨 | nginx:3150 | `교사단말A - - [17/Sep/2026:09:52:17 +0900] "POST /api/distributions/41/redistribute HTTP/1.1" 500 176` |
| 09:52:30 · 09:52:41 | distribution 41 재배포 500 2건 더(같은 단말) | app:232, app:270, nginx:3162, nginx:3174 | `ERROR … unhandled exception on /api/distributions/41/redistribute` |
| 10:31:05.909 | distribution 41 **단건 조회**도 같은 예외로 500 | app:451, app:452, nginx:5563 | `ERROR … unhandled exception on /api/distributions/41` / `… Assignment … identifier value \`9\` does not exist` / `"GET /api/distributions/41 HTTP/1.1" 500 176` |
| 11:17:40 ~ 11:18:02 | distribution 41 재배포 500 3건(같은 단말) | app:655, app:695, app:733, nginx:8442, nginx:8466, nginx:8468 | `ERROR … unhandled exception on /api/distributions/41/redistribute` |

### 2.2 분석 범위 (13:10:00 ~ 14:39:58)

| 시각(KST) | 사건 | 출처 | 원문 발췌 |
|---|---|---|---|
| 13:10:00 | 분석 범위 시작. 헬스체크 정상 | nginx:14793 | `<IP> - - [17/Sep/2026:13:10:00 +0900] "GET / HTTP/1.1" 200 40 "-" "kube-probe/1.29"` |
| 13:38 | v1.4.2 배포 승인 | deploy:21 | `- 승인: <EMAIL> (13:38)` |
| 13:40:05 | v1.4.2 배포 시작(파이프라인 기록) | deploy:7 | `\| v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \| <EMAIL> \| 재배포 응답에 학급 배포 이력(history) 포함 — \`DistributionController.redistribute\` \| …DistributionController.java \|` |
| (시각 없음) | 배포 명령. 대상 호스트는 API-1 | deploy:20 | `curl -s -X POST https://deploy.example.com/api/v1/apps/item-bank-api/releases -H "Authorization: Bearer <TOKEN>" -d '{"version":"1.4.2","host":"API-1"}'` |
| (시각 없음) | 변경 내용: 재배포 응답 `{distribution}` → `{distribution, history[]}`, 컨트롤러에 `listByClass(classId)` 호출 추가 | deploy:15, deploy:16 | `변경 지점: \`DistributionController.redistribute\` 에서 재배포 후 \`DistributionService.listByClass(classId)\` 호출 추가.` |
| (시각 없음) | 사전 검증은 스테이징 DB(시드 데이터)에서만 함 | deploy:17 | `테스트: \`./gradlew test\` 통과 (24 tests). 스테이징 DB(시드 데이터)에서 재배포 3건 수동 확인.` |
| 13:41:20.994 | **배포 전 마지막 정상 재배포**: distribution 43(class 1) | app:1238 | `INFO … DistributionService : redistributed distribution 43 (assignment 6, class 1) reason=학생A 요청 — 제출 화면 오류` |
| 13:41:20 (선후 불확실) | 같은 요청이 200, 응답 **183 byte**(이전 형식 크기) | nginx:16829 | `교사단말A - - [17/Sep/2026:13:41:20 +0900] "POST /api/distributions/43/redistribute HTTP/1.1" 200 183` |
| 13:41:48.214 | graceful shutdown 시작 | app:1240 | `INFO … GracefulShutdown : Commencing graceful shutdown. Waiting for active requests to complete` |
| 13:41:50.144 | graceful shutdown 완료 | app:1241 | `INFO … GracefulShutdown : Graceful shutdown complete` |
| 13:41:50 (선후 불확실) | nginx 에 마지막 200(헬스체크) | nginx:16862 | `<IP> - - [17/Sep/2026:13:41:50 +0900] "GET / HTTP/1.1" 200 40 "-" "kube-probe/1.29"` |
| 13:41:50.184 ~ .224 | JPA EntityManagerFactory · Hikari 풀 종료 | app:1242, app:1243, app:1244 | `Closing JPA EntityManagerFactory …` / `itembank-pool - Shutdown initiated...` / `itembank-pool - Shutdown completed.` |
| 13:41:51 | 첫 502 | nginx:16865 | `<IP> - - [17/Sep/2026:13:41:51 +0900] "GET /api/units/M5-2/items?student=학생B HTTP/1.1" 502 157` |
| 13:41:52 ~ 13:41:58 | 502 연속(헬스체크 포함) | nginx:16866, 16867, 16868, 16869, 16870, 16871 | `"GET / HTTP/1.1" 502 157 "-" "kube-probe/1.29"` / `"GET /api/distributions/8?student=학생C HTTP/1.1" 502 157` / `"GET /api/distributions/5 HTTP/1.1" 502 157` 등 |
| 13:41:55.902 | **v1.4.2 기동 시작**, 프로필 `default` | app:1245, app:1246 | `Starting ItemBankApplication v1.4.2 using Java 21.0.4 with PID 1` / `No active profile set, falling back to 1 default profile: "default"` |
| 13:41:58.533 | 커넥션 풀 기동 | app:1259, app:1260 | `itembank-pool - Added connection org.mariadb.jdbc.Connection@4c1e7a3b` / `itembank-pool - Start completed.` |
| 13:41:59 | 마지막 502 | nginx:16872 | `<IP> - - [17/Sep/2026:13:41:59 +0900] "GET /api/units/M6-1/items?student=학생A HTTP/1.1" 502 157` |
| 13:42:00.290 | Tomcat 8080 기동, 애플리케이션 기동 완료 | app:1263, app:1264 | `Tomcat started on port 8080 (http) with context path '/'` / `Started ItemBankApplication in 4.921 seconds` |
| 13:42:01 | 재기동 뒤 첫 200 | nginx:16873 | `<IP> - - [17/Sep/2026:13:42:01 +0900] "GET /api/units HTTP/1.1" 200 604` |
| 13:42:10.447 | 재기동 뒤 첫 재배포 성공: distribution 7(class 3) | app:1266 | `INFO … DistributionService : redistributed distribution 7 (assignment 5, class 3) reason=보강 수업 후 재배포` |
| 13:42:10 (선후 불확실) | 같은 요청이 200, 응답 **802 byte**(새 형식 `history[]` 포함으로 추정) | nginx:16884 | `교사단말B - - [17/Sep/2026:13:42:10 +0900] "POST /api/distributions/7/redistribute HTTP/1.1" 200 802` |
| 13:42:31 | 배포 완료(파이프라인 기록). 앱 기동 완료(13:42:00)와 31초 차이. 파이프라인이 무엇을 완료 기준으로 삼는지는 확인 필요 | deploy:7 | `\| v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \| …` |
| 13:46:38.107 | **class 1 재배포 쓰기 로그 출력**: distribution 42 | app:1274 | `INFO … DistributionService : redistributed distribution 42 (assignment 4, class 1) reason=보강 수업 후 재배포` |
| 13:46:38.113 | 6 ms 뒤 같은 스레드(`exec-1`)에서 500. 예외는 배포 전과 같은 `Assignment` id 9 없음. 경유 지점은 **`DistributionService.listByClass:38` ← `DistributionController.redistribute:40`**(배포 전에는 `:38`) | app:1275, app:1276, app:1285-1286, app:1294 | `ERROR … unhandled exception on /api/distributions/42/redistribute` / `JpaObjectRetrievalFailureException: Entity \`com.example.assignment.Assignment\` with identifier value \`9\` does not exist` / `at …findByClassRoomIdOrderByDistributedAtAsc(Unknown Source)` / `at com.example.assignment.DistributionService.listByClass(DistributionService.java:38)` / `at com.example.assignment.DistributionController.redistribute(DistributionController.java:40)` |
| 13:46:38 (선후 불확실) | 같은 요청이 nginx 에 500 | nginx:17155 | `교사단말A - - [17/Sep/2026:13:46:38 +0900] "POST /api/distributions/42/redistribute HTTP/1.1" 500 176` |
| 13:46:58.034 / .040 | distribution 42 재시도: 쓰기 로그 출력 후 500 | app:1312, app:1313, nginx:17170 | `redistributed distribution 42 (assignment 4, class 1) reason=학생D 결석으로 재배포` / `ERROR … unhandled exception on /api/distributions/42/redistribute` |
| 13:47:08.759 / .765 | distribution 42 재시도: 쓰기 로그 출력 후 500 | app:1350, app:1351, nginx:17181 | `redistributed distribution 42 (assignment 4, class 1) reason=null` / `ERROR … unhandled exception on /api/distributions/42/redistribute` |
| 13:50:00 | class 2 의 distribution 8 재배포는 200, 응답 908 byte | nginx:17353 | `교사단말A - - [17/Sep/2026:13:50:00 +0900] "POST /api/distributions/8/redistribute HTTP/1.1" 200 908` |
| 13:50:12.717 / .723 | distribution 43(class 1): 쓰기 로그 출력 후 500 | app:1395, app:1396, nginx:17365 | `redistributed distribution 43 (assignment 6, class 1) reason=학생A 요청 — 제출 화면 오류` / `ERROR … unhandled exception on /api/distributions/43/redistribute` |
| 13:56:43 | class 2 의 distribution 44 재배포는 200, 응답 640 byte | nginx:17734 | `교사단말A - - [17/Sep/2026:13:56:43 +0900] "POST /api/distributions/44/redistribute HTTP/1.1" 200 640` |
| 14:02:45.634 / .640 | distribution 42: 쓰기 로그 출력 후 500(같은 패턴 반복) | app:1857, app:1858 | `redistributed distribution 42 (assignment 4, class 1) reason=null` / `ERROR … unhandled exception on /api/distributions/42/redistribute` |
| 14:21:02 | class 3 의 distribution 5 재배포는 200, 응답 910 byte | nginx:19238 | `교사단말B - - [17/Sep/2026:14:21:02 +0900] "POST /api/distributions/5/redistribute HTTP/1.1" 200 910` |
| 14:34:45.433 / .439 | **마지막 500**: distribution 43, 쓰기 로그 출력 후 500 | app:2886, app:2887 | `redistributed distribution 43 (assignment 6, class 1) reason=학생A 요청 — 제출 화면 오류` / `ERROR … unhandled exception on /api/distributions/43/redistribute` |
| 14:34:45 (선후 불확실) | 같은 요청이 nginx 에 500 | nginx:20084 | `교사단말A - - [17/Sep/2026:14:34:45 +0900] "POST /api/distributions/43/redistribute HTTP/1.1" 500 176` |
| 14:39:57.905 / 14:39:58 | 로그 끝. 롤백 · 재기동 · 회복 기록 없음 | app:2938, nginx:20365 | `INFO … no handler for /favicon.ico` / `"GET /api/units HTTP/1.1" 200 613` |

### 2.3 타임라인 보조 집계

배포 후(13:42:00 이후, `app.log` 1264행 이후 · `nginx-access.log` 16872행 이후) 재배포 결과를 distribution 별로 센 결과:

```bash
grep -n 'redistribute HTTP' nginx-access.log | awk '{split($0,a,":"); n=a[1]; match($7,/[0-9]+/); id=substr($7,RSTART,RLENGTH); ph=(n<16873)?"pre":"post"; print ph, id, $9}' | sort | uniq -c
```

| distribution | 학급(app INFO 기준) | 배포 전 결과 | 배포 후 결과 |
|---|---|---|---|
| 41 | 로그에 학급 표기 없음(확인 필요) | 500 × 6 | 호출 없음 |
| 42 | class 1 (`app.log:1274`) | 200 × 5 | **500 × 19** |
| 43 | class 1 (`app.log:1238`) | 200 × 13 | **500 × 20** |
| 44 | class 2 | 200 × 17 | 200 × 1 |
| 5 · 6 · 7 | class 3 | 200 | 200 (5 · 7) |
| 8 | class 2 | 200 × 8 | 200 × 1 |
| 1 · 2 · 3 | — | 409 | 409 |

- 배포 후 200 응답 크기: 640 ~ 910 byte(`nginx-access.log:16884, 17353, 17424, 17734, 19238, 19347`). 배포 전 200 응답은 170 ~ 200 byte 이다. nginx 바이트 수에서도 응답 형식이 바뀐 것이 보인다.
- 배포 후 app ERROR 39건은 모두 바로 앞 줄이 `redistributed distribution 42|43 … class 1` INFO 다. 쓰기 로그가 먼저 나오고 응답만 실패하는 패턴이다.

```bash
awk 'NR>1264' app.log | grep -cE 'redistributed distribution 4[23] '        # → 39
awk 'NR>1264' app.log | grep -c ' ERROR '                                   # → 39
awk 'NR>1264 && / ERROR / {if (prev !~ /redistributed distribution/) print NR} {prev=$0}' app.log   # → 출력 없음
```

- 배포 후 500 39건과 배포 전 distribution 41 의 500 7건(재배포 6건 + 단건 조회 1건)은 모두 한 단말에서 왔다.

```bash
awk 'NR>16872 && $9==500 {print $1, $11}' nginx-access.log | sort | uniq -c
```

```
     19 교사단말A "https://lms.example.com/teacher/distributions/42"
     20 교사단말A "https://lms.example.com/teacher/distributions/43"
```

- 예외에 나온 식별자는 46건 전부 `Assignment` id `9` 다.

```bash
grep -oE 'Caused by: .*identifier value `[0-9]+`' app.log | sort | uniq -c
#      46 Caused by: org.hibernate.FetchNotFoundException: Entity `com.example.assignment.Assignment` with identifier value `9`
```

---

## 3. 가설과 검증

### 3.1 증상 분류

- **증상(가장 많이 나온 오류)**: `POST /api/distributions/{42|43}/redistribute` 의 500 39건과 `JpaObjectRetrievalFailureException: Entity Assignment … identifier value 9 does not exist`. 이 예외 자체를 원인으로 보지 않는다. 아래 가설들은 "왜 배포 후부터 class 1 재배포에서 이 예외가 나는가"를 설명하려는 것이다.
- **별도 증상**: 13:41:51 ~ 13:41:59 의 502 8건(재기동 구간).
- **부수 현상**: 500 을 받은 단말의 반복 재시도(교사단말A, 39회). 그때마다 `redistributed …` 쓰기 로그가 남았다.

### 3.2 가설 표

| ID | 가설 | 설명하는 증상 | 반증 조건(이 로그 · 데이터가 나오면 틀렸다) | 확인 방법(명령 · 볼 파일) |
|---|---|---|---|---|
| H1 | v1.4.2 가 재배포 응답에 넣은 `listByClass(classId)` 가 class 1 의 배포 이력 전체를 읽는다. 그 이력에 **원래부터 있던 고아 참조**(존재하지 않는 Assignment 9 를 가리키는 배포 행, distribution 41 로 추정)가 있어 조회 중 예외가 나고, 재배포 응답이 500 이 된다. 배포 전에는 41 을 직접 열 때만 실패했다 | 배포 후 class 1(42 · 43)만 500, class 2 · 3 은 200. 스택이 `DistributionController.redistribute:40` → `listByClass:38` 로 바뀜. 배포 전 41 에서 같은 id 9 예외 | ① 배포 후 500 중 class 1 이 아닌 배포가 있다 ② 배포 후 class 1 재배포 200 이 있다 ③ 예외 식별자가 9 가 아닌 값이다 ④ DB 에서 distribution 41 이 class 1 이 아니거나, `assignment` 9 가 실제로 있다 ⑤ 배포 후 500 의 스택이 `listByClass` 를 거치지 않는다 | `grep -n 'redistributed distribution' app.log`(학급 · 결과 대조), `grep -oE 'identifier value \`[0-9]+\`' app.log \| sort \| uniq -c`, 배포 후 ERROR 스택의 호출 줄 집계. DB: `distribution` 의 41번 행 `class_id` · `assignment_id`, `assignment` 의 id 9 존재 여부(읽기 전용 조회). 코드: `modern/api/src/main/java/com/example/assignment/DistributionController.java`, `DistributionService.java`, 리포지토리 `findByClassRoomIdOrderByDistributedAtAsc` |
| H2 | 배포와 거의 같은 때에 **데이터가 바뀌었다**(Assignment 9 삭제, class 1 에 배포 행 추가 등). 코드 변경과 상관없이 class 1 조회가 깨졌다 | 13:46 이후 class 1 500 | ① 배포(13:41:48) 전에도 같은 Assignment 9 예외가 있다 ② 13:30 ~ 13:46 사이에 데이터 변경 흔적(DDL · DELETE · 관리 화면 호출)이 없다 | `grep -n 'identifier value \`9\`' app.log` 의 첫 출현 시각. `nginx-access.log` 에서 13:30 ~ 13:46 의 `POST|PUT|DELETE` 요청 목록. DB 감사 로그 · binlog(이번 묶음에는 없음) |
| H3 | `listByClass` 의 조회 쿼리 · 매핑 결함(조인 컬럼이나 `@EntityGraph` 지정 오류)으로 **데이터가 정상인데도** 잘못된 Assignment id 를 읽는다 | 배포 후 class 1 500, `findByClassRoomIdOrderByDistributedAtAsc` 경유 | ① DB 에 `assignment_id = 9` 인 배포 행이 실제로 있고 `assignment` 9 가 없다(H1 쪽 근거) ② 같은 쿼리를 쓰는 class 2 · 3 이 정상이다 ③ 쿼리를 거치지 않는 배포 전 `loadOrThrow`(단건 조회)에서도 같은 id 9 예외가 났다 | `app.log` 에서 배포 전 · 후 스택 경유 메서드 비교(`findWithDetailsById` 와 `findByClassRoomIdOrderByDistributedAtAsc`). 코드: 리포지토리 · `Distribution` 엔티티의 `@ManyToOne` 조인 컬럼. DB: 조인 결과 직접 조회 |
| H4 | 13:41:51 ~ 13:41:59 의 502 는 **단일 호스트 무중단 아님 재기동**(graceful shutdown ~ Tomcat 기동 사이에 받을 upstream 이 없음) 때문이다. 500 과는 따로 생긴 현상이다 | 502 8건 | ① 13:42:01(첫 200) 이후에도 502 가 있다 ② 502 시각이 shutdown 완료(13:41:50.144) ~ Tomcat 기동(13:42:00.290) 밖에 있다 ③ 대기 호스트 API-2 가 같은 시간에 트래픽을 받고 있었다 | `grep -n '" 502 ' nginx-access.log`, `app.log:1240-1264`, `deploy-history.md:3, 18`. nginx upstream 설정 · error log(이번 묶음에는 없음) |
| H5 (전파) | 재배포 쓰기는 앞선 트랜잭션에서 이미 커밋되고, 그 뒤 응답을 만드는 단계(`listByClass`)만 실패한다. 그래서 단말이 실패로 알고 재시도하고, **재배포가 중복 실행돼 건수가 불어났다** | 같은 단말 · 같은 배포(42 · 43)에 500 39회 반복. ERROR 마다 바로 앞에 `redistributed …` INFO | ① ERROR 앞에 `redistributed` INFO 가 없는 500 이 있다 ② DB 의 재배포 이력이 배포 후 42 · 43 에 대해 0건(롤백됨)이다 ③ 재시도가 여러 단말에 흩어져 있다 | `awk 'NR>1264 && / ERROR / {if (prev !~ /redistributed distribution/) print NR} {prev=$0}' app.log`. DB: 13:46 이후 42 · 43 의 재배포 이력 건수. 코드: `DistributionService.redistribute` · `listByClass` 의 `@Transactional` 경계와 컨트롤러 호출 순서 |

> 반증 실행 · 코드 대조 · 판정(4절)과 그 이후 절(요약 · 원인 · 근거 로그 줄 · 수정안 · 재발 방지 · 모니터링 · 확인하지 못한 것)은 요청에 따라 작성하지 않았다.

마스킹 적용: IP 주소 21건 · 이메일 2건 · 학생 식별자 9건 · 토큰 1건 · 비밀번호/접속 문자열 0건
