# 성적 집계(legacy/grade-mssql) 비즈니스 규칙 후보

분석 기준일: 2026-09-29. 선행 문서는 [ARCHITECTURE.md](ARCHITECTURE.md), [ERD.md](ERD.md) 다.
소스를 읽어서 정리했다. 화면이나 DB 를 실행하지 않았다.

근거의 파일 경로는 저장소 루트 기준으로 적는다. 비고와 표에서는 아래 줄임을 쓴다.

| 줄임 | 가리키는 곳 |
|---|---|
| `AGG:` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql` (집계 프로시저) |
| `REP:` | `legacy/grade-mssql/sql/usp_class_report.sql` (보고 프로시저) |
| `CTL:` | `legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java` |
| `SCH:` | `db/mssql/init/01-schema.sql` |
| `SEED:` | `db/mssql/init/02-seed.sql` |

> **전제 1 — 실행되는 프로시저는 `db/mssql/init/04-procs.sql` 이다** (db/mssql/entrypoint.sh:55). 아래 근거는 본문이 같은 읽기용 사본의 줄번호다 (legacy/grade-mssql/sql/usp_aggregate_grades.sql:10-11). `04-procs.sql` 의 줄번호는 집계가 사본 + 5, 보고가 사본 + 443 이다.
>
> **전제 2 — "현재 시각"은 DB 서버의 `SYSDATETIME()` 이고 기록에만 쓴다** (legacy/grade-mssql/sql/usp_aggregate_grades.sql:30, legacy/grade-mssql/sql/usp_class_report.sql:30). 지연 판정은 현재 시각과 무관하다.
>
> **전제 3 — 앱은 기본 계정 `app` 으로 접속한다** (legacy/grade-mssql/src/main/resources/application.properties:6).

- 코드(조건문 · SQL)에 있는 것만 규칙으로 올렸다. 주석뿐인 내용은 6절에 따로 모았다.
- 확신도 "확실"은 조건문 · SQL 로 확인한 것, "추정"은 이름이나 주석으로 짐작한 것이다.
- 용어: **유효 제출**은 무효(`X`)가 아닌 제출 중 학생 · 과제별로 가장 늦은 1건이다(BR-08, BR-09).
- 이 모듈에는 학급 · 학생 · 과제 · 제출을 만들거나 고치는 기능이 없다. 쓰기는 집계 결과 저장과 처리 시각 기록뿐이다(`ERD.md` 3절).


## 1. 요청 처리 (Java 호출부)

#### BR-01
- 규칙: `class_id` 가 없거나, 비어 있거나, 공백뿐이면 학급 `C1` 로 조회한다.
- 근거: legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:23, legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:83-86
- 근거 코드:
  ```java
  if (classId == null || classId.isBlank()) {
      return DEFAULT_CLASS;
  }
  ```
- 확신도: 확실
- 비고: `/aggregate` 와 `/report` 가 같은 함수를 쓴다 (CTL:48, CTL:64). 오류나 안내 없이 기본 학급의 결과를 보여 주고, 그 학급의 테이블도 갱신한다(BR-36).

#### BR-02
- 규칙: `class_id` 는 앞뒤 공백을 제거한 값으로 조회한다.
- 근거: legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:87
- 근거 코드:
  ```java
  return classId.trim();
  ```
- 확신도: 확실
- 비고: 길이 · 형식 · 대소문자는 검사하지 않는다. 숫자(`0`, `-1`)나 임의 문자열은 그대로 프로시저에 넘어가 없는 학급(BR-06, BR-24)으로 처리된다. 프로시저의 인자는 `VARCHAR(10)` 이다 (AGG:24). 10자를 넘는 값의 처리는 확인하지 못했다(미확인 목록).

#### BR-03
- 규칙: 프로시저 호출에서 DB 예외가 나면 상태 코드 502 와 함께 DB 의 오류 메시지를 화면에 보여 준다.
- 근거: legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:57-58, legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:108-113
- 근거 코드:
  ```java
  String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
  String body = head("오류")
          + "<h1>DB 오류</h1>\n<p id=\"message\">" + esc(msg) + "</p>\n"
          + foot();
  return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.parseMediaType(HTML)).body(body);
  ```
- 확신도: 확실
- 비고: 저장 검증 실패(BR-20)의 `RAISERROR` 도 이 경로로 나온다. 로그는 남기지 않는다. `DataAccessException` 이 아닌 예외는 잡지 않는다.

#### BR-04
- 규칙: 결과 값이 NULL 이면 빈 칸으로, DECIMAL 이면 자릿수를 그대로, 그 밖은 문자열로 바꿔 앞뒤 공백을 제거해 표시한다.
- 근거: legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java:45-52
- 근거 코드:
  ```java
  if (v == null) {
      return "";
  }
  if (v instanceof BigDecimal d) {
      return d.toPlainString();
  }
  return v.toString().trim();
  ```
- 확신도: 확실
- 비고: 점수는 소수 첫째 자리, 평균은 둘째 자리까지 나온다(주석의 예 `87.5`, `78.75`, legacy/grade-mssql/src/main/java/com/example/grade/GradeRepository.java:44). 표의 값은 HTML 이스케이프한다 (CTL:100).

#### BR-05
- 규칙: 화면의 건수는 프로시저가 돌려준 행 수다.
- 근거: legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:53, legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:69
- 근거 코드:
  ```java
  + "<p id=\"count\">집계 결과 " + rows.size() + "건</p>\n"
  ```
  ```java
  + "<p id=\"count\">단원 " + rows.size() + "건</p>\n"
  ```
- 확신도: 확실
- 비고: 집계 화면은 학생 수가 아니라 행 수(단원 행 + 종합 행)다. 없는 학급이면 0건이다(BR-06, BR-24).


## 2. 성적 집계 (`usp_aggregate_grades`, `GET /aggregate`)


### 2.1 대상

#### BR-06
- 규칙: 학급 테이블에 없는 `class_id` 면 오류 없이 빈 결과를 돌려주고 아무것도 저장하지 않는다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:39-52
- 근거 코드:
  ```sql
  SELECT @class_name = c.name
    FROM dbo.class AS c
   WHERE c.id = @class_id;

  IF @class_name IS NULL
  BEGIN
  ```
  ```sql
   WHERE 1 = 0;
  RETURN;
  ```
- 확신도: 확실
- 비고: 판정 기준이 학급 테이블이다. 보고 프로시저는 학생 수로 판정한다(BR-24). 학급은 있는데 학생이 없으면 집계는 저장 단계까지 진행한다(BR-35).

#### BR-07
- 규칙: 집계 대상 학생은 `class_id` 가 일치하는 학생 전원이며 학생 ID 순으로 처리한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:96-100
- 근거 코드:
  ```sql
  SELECT s.id, s.name
    FROM dbo.student AS s
   WHERE s.class_id = @class_id
   ORDER BY s.id;
  ```
- 확신도: 확실
- 비고: 재학 · 전출 같은 학생 상태 컬럼이 없다 (SCH:32-37). 정렬 결과는 콜레이션이 결정한다(미확인 목록).

#### BR-08
- 규칙: 상태가 `X` 인 제출은 집계에서 제외한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:130
- 근거 코드:
  ```sql
  WHERE sub.status <> 'X'
  ```
- 확신도: 확실
- 비고: 제외 조건은 이것 하나다. `S` 가 아닌 다른 상태 값은 제외되지 않는다. 보고 프로시저에 같은 조건이 따로 있다(BR-25).

#### BR-09
- 규칙: 같은 학생 · 같은 과제의 제출이 여러 건이면 제출 시각이 가장 늦은 1건만, 시각이 같으면 id 가 큰 1건만 인정한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:123-126, legacy/grade-mssql/sql/usp_aggregate_grades.sql:132
- 근거 코드:
  ```sql
  ROW_NUMBER() OVER (
      PARTITION BY sub.student_id, sub.assignment_id
      ORDER BY sub.submitted_at DESC, sub.id DESC
  ) AS rn
  ```
  ```sql
  WHERE v.rn = 1;
  ```
- 확신도: 확실
- 비고: 점수가 높은 쪽이 아니라 늦은 쪽이다. 무효를 먼저 빼고 고르므로, 가장 늦은 제출이 `X` 면 그 앞의 제출이 인정된다. 보고 프로시저에 같은 규칙이 따로 있다(BR-25).

#### BR-10
- 규칙: 단원과 제출은 과제 id 로 잇는다. 단원의 과제는 `assignment.unit_code` 로 찾고, 제출의 `unit_code` 는 쓰지 않는다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:105-109, legacy/grade-mssql/sql/usp_aggregate_grades.sql:177-182
- 근거 코드:
  ```sql
  FROM dbo.unit AS u
  LEFT JOIN dbo.assignment AS a
    ON a.unit_code = u.code
  ```
  ```sql
  WHERE v.student_id    = @student_id
    AND v.assignment_id = @assignment_id;
  ```
- 확신도: 확실
- 비고: `#valid` 에 제출의 `unit_code` 를 담지만 (AGG:116) 읽는 곳이 없다. 보고 프로시저는 제출의 `unit_code` 로 단원을 나눈다(BR-30). 과제가 없는 단원은 `assignment_id` 가 NULL 이라 항상 미제출이 된다(BR-11).


### 2.2 단원 점수

#### BR-11
- 규칙: 유효 제출이 없는 단원은 가중치가 0 이면 행을 만들지 않고, 가중치가 0 이 아니면 0점으로 집계한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:184-198
- 근거 코드:
  ```sql
  IF @found = 0
  BEGIN
      -- 미제출
      SET @is_missing = 1;

      IF @unit_weight = 0.00
      BEGIN
          -- 보너스 단원은 미제출이면 행 자체를 만들지 않는다 (2023-11)
          FETCH NEXT FROM cur_unit INTO @unit_code, @unit_weight, @assignment_id, @due_at;
          CONTINUE;
      END;

      -- 가중치가 있는 단원은 0점으로 집계
      SET @adj = 0.0;
  END
  ```
- 확신도: 확실
- 비고: 가중치는 `unit.weight` 값이다 (AGG:105, AGG:189). 종합 점수에 쓰는 가중치(BR-15)와 출처가 다르다. 0점 단원의 원점수(`raw_score`)는 NULL, 등급은 F 다.

  | 유효 제출 | `unit.weight` | 결과 | 줄 (AGG) |
  |---|---|---|---|
  | 없음 | 0 | 행 없음 | 189-194 |
  | 없음 | 0 아님 | 0점, 미제출 표시 | 187, 196-197 |
  | 있음 | 무관 | BR-12, BR-13 | 199-215 |

#### BR-12
- 규칙: 유효 제출의 점수가 NULL 이면 0점으로 본다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:202-203
- 근거 코드:
  ```sql
  IF @raw IS NULL
      SET @raw = 0.0;
  ```
- 확신도: 확실
- 비고: 채점 전 제출과 0점 제출을 구분하지 않는다. 원점수(`raw_score`)에도 NULL 이 아니라 0.0 이 저장된다 (AGG:218). 미제출 표시는 붙지 않는다. 보고 프로시저는 `ISNULL` 로 같은 처리를 한다(BR-26).

#### BR-13
- 규칙: 제출 시각이 마감에 2일을 더한 시각보다 늦으면 지연 제출로 보고 점수에 0.9 를 곱해 소수 첫째 자리로 반올림한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:208-214
- 근거 코드:
  ```sql
  IF @due_at IS NOT NULL
     AND @submitted_at > DATEADD(DAY, 2, @due_at)
  BEGIN
      SET @is_late = 1;
      -- 반올림: 소수 첫째 자리, 5 는 올림 (ROUND 는 0.5 를 올린다)
      SET @adj = ROUND(@raw * 0.9, 1);
  END;
  ```
- 확신도: 확실
- 비고: 마감 + 2일과 같은 시각은 지연이 아니다(`>`). 마감 뒤 2일 안의 제출은 감점도 지연 표시도 없다. 감점은 원점수에 한 번만 적용한다. 반올림 방식(0.05 단위의 올림)은 실행하지 않아 확인하지 못했다(미확인 목록).

  | 제출 시각 | 지연 표시 | 점수 | 줄 (AGG) |
  |---|---|---|---|
  | 마감 이전 또는 같음 | 0 | 원점수 | 205 |
  | 마감 초과, 마감 + 2일 미만 | 0 | 원점수 | 205, 209 |
  | 마감 + 2일과 같음 | 0 | 원점수 | 209 |
  | 마감 + 2일 초과 | 1 | 원점수 × 0.9 반올림 | 211-213 |

#### BR-14
- 규칙: 단원 점수가 90 이상이면 A, 80 이상이면 B, 70 이상이면 C, 60 이상이면 D, 그 밖은 F 다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:236-243
- 근거 코드:
  ```sql
  UPDATE #calc
     SET grade = CASE
                     WHEN adj_score >= 90.0 THEN 'A'
                     WHEN adj_score >= 80.0 THEN 'B'
                     WHEN adj_score >= 70.0 THEN 'C'
                     WHEN adj_score >= 60.0 THEN 'D'
                     ELSE 'F'
                 END;
  ```
- 확신도: 확실
- 비고: 감점 후 점수(`adj_score`)로 판정한다. 미제출 0점은 F 다. 같은 경계가 종합 등급(BR-16)에 따로 적혀 있다. 과제 배포 모듈에도 같은 경계가 있다 (legacy/assignment-thymeleaf/src/main/java/com/example/assign/service/DistributionService.java:458-474).


### 2.3 종합(TOTAL)

#### BR-15
- 규칙: 학생마다 종합 행 `TOTAL` 을 하나 만들고, 점수는 단원 점수에 단원 가중치를 곱해 더한 값을 소수 첫째 자리로 반올림한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:281-290, legacy/grade-mssql/sql/usp_aggregate_grades.sql:303-308
- 근거 코드:
  ```sql
  SET @w = CASE @unit_code
               WHEN 'M5-1' THEN 0.30
               WHEN 'M5-2' THEN 0.25
               WHEN 'M5-3' THEN 0.25
               WHEN 'M6-1' THEN 0.20
               WHEN 'M6-2' THEN 0.00
               ELSE 0.00
           END;

  SET @total = @total + (@unit_score * @w);
  ```
  ```sql
  ROUND(@total, 1),
  ```
- 확신도: 확실
- 비고: **가중치가 프로시저 안에 고정돼 있다.** `unit.weight` 를 읽지 않는다(TODO 주석, AGG:280). 표에 없는 단원 코드는 가중치 0 이다. 시드의 `unit.weight` 는 이 값과 같다 (SEED:24-28). `unit.weight` 만 바꾸면 미제출 판정(BR-11)만 달라지고 종합 점수는 그대로다.

  | 단원 코드 | 가중치 | 줄 (AGG) |
  |---|---|---|
  | `M5-1` | 0.30 | 282 |
  | `M5-2` | 0.25 | 283 |
  | `M5-3` | 0.25 | 284 |
  | `M6-1` | 0.20 | 285 |
  | `M6-2` | 0.00 | 286 |
  | 그 밖 | 0.00 | 287 |

#### BR-16
- 규칙: 종합 등급은 반올림한 종합 점수에 단원 등급과 같은 경계(90 · 80 · 70 · 60)를 적용한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:311-317
- 근거 코드:
  ```sql
  CASE
      WHEN ROUND(@total, 1) >= 90.0 THEN 'A'
      WHEN ROUND(@total, 1) >= 80.0 THEN 'B'
      WHEN ROUND(@total, 1) >= 70.0 THEN 'C'
      WHEN ROUND(@total, 1) >= 60.0 THEN 'D'
      ELSE 'F'
  END
  ```
- 확신도: 확실
- 비고: 경계 값이 BR-14 와 따로 적혀 있다. 단원 행이 하나도 없는 학생도 종합 행은 생기며 0점 F 다 (AGG:265, AGG:303).

#### BR-17
- 규칙: 종합 행의 지연 · 미제출 표시는 그 학생의 단원 행 중 하나라도 해당하면 1 이다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:292-295, legacy/grade-mssql/sql/usp_aggregate_grades.sql:309-310
- 근거 코드:
  ```sql
  IF @is_late = 1
      SET @has_late = 1;
  IF @is_missing = 1
      SET @has_missing = 1;
  ```
- 확신도: 확실
- 비고: 가중치 0 단원의 지연 제출도 종합 행의 지연 표시를 켠다. 가중치 0 단원의 미제출은 행이 없으므로(BR-11) 미제출 표시에 잡히지 않는다. 종합 행의 원점수는 NULL 이다 (AGG:307).


### 2.4 저장

#### BR-18
- 규칙: 집계 결과는 학생 · 단원이 같은 행이 있으면 덮어쓰고 없으면 추가하며, 학급 · 집계 시각도 함께 덮어쓴다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:333-344, legacy/grade-mssql/sql/usp_aggregate_grades.sql:347-356
- 근거 코드:
  ```sql
  UPDATE gs
     SET gs.class_id      = @class_id,
         gs.raw_score     = c.raw_score,
         gs.score         = c.adj_score,
         gs.grade         = c.grade,
         gs.is_late       = c.is_late,
         gs.is_missing    = c.is_missing,
         gs.aggregated_at = @now
  ```
  ```sql
  WHERE NOT EXISTS (
            SELECT 1
              FROM dbo.grade_summary AS gs
             WHERE gs.student_id = c.student_id
               AND gs.unit_code  = c.unit_code
        );
  ```
- 확신도: 확실
- 비고: 키는 `(student_id, unit_code)` 다 (SCH:105). 다른 학급으로 저장돼 있던 행도 이번 학급으로 바뀐다. 보고 시각(`last_report_at`)은 건드리지 않는다.

#### BR-19
- 규칙: 그 학급으로 저장된 행 중 이번 집계 결과에 없는 행은 삭제한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:359-367
- 근거 코드:
  ```sql
  DELETE gs
    FROM dbo.grade_summary AS gs
   WHERE gs.class_id = @class_id
     AND NOT EXISTS (
             SELECT 1
               FROM #calc AS c
              WHERE c.student_id = gs.student_id
                AND c.unit_code  = gs.unit_code
         );
  ```
- 확신도: 확실
- 비고: 전출 학생의 행, 가중치 0 단원을 냈다가 무효가 된 행이 해당한다(주석, AGG:358). 학생이 없는 학급을 집계하면 그 학급의 저장 행이 모두 지워진다(BR-35).

#### BR-20
- 규칙: 저장 후 계산한 점수와 저장된 점수가 한 건이라도 다르거나 저장된 행이 없으면 저장을 모두 되돌리고 오류를 낸다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:385-391, legacy/grade-mssql/sql/usp_aggregate_grades.sql:401-406
- 근거 코드:
  ```sql
  IF @saved IS NULL OR @saved <> @adj
      SET @mismatch = @mismatch + 1;
  ```
  ```sql
  IF @mismatch > 0
  BEGIN
      ROLLBACK TRANSACTION;
      RAISERROR(N'usp_aggregate_grades: grade_summary 저장 불일치 %d건 (class %s)', 16, 1, @mismatch, @class_id);
      RETURN;
  END;
  ```
- 확신도: 확실
- 비고: **주석과 코드가 다르다.** 주석은 "행 수가 같은지"인데 (AGG:369) 코드는 행마다 점수를 비교한다. 등급 · 표시는 비교하지 않는다. 오류는 화면에 502 로 나온다(BR-03). `@row_count` 는 세기만 하고 쓰지 않는다 (AGG:393).

#### BR-21
- 규칙: 집계가 끝나면 학급 학생의 모든 제출 행에 집계 시각을 기록한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:409-413
- 근거 코드:
  ```sql
  UPDATE sub
     SET sub.aggregated_at = @now
    FROM dbo.submission AS sub
    JOIN #stu AS st
      ON st.student_id = sub.student_id;
  ```
- 확신도: 확실
- 비고: 무효 제출과 재제출로 밀려난 제출에도 기록한다. 조건이 학급 학생뿐이다. 저장 검증(BR-20)에 실패하면 기록하지 않는다.

#### BR-22
- 규칙: 한 번의 집계에서 기록하는 시각은 모두 프로시저 시작 때 구한 같은 값이다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:30, legacy/grade-mssql/sql/usp_aggregate_grades.sql:340, legacy/grade-mssql/sql/usp_aggregate_grades.sql:349, legacy/grade-mssql/sql/usp_aggregate_grades.sql:410
- 근거 코드:
  ```sql
  DECLARE @now            DATETIME2(0) = SYSDATETIME();
  ```
  ```sql
  gs.aggregated_at = @now
  ```
  ```sql
  SET sub.aggregated_at = @now
  ```
- 확신도: 확실
- 비고: 초 단위까지다(`DATETIME2(0)`). 보고 프로시저도 같은 방식이다 (REP:30).


### 2.5 결과

#### BR-23
- 규칙: 집계 결과는 학생 ID 순으로, 한 학생 안에서는 단원 코드 순으로 정렬하고 종합 행을 마지막에 둔다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:428-430
- 근거 코드:
  ```sql
  ORDER BY c.student_id,
           CASE WHEN c.unit_code = 'TOTAL' THEN 1 ELSE 0 END,
           c.unit_code;
  ```
- 확신도: 확실
- 비고: 이관 시 행 순서 비교 대상이다. 문자열 정렬 결과는 콜레이션이 결정한다(미확인 목록). 결과 열은 학생 ID · 이름 · 단원 · 점수 · 등급 다섯 개이고 원점수 · 지연 · 미제출 표시는 화면에 나오지 않는다 (AGG:420-424).


## 3. 학급 단원별 현황 (`usp_class_report`, `GET /report`)

#### BR-24
- 규칙: 그 학급의 학생이 0명이면 오류 없이 빈 결과를 돌려주고 아무것도 기록하지 않는다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:33-51
- 근거 코드:
  ```sql
  SELECT @enrolled = COUNT(*)
    FROM dbo.student AS s
   WHERE s.class_id = @class_id;

  IF @enrolled IS NULL OR @enrolled = 0
  ```
  ```sql
   WHERE 1 = 0;
  RETURN;
  ```
- 확신도: 확실
- 비고: 없는 학급도 학생이 0명이라 같은 길을 간다. 집계 프로시저는 학급 테이블로 판정한다(BR-06). `COUNT(*)` 는 NULL 이 되지 않으므로 `@enrolled IS NULL` 은 닿지 않는 조건이다.

#### BR-25
- 규칙: 보고도 상태가 `X` 인 제출을 제외하고, 학생 · 과제별로 제출 시각이 가장 늦은(같으면 id 가 큰) 1건만 쓴다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:81-89, legacy/grade-mssql/sql/usp_class_report.sql:93
- 근거 코드:
  ```sql
  ROW_NUMBER() OVER (
      PARTITION BY sub.student_id, sub.assignment_id
      ORDER BY sub.submitted_at DESC, sub.id DESC
  ) AS rn
  ```
  ```sql
  AND sub.status <> 'X'
  ```
  ```sql
  WHERE v.rn = 1;
  ```
- 확신도: 확실
- 비고: 집계의 BR-08, BR-09 와 같은 내용을 따로 구현했다. 학급 조건을 `student` 와의 JOIN 으로 건다 (REP:86-88).

#### BR-26
- 규칙: 보고도 제출 시각이 마감 + 2일보다 늦으면 점수에 0.9 를 곱해 소수 첫째 자리로 반올림하고, 점수 NULL 은 0점으로 본다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:69-74
- 근거 코드:
  ```sql
  CASE
      WHEN v.submitted_at > DATEADD(DAY, 2, a.due_at)
          THEN ROUND(ISNULL(v.score, 0.0) * 0.9, 1)     -- 지연 10% 감점
      ELSE ISNULL(v.score, 0.0)
  END,
  CASE WHEN v.submitted_at > DATEADD(DAY, 2, a.due_at) THEN 1 ELSE 0 END
  ```
- 확신도: 확실
- 비고: 집계의 BR-12, BR-13 과 같은 내용을 따로 구현했다. 집계에 있는 `@due_at IS NOT NULL` 검사 (AGG:208) 가 여기에는 없다. 과제와 INNER JOIN 하므로 (REP:91-92) 마감은 항상 있다.

#### BR-27
- 규칙: 단원의 제출 수는 유효 제출 건수이고, 미제출 수는 재적 인원에서 제출 수를 뺀 값이다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:116-117, legacy/grade-mssql/sql/usp_class_report.sql:137
- 근거 코드:
  ```sql
  ISNULL(e.submitted, 0),
  @enrolled - ISNULL(e.submitted, 0),
  ```
  ```sql
  COUNT(*)                 AS submitted,
  ```
- 확신도: 확실
- 비고: 무효 제출만 있는 학생은 미제출로 센다. 가중치 0 단원도 미제출 수를 계산한다. 재적 인원은 모든 단원에 같은 값이다 (REP:184).

#### BR-28
- 규칙: 단원의 지연 수는 유효 제출 중 지연 제출의 건수다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:118, legacy/grade-mssql/sql/usp_class_report.sql:138
- 근거 코드:
  ```sql
  ISNULL(e.late, 0),
  ```
  ```sql
  SUM(CAST(is_late AS INT)) AS late,
  ```
- 확신도: 확실
- 비고: 마감 뒤 2일 안의 제출은 지연으로 세지 않는다(BR-26).

#### BR-29
- 규칙: 단원의 무효 수는 그 학급 학생의 상태 `X` 제출 행 수다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:119, legacy/grade-mssql/sql/usp_class_report.sql:147-153
- 근거 코드:
  ```sql
  SELECT sub.unit_code, COUNT(*) AS excluded
    FROM dbo.submission AS sub
    JOIN dbo.student AS s
      ON s.id = sub.student_id
   WHERE s.class_id = @class_id
     AND sub.status = 'X'
   GROUP BY sub.unit_code
  ```
- 확신도: 확실
- 비고: 학생 · 과제별로 묶지 않고 행을 센다. 같은 학생이 무효 제출을 두 번 하면 2 다.

#### BR-30
- 규칙: 보고는 제출 행의 `unit_code` 로 단원을 나눈다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:77, legacy/grade-mssql/sql/usp_class_report.sql:136-143, legacy/grade-mssql/sql/usp_class_report.sql:147-153
- 근거 코드:
  ```sql
  SELECT unit_code,
         COUNT(*)                 AS submitted,
         SUM(CAST(is_late AS INT)) AS late,
         SUM(eff_score)           AS sum_score,
         MAX(eff_score)           AS max_score,
         MIN(eff_score)           AS min_score
    FROM #eff
   GROUP BY unit_code
  ```
- 확신도: 확실
- 비고: 집계는 과제의 `unit_code` 를 쓴다(BR-10). 제출의 단원 코드가 과제의 단원 코드와 다르면 두 화면의 결과가 달라진다. 두 값을 맞추는 제약은 없다(`ERD.md` 2절). 시드는 모두 일치한다.

#### BR-31
- 규칙: 단원 평균은 가중치 0 단원이면 제출자 평균(제출이 없으면 빈 값)이고, 그 밖의 단원이면 점수 합을 재적 인원으로 나눈 값이며, 소수 둘째 자리로 반올림한다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:120-127
- 근거 코드:
  ```sql
  CASE
      -- 보너스 단원: 제출자 평균. 아무도 안 냈으면 NULL
      WHEN u.weight = 0.00 THEN
          CASE WHEN ISNULL(e.submitted, 0) = 0 THEN NULL
               ELSE ROUND(e.sum_score / e.submitted, 2) END
      -- 일반 단원: 미제출 0점 포함, 재적 인원으로 나눈다
      ELSE ROUND(ISNULL(e.sum_score, 0.0) / @enrolled, 2)
  END,
  ```
- 확신도: 확실
- 비고: 일반 단원은 미제출을 0점으로 넣는 것과 같다. 감점 후 점수로 계산한다.

  | `unit.weight` | 제출 수 | 평균 | 줄 (REP) |
  |---|---|---|---|
  | 0 | 0 | 빈 값(NULL) | 123 |
  | 0 | 1 이상 | 점수 합 ÷ 제출 수 | 124 |
  | 0 아님 | 0 | 0.00 | 126 |
  | 0 아님 | 1 이상 | 점수 합 ÷ 재적 인원 | 126 |

#### BR-32
- 규칙: 단원 최고점은 유효 제출의 최고점이고, 최저점은 가중치 0 이 아닌 단원에 미제출이 있으면 0, 아니면 유효 제출의 최저점이다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:128-133
- 근거 코드:
  ```sql
  e.max_score,
  CASE
      WHEN u.weight = 0.00 THEN e.min_score
      WHEN ISNULL(e.submitted, 0) < @enrolled THEN 0.0     -- 미제출이 있으면 최저는 0
      ELSE e.min_score
  END
  ```
- 확신도: 확실
- 비고: 제출이 없는 일반 단원은 최고점이 빈 값, 최저점이 0 이다. 가중치 0 단원은 미제출이 있어도 제출자의 최저점이다.

#### BR-33
- 규칙: 보고를 조회하면 학급 학생의 모든 제출 행과 그 학급의 집계 행에 보고 시각을 기록한다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:164-169, legacy/grade-mssql/sql/usp_class_report.sql:172-175
- 근거 코드:
  ```sql
  UPDATE sub
     SET sub.reported_at = @now
    FROM dbo.submission AS sub
    JOIN dbo.student AS s
      ON s.id = sub.student_id
   WHERE s.class_id = @class_id;
  ```
  ```sql
  UPDATE gs
     SET gs.last_report_at = @now
    FROM dbo.grade_summary AS gs
   WHERE gs.class_id = @class_id;
  ```
- 확신도: 확실
- 비고: 무효 제출에도 기록한다. 집계한 적이 없는 학급은 집계 행이 없어 제출에만 기록된다. 갱신 순서가 집계와 반대다(`ARCHITECTURE.md` 4.3절).

#### BR-34
- 규칙: 보고 결과는 단원 전체를 단원 코드 순으로 한 행씩 보여 준다.
- 근거: legacy/grade-mssql/sql/usp_class_report.sql:134, legacy/grade-mssql/sql/usp_class_report.sql:193
- 근거 코드:
  ```sql
  FROM dbo.unit AS u
  ```
  ```sql
  ORDER BY r.unit_code;
  ```
- 확신도: 확실
- 비고: 과제나 제출이 없는 단원도 나온다. 이관 시 행 순서 비교 대상이다. 정렬 결과는 콜레이션이 결정한다(미확인 목록).


## 4. 공통

#### BR-35
- 규칙: 학급에 학생이 없으면 집계는 빈 결과를 돌려주면서 그 학급으로 저장된 집계 행을 모두 삭제한다.
- 근거: legacy/grade-mssql/sql/usp_aggregate_grades.sql:43, legacy/grade-mssql/sql/usp_aggregate_grades.sql:96-100, legacy/grade-mssql/sql/usp_aggregate_grades.sql:359-367
- 근거 코드:
  ```sql
  IF @class_name IS NULL
  ```
  ```sql
  WHERE gs.class_id = @class_id
    AND NOT EXISTS (
  ```
- 확신도: 추정
- 비고: 학급 테이블에는 있으므로 BR-06 에 걸리지 않고 저장 단계까지 간다. 계산 결과가 비어 있어 삭제 조건(BR-19)이 그 학급의 모든 행에 맞는다. 실행하지 않았고 코드를 따라간 결과다.

#### BR-36
- 규칙: 성적 집계와 학급 현황은 조회(`GET`)할 때마다 테이블을 갱신한다.
- 근거: legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:46-50, legacy/grade-mssql/src/main/java/com/example/grade/GradeController.java:62-66, legacy/grade-mssql/sql/usp_aggregate_grades.sql:330, legacy/grade-mssql/sql/usp_class_report.sql:161
- 근거 코드:
  ```java
  @GetMapping(value = "/aggregate", produces = HTML)
  public ResponseEntity<String> aggregate(@RequestParam(name = "class_id", required = false) String classId) {
      String cid = normalize(classId);
      try {
          List<Map<String, String>> rows = repository.aggregateGrades(cid);
  ```
  ```sql
  BEGIN TRANSACTION;
  ```
  ```sql
  BEGIN TRANSACTION;
  ```
- 확신도: 확실
- 비고: 새로 고침, 링크 미리 보기, 기본 학급으로 떨어진 요청(BR-01)도 갱신을 일으킨다. 읽기 전용 계정으로 프로시저를 실행해도 갱신된다 (db/mssql/init/04-procs.sql:648-649).


## 5. 두 프로시저의 차이

같은 규칙이어야 한다고 주석에 적혀 있지만 (REP:13) 구현이 다른 곳이다.

| 항목 | 집계 (`usp_aggregate_grades`) | 보고 (`usp_class_report`) | 규칙 |
|---|---|---|---|
| 빈 결과 판정 | 학급 테이블에 없음 (AGG:43) | 학생 0명 (REP:37) | BR-06, BR-24 |
| 단원을 정하는 값 | 과제의 `unit_code` (AGG:108) | 제출의 `unit_code` (REP:77) | BR-10, BR-30 |
| 마감 NULL 검사 | 있음 (AGG:208) | 없음 (REP:70) | BR-13, BR-26 |
| NULL 점수 | 변수에 0.0 대입 (AGG:203) | `ISNULL` (REP:71-72) | BR-12, BR-26 |
| 미제출 0점 | 행으로 만듦 (AGG:197) | 재적 인원으로 나눔 (REP:126) | BR-11, BR-31 |
| 가중치 출처 | 미제출 판정은 `unit.weight`, 종합은 `CASE` (AGG:189, AGG:281) | `unit.weight` (REP:122) | BR-11, BR-15, BR-31 |
| 반올림 자릿수 | 점수 1자리 (AGG:213, AGG:308) | 점수 1자리, 평균 2자리 (REP:71, REP:124) | BR-13, BR-15, BR-31 |
| 갱신 순서 | `grade_summary` → `submission` (AGG:333, AGG:409) | `submission` → `grade_summary` (REP:164, REP:172) | BR-21, BR-33 |

## 6. 규칙으로 올리지 않은 것

주석 · 안내 문구에만 있고 그대로 실행하는 코드가 없는 내용이다.

| 내용 | 위치 | 올리지 않은 이유 |
|---|---|---|
| "저장된 행 수와 계산한 행 수가 같은지" | AGG:369 | 코드는 점수를 비교한다(BR-20) |
| "집계 규칙은 usp_aggregate_grades 와 같아야 하지만" | REP:13 | 요구일 뿐 강제하는 코드가 없다. 차이는 5절 |
| "평균은 소수 둘째 자리 (집계 프로시저는 첫째 자리)" | REP:18 | 집계 프로시저에는 평균이 없다. 점수의 자릿수다 |
| "weight = 학기 성적 가중치(합계 1.00)" | SCH:41 | 합계를 검사하는 제약 · 코드가 없다 |
| "0.00 인 단원은 보너스 단원(성적 미반영)" | SCH:41 | 종합 점수의 가중치는 `CASE` 문이 정한다(BR-15) |
| "과제. 단원당 1개" | SCH:52 | UNIQUE 제약이 없다 |
| "ROUND 는 0.5 를 올린다" | AGG:212 | 실행하지 않아 확인하지 못했다 |
| "아래 CASE 문은 아직 정리 안 됨", "TODO: unit.weight 를 쓰도록 바꾸기" | AGG:16, AGG:280 | 하지 않은 일의 기록이다 |
| "compose 에서는 GRADES_JDBC_URL 로 mssql 서비스 주소를 넘긴다" | legacy/grade-mssql/src/main/resources/application.properties:4 | compose 는 이 변수를 넘기지 않는다. Dockerfile 이 준다 (legacy/grade-mssql/Dockerfile:11) |
| "학급: C1, C2, C3" | CTL:41 | 고정 문구다. 학급 목록을 조회하지 않는다 |
| 시드 머리말의 제출 구성 건수 | SEED:4 | 프로시저의 기준과 세는 방식이 다르다(`ERD.md` 4.2절) |

## 7. 매직 넘버

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `"C1"` | CTL:23 | `class_id` 기본값 |
| `502` (`BAD_GATEWAY`) | CTL:113 | DB 오류의 상태 코드 |
| `'X'` | AGG:130, REP:89, REP:152 | 무효 제출 상태 |
| `2` (일) | AGG:209, REP:70, REP:74 | 지연 유예 기간. 세 곳에 있다 |
| `0.9` | AGG:213, REP:71 | 지연 감점 비율. 두 곳에 있다 |
| `1`, `2` (ROUND 자릿수) | AGG:213, AGG:308, REP:71, REP:124, REP:126 | 점수 1자리, 평균 2자리 |
| `90.0` · `80.0` · `70.0` · `60.0` | AGG:238-241, AGG:312-315 | 등급 경계. 두 곳에 있다 |
| `0.30` · `0.25` · `0.25` · `0.20` · `0.00` | AGG:282-287 | 단원 가중치. `unit.weight` 와 이중이다 |
| `0.00` | AGG:189, REP:122, REP:130 | 보너스 단원 판정 값 |
| `'TOTAL'` | AGG:306, AGG:429 | 종합 행의 단원 코드 |
| `16` | AGG:404 | `RAISERROR` 심각도 |
| `3`, `5000` | legacy/grade-mssql/src/main/resources/application.properties:12-13 | 커넥션 풀 최대, 대기 시간(ms) |

## 8. 표본 점검 추천

사람이 코드와 직접 대조해 볼 규칙 3개다.

| 규칙 | 고른 이유 | 확인할 곳 |
|---|---|---|
| BR-13 지연 감점 | 가장 그럴듯해 보이는 규칙이다. 변경 이력 주석과도 맞아 의심 없이 넘어가기 쉽다. 경계(마감 + 2일과 같은 시각)와 반올림을 봐야 한다 | legacy/grade-mssql/sql/usp_aggregate_grades.sql:208-214, legacy/grade-mssql/sql/usp_class_report.sql:69-74 |
| BR-15 종합 점수의 가중치 | 근거가 프로시저 · 스키마 · 시드 세 파일에 걸쳐 있다. 같은 값이 두 곳에 있고 쓰임이 다르다 | legacy/grade-mssql/sql/usp_aggregate_grades.sql:281-288, legacy/grade-mssql/sql/usp_aggregate_grades.sql:189, db/mssql/init/01-schema.sql:47, db/mssql/init/02-seed.sql:24-28 |
| BR-20 저장 검증 | 주석(행 수)과 코드(점수 비교)가 다르다 | legacy/grade-mssql/sql/usp_aggregate_grades.sql:369, legacy/grade-mssql/sql/usp_aggregate_grades.sql:390-391 |

## 9. 미확인 목록

| 항목 | 미확인 사유 |
|---|---|
| `ROUND` 의 반올림 방식(예: 74.25 → 74.3 인지) (BR-13, BR-15, BR-31) | DB 가 결정한다. 실행하지 않았다 |
| `@raw * 0.9`, `@unit_score * @w` 의 결과 자릿수와 넘침 여부 | DB 의 DECIMAL 연산 규칙이 결정한다. 실행하지 않았다 |
| 문자열 비교의 대소문자 구분(`'c1'`, `'x'`, `'total'`) (BR-02, BR-08) | 콜레이션이 결정한다. 실행하지 않았다 |
| 결과 행의 실제 순서 (BR-23, BR-34) | 콜레이션이 결정한다. 실행하지 않았다 |
| 10자를 넘는 `class_id` 가 잘리는지 오류인지 (BR-02) | 드라이버와 DB 의 형 변환이 결정한다. 실행하지 않았다 |
| 학생 없는 학급을 집계했을 때 저장 행이 실제로 지워지는지 (BR-35) | 코드를 따라간 결과다. 실행하지 않았다 |
| 저장 검증이 실제로 실패하는 경우가 있는지 (BR-20) | 같은 트랜잭션에서 방금 쓴 값을 읽으므로 드물 것으로 보이나(추정) 실행하지 않았다 |
| 계산과 저장 사이에 제출이 바뀌는 경우의 결과 | 동시 실행이 필요하다 |
| 운영의 `unit.weight` 가 `CASE` 문의 값과 같은지 (BR-15) | 저장소 밖이다. 시드는 같다 |
| 가중치 · 등급 경계 · 유예 2일의 출처 문서 | 저장소에 없다 |
