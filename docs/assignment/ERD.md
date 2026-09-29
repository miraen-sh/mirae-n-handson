# 과제 배포(legacy/assignment-thymeleaf) 데이터 흐름 · ERD

분석 기준일: 2026-09-29. 선행 문서는 [ARCHITECTURE.md](ARCHITECTURE.md) 다.
근거의 파일 경로는 저장소 루트 기준으로 적는다. `…/assign/` 은 `legacy/assignment-thymeleaf/src/main/java/com/example/assign/` 의 줄임이다.
소스와 스키마 정의(`db/mariadb/init/01-schema.sql`)를 읽어서 정리했다. DB 를 실행하지 않았다.

> **시각 고정**: `AppClock.now()` 는 기본으로 `2026-09-15 00:00:00` 을 돌려준다 (…/assign/AppClock.java:9, …/assign/AppClock.java:14-19). `distribution.distributed_at` 에 저장되는 값도 이 시각이다 (…/assign/service/DistributionService.java:63).

## 1. 테이블 목록

### 1.1 이 모듈이 쓰는 테이블

| 테이블 | 정의 | 접근 | 비고 |
|---|---|---|---|
| `assignment` | db/mariadb/init/01-schema.sql:82-90 | 읽기 | 이 모듈에는 과제를 만들거나 상태를 바꾸는 SQL 이 없다 |
| `class` | db/mariadb/init/01-schema.sql:75-80 | 읽기 | 예약어라 쿼리에서 백틱으로 감싼다 (…/assign/dao/ClassDao.java:21) |
| `distribution` | db/mariadb/init/01-schema.sql:92-103 | 읽기 · 쓰기 | INSERT 와 `redistributed` UPDATE |
| `submission` | db/mariadb/init/01-schema.sql:105-115 | 읽기 | 제출을 쓰는 SQL 은 없다 |
| `unit` | db/mariadb/init/01-schema.sql:11-18 | 읽기 | 문항 은행과 공유. `LEFT JOIN` 으로 코드 · 이름만 읽는다 (…/assign/dao/AssignmentDao.java:24) |

뷰는 쓰지 않는다.

### 1.2 같은 스키마에 있지만 이 모듈이 쓰지 않는 것

`item`, `tag`, `item_tag`, 뷰 `v_item_public` (db/mariadb/init/01-schema.sql:20, db/mariadb/init/01-schema.sql:35, db/mariadb/init/01-schema.sql:42, db/mariadb/init/01-schema.sql:52).

## 2. 테이블 관계

```mermaid
erDiagram
    unit ||--o{ assignment : "unit_id (선언)"
    assignment ||--o{ distribution : "assignment_id (선언)"
    class ||--o{ distribution : "class_id (선언)"
    distribution ||--o{ submission : "distribution_id (선언)"

    assignment {
        INT id PK
        VARCHAR title
        INT unit_id FK
        DATETIME due_at
        CHAR status
    }
    class {
        INT id PK
        VARCHAR name
        VARCHAR teacher_id
    }
    distribution {
        INT id PK
        INT assignment_id FK
        INT class_id FK
        DATETIME distributed_at
        TINYINT redistributed
    }
    submission {
        INT id PK
        INT distribution_id FK
        VARCHAR student_id
        DATETIME submitted_at
        DECIMAL score
    }
```

| 관계 | 구분 | 근거 |
|---|---|---|
| `assignment.unit_id` → `unit.id` | 선언(FK) | db/mariadb/init/01-schema.sql:89 |
| `distribution.assignment_id` → `assignment.id` | 선언(FK) | db/mariadb/init/01-schema.sql:101 |
| `distribution.class_id` → `class.id` | 선언(FK) | db/mariadb/init/01-schema.sql:102 |
| `submission.distribution_id` → `distribution.id` | 선언(FK) | db/mariadb/init/01-schema.sql:114 |
| `submission.student_id` → 학생 | 추정 | 학생 테이블이 스키마에 없다. 형식만 주석에 있다 (db/mariadb/init/01-schema.sql:108) |
| `class.teacher_id` → 교사 | 추정 | 교사 테이블이 스키마에 없다 (db/mariadb/init/01-schema.sql:78) |
| `(assignment_id, class_id)` 유일 | 선언 없음 | UNIQUE 제약이 없고 코드가 막는다 (…/assign/dao/DistributionDao.java:46-52). 3.4절 참고 |

## 3. 읽기 · 쓰기 위치

### 3.1 쿼리별

| DAO 메서드 | 구분 | 테이블 | 조건 · 정렬 | 근거 |
|---|---|---|---|---|
| `AssignmentDao.findAllForList` | 읽기 | `assignment`, `unit`, `distribution`(서브쿼리) | `status <> 'D'`, `due_at ASC, id ASC` | …/assign/dao/AssignmentDao.java:21-24, …/assign/dao/AssignmentDao.java:27-31 |
| `AssignmentDao.findAllForSelect` | 읽기 | 같음 | `status IN ('O','X','C')`, `due_at ASC, id ASC` | …/assign/dao/AssignmentDao.java:34-38 |
| `AssignmentDao.findById` | 읽기 | 같음 | `a.id = ?` (상태 조건 없음) | …/assign/dao/AssignmentDao.java:41-43 |
| `ClassDao.findAll` | 읽기 | `class` | `id ASC` | …/assign/dao/ClassDao.java:20-21 |
| `ClassDao.findById` | 읽기 | `class` | `id = ?` | …/assign/dao/ClassDao.java:24-25 |
| `DistributionDao.findAllForList` | 읽기 | `distribution`, `assignment`, `class`, `submission`(서브쿼리) | `a.status <> 'D'`, `a.due_at ASC, d.id ASC` | …/assign/dao/DistributionDao.java:23-29, …/assign/dao/DistributionDao.java:31-35 |
| `DistributionDao.findById` | 읽기 | 같음 | `d.id = ?` | …/assign/dao/DistributionDao.java:38-39 |
| `DistributionDao.countByAssignmentAndClass` | 읽기 | `distribution` | `assignment_id = ? AND class_id = ?` | …/assign/dao/DistributionDao.java:47-51 |
| `DistributionDao.nextId` | 읽기 | `distribution` | `COALESCE(MAX(id), 0)` | …/assign/dao/DistributionDao.java:54-56 |
| `DistributionDao.insert` | **쓰기** | `distribution` | `redistributed` 는 항상 0 | …/assign/dao/DistributionDao.java:59-62 |
| `DistributionDao.markRedistributed` | **쓰기** | `distribution` | `SET redistributed = 1 WHERE id = ?` | …/assign/dao/DistributionDao.java:65-66 |
| `DistributionDao.findSubmissions` | 읽기 | `submission` | `distribution_id = ?`, `id ASC` | …/assign/dao/DistributionDao.java:69-72 |

쓰기는 위 두 개뿐이다. DELETE 문은 없다.

### 3.2 화면별 흐름

| 화면 · 동작 | 읽기 | 쓰기 |
|---|---|---|
| `GET /assignments` | `assignment`, `unit`, `distribution` | 없음 |
| `GET /distributions` | `distribution`, `assignment`, `class`, `submission` | 없음 |
| `GET /distributions/new` | `assignment`, `unit`, `distribution`, `class` | 없음 |
| `POST /distributions` | `assignment`, `unit`, `class`, `distribution` | `distribution` INSERT |
| `POST /distributions/{id}/redistribute` | `distribution`, `assignment`, `unit`, `class`, `submission` | `distribution.redistributed` UPDATE |

### 3.3 계산 컬럼

| 이름 | 계산 | 근거 |
|---|---|---|
| `class_cnt` (배포 학급 수) | 그 과제가 배포된 `class_id` 의 DISTINCT 수 | …/assign/dao/AssignmentDao.java:23 |
| `submitted_cnt` (제출 수) | 그 배포의 제출 중 `submitted_at IS NOT NULL` 인 건수 | …/assign/dao/DistributionDao.java:26 |

### 3.4 같은 테이블을 쓰는 다른 모듈

| 모듈 | 테이블 | 접근 | 근거 |
|---|---|---|---|
| `modern/api` | `assignment` | 엔티티 매핑 | modern/api/src/main/java/com/example/assignment/Assignment.java:15 |
| `modern/api` | `distribution` | 읽기(학급별 조회) · **쓰기**(재배포 시 `redistributed` 와 `distributed_at` 을 함께 바꾼다) | modern/api/src/main/java/com/example/assignment/Distribution.java:16, modern/api/src/main/java/com/example/assignment/Distribution.java:50-53, modern/api/src/main/java/com/example/assignment/DistributionService.java:55, modern/api/src/main/java/com/example/assignment/DistributionRepository.java:14 |
| `modern/api` | `class` | 엔티티 매핑 | modern/api/src/main/java/com/example/assignment/ClassRoom.java:10 |
| `modern/api` | `submission` | 읽기 | modern/api/src/main/java/com/example/assignment/Submission.java:17, modern/api/src/main/java/com/example/assignment/SubmissionRepository.java:8 |
| `legacy/item-bank-php` | `unit` | 읽기 | legacy/item-bank-php/register.php:27, legacy/item-bank-php/search.php:125, legacy/item-bank-php/search.php:156, legacy/item-bank-php/units.php:18 |

- `legacy/grade-mssql` 의 `dbo.assignment` · `dbo.submission` 은 이름만 같고 MS-SQL `grades` 의 다른 테이블이다 (db/mssql/init/01-schema.sql:54).
- 레거시 재배포는 `redistributed` 만 바꾸고 배포 시각은 그대로 둔다 (…/assign/dao/DistributionDao.java:66). `modern/api` 는 배포 시각도 덮어쓴다. 두 모듈이 같은 행을 다르게 갱신한다.

## 4. 눈에 띄는 점

- **스키마 주석보다 코드가 아는 상태 값이 많다.** 스키마 주석은 `O=진행 C=마감` 둘뿐인데 (db/mariadb/init/01-schema.sql:87), 코드는 `O` · `X` · `C` · `R` · `D` 를 다룬다 (…/assign/service/DistributionService.java:181-213). 시드에는 `O` 와 `C` 만 있다 (db/mariadb/init/02-seed.sql:120-125).
- **NOT NULL 컬럼에 대한 null 분기가 코드에 있다.** `assignment.due_at` (db/mariadb/init/01-schema.sql:86) 과 `submission.submitted_at` (db/mariadb/init/01-schema.sql:109) 은 NOT NULL 인데, 코드는 마감 없음 (…/assign/service/DistributionService.java:269) 과 미제출 (…/assign/service/DistributionService.java:407) 을 처리한다. 현재 스키마 그대로면 이 분기에 들어갈 행을 저장할 수 없다. 그래서 `submitted_cnt` 의 `IS NOT NULL` 조건도 현재 스키마에서는 전체 건수와 같다.
- **시드가 중복 금지 규칙과 어긋난다.** 배포 3 · 4 가 모두 과제 2 · 학급 1 이다 (db/mariadb/init/02-seed.sql:133-134). 새 배포는 같은 조합을 막는다 (…/assign/service/DistributionService.java:59).
- **id 를 `MAX(id)+1` 로 직접 매긴다.** `distribution.id` 에 AUTO_INCREMENT 가 없다 (db/mariadb/init/01-schema.sql:93, …/assign/dao/DistributionDao.java:54-56). 동시 요청 시 같은 id 가 나올 수 있는지는 실행하지 않아 확인하지 못했다.
- **`unit` 은 LEFT JOIN 이지만** `assignment.unit_id` 가 NOT NULL + FK 라서 (db/mariadb/init/01-schema.sql:85, db/mariadb/init/01-schema.sql:89) 단원이 빠진 과제 행은 생길 수 없다.
- **`redistributed` 는 0 → 1 로만 바뀐다.** 1 을 0 으로 되돌리는 SQL 은 없다. 재배포 횟수 · 사유 · 작업자는 DB 에 저장하지 않고 표준 출력에만 남는다 (…/assign/service/DistributionService.java:693).
- **컬럼 길이와 코드의 자르기 기준.** 작업자 ID 20자 자르기 (…/assign/service/DistributionService.java:90-91) 는 `class.teacher_id VARCHAR(20)` (db/mariadb/init/01-schema.sql:78) 과 값이 같지만, 작업자 ID 는 저장되지 않으므로 관련성은 추정이다.

## 5. 미확인 목록

| 항목 | 미확인 사유 |
|---|---|
| `due_at ASC` 에서 같은 마감 시각끼리의 순서 외 정렬 세부(콜레이션 등) | DB 가 결정한다. 실행하지 않았다 |
| 동시 배포 시 `MAX(id)+1` 충돌 · 중복 검사 경합 | 실행하지 않았다. 격리 수준도 확인하지 않았다 |
| FK 위반 · PK 중복 시 화면에 나오는 메시지 | DB 오류 문구는 실행해야 알 수 있다 |
| 실제 DB 에 `X` · `R` · `D` 상태 과제가 있는지 | 시드에는 없다. 상태를 바꾸는 코드가 이 모듈에 없다 |
| `submission` 을 쓰는 주체 | 이 모듈에는 제출을 넣는 코드가 없다. 저장소 밖 시스템일 수 있다 |
| 컬럼 `score DECIMAL(5,1)` 에 0~100 밖의 값이 들어 있는지 | CHECK 제약이 없다 (db/mariadb/init/01-schema.sql:110). 데이터를 조회하지 않았다 |
