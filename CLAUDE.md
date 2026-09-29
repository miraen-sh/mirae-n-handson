# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Claude Code 심화 과정 실습 저장소다. 코드 · 데이터 · 로그는 전부 더미 에듀테크 도메인(문항 은행 · 과제 배포 · 성적 집계)이다. 루트에는 빌드 파일이 없고, 명령은 해당 모듈 폴더에서 실행한다.

- `legacy/` 는 분석 · 이관 대상이며 허락 없이 수정하지 않는다.
- 답변과 문서는 한국어로 쓴다.

## 1. 빌드 · 테스트 명령

### modern/api (Spring Boot 3.3 · Java 21 · Gradle wrapper)

```bash
cd modern/api && ./gradlew test                                  # DB 없이 통과한다 (test 프로필 = H2)
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest'                  # 클래스 하나
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest.listUnitsEmpty'   # 메서드 하나
cd modern/api && ./gradlew build                                 # 테스트 포함 전체 빌드
docker compose --profile modern up -d                            # 저장소 루트에서. bootRun 에 필요한 MariaDB(3306)
cd modern/api && ./gradlew bootRun                               # 8080. "Tomcat started on port 8080"
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/  # 200 = 떠 있음, 000 = 꺼짐
docker compose --profile modern down                             # down 에도 프로필을 붙인다
```

### modern/web (React 18 · TypeScript · Vite · Vitest)

```bash
cd modern/web && npm ci                                          # npm install 을 쓰지 않는다 (package-lock.json 변경 방지)
cd modern/web && npm run dev                                     # 5173. API(8080)가 떠 있어야 데이터가 보인다
cd modern/web && npm run lint && npm run typecheck && npm test
cd modern/web && npx vitest run src/hooks/useUnits.test.ts       # 파일 하나
cd modern/web && npx vitest run -t '<테스트 이름 일부>'           # 이름으로 고르기
cd modern/web && npm run build
```

- 작업을 마치면 바꾼 모듈의 테스트 명령을 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.
- 테스트 설정은 `modern/api/src/test/resources/application-test.yml` 이다. 테스트가 MariaDB 에 접속하게 만들지 않는다.

## 2. 코딩 컨벤션

### 계층 (modern/api)

- 한 도메인 패키지 안에서 `Controller` → `Service` → `Repository` → 엔티티 순으로 호출한다.
- 컨트롤러에서 Repository 를 직접 호출하지 않는다. 컨트롤러는 서비스만 주입받고, SQL 문자열을 실행하지 않는다.
- 서비스가 다른 도메인의 데이터를 쓸 때는 그 도메인의 서비스를 주입받는다. 다른 도메인의 Repository 를 주입받지 않는다.
- 요청 · 응답은 record DTO 다. 컨트롤러 메서드의 반환 타입에 엔티티를 쓰지 않는다.
- 엔티티 → DTO 변환은 서비스의 트랜잭션 안에서 끝낸다(`open-in-view: false`). 연관 데이터는 `@EntityGraph` 나 조회 쿼리로 함께 가져온다.
- 현재 시각은 `Clock` 빈을 주입받아 구한다. `LocalDateTime.now()` 를 인자 없이 호출하지 않는다.
- 새 엔드포인트의 경로는 `/api/<도메인 복수형>` 으로 시작한다.

### 예외 · 로깅

- 예외를 삼키지 않는다. `catch` 블록은 로그를 남기고 다시 던지거나 도메인 예외로 바꿔 던진다. 빈 `catch` 블록을 두지 않는다.
- 예외 → HTTP 응답 변환은 `GlobalExceptionHandler` 에서만 한다. 컨트롤러 메서드 안에 try-catch 를 두지 않는다.
- 없는 리소스는 `NotFoundException`(404), 검증 실패는 400, 그 밖의 예외는 500 이다.
- 로그는 SLF4J(`org.slf4j.Logger`)로만 남긴다. `System.out.println`, `System.err.println`, `e.printStackTrace()` 를 쓰지 않는다.
- 로그 레벨: 정상 흐름은 `INFO` 이하, 복구 가능한 실패는 `WARN`, 요청을 실패시키는 예외는 `ERROR`.
- 로그 문자열에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다.

### 테스트

- 새 엔드포인트와 새 public 서비스 메서드마다 테스트가 1개 이상 있다.
- 컨트롤러는 `@WebMvcTest`, 서비스는 Mockito 단위 테스트, 리포지토리 쿼리는 `@DataJpaTest`(H2)로 검증한다. 모두 `@ActiveProfiles("test")` 를 붙인다.
- 테스트 메서드 이름은 camelCase 이고 `@DisplayName` 에 한국어 설명을 붙인다. 엔티티는 `ItemFixtures` 로 만든다.
- `modern/web` 테스트는 `src/test/mockFetch.ts` 로 `fetch` 를 가로챈다. `localhost:8080` 을 실제로 호출하지 않는다.
- 기존 테스트를 지우거나 `@Disabled` · `skip` 으로 바꾸지 않는다.

### 화면 (modern/web)

- 컴포넌트 안에서 `fetch` 를 호출하지 않는다. 서버 데이터는 `src/hooks/use*.ts` 로 읽고, 요청은 `src/api/client.ts` 의 `getJson` 을 거친다.
- 응답 타입은 `src/api/types.ts` 에 백엔드 JSON 필드명 그대로 선언한다.
- 함수 컴포넌트만 쓴다. 이벤트 핸들러 prop 은 `on<동작>`, 구현 함수는 `handle<동작>` 이다.

### 이름 · 형식

- Java: 클래스 `PascalCase`, 메서드 · 필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`. 약어는 `ItemDto` 처럼 첫 글자만 대문자.
- Java: 들여쓰기 4칸, 한 줄 120자 이내, 와일드카드 import 를 쓰지 않는다.
- 조건식 · 계산식에 숫자 리터럴을 직접 쓰지 않고 이름 붙인 상수로 뺀다. 문항 상태 값은 `ItemStatus` 상수를 쓴다.
- 수정하는 파일에 이미 쓰인 스타일을 따른다. 내 취향과 다르더라도 그렇게 한다.
- 식별자와 로그 문자열은 영어로 쓴다. 주석 · Javadoc · `@DisplayName` · 예외 메시지 · 커밋 메시지 · 문서는 한국어로 쓴다.
- 내 변경으로 쓰이지 않게 된 import · 변수 · 함수는 제거한다. 원래부터 있던 죽은 코드는 고치지 않고 답변에 언급만 한다.

### 작업 방식 · 답변

- 요청받은 것을 의도된 범위로 전달한다. 일상적인 판단은 스스로 내리고, 해석에 따라 결과물이 달라지는 경우에만 확인을 구한다.
- 요청이 잘못됐거나 더 나은 방법이 있으면 한 문장으로 말한다. 범위를 좁히거나 넓히거나 다른 일로 바꾸지 않고 요청받은 대로 진행한다.
- 작업은 끝까지 완료한다. 요청 범위를 벗어나는 행동은 하기 전에 멈춘다.
- 발견했지만 손대지 않은 것은 답변 마지막에 목록으로 적는다.
- 답변은 본 답변이 분량의 절반 이상을 차지한다. 단서 · 주의사항은 본 답변보다 짧게 쓴다.
- 설명 요청에는 개괄 수준으로 답한다. 깊이 있는 설명은 따로 요구받았을 때만 쓴다.
- 레거시 동작을 설명하거나 규칙을 옮길 때는 근거를 `파일:줄번호` 로 적는다. 근거 줄을 댈 수 없으면 "확인 필요"로 적는다.

## 3. 금지 사항

- `legacy/` 의 파일을 허락 없이 수정하지 않는다.
- DB 스키마 · 시드(`db/*/init/`)를 바꾸기 전에 사람에게 묻는다. 테이블 · 컬럼 · 인덱스 변경과 마이그레이션 파일 추가가 해당한다.
- 의존성을 추가 · 업그레이드(`build.gradle` 의 `dependencies`, `package.json`)하기 전에 사람에게 묻는다.
- `package-lock.json` 을 손으로 고치거나 지우지 않는다.
- `application.yml` 의 DB 접속 정보 · 커넥션 풀 설정을 바꾸지 않는다.
- 비밀번호 · 토큰 · 키를 코드나 설정 파일에 리터럴로 넣지 않는다. 실습용 더미 값(`application.yml` 의 `app-pass`)은 예외다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출이나 건수 제한 없는 루프를 실행하지 않는다(커넥션 풀 최대 5, 대기 3초).
- `modern/web` 에서 `any`, `as unknown as`, `@ts-ignore`, `dangerouslySetInnerHTML`, `console.log` 를 쓰지 않는다.
- `.env*` 파일을 읽거나 만들지 않는다.
- `characterization/` 의 스냅샷을 깨뜨리는 변경은 하기 전에 사람에게 알린다.
- 요청받지 않은 파일을 고치지 않는다. 포맷팅 · import 정리도 요청 범위 안의 파일에서만 한다.

## 4. 아키텍처 안내

### 이관 구도

| 모듈 | 스택 | 포트 |
|---|---|---|
| `legacy/item-bank-php` 문항 은행 | PHP 7.4 · mysqli | 8081 |
| `legacy/assignment-thymeleaf` 과제 배포 | Spring MVC · Thymeleaf · JDBC | 8082 |
| `legacy/grade-mssql` 성적 집계 | MS-SQL 저장 프로시저 + Java 호출부 | 8083 |
| `modern/api` 이관 목적지 | Spring Boot 3 · JPA | 8080 |
| `modern/web` 화면 | React 18 · Vite | 5173 |

- 문항 은행 · 과제 배포 · `modern/api` 는 MariaDB `itembank` 스키마(`db/mariadb/init/`) 하나를 공유한다. 성적 집계만 MS-SQL `grades` 를 쓴다.
- `characterization/` 은 레거시 HTML 응답과 새 API 의 JSON 응답을 `{status, rows, count, message}` 로 정규화해 같은 스냅샷과 비교한다. 행 순서 · 태그 순서도 비교 대상이므로 새 API 의 정렬은 레거시와 같아야 한다.
- 도메인 용어: 문항 `item` · 단원 `unit`(코드 `M5-1` = 학년-순번) · 난이도 `level`(1~5) · 태그 `tag` / 학급 `class` · 과제 `assignment` · 배포 `distribution` · 제출 `submission`.

### modern/api

```
modern/api/src/main/java/com/example/
├── item/          문항 · 단원 · 태그
├── assignment/    과제 배포 · 재배포 · 학급 리포트
├── common/        GlobalExceptionHandler, NotFoundException, ErrorResponse, Clock 빈
└── config/        CORS (WebConfig)
```

- 외부에 노출하는 문항은 공개(`status='A'`)뿐이다. 삭제는 플래그가 아니라 상태 코드 `D` 다.
- CORS 는 `http://localhost:5173` 출처의 `GET` 만 허용한다. 화면에서 `GET` 이 아닌 엔드포인트를 부르려면 `WebConfig` 변경이 필요하다.
- 기본 프로필은 로컬 MariaDB, `test` 프로필은 H2(MariaDB 모드)다.

### modern/web

- 데이터 흐름: 컴포넌트 → `src/hooks/use*.ts` → `src/api/items.ts` → `src/api/client.ts` 의 `getJson` → `modern/api`.
- 모든 데이터 훅은 `useApiQuery` 위에 만든다. 로딩 · 오류 · 데이터 세 상태는 `AsyncSection` 이 그린다.
- API 기본 주소는 환경 변수 `VITE_API_BASE` 이고 기본값은 `http://localhost:8080` 이다.

## 5. 완료 기준

- 이관 · 리팩토링 작업은 `characterization/` 의 `npm test` 가 전부 통과하기 전에는 완료라고 보고하지 않는다.
- 테스트가 실패하면 실패한 케이스와 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
- 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 멈추고 묻는다.
- 레거시 동작이 버그로 보여도 이관 중에는 고치지 않는다. "의심 동작" 목록으로 따로 보고한다.
