# 과제 배포(legacy/assignment-thymeleaf) 비즈니스 규칙 후보

분석 기준일: 2026-09-29. 선행 문서는 [ARCHITECTURE.md](ARCHITECTURE.md), [ERD.md](ERD.md) 다.
근거의 파일 경로는 저장소 루트 기준으로 적는다. 비고에서 폴더 없이 쓴 파일 이름(`DistributionService.java:652` 등)은 `legacy/assignment-thymeleaf/src/main/` 아래의 그 파일이다.
소스를 읽어서 정리했다. 화면이나 DB 를 실행하지 않았다.

- 코드(조건문 · SQL · 템플릿 식)에 있는 것만 규칙으로 올렸다. 주석 · 안내 문구뿐인 내용은 9절에 따로 모았다.
- 확신도 "확실"은 조건문 · SQL 로 확인한 것, "추정"은 이름이나 주석으로 짐작한 것이다.
- **"현재 시각"은 `AppClock.now()` 다.** 시스템 속성 `app.clock=system` 이 없으면 `2026-09-15 00:00:00` 고정이다 (legacy/assignment-thymeleaf/src/main/java/com/example/assign/AppClock.java:9, legacy/assignment-thymeleaf/src/main/java/com/example/assign/AppClock.java:14-19). 아래 규칙의 "현재"는 모두 이 값이다.
- **이 모듈에는 과제를 만들거나 상태를 바꾸는 기능, 제출을 넣는 기능이 없다** (`ERD.md` 3.1절). 쓰기는 배포 등록과 재배포 플래그 갱신뿐이다.
- 과제 상태 코드: `O` 진행중 · `X` 연장 · `C` 종료 · `R` 검수중 · `D` 삭제 (legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:181-199).

## 1. 과제 목록 (`GET /assignments`)

#### BR-01
- 규칙: 과제의 상태가 삭제(`D`)이면 과제 목록에서 제외한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/AssignmentDao.java:29
- 근거 코드:
  ```java
  + " WHERE a.status <> 'D' "
  ```
- 확신도: 확실
- 비고: 검수중(`R`) 과제는 목록에 나온다. 같은 조건이 배포 목록에도 있다(BR-05).

#### BR-02
- 규칙: 과제 목록은 마감 시각 오름차순으로, 마감이 같으면 과제 id 오름차순으로 정렬한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/AssignmentDao.java:30
- 근거 코드:
  ```java
  + " ORDER BY a.due_at ASC, a.id ASC";
  ```
- 확신도: 확실
- 비고: 새 배포 폼의 과제 선택 목록도 같은 정렬이다 (AssignmentDao.java:37). 이관 시 행 순서 비교 대상이다.

#### BR-03
- 규칙: 마감이 현재보다 이전이고 상태가 `X` 가 아닌 과제 행은 지난 과제(`past`)로 표시한다.
- 근거: legacy/assignment-thymeleaf/src/main/resources/templates/assignments.html:21
- 근거 코드:
  ```html
  <tr th:each="a : ${rows}" th:classappend="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'} ? 'past'">
  ```
- 확신도: 확실
- 비고: 표시만 바뀐다(회색 글자, layout.html:13). 같은 조건식이 새 배포 폼(BR-11)과 컨트롤러(BR-13)에 따로 구현돼 있다.

#### BR-04
- 규칙: 과제의 "배포 학급 수"는 그 과제가 배포된 학급을 중복 없이 센 수다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/AssignmentDao.java:23
- 근거 코드:
  ```java
  + " (SELECT COUNT(DISTINCT d.class_id) FROM distribution d WHERE d.assignment_id = a.id) AS class_cnt "
  ```
- 확신도: 확실
- 비고: 같은 학급에 두 번 배포된 건(시드의 배포 3 · 4, db/mariadb/init/02-seed.sql:133-134)은 1로 센다.

## 2. 배포 목록 (`GET /distributions`)

#### BR-05
- 규칙: 배포의 과제 상태가 삭제(`D`)이면 배포 목록에서 제외한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:33
- 근거 코드:
  ```java
  + " WHERE a.status <> 'D' "
  ```
- 확신도: 확실
- 비고: `assignment` · `class` 와 INNER JOIN 이므로 (DistributionDao.java:28-29) 과제나 학급 행이 없는 배포도 나오지 않는다.

#### BR-06
- 규칙: 배포 목록은 과제 마감 시각 오름차순으로, 마감이 같으면 배포 id 오름차순으로 정렬한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:34
- 근거 코드:
  ```java
  + " ORDER BY a.due_at ASC, d.id ASC";
  ```
- 확신도: 확실
- 비고: 배포 시각 순이 아니다. 이관 시 행 순서 비교 대상이다.

#### BR-07
- 규칙: 배포의 "제출 수"는 그 배포의 제출 중 제출 시각이 있는 것만 센다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:26
- 근거 코드:
  ```java
  + " (SELECT COUNT(*) FROM submission s WHERE s.distribution_id = d.id AND s.submitted_at IS NOT NULL) AS submitted_cnt "
  ```
- 확신도: 확실
- 비고: `submitted_at` 은 NOT NULL 컬럼이라 (db/mariadb/init/01-schema.sql:109) 현재 스키마에서는 전체 제출 건수와 같다.

#### BR-08
- 규칙: 아직 재배포하지 않았거나(`redistributed == 0`) 과제 상태가 `X` 인 배포에만 재배포 입력 폼을 보여 준다.
- 근거: legacy/assignment-thymeleaf/src/main/resources/templates/distributions.html:31-32
- 근거 코드:
  ```html
  th:if="${d.redistributed == 0 or d.assignmentStatus == 'X'}">
  ```
- 확신도: 확실
- 비고: 서비스는 이미 재배포된 건도 사유가 "시스템 오류"면 허용하는데(BR-39) 화면에서는 폼이 없어 그 경로에 닿을 수 없다. URL 을 직접 POST 하면 닿는다. 마감 경과 여부는 폼 표시 조건에 없다.

#### BR-09
- 규칙: 화면에서 재배포하면 작업자 ID 는 항상 `teacher-01` 로 전송된다.
- 근거: legacy/assignment-thymeleaf/src/main/resources/templates/distributions.html:33
- 근거 코드:
  ```html
  <input type="hidden" name="operator" value="teacher-01">
  ```
- 확신도: 확실
- 비고: 로그인 · 세션 코드가 모듈에 없다. 작업자 관련 규칙(BR-18~BR-20, BR-33)의 다른 분기는 화면으로는 실행되지 않는다.

## 3. 새 배포 (`GET /distributions/new`, `POST /distributions`)

검사 순서: 과제 존재(BR-12) → 마감(BR-13) → 서비스 진입 → 과제 존재(BR-12) → 학급 존재(BR-14) → 삭제 과제(BR-15) → 중복(BR-16).

#### BR-10
- 규칙: 새 배포 폼의 과제 선택 목록에는 상태가 `O` · `X` · `C` 인 과제만 나온다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/AssignmentDao.java:36
- 근거 코드:
  ```java
  + " WHERE a.status IN ('O', 'X', 'C') "
  ```
- 확신도: 확실
- 비고: 검수중(`R`) 과제는 목록에 없지만, 등록 처리에는 `R` 을 막는 검사가 없다(BR-15 비고).

#### BR-11
- 규칙: 선택 목록에서 마감이 현재보다 이전이고 상태가 `X` 가 아닌 과제는 선택할 수 없게(disabled) 표시한다.
- 근거: legacy/assignment-thymeleaf/src/main/resources/templates/distribution_form.html:16
- 근거 코드:
  ```html
  th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"
  ```
- 확신도: 확실
- 비고: 마감이 지난 과제는 이름 뒤에 `[마감]`, `X` 상태면 `[연장]` 이 붙는다 (distribution_form.html:18). 서버 쪽 검사는 BR-13 이다.

#### BR-12
- 규칙: 요청한 과제 id 의 과제가 없으면 배포를 등록하지 않고 오류를 보여 준다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:53-57, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:48-51
- 근거 코드:
  ```java
  AssignmentRow a = assignmentDao.findById(assignmentId);
  if (a == null) {
      ra.addFlashAttribute("error", "존재하지 않는 과제입니다.");
  ```
- 확신도: 확실
- 비고: 같은 검사가 컨트롤러와 서비스에 두 번 있다. 메시지가 다르다(서비스 쪽은 `(id=…)` 가 붙는다). `assignmentId` · `classId` 가 비었거나 숫자가 아닐 때의 응답은 Spring 의 파라미터 변환이 결정하며 확인하지 못했다(미확인 목록).

#### BR-13
- 규칙: 과제의 마감이 현재보다 이전이고 상태가 `X` 가 아니면 새 배포를 등록하지 않는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:60-64
- 근거 코드:
  ```java
  if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
      if (!"X".equals(a.getStatus())) {
  ```
- 확신도: 확실
- 비고: 마감이 현재와 같으면(`isBefore` 가 거짓) 허용한다. 마감이 null 이면 허용한다. 이 검사는 컨트롤러에만 있고 `DistributionService.distribute` 에는 없다. 상태 값을 정규화하지 않고 비교한다(재배포는 정규화한다, BR-27).

#### BR-14
- 규칙: 요청한 학급 id 의 학급이 없으면 배포를 등록하지 않는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:52-55
- 근거 코드:
  ```java
  ClassRow c = classDao.findById(classId);
  if (c == null) {
      throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");
  ```
- 확신도: 확실
- 비고: 없음.

#### BR-15
- 규칙: 과제 상태가 삭제(`D`)이면 새 배포를 등록하지 않는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:56-58
- 근거 코드:
  ```java
  if ("D".equals(a.getStatus())) {
      throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");
  ```
- 확신도: 확실
- 비고: 검수중(`R`) 과제를 막는 검사는 없다. 재배포는 `R` 을 막는다(BR-29).

#### BR-16
- 규칙: 같은 과제가 같은 학급에 이미 배포돼 있으면 새 배포를 등록하지 않는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:59-61, legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:47-51
- 근거 코드:
  ```java
  if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {
  ```
  ```java
  "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",
  ```
- 확신도: 확실
- 비고: DB 에 UNIQUE 제약이 없다. 시드의 배포 3 · 4 는 같은 과제 · 학급이다 (db/mariadb/init/02-seed.sql:133-134).

#### BR-17
- 규칙: 새 배포는 id 를 기존 최대 id + 1 로, 배포 시각을 현재 시각으로, 재배포 여부를 0 으로 저장한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:62-63, legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:55-56, legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:61
- 근거 코드:
  ```java
  Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
  ```
  ```java
  "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",
  ```
- 확신도: 확실
- 비고: 시각이 고정이면 모든 새 배포의 배포 시각이 `2026-09-15 00:00:00` 이 된다. 동시 요청 시의 id 충돌은 확인하지 못했다.

## 4. 재배포 — 입력값 (`POST /distributions/{id}/redistribute`)

#### BR-18
- 규칙: 작업자 ID 는 앞뒤 공백을 제거하고, null 이거나 비어 있으면 `SYSTEM` 으로 바꾼다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:82-89
- 근거 코드:
  ```java
  op = op.trim();
  if (op.length() == 0) {
      op = "SYSTEM";
  ```
- 확신도: 확실
- 비고: 같은 null → 빈 문자열 → `trim` 처리를 사유(BR-21), 과제 상태(BR-27), 과제 제목(BR-32), 학급 이름 · 담당 교사 ID(BR-33), 학생 ID(BR-45)도 받는다.

#### BR-19
- 규칙: 작업자 ID 가 20자를 넘으면 앞 20자만 쓰고 경고한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:90-93
- 근거 코드:
  ```java
  } else if (op.length() > 20) {
      op = op.substring(0, 20);
  ```
- 확신도: 확실
- 비고: 정확히 20자면 자르지 않는다.

#### BR-20
- 규칙: 작업자 ID 가 `SYSTEM` 이 아니고 `teacher-` 나 `admin-` 으로 시작하지도 않으면 경고한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:94-96
- 근거 코드:
  ```java
  if (!op.equals("SYSTEM") && !op.startsWith("teacher-") && !op.startsWith("admin-")) {
  ```
- 확신도: 확실
- 비고: 자른 뒤의 값으로 검사한다. 대소문자를 구분한다. 경고 문구에 작업자 ID 가 들어간다.

#### BR-21
- 규칙: 재배포 사유는 앞뒤 공백을 제거하고, 줄바꿈 · 탭을 공백으로 바꾸고, 연속된 공백을 하나로 줄인다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:99-107
- 근거 코드:
  ```java
  rsn = rsn.trim();
  rsn = rsn.replace("\r", " ").replace("\n", " ").replace("\t", " ");
  while (rsn.contains("  ")) {
  ```
- 확신도: 확실
- 비고: null 은 빈 문자열로 본다. 사유는 DB 에 저장하지 않고 결과 메시지에만 쓴다 (DistributionService.java:652).

#### BR-22
- 규칙: 정리한 사유가 비어 있으면 `(사유 없음)` 으로 바꾼다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:108-110
- 근거 코드:
  ```java
  if (rsn.length() == 0) {
      rsn = "(사유 없음)";
  ```
- 확신도: 확실
- 비고: 경고가 아니라 참고(`notes`)로 남긴다. 사유는 필수가 아니다.

#### BR-23
- 규칙: 정리한 사유가 200자를 넘으면 앞 197자에 `...` 을 붙이고 경고한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:111-114
- 근거 코드:
  ```java
  } else if (rsn.length() > 200) {
      rsn = rsn.substring(0, 197) + "...";
  ```
- 확신도: 확실
- 비고: 정확히 200자면 자르지 않는다. 자른 뒤에 꺾쇠 치환(BR-24)을 하므로 최종 길이는 200자를 넘을 수 있다.

#### BR-24
- 규칙: 사유에 `<` 나 `>` 가 있으면 `&lt;` · `&gt;` 로 바꾼다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:115-118
- 근거 코드:
  ```java
  if (rsn.indexOf('<') >= 0 || rsn.indexOf('>') >= 0) {
      rsn = rsn.replace("<", "&lt;").replace(">", "&gt;");
  ```
- 확신도: 확실
- 비고: 화면은 메시지를 `th:text` 로 출력한다 (layout.html:29). 이중으로 이스케이프돼 `&lt;` 가 글자 그대로 보이는지는 실행하지 않아 확인하지 못했다.

#### BR-25
- 규칙: 사유에 들어 있는 낱말로 사유를 분류하며, 여러 낱말이 있으면 아래 표의 위쪽이 이긴다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:119-130
- 근거 코드:
  ```java
  if (rsn.contains("오류") || rsn.contains("장애")) {
      reasonCategory = 1;
  } else if (rsn.contains("연장") || rsn.contains("추가")) {
  ```
- 확신도: 확실
- 비고: 분류 값은 재배포 재허용(BR-39)과 재제출 대상 계산(BR-49)에 쓰인다. 꺾쇠 치환 · 자르기가 끝난 문자열로 판정한다.

  | 순서 | 포함 낱말 | 분류 값 | 이름 | 줄 |
  |---|---|---|---|---|
  | 1 | `오류` 또는 `장애` | 1 | 시스템 오류 | 120-121 |
  | 2 | `연장` 또는 `추가` | 2 | 기간 연장 | 122-123 |
  | 3 | `정정` 또는 `수정` | 3 | 문항 정정 | 124-125 |
  | 4 | `재시험` 또는 `재평가` | 4 | 재평가 | 126-127 |
  | 5 | 전체가 `(사유 없음)` | 9 | 없음 | 128-129 |
  | 6 | 그 밖 | 0 | 기타 | 119, 147-149 |

#### BR-26
- 규칙: 입력값 · 데이터에 대한 경고는 재배포를 막지 않으며, 재배포가 성공했을 때만 화면에 "주의"로 표시한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:92, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:95, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:113, legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:91-98, legacy/assignment-thymeleaf/src/main/resources/templates/layout.html:31-33
- 근거 코드:
  ```java
  if (r.isOk()) {
      ra.addFlashAttribute("message", r.getMessage());
      if (!r.getWarnings().isEmpty()) {
          ra.addFlashAttribute("warnings", r.getWarnings());
  ```
- 확신도: 확실
- 비고: 재배포를 멈추는 것은 E101~E107 뿐이다(BR-28~BR-30, BR-38~BR-40, BR-52). 실패하면 그때까지 쌓인 경고는 화면에 나오지 않는다. 참고(`notes`)는 어떤 경우에도 화면에 나오지 않는다.

## 5. 재배포 — 배포 · 과제 · 학급 점검

#### BR-27
- 규칙: 재배포에서 과제 상태 코드는 null 을 빈 문자열로 바꾸고, 앞뒤 공백을 제거하고, 대문자로 바꾼 뒤 비교한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:172-176
- 근거 코드:
  ```java
  st = st.trim().toUpperCase();
  ```
- 확신도: 확실
- 비고: 새 배포(BR-13, BR-15)와 템플릿(BR-03, BR-08, BR-11), SQL(BR-01, BR-05, BR-10)은 정규화 없이 비교한다. SQL 비교가 대소문자를 구분하는지는 콜레이션이 결정하며 확인하지 못했다.

#### BR-28
- 규칙: 요청한 id 의 배포가 없으면 재배포를 거부한다(E101).
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:153-160
- 근거 코드:
  ```java
  DistributionRow dist = distributionDao.findById(distributionId);
  if (dist == null) {
      result.setOk(false);
      result.setCode("E101");
  ```
- 확신도: 확실
- 비고: `findById` 는 `assignment` · `class` 와 INNER JOIN 이라 (DistributionDao.java:28-29) 과제나 학급 행이 없는 배포도 E101 이 된다. 그래서 "과제 없음" E102 (DistributionService.java:165-171) 와 "학급 없음" 경고 (DistributionService.java:242-245) 분기는 이 조회를 거쳐서는 닿기 어렵다(조회 사이에 행이 지워진 경우만 가능, 추정).

#### BR-29
- 규칙: 과제 상태가 삭제(`D`)이면 E103, 검수중(`R`)이면 E104 로 재배포를 거부한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:194-204, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:215-221
- 근거 코드:
  ```java
  case "D":
      statusLabel = "삭제";
      result.setOk(false);
      result.setCode("E103");
  ```
  ```java
  if (underReview) {
      result.setOk(false);
      result.setCode("E104");
  ```
- 확신도: 확실
- 비고: 배포 목록은 `D` 과제의 배포를 보여 주지 않으므로(BR-05) E103 은 URL 직접 호출에서만 나온다.

#### BR-30
- 규칙: 과제 상태가 비어 있거나 `O` · `X` · `C` · `R` · `D` 가 아닌 값이면 경고하고 진행중(`O`)과 같게 처리한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:205-212
- 근거 코드:
  ```java
  case "":
      statusLabel = "미지정";
      warnings.add("과제 상태 코드가 비어 있습니다. 진행중으로 간주합니다.");
      break;
  default:
  ```
- 확신도: 확실
- 비고: 빈 값과 알 수 없는 값은 경고 문구가 다르다. 둘 다 `extended` · `closedByTeacher` · `underReview` 가 거짓이어서 `O` 와 같은 길을 간다. `status` 는 NOT NULL 컬럼이다 (db/mariadb/init/01-schema.sql:87).

#### BR-31
- 규칙: 과제 상태가 종료(`C`)여도 재배포를 바로 거부하지 않고 마감 판정(BR-37~BR-38)에 따른다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:190-193, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:222-225
- 근거 코드:
  ```java
  if (closedByTeacher && !extended) {
      // 종료 처리된 과제라도 마감 후 3일 안이면 아래 마감 판정에 맡긴다
      notes.add("교사 종료 과제 → 마감 후 유예 기간 판정으로 진행");
  ```
- 확신도: 확실
- 비고: `C` 는 허용 · 거부 결과를 바꾸지 않는다. 참고 문구만 추가한다(DistributionService.java:224, DistributionService.java:326-328). 결과적으로 `C` 와 `O` 의 판정은 같다.

#### BR-32
- 규칙: 메시지에 쓰는 과제 제목은 앞뒤 공백을 제거하고, 비어 있으면 `(제목 없음)` 으로 바꾸고 경고하며, 40자를 넘으면 앞 40자에 `…` 을 붙인다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:226-236
- 근거 코드:
  ```java
  } else if (title.length() > 40) {
      title = title.substring(0, 40) + "…";
  ```
- 확신도: 확실
- 비고: 40자 자르기는 경고하지 않는다. E103 · E104 메시지는 자르기 전의 원래 제목을 쓴다 (DistributionService.java:202, DistributionService.java:218).

#### BR-33
- 규칙: 학급의 담당 교사 ID 가 비어 있거나, `teacher-` 로 시작하지 않거나, 길이가 10자가 아니면 경고하고 재배포는 계속한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:251-258
- 근거 코드:
  ```java
  if (teacherId.length() == 0) {
      warnings.add("학급 담당 교사가 지정되지 않았습니다.");
  } else if (!teacherId.startsWith("teacher-")) {
  ```
- 확신도: 확실
- 비고: 세 조건은 `else if` 로 이어져 하나만 경고한다. 학급 이름이 비어 있으면 `학급 #<id>` 로 바꾼다(경고 없음, DistributionService.java:247-250).

#### BR-34
- 규칙: 작업자가 담당 교사가 아니어도 재배포를 막지 않고 참고로만 기록한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:259-261
- 근거 코드:
  ```java
  if (!op.equals("SYSTEM") && teacherId.length() > 0 && !op.equals(teacherId) && !op.startsWith("admin-")) {
      notes.add("담당 교사(" + teacherId + ")가 아닌 작업자(" + op + ")의 재배포");
  ```
- 확신도: 확실
- 비고: 권한 검사가 아니다. 화면은 항상 `teacher-01` 을 보내므로(BR-09) 시드의 6학년 1반(`teacher-02`, db/mariadb/init/02-seed.sql:114) 배포에서는 이 참고가 기록된다.

## 6. 재배포 — 마감 판정과 허용 여부

#### BR-35
- 규칙: 과제에 마감 시각이 없으면 경고하고 마감 규칙 없이 재배포를 허용한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:269-273, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:310-311
- 근거 코드:
  ```java
  if (due == null) {
      hoursAfterDue = Long.MIN_VALUE;
      dueBucket = -1;
  ```
- 확신도: 확실
- 비고: `due_at` 은 NOT NULL 컬럼이라 (db/mariadb/init/01-schema.sql:86) 현재 스키마에서는 생기지 않는 경우다. 이미 재배포된 건 검사(BR-39)는 그대로 받는다.

#### BR-36
- 규칙: 현재 시각에서 마감 시각을 뺀 값으로 마감 구간을 아래 표와 같이 나눈다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:275-302
- 근거 코드:
  ```java
  Duration diff = Duration.between(due, now);
  hoursAfterDue = diff.toHours();
  long minutesAfterDue = diff.toMinutes();
  if (minutesAfterDue < -72 * 60) {
  ```
- 확신도: 확실
- 비고: 마감 전은 분 단위, 마감 후는 시간 단위(분 버림)로 비교한다. 초는 `toMinutes` 에서 버려지므로 마감 앞뒤 59초까지는 "정각"일 것이다(음수 버림 방향은 실행으로 확인하지 않음). 구간 5 · 6 의 구분은 판정 결과에 영향이 없고 문구만 다르다.

  | 조건 (위에서부터 처음 맞는 것) | 구간 | 문구 | 줄 |
  |---|---|---|---|
  | 마감까지 72시간보다 많이 남음 (`분 < -4320`) | 0 | 마감 전 | 278-280 |
  | 마감까지 24시간 초과 ~ 72시간 이하 남음 (`분 < -1440`) | 1 | 마감 임박 | 281-283 |
  | 마감까지 1분 ~ 24시간 남음 (`분 < 0`) | 2 | 마감 당일 | 284-286 |
  | 마감과 같은 분 (`분 == 0`) | 2 | 마감 시각 정각 | 287-289 |
  | 마감 후 1분 이상, 72시간 미만 (`시간 < 72`) | 3 | 유예 기간 | 290-292 |
  | 마감 후 72시간 이상, 7일 미만 (`시간 < 168`) | 4 | 유예 종료 | 293-295 |
  | 마감 후 7일 이상, 30일 미만 (`시간 < 720`) | 5 | N일 경과 | 296-298 |
  | 마감 후 30일 이상 | 6 | N개월 이상 경과 | 299-301 |

#### BR-37
- 규칙: 마감 구간이 2 이하(마감 전 또는 마감과 같은 분)이면 과제 상태와 관계없이 마감 때문에 거부하지 않는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:312-313
- 근거 코드:
  ```java
  } else if (dueBucket <= 2) {
      allowed = true;
  ```
- 확신도: 확실
- 비고: 없음.

#### BR-38
- 규칙: 마감이 지난 뒤에는 과제 상태가 `X` 이면 기간과 관계없이 허용하고, `X` 가 아니면 마감 후 72시간 미만일 때만 허용하며 72시간 이상이면 거부한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:314-325
- 근거 코드:
  ```java
  } else if (extended) {
      allowed = true;
      notes.add("상태 '연장' → 마감 경과에도 재배포 허용");
  } else {
      // 재배포는 마감 후 7일 이내에만 허용한다 (교무 지침 §4.2)
      if (hoursAfterDue >= 72) {
          allowed = false;
  ```
- 확신도: 확실
- 비고: **주석과 코드가 다르다.** 주석은 "7일 이내"(DistributionService.java:318)인데 코드는 72시간(3일)이다. 다른 주석은 "3일"이라고 적는다 (DistributionService.java:223). 새 배포는 마감이 지나면 유예 없이 거부한다(BR-13). 거부 코드는 BR-40 을 따른다.

#### BR-39
- 규칙: 이미 재배포된 배포(`redistributed == 1`)는 과제 상태가 `X` 이거나 사유 분류가 시스템 오류(1)일 때만 다시 재배포할 수 있고, 아니면 거부한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:329-338
- 근거 코드:
  ```java
  if (allowed && dist.getRedistributed() == 1) {
      if (extended) {
          notes.add("이미 재배포된 건이지만 '연장' 상태 → 재배포 다시 허용");
      } else if (reasonCategory == 1) {
  ```
- 확신도: 확실
- 비고: 마감 판정(BR-37 · BR-38)을 통과한 뒤에만 검사한다. 사유에 `오류` 나 `장애` 라는 낱말만 넣으면 통과한다(BR-25). 재배포 횟수는 세지 않으므로 조건만 맞으면 횟수 제한이 없다. 화면 폼 조건(BR-08)과 다르다.

#### BR-40
- 규칙: 재배포를 거부할 때 코드는 이미 재배포된 배포면 E106, 아니면 E105 다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:339-345
- 근거 코드:
  ```java
  result.setCode(dist.getRedistributed() == 1 ? "E106" : "E105");
  ```
- 확신도: 확실
- 비고: 코드는 거부 이유가 아니라 `redistributed` 값으로 정한다. 이미 재배포된 배포가 마감 경과로 거부되면 코드는 E106 인데 메시지는 마감 유예 문구다. 코드는 화면에 나오지 않고 표준 출력에만 남는다 (DistributionService.java:343).

## 7. 재배포 — 제출 집계

#### BR-41
- 규칙: 제출 한 건은 제출 시각과 점수의 유무로 미제출 · 채점완료(제출시각없음) · 채점전 · 채점완료 넷 중 하나로 분류한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:407-422
- 근거 코드:
  ```java
  if (at == null && sc == null) {
      notSubmitted++;
  } else if (at == null && sc != null) {
  ```
- 확신도: 확실
- 비고: 제출 시각 없이 점수만 있으면 채점완료로 세고 경고한다. `submitted_at` 이 NOT NULL 이라 (db/mariadb/init/01-schema.sql:109) 현재 스키마에서는 앞의 두 분류에 들어갈 행이 없다. 따라서 미제출 수는 항상 0 이다(추정: 스키마가 시드와 같을 때).

  | 제출 시각 | 점수 | 분류 | 줄 |
  |---|---|---|---|
  | 없음 | 없음 | 미제출 | 407-410 |
  | 없음 | 있음 | 채점완료(제출시각없음) + 경고 | 411-415 |
  | 있음 | 없음 | 채점전 | 416-418 |
  | 있음 | 있음 | 채점완료 | 419-421 |

#### BR-42
- 규칙: 제출 시각이 과제 마감보다 뒤이면 지연 제출로 센다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:428-434
- 근거 코드:
  ```java
  if (due != null && at.isAfter(due)) {
      late++;
  ```
- 확신도: 확실
- 비고: 마감과 같은 시각은 지연이 아니다. 지연이어도 제출은 유지되고 점수도 집계에 들어간다.

#### BR-43
- 규칙: 점수는 90 이상 A, 80 이상 B, 70 이상 C, 60 이상 D, 60 미만 F 로 등급을 매기고, 100 과 같으면 만점으로 센다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:454-474
- 근거 코드:
  ```java
  if (sc.compareTo(new BigDecimal("90")) >= 0) {
      bandA++;
      grade = "A";
  } else if (sc.compareTo(new BigDecimal("80")) >= 0) {
  ```
- 확신도: 확실
- 비고: 통계 문구와 참고에만 쓰인다. DB 에 저장하지 않는다. 성적 모듈(`legacy/grade-mssql`)의 등급 기준과 같은지는 확인하지 않았다.

#### BR-44
- 규칙: 점수가 0 미만이거나 100 초과이면 경고하되, 합계 · 평균 · 최저 · 최고 · 등급 집계에는 그대로 넣는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:443-447
- 근거 코드:
  ```java
  if (sc.compareTo(BigDecimal.ZERO) < 0 || sc.compareTo(new BigDecimal("100")) > 0) {
      scoreOutOfRange++;
      warnings.add(sid + " : 점수 범위 이탈 (" + sc + ")");
  }
  sum = sum.add(sc);
  ```
- 확신도: 확실
- 비고: 100 초과 점수는 A 등급이 되지만 만점으로는 세지 않는다. 경고 문구에 학생 ID 가 들어간다(8절).

#### BR-45
- 규칙: 학생 ID 는 앞뒤 공백 제거 후 `STU-` 로 시작하는 8자이고 뒤 4자가 숫자여야 하며, 어긋나면 건수를 세어 경고한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:377-398, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:506-508
- 근거 코드:
  ```java
  } else if (!sid.startsWith("STU-")) {
      badStudentId++;
  } else if (sid.length() != 8) {
  ```
- 확신도: 확실
- 비고: 빈 ID · 접두어 다름 · 길이 다름 · 숫자 아님 네 경우를 각각 1건으로 센다. 형식이 틀려도 그 제출은 집계에 포함한다. 빈 ID 는 `(학생ID 없음 #<제출 id>)` 로 바꿔 쓴다.

#### BR-46
- 규칙: 같은 배포 안에서 같은 학생 ID 의 제출이 두 번 이상 나오면 둘째부터 중복으로 세어 경고한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:399-403, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:503-505
- 근거 코드:
  ```java
  if (seenStudents.contains(sid)) {
      dupStudent++;
  ```
- 확신도: 확실
- 비고: 중복 제출도 건수 · 점수 집계에는 모두 들어간다. 비교는 `trim` 뒤의 값으로 하며 대소문자를 구분한다.

#### BR-47
- 규칙: 제출 시각이 현재보다 미래이면 경고한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:424-427, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:576-578
- 근거 코드:
  ```java
  if (at.isAfter(now)) {
      futureSubmit++;
  ```
- 확신도: 확실
- 비고: 현재 시각이 `2026-09-15 00:00:00` 고정이라, 시드의 제출 `2026-09-15 08:20:00` (db/mariadb/init/02-seed.sql:174) 같은 행은 미래 제출로 경고된다(계산, 실행하지 않음).

#### BR-48
- 규칙: 재배포는 기존 제출을 지우거나 바꾸지 않는다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:484-485, legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:65-66, legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:69-72
- 근거 코드:
  ```java
  return jdbc.update("UPDATE distribution SET redistributed = 1 WHERE id = ?", id);
  ```
- 확신도: 확실
- 비고: `submission` 에 쓰는 SQL 이 모듈에 없다(`ERD.md` 3.1절). 재배포가 DB 에 남기는 변화는 `redistributed = 1` 하나다. "유지 건수"는 전체 − 미제출이다 (DistributionService.java:485).

#### BR-49
- 규칙: 재제출 대상 수는 기본이 미제출 수이고, 과제 상태가 `X` 이면 채점전 수를 더하며, 사유 분류가 재평가(4)이면 전체 제출 수로 한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:486-496
- 근거 코드:
  ```java
  int reopen = notSubmitted;
  if (extended) {
      reopen = notSubmitted + submittedNoScore;
  ```
  ```java
  if (reasonCategory == 4) {
      reopen = total;
  ```
- 확신도: 확실
- 비고: 재평가가 `X` 보다 나중에 적용돼 이긴다. 이 수는 메시지 문구로만 나가고 (DistributionService.java:648) DB 에는 어떤 제출도 "재제출 가능"으로 표시되지 않는다. 실제로 학생이 다시 낼 수 있게 하는 코드는 이 모듈에 없다.

#### BR-50
- 규칙: 제출률은 (전체 − 미제출) × 100 ÷ 전체를 반올림한 정수이고, 평균은 점수 합 ÷ 채점 건수를 소수 첫째 자리까지 반올림(HALF_UP)한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:522-523, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:537-538
- 근거 코드:
  ```java
  int rate = total == 0 ? 0 : (int) Math.round(submittedAny * 100.0 / total);
  ```
  ```java
  BigDecimal avg = sum.divide(new BigDecimal(graded), 1, RoundingMode.HALF_UP);
  ```
- 확신도: 확실
- 비고: 제출이 0건이면 제출률을 계산하지 않는다 (DistributionService.java:518). 그 안쪽의 `total == 0 ? 0 :` 은 닿지 않는 분기다. 제출률의 분모는 학급 인원이 아니라 제출 행 수다.

#### BR-51
- 규칙: 제출률 · 평균 · 등급 분포가 아래 조건에 해당하면 경고나 참고를 남긴다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:525-535, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:554-561, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:582-586
- 근거 코드:
  ```java
  if (avg.compareTo(new BigDecimal("40")) < 0) {
  ```
  ```java
  if (bandF > graded / 2) {
      warnings.add("채점자 절반 이상이 F");
  ```
- 확신도: 확실
- 비고: **문구와 코드가 다르다.** "절반 이상"이라고 쓰지만 코드는 정수 나눗셈 뒤 "초과"다. 채점 4건 중 F 2건이면 경고하지 않고, 3건 중 2건이면 경고한다.

  | 조건 | 종류 | 줄 |
  |---|---|---|
  | 제출률 100 | 참고 | 525-526 |
  | 제출률 80 이상 100 미만 | 참고 | 527-528 |
  | 제출률 50 이상 80 미만 | 참고 | 529-530 |
  | 제출률 1 이상 50 미만 | 경고 | 531-532 |
  | 제출률 0 | 경고 | 533-534 |
  | 평균 40 미만 | 경고 | 554-555 |
  | 평균 95 초과이고 채점 5건 이상 | 참고 | 556-557 |
  | F 건수 > 채점 건수 ÷ 2 (정수 나눗셈) | 경고 | 559-560 |
  | 채점 3건 이상이고 모두 같은 등급 | 참고 | 582-585 |

## 8. 재배포 — 갱신과 결과

#### BR-52
- 규칙: 재배포가 허용되면 배포의 `redistributed` 를 1 로 바꾸고, 바뀐 행이 0건이면 E107 로 실패 처리한다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:620-626
- 근거 코드:
  ```java
  int updated = distributionDao.markRedistributed(distributionId);
  if (updated == 0) {
      result.setOk(false);
      result.setCode("E107");
  ```
- 확신도: 확실
- 비고: 이미 1 인 행에 다시 1 을 쓸 때 갱신 건수가 0 으로 돌아와 E107 이 되는지는 JDBC 드라이버 설정(`useAffectedRows`)이 결정하며 확인하지 못했다(미확인 목록). 이미 재배포된 건의 재허용(BR-39)이 실제로 성공하는지가 여기에 달려 있다.

#### BR-53
- 규칙: 성공한 재배포의 결과 코드는 경고가 없으면 `OK`, 1~2건이면 `OK-W`, 3건 이상이면 `OK-W<경고 건수>` 다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:685-691
- 근거 코드:
  ```java
  } else if (warnings.size() <= 2) {
      result.setCode("OK-W");
  } else {
      result.setCode("OK-W" + warnings.size());
  ```
- 확신도: 확실
- 비고: 코드는 화면에 나오지 않고 표준 출력에만 남는다 (DistributionService.java:692-693).

#### BR-54
- 규칙: 재배포 처리 중 DB 예외가 나면 "DB 오류로 재배포에 실패했습니다." 를 보여 주고 배포 목록으로 돌아간다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/web/DistributionController.java:85-90
- 근거 코드:
  ```java
  } catch (DataAccessException e) {
      ra.addFlashAttribute("error", "DB 오류로 재배포에 실패했습니다.");
      return "redirect:/distributions";
  ```
- 확신도: 확실
- 비고: 예외 내용은 로그에 남기지 않는다. 새 배포도 같은 방식이다 (DistributionController.java:72-75). 조회 화면의 DB 예외는 `db_error` 화면으로 가고 원인 메시지를 화면에 그대로 출력한다 (DbErrorAdvice.java:11-16, db_error.html:8).

#### BR-55
- 규칙: 재배포 요청마다 결과와 관계없이 `[REDIST]` 로 시작하는 감사 줄을 표준 출력에 남기고, 성공했으면 경고도 한 줄씩 남긴다.
- 근거: legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:79, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:158, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:692-698
- 근거 코드:
  ```java
  System.out.println("[REDIST]   warn: " + warnings.get(i));
  ```
- 확신도: 확실
- 비고: 경고 문구 일부에 학생 ID(`STU-…`)가 들어간다 (DistributionService.java:415, DistributionService.java:426, DistributionService.java:445). 같은 경고가 화면에도 나온다(BR-26). 이관 시 `modern/api` 의 로그 규칙(학생 식별자 금지)과 충돌한다.

## 9. 규칙으로 올리지 않은 것

주석 · 안내 문구에만 있고 그대로 실행하는 코드가 없는 내용이다.

| 내용 | 위치 | 올리지 않은 이유 |
|---|---|---|
| "재배포는 마감 후 7일 이내에만 허용한다 (교무 지침 §4.2)" | DistributionService.java:318 | 코드는 72시간이다(BR-38). 교무 지침 문서는 저장소에 없다 |
| "마감 후 3일 안이면" | DistributionService.java:223 | 주석. 실제 기준은 BR-38 의 72시간 |
| "재배포는 진행하지만 알림은 발송되지 않습니다" | DistributionService.java:245 | 알림을 보내는 코드가 모듈에 없다 |
| "과제 상태 '연장' → 마감 판정을 완화합니다" | DistributionService.java:188 | 문구. 실제 동작은 BR-38 |
| "연장 상태면 채점 전 제출자도 다시 낼 수 있다" | DistributionService.java:488 | 대상 수 계산만 한다(BR-49). 다시 낼 수 있게 하는 코드는 없다 |
| "제출 데이터 없음 → 학급 전원이 재제출 대상" | DistributionService.java:510 | 문구. 계산된 재제출 대상 수는 0 이다 (DistributionService.java:486) |
| "점수 변경이 필요하면 성적 모듈에서 처리하세요" | DistributionService.java:498 | 안내 문구. 성적 모듈과의 연동 코드는 없다 |
| "같은 학급 + 같은 과제 는 한 건만 존재해야 한다" | DistributionDao.java:46 | 새 배포의 검사만 BR-16 으로 올렸다. 기존 데이터에 대한 보장은 없다 |
| "운영 반영 전 LocalDateTime.now() 로 되돌릴 것" | AppClock.java:8 | 되돌려지지 않았다 |
| "O=진행 C=마감" | db/mariadb/init/01-schema.sql:87 | 스키마 주석. 코드는 5개 상태를 다룬다 |
| "제출 건수 집계는 submitted_at 기준, 여기 계산은 score 포함 → 다를 수 있음" | DistributionService.java:637 | 주석. 차이가 나면 참고만 남긴다 (DistributionService.java:636-638) |

위 파일 이름은 `legacy/assignment-thymeleaf/src/main/java/com/example/assign/` 아래 `service/` · `dao/` · 루트에 있다.

## 10. 매직 넘버

| 값 | 위치 (DistributionService.java) | 추정 의미 |
|---|---|---|
| `20` | 90-91 | 작업자 ID 최대 길이 |
| `200`, `197` | 111-112 | 사유 최대 길이, 말줄임 3자를 뺀 길이 |
| `1` · `2` · `3` · `4` · `9` · `0` | 119-129 | 사유 분류 값(BR-25) |
| `40` | 234-235 | 메시지에 쓰는 제목 최대 길이 |
| `10` | 256 | 담당 교사 ID 길이(`teacher-` 8자 + 숫자 2자) |
| `-72 * 60`, `-24 * 60` | 278, 281 | 마감 전 72시간 · 24시간(분) |
| `72` | 290, 319 | 마감 후 유예 시간. 같은 값이 두 곳에 있다 |
| `24 * 7`, `24 * 30` | 293, 296 | 7일 · 30일(시간) |
| `-1` ~ `6` | 271-300 | 마감 구간(BR-36) |
| `8`, `4` | 383, 386 | 학생 ID 길이, 접두어 `STU-` 길이 |
| `100`, `0` | 443, 454 | 점수 범위, 만점 |
| `90` · `80` · `70` · `60` | 458-467 | 등급 경계 |
| `100` · `80` · `50` | 525-529 | 제출률 구간 |
| `40`, `95`, `5` | 554, 556 | 평균 경고 기준, 평균 참고 기준, 최소 채점 건수 |
| `3` | 583 | "전원 같은 등급" 참고의 최소 채점 건수 |
| `30` | 672 | 요약에 나열하는 학생 수 상한 |
| `2` | 687 | `OK-W` 와 `OK-W<n>` 의 경계 |
| `"2026-09-15 00:00:00"` | AppClock.java:9 | 고정된 현재 시각 |

## 11. 시드 데이터에 규칙을 적용한 계산 (실행하지 않음)

현재 시각 `2026-09-15 00:00:00`, 시드 (db/mariadb/init/02-seed.sql:120-125, db/mariadb/init/02-seed.sql:131-138) 기준으로 손으로 계산한 값이다. 실행 결과가 아니다.

| 과제 | 상태 | 마감 | 마감 구간 | 새 배포(BR-13) | 그 과제의 배포 재배포(BR-37~BR-40) |
|---|---|---|---|---|---|
| 1 | C | 2026-08-20 23:59 | 5 (25일 경과) | 거부 | 배포 1 · 2: 거부 E105 |
| 2 | C | 2026-08-28 23:59 | 5 (17일 경과) | 거부 | 배포 3: 거부 E105, 배포 4(`redistributed=1`): 거부 E106(메시지는 마감 유예 문구) |
| 3 | O | 2026-09-10 23:59 | 4 (96시간 경과) | 거부 | 배포 5: 거부 E105. 주석대로 7일이면 허용됐을 경우다 |
| 4 | O | 2026-09-25 23:59 | 0 | 허용 | 배포 6: 허용 |
| 5 | O | 2026-10-02 23:59 | 0 | 허용 | 배포 7: 허용 |
| 6 | O | 2026-09-30 23:59 | 0 | 허용 | 배포 8: 허용 |

## 12. 표본 점검 추천

사람이 코드와 직접 대조해 볼 규칙 3개다.

| 규칙 | 고른 이유 | 확인할 곳 |
|---|---|---|
| BR-48 재배포는 기존 제출을 지우지 않는다 | 가장 그럴듯해 보이는 규칙이다. 주석과도 맞아서 의심 없이 넘어가기 쉽다. "쓰는 SQL 이 없다"는 부재 증명이라 DAO 전체를 봐야 한다 | legacy/assignment-thymeleaf/src/main/java/com/example/assign/dao/DistributionDao.java:59-72, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:484-496 |
| BR-39 이미 재배포된 건의 재허용 (+ BR-08, BR-52) | 근거가 템플릿 · 서비스 · DAO 세 파일에 걸쳐 있고, 화면 조건과 서비스 조건이 다르다 | legacy/assignment-thymeleaf/src/main/resources/templates/distributions.html:32, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:329-341, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:620-626 |
| BR-38 마감 후 72시간 유예 | 주석(7일)과 코드(72시간)가 다르다. 어느 쪽이 의도인지는 사람이 정해야 한다 | legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:290, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:318-319, legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:223 |

## 13. 미확인 목록

| 항목 | 미확인 사유 |
|---|---|
| 마감 기준이 3일인지 7일인지 (BR-38) | 주석과 코드가 다르다. 교무 지침 §4.2 가 저장소에 없다 |
| 이미 1 인 `redistributed` 를 다시 갱신할 때의 갱신 건수 (BR-52) | MariaDB 드라이버 설정이 결정한다. 실행하지 않았다 |
| `assignmentId` · `classId` · 경로의 `id` 가 비었거나 숫자가 아닐 때의 응답 (BR-12) | Spring 의 파라미터 변환 오류 처리에 달려 있다. 실행하지 않았다. 음수 · `0` 은 숫자로 변환되므로 "없는 과제 · 배포"(BR-12, BR-28) 경로로 간다 |
| SQL 의 상태 비교가 대소문자를 구분하는지 (BR-27) | 콜레이션 `utf8mb4_unicode_ci` 가 결정한다. 실행하지 않았다 |
| 사유의 `&lt;` 가 화면에 어떻게 보이는지 (BR-24) | 실행하지 않았다 |
| `@Transactional` 과 결과 객체 반환 방식의 롤백 동작 | E107 은 예외가 아니라 반환이라 롤백되지 않을 것으로 보이나(추정) 실행하지 않았다 |
| `trim()` 이 전각 공백을 지우는지 (BR-18, BR-21) | Java `String.trim` 의 동작 범위를 실행으로 확인하지 않았다 |
| 실제 데이터에 `X` · `R` · `D` 상태, null 마감, 미제출 행이 있는지 | 시드에 없다. 운영 데이터를 볼 수 없다 |
| 성적 모듈과 등급 기준이 같은지 (BR-43) | `legacy/grade-mssql` 을 이 분석에서 읽지 않았다 |
