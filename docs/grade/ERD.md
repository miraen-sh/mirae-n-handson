# 성적 집계(legacy/grade-mssql) 데이터 흐름 · ERD

분석 기준일: 2026-09-29. 선행 문서는 [ARCHITECTURE.md](ARCHITECTURE.md) 다.
소스와 스키마 정의(`db/mssql/init/01-schema.sql`)를 읽어서 정리했다. DB 를 실행하지 않았다.

근거의 파일 경로는 저장소 루트 기준으로 적는다. 아래 줄임을 쓴다.

| 줄임 | 가리키는 곳 |
|---|---|
| `AGG:` | `legacy/grade-mssql/sql/usp_aggregate_grades.sql` |
| `REP:` | `legacy/grade-mssql/sql/usp_class_report.sql` |
| `SCH:` | `db/mssql/init/01-schema.sql` |
| `SEED:` | `db/mssql/init/02-seed.sql` |

> **전제 1 — 실행되는 프로시저는 `db/mssql/init/04-procs.sql` 이다** (db/mssql/entrypoint.sh:55). `AGG:` · `REP:` 는 본문이 같은 읽기용 사본이다 (legacy/grade-mssql/sql/usp_aggregate_grades.sql:10-11).
>
> **전제 2 — 기록 시각은 DB 서버의 `SYSDATETIME()` 이다** (legacy/grade-mssql/sql/usp_aggregate_grades.sql:30, legacy/grade-mssql/sql/usp_class_report.sql:30).
>
> **전제 3 — 앱은 기본 계정 `app` 으로 접속한다** (legacy/grade-mssql/src/main/resources/application.properties:6). compose 의 `DB_USER: sa` 는 앱이 읽지 않는다 (docker-compose.yml:89).

## 1. 테이블 목록

DB 는 MS-SQL `grades` 하나다 (SCH:6-8). 뷰 · 트리거 · 함수는 저장소에 없다.

| 테이블 | 정의 | 집계 프로시저 | 보고 프로시저 |
|---|---|---|---|
| `dbo.class` | SCH:22-25 | 읽기 | 쓰지 않음 |
| `dbo.student` | SCH:32-37 | 읽기 | 읽기 |
| `dbo.unit` | SCH:44-48 | 읽기 | 읽기 |
| `dbo.assignment` | SCH:55-61 | 읽기 | 읽기 |
| `dbo.submission` | SCH:69-82 | 읽기 · 쓰기 | 읽기 · 쓰기 |
| `dbo.grade_summary` | SCH:94-106 | 읽기 · 쓰기 | 쓰기 |

임시 테이블: 집계는 `#stu` · `#unit` · `#valid` · `#calc` (AGG:57, AGG:63, AGG:73, AGG:82), 보고는 `#eff` · `#rep` (REP:56, REP:99) 를 만든다.

## 2. 테이블 관계

```mermaid
erDiagram
    class ||--o{ student : "class_id (선언)"
    unit ||--o{ assignment : "unit_code (선언)"
    student ||--o{ submission : "student_id (선언)"
    unit ||--o{ submission : "unit_code (선언)"
    assignment ||--o{ submission : "assignment_id (선언)"
    student ||--o{ grade_summary : "student_id (추정)"
    class ||--o{ grade_summary : "class_id (추정)"
    unit ||--o{ grade_summary : "unit_code (추정, TOTAL 제외)"

    student {
        VARCHAR id PK
        NVARCHAR name
        VARCHAR class_id FK
    }
    unit {
        VARCHAR code PK
        NVARCHAR name
        DECIMAL weight
    }
    assignment {
        VARCHAR id PK
        VARCHAR unit_code FK
        DATETIME2 due_at
    }
    submission {
        INT id PK
        VARCHAR student_id FK
        VARCHAR unit_code FK
        VARCHAR assignment_id FK
        DECIMAL score
        DATETIME2 submitted_at
        CHAR status
        DATETIME2 aggregated_at
        DATETIME2 reported_at
    }
    grade_summary {
        VARCHAR student_id PK
        VARCHAR unit_code PK
        VARCHAR class_id
        DECIMAL raw_score
        DECIMAL score
        CHAR grade
        BIT is_late
        BIT is_missing
        DATETIME2 aggregated_at
        DATETIME2 last_report_at
    }
```

| 관계 | 구분 | 근거 |
|---|---|---|
| `student.class_id` → `class.id` | 선언(FK) | SCH:35-36 |
| `assignment.unit_code` → `unit.code` | 선언(FK) | SCH:57-58 |
| `submission.student_id` → `student.id` | 선언(FK) | SCH:71-72 |
| `submission.unit_code` → `unit.code` | 선언(FK) | SCH:73-74 |
| `submission.assignment_id` → `assignment.id` | 선언(FK) | SCH:75-76 |
| `grade_summary.student_id` · `class_id` · `unit_code` → 각 테이블 | 추정 | FK 가 없다 (SCH:94-106). `unit_code` 에는 단원이 아닌 `'TOTAL'` 도 들어간다 (SCH:97, AGG:306) |
| 단원 하나에 과제 하나 | 선언 없음 | 주석뿐이다 (SCH:52). `assignment.unit_code` 에 UNIQUE 가 없다 |
| 제출의 `unit_code` = 그 과제의 `unit_code` | 선언 없음 | 두 컬럼을 맞추는 제약이 없다. 시드 147건은 모두 일치한다(계산) |

## 3. 읽기 · 쓰기 위치

### 3.1 집계 프로시저 (`GET /aggregate`)

| 구분 | 테이블 | 내용 | 근거 |
|---|---|---|---|
| 읽기 | `class` | 학급 이름 조회(존재 확인) | AGG:39-41 |
| 읽기 | `student` | 학급 학생 | AGG:97-100 |
| 읽기 | `unit`, `assignment` | 단원 전체와 단원의 과제(LEFT JOIN) | AGG:105-109 |
| 읽기 | `submission` | 학급 학생의 무효 아닌 제출 | AGG:127-130 |
| **쓰기** | `grade_summary` | UPDATE (기존 행) | AGG:333-344 |
| **쓰기** | `grade_summary` | INSERT (새 행) | AGG:347-356 |
| **쓰기** | `grade_summary` | DELETE (이번 집계에 없는 학급 행) | AGG:359-367 |
| 읽기 | `grade_summary` | 저장 검증 | AGG:385-388 |
| **쓰기** | `submission` | UPDATE `aggregated_at` | AGG:409-413 |

### 3.2 보고 프로시저 (`GET /report`)

| 구분 | 테이블 | 내용 | 근거 |
|---|---|---|---|
| 읽기 | `student` | 재적 인원 수 | REP:33-35 |
| 읽기 | `submission`, `student`, `assignment` | 유효 제출과 마감 | REP:85-92 |
| 읽기 | `unit` | 단원 전체 | REP:134 |
| 읽기 | `submission`, `student` | 무효 제출 수 | REP:147-153 |
| **쓰기** | `submission` | UPDATE `reported_at` | REP:164-169 |
| **쓰기** | `grade_summary` | UPDATE `last_report_at` | REP:172-175 |

### 3.3 컬럼을 채우는 주체

| 컬럼 | 채우는 곳 | 근거 |
|---|---|---|
| `grade_summary` 의 `last_report_at` 외 전부 | 집계 프로시저 | AGG:334-340, AGG:347-349 |
| `grade_summary.last_report_at` | 보고 프로시저 | REP:173 |
| `submission.aggregated_at` | 집계 프로시저 | AGG:410 |
| `submission.reported_at` | 보고 프로시저 | REP:165 |
| `class` · `student` · `unit` · `assignment` 와 `submission` 의 나머지 컬럼 | 저장소 안에서는 시드뿐이다 | SEED:17, SEED:23, SEED:31, SEED:39, SEED:72 |

### 3.4 같은 테이블을 쓰는 다른 모듈

`grade_summary`, `usp_aggregate_grades`, `usp_class_report`, `aggregated_at`, `reported_at` 을 저장소 전체에서 검색했다. `legacy/` · `modern/` · `characterization/` 에는 이 모듈과 `db/mssql/` 밖의 사용처가 없다.

| 위치 | 내용 | 근거 |
|---|---|---|
| `db/mssql/Dockerfile` | 상태 점검이 `usp_class_report` 의 존재를 확인한다 | db/mssql/Dockerfile:21 |
| `db/mssql/entrypoint.sh` | 같은 프로시저의 존재로 초기화 여부를 판정한다 | db/mssql/entrypoint.sh:50 |
| `incident-logs/` | 두 프로시저의 잠금 대기 기록 | incident-logs/d-mssql-deadlock/mssql-wait.log:19-20 |

- MariaDB `itembank` 의 `class` · `unit` · `assignment` · `submission` 은 이름만 같은 다른 테이블이다 (db/mariadb/init/01-schema.sql:75, db/mariadb/init/01-schema.sql:82, db/mariadb/init/01-schema.sql:105). 키의 형식도 다르다(정수 id 대 `'C1'` · `'A1'` 문자열).
- `modern/api` 의 학급 리포트는 MariaDB 의 배포 · 제출을 읽으며 (modern/api/src/main/java/com/example/assignment/ReportService.java:43-54) 이 모듈의 테이블을 쓰지 않는다.

## 4. 눈에 띄는 점

### 4.1 스키마 제약과 코드 대조

| 코드의 분기 | 제약 | 결과 |
|---|---|---|
| 지연 판정의 `@due_at IS NOT NULL` (AGG:208) | `assignment.due_at` NOT NULL (SCH:60) | 과제 없는 단원은 `due_at` 이 NULL 이지만 그때는 제출도 찾지 못해 이 분기에 오지 않는다. 검사가 거짓이 되는 경우가 없다 |
| 점수 NULL 을 0점으로 (AGG:202-203, REP:71-72) | `submission.score` NULL 허용 (SCH:77) | 닿을 수 있다. 시드에는 NULL 점수가 없다(계산) |
| `#calc` 의 기본 키 `(student_id, unit_code)` (AGG:90) | 단원당 과제 하나라는 제약 없음 (SCH:55-61) | 한 단원에 과제가 둘이면 `#unit` 에 그 단원이 두 행 생기고 (AGG:105-109) 같은 키를 두 번 넣게 된다. 실제 오류는 실행하지 않아 확인하지 못했다 |
| 보고의 `missing = 재적 − 제출` (REP:117) | 같음 | 한 단원에 과제가 둘이면 제출 수가 재적을 넘어 음수가 될 수 있다(추정) |
| `grade_summary.grade` NOT NULL (SCH:100) | `#calc.grade` 는 NULL 허용 (AGG:89) | 등급은 저장 전에 모두 채워진다 (AGG:236-243, AGG:311-317) |
| `status` 비교 `<> 'X'` (AGG:130) | `status` 에 CHECK 없음 (SCH:79) | `'S'` 가 아닌 다른 값도 유효 제출로 집계된다 |

### 4.2 시드 대조 (계산, 실행하지 않음)

시드 파일을 읽어 손으로(스크립트로) 센 값이다. 프로시저 실행 결과가 아니다.

| 항목 | 값 | 비고 |
|---|---|---|
| 제출 행 | 147 (C1 49 · C2 48 · C3 50) | 머리말과 같다 (SEED:3) |
| 무효(`X`) 행 | 9 | 머리말과 같다 (SEED:4) |
| 점수 NULL 행 | 0 | |
| 유효 제출(학생 · 과제별 최신 1건) | 125 | 무효가 아닌 138행 중 13행이 재제출로 밀린다 |
| 유효 제출 중 2일 초과 지연 | 10 | 머리말은 11 이다. 행 단위(무효 아닌 138행)로 세면 11 이다 |
| 유효 제출 중 유예 기간 안 지연 | 8 | 머리말은 9 다. 행 단위로 세면 9 다 |
| 유효 제출이 없는 학생 · 과제 조합 | 25 (보너스 과제 제외 13) | 머리말은 16 이다. 무효 제출도 제출로 치면 16 이다 |
| 머리말의 "정상 114" | 재현하지 못함 | 무효 아닌 제때 제출은 118행이다 |

- 머리말의 건수는 행 단위로 센 것으로 보이며(추정) 프로시저의 기준(유효 제출)과 다르다.
- 시드의 가중치는 `CASE` 문의 값과 같다 (SEED:24-28, AGG:282-286).
- 시드의 단원은 모두 과제가 하나씩이다 (SEED:32-36).

### 4.3 그 밖

- **집계는 다른 학급의 행도 옮긴다.** `grade_summary` 의 키는 `(student_id, unit_code)` 이고 갱신 때 `class_id` 를 덮어쓴다 (SCH:105, AGG:334). 전학 온 학생의 이전 학급 행이 새 학급 행이 된다.
- **`aggregated_at` · `reported_at` 은 무효 제출과 밀려난 재제출에도 찍힌다.** 갱신 조건이 학급 학생뿐이다 (AGG:409-413, REP:164-169).
- **보고는 집계 결과를 읽지 않는다.** `grade_summary` 에는 시각만 쓰고, 수치는 `submission` 에서 다시 계산한다 (REP:85, REP:172-175).
- **격리 수준**: `READ_COMMITTED_SNAPSHOT` 이 꺼져 있어 (SCH:12) 읽기도 잠금을 기다린다.

## 5. 미확인 목록

| 항목 | 미확인 사유 |
|---|---|
| 문자열 비교 · 정렬의 대소문자 구분(`'c1'` 과 `'C1'`, `'x'` 와 `'X'`) | DB 콜레이션이 결정한다. 스키마에 콜레이션 지정이 없고 실행하지 않았다 |
| `ORDER BY` 의 단원 코드 · 학생 ID 순서 | 콜레이션이 결정한다. 실행하지 않았다 |
| 한 단원에 과제가 둘일 때의 오류 | 실행해야 알 수 있다. 시드에는 그런 단원이 없다 |
| 두 프로시저 동시 실행 시의 교착 | 실행해야 알 수 있다 |
| 운영 데이터에 NULL 점수, `S` · `X` 외의 상태 값, 단원 코드가 과제와 다른 제출이 있는지 | 저장소 밖이다 |
| `class` · `student` · `submission` 을 넣는 주체 | 저장소에는 시드뿐이다. 다른 시스템일 수 있다 |
| 10자를 넘는 `class_id` 를 넘겼을 때 잘리는지 오류인지 | 드라이버와 DB 의 형 변환이 결정한다. 실행하지 않았다 |
