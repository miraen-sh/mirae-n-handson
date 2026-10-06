# 머지 전 검증 — migration-review-1

- 검증 대상: `upstream/main...HEAD` (커밋 3개: `4006d82` 테스트 베이스라인, `717e2a2` 문서 · Skill, `4a88d3f` 기능 1개 이관)
- 요청 문장: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 검증자는 코드를 수정하지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 51건 실행, 통과 51 · 실패 0 · 오류 0 · 건너뜀 0. `cd characterization && npm test`(레거시 8081) → 36건 통과. 그러나 이관의 완료 기준인 새 API 대상 `TARGET_BASE_URL=http://localhost:8080 npm test` 는 **실행하지 않음**(8080 응답 없음, 서버를 직접 띄우지 않음). 아래 "확인 필요" 1번 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음 (SQL 은 Specification · JPQL 바인딩, 비밀값 · 삭제 코드 없음) |
| 요청 범위 이탈 없음 | 미충족 | 요청은 엔드포인트 1개인데 2개가 이관됨: `GET /api/items/search`(search.php) 와 `GET /api/units/item-counts`(units.php). 이슈 #1 |
| 컨벤션 준수 | 충족 | 위반 없음 (계층 · DTO record · `@ActiveProfiles("test")` · `@DisplayName` · 120자 · 와일드카드 import 없음 확인). 제안 #3 만 있음 |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/UnitController.java:25-29` (`UnitService.java:31`, `UnitRepository.java` `countItemsByUnit`, `UnitItemCountResponse.java:3`) | 요청은 "엔드포인트 1개" 이관인데 검색 외에 `units.php` 를 옮긴 두 번째 엔드포인트가 추가됐다. characterization 테스트(`characterization/tests/item-bank.test.js:1,15`)는 `search.php` 만 다뤄 이 엔드포인트의 동작 보존은 레거시 대비 검증되지 않았다. 기존 `/api/units` 도 `PATH_ALIASES`(`characterization/lib/target.mjs`)가 `units.php` 에 대응시켜 둬서 경로 의미도 겹친다 | 사람이 둘 다 요청 범위로 볼지 결정한다. 아니면 이 엔드포인트를 별도 변경으로 분리하고, 유지한다면 `units.php` 용 characterization 케이스를 추가한다(베이스라인 스냅샷 변경은 사람과 먼저 상의) |
| 2 | 경고 | `git diff upstream/main...HEAD --stat` 의 `CLAUDE.md`, `.claude/skills/*`, `docs/**`(약 3,900줄) | 변경 34개 중 이관 코드 · 테스트는 `modern/api` 와 `characterization` 뿐이고 나머지는 문서 · Skill · 지침 파일이다. "엔드포인트 1개 이관" 요청 문장으로는 설명되지 않는다(앞선 커밋의 문서화 작업이 같은 범위에 섞인 것으로 보임) | 머지 단위를 나누거나, 요청 문장을 "이관 + 문서화"로 확정한다 |
| 3 | 제안 | `modern/api/src/main/java/com/example/item/UnitRepository.java` `countItemsByUnit` | 저장소 JPQL 이 응답 DTO(`UnitItemCountResponse`)를 직접 생성한다. 컨벤션상 DTO 변환은 서비스 몫이라 계층 경계가 흐려진다(위반 규정은 아님) | 프로젝션 인터페이스나 튜플을 반환하고 서비스에서 DTO 로 변환 |

## 확인 필요

1. 새 API 대상 characterization(`cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test`) 미실행. `http://localhost:8080/` 이 `000`(꺼짐). `docker compose --profile modern up -d` 와 `cd modern/api && ./gradlew bootRun` 으로 기동한 뒤 다시 검증해야 한다. 이관 완료 기준(`CLAUDE.md` 5절)상 이 결과 없이는 승인할 수 없다.
2. `units.php` 의 `item_count` 정렬 · 응답 모양이 `/api/units/item-counts` 와 같은지 레거시 대비 확인된 바 없음(근거 테스트 없음).
3. `ItemSearchCondition` 의 PHP 보정 흉내(`phpTrim`, `phpIntValue`, `PAGE_DIGITS`)가 `search.php` 와 모든 입력에서 일치하는지는 스냅샷 36건이 다룬 경계값 밖에서는 확인하지 못했다.
