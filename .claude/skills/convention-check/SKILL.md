---
name: convention-check
description: 변경된 코드가 CLAUDE.md 의 코딩 컨벤션과 금지 사항을 지키는지 점검하고 위반 목록을 표로 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인" 같은 요청을 받았을 때, 또는 커밋 · 리뷰 요청 직전에 사용한다.
argument-hint: "[점검할 경로]"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 코딩 컨벤션 점검

변경된 코드가 CLAUDE.md 의 규칙을 지키는지 점검하고 결과를 보고한다.

- 코드를 직접 고치지 않는다. 점검 결과만 보고한다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.

## 1. 점검 대상

- 인자(`$ARGUMENTS`)로 경로를 받으면 그 경로의 파일을 점검한다.
- 인자가 없으면 `git status --porcelain` 과 `git diff HEAD --name-only` 에 잡힌 변경 파일을 점검한다.
  아직 add 하지 않은 새 파일(`??`)도 포함한다.
- 삭제된 파일과 `legacy/` 의 파일은 점검 대상에서 뺀다.
- 점검 대상이 없으면 "점검할 변경 파일이 없음"이라고 답하고 끝낸다.

## 2. 점검 항목

각 항목을 예 / 아니오로 판정한다. "예"가 위반이다.

| # | 점검 항목 | 대상 |
|---|---|---|
| 1 | 컨트롤러가 Repository 를 주입 · 호출하거나 SQL 문자열을 실행하는가 | `modern/api` `*Controller.java` |
| 2 | 컨트롤러 메서드의 반환 타입에 엔티티가 쓰였는가 | `modern/api` `*Controller.java` |
| 3 | 컨트롤러 메서드 안에 try-catch 가 있거나, 빈 `catch` 블록이 있는가 | `modern/api` Java |
| 4 | `System.out.println`, `System.err.println`, `e.printStackTrace()` 를 쓰는가 | `modern/api` Java |
| 5 | `LocalDateTime.now()` 를 인자 없이 호출하는가 | `modern/api` Java |
| 6 | 와일드카드 import 가 있는가 | `modern/api` Java |
| 7 | 테스트에 `@Disabled` · `skip` 이 있거나, `@ActiveProfiles("test")` · 한국어 `@DisplayName` 이 빠졌는가 | 테스트 파일 |
| 8 | `any`, `as unknown as`, `@ts-ignore`, `dangerouslySetInnerHTML`, `console.log` 를 쓰거나, 컴포넌트 안에서 `fetch` 를 직접 호출하는가 | `modern/web` |

## 3. 점검 절차

1. 점검 대상 파일 목록을 확정하고, 파일마다 해당하는 항목을 고른다.
2. Grep 으로 후보 줄을 찾고, Read 로 앞뒤 문맥을 읽어 실제 위반인지 확인한다.
   주석 · 문자열 안의 일치는 위반으로 세지 않는다.
3. 위반마다 `파일:줄번호` 를 적는다. 줄번호를 확인하지 못한 것은 위반 목록에 넣지 않는다.
4. 위 8개 항목 밖의 문제는 위반 목록에 넣지 않고, 필요하면 "확인 필요"에 한 줄로 적는다.

## 4. 출력 형식

아래 순서를 바꾸지 않는다.

### 판정 요약

- 점검 파일 수, 위반 건수, 항목별 판정(1~8 각각 통과 / 위반 / 해당 없음)을 적는다.

### 위반 목록

| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|
| `modern/api/src/main/java/com/example/item/UnitController.java:42` | 3. 컨트롤러 안 try-catch | 예외를 던지고 `GlobalExceptionHandler` 에서 변환 |

- 위반이 없으면 표 대신 "위반 없음"이라고 적는다.
- 판정하지 못한 것은 표 아래에 "확인 필요" 목록으로 적는다.
