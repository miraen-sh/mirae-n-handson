# 머지 전 검증 — migration-review-3

- 검증 대상: `git diff upstream/main...HEAD -- modern characterization` (변경 21개 파일, +2649 / −2). HEAD = `2bfb76a`
- 요청 문장: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 절차: 체크리스트 대조는 reviewer Sub-agent, 테스트 실행은 tester Sub-agent 에 위임했고, 네 기준의 최종 판정은 메인 세션이 내렸다. 검증자는 코드를 수정하지 않았고, tester 가 추가한 테스트 파일도 없다.
- 이전 리뷰: `docs/verify/migration-review-1.md`, `docs/verify/migration-review-2.md`. `git diff 769fcb2 HEAD -- modern characterization` 이 비어 있어 2차 리뷰 이후 코드 변경은 없다.
- 작업 트리에 커밋되지 않은 변경 2건(`ItemSearchCondition.java`, `ItemSearchSpecifications.java`)이 있다. 검증 범위(`HEAD`) 밖이라 판정에서 뺐지만 테스트 결과에 영향을 준다. "확인 필요" 1번을 본다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 확인 필요 | 실패 0건이다. 다만 `HEAD` 단독 결과가 아니다(확인 필요 1). `cd modern/api && ./gradlew test`: 51건 통과 · 실패 0 · 건너뜀 0. 이번에는 `:test UP-TO-DATE` 라 직전 실행 결과를 재사용했고, 커밋 안 된 변경이 들어간 작업 트리 기준이다. `cd characterization && npm test`(레거시 8081): 37건 통과 · 실패 0. `characterization/` 은 커밋 안 된 변경이 없어 `HEAD` 와 같다. `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test`: 36건 통과 · 실패 0 · 건너뜀 1. 건너뛴 1건은 원래 있던 `characterization/tests/example-units.test.js:21` 의 `skipIf` 로, 이번 diff 에 없다. 새 public 메서드마다 호출 테스트가 있고(이슈 아래 표), 기존 테스트를 지우거나 skip 하지 않았다 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. `ItemSearchSpecifications.java:44` 의 `"%" + keyword + "%"` 는 `cb.like` 에 바인딩되는 값이다. `UnitRepository.java:15-21` 의 JPQL 은 고정 문자열이고 `:status` 를 바인딩한다. 비밀값 · 삭제 · 덮어쓰기 · 인증 변경도 없다 |
| 요청 범위 이탈 없음 | 미충족 | 요청은 엔드포인트 1개다. 그런데 `GET /api/items/search`(`legacy/item-bank-php/search.php`)와 `GET /api/units/item-counts`(`legacy/item-bank-php/units.php`), 2개가 이관됐다(`ItemSearchController.java` `@GetMapping("/search")`, `UnitController.java:25-29`). 사람이 범위를 확정한 기록이 없다. 이슈 #1, #2 |
| 컨벤션 준수 | 충족 | 반려 항목 위반이 없다. 컨트롤러는 서비스만 주입받는다(`ItemSearchController.java:17-21`). 엔티티 → DTO 변환은 읽기 전용 트랜잭션 안에서 끝난다(`ItemSearchService.java:19`, `:47-58`). 범위 안에 빈 `catch`, `System.out` · `System.err` · `printStackTrace()`, 와일드카드 import, 120자 초과 줄이 없다. 테스트의 상태 리터럴 1건은 제안(#3)이다 |

## 2차 이슈 처리 현황

| 2차 # | 상태 | 확인 |
|---|---|---|
| 1 (엔드포인트 2개 · 범위 확정 기록 없음) | 미해결 | 코드 변경 없음. 범위 확정 기록 없음 |
| 2 (characterization 변경의 승인 여부) | 미해결 | 새 API 대상 실행에서 units 케이스가 통과했다. 하지만 사람의 승인 기록은 여전히 없다 |
| 4 (`UnitRepository` 가 응답 DTO 를 직접 만듦) | 그대로 | 제안 #4 |

## 레거시 동작 비교 (reviewer 대조)

코드로 대조한 범위에서 동작 차이는 없다.

| 항목 | 레거시 근거 | modern |
|---|---|---|
| 상태 'A' 만 노출 | `db/mariadb/init/01-schema.sql:52-70`, `search.php:522` | `ItemSearchSpecifications.java:22` |
| 키워드 trim · 100자 · 제목 또는 지문 LIKE | `search.php:85-101` | `ItemSearchCondition.java:68`, `ItemSearchSpecifications.java:42-48` |
| 단원 정확 일치 | `search.php:109-120` | `ItemSearchCondition.java:69`, `ItemSearchSpecifications.java:50-52` |
| 난이도 기본 `level<5` · (int) 변환 | `search.php:211-230` | `ItemSearchCondition.java:124-143`, `ItemSearchSpecifications.java:29-31` |
| 태그 trim · 50자 · EXISTS | `search.php:235-248` | `ItemSearchCondition.java:70`, `ItemSearchSpecifications.java:62-71` |
| 정렬 표 · 기본 level DESC, id ASC | `search.php:266-327` | `ItemSearchCondition.java:145-164` |
| 페이지 1~999 · 20건 | `search.php:336-349`, `:523` | `ItemSearchCondition.java:177-190`, `ItemSearchService.java:23` |
| 배열 파라미터는 첫 값 | `search.php:53-80` | `ItemSearchCondition.java:88-98` |
| 응답 필드 · 태그 id 순 | `search.php:674-678`, `01-schema.sql:64` | `ItemSearchRow.java:8-21`, `Item.java:59` |
| 전체 건수 · 0건일 때만 안내 문구 | `search.php:594`, `:655-659` | `ItemSearchService.java:46-50` |
| units: 공개 문항 수 · 0개 단원 포함 · 학년 · 코드 순 | `units.php:16-19`, `:40-43` | `UnitRepository.java:15-21`, `UnitItemCountResponse.java:9-13` |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/UnitController.java:25-29`, `UnitService.java:30-35`, `UnitRepository.java:13-22`, `UnitItemCountResponse.java:1-14` | 요청은 엔드포인트 1개다. 그런데 search 와 함께 `GET /api/units/item-counts` 가 이관됐다. 커밋 `4a88d3f` 제목은 "기능 1개 이관"이다. 체크리스트 3절 "모든 변경 파일이 요청 문장으로 설명된다"가 미충족이다. 1 · 2차 리뷰에서 범위 확정을 요청했지만 기록이 없다 | 사람이 요청 문장을 "search · units 2개 이관"으로 고쳐 확정한다. 아니면 units 관련 변경(위 4개 파일과 `UnitControllerTest` · `UnitRepositoryTest` · `UnitServiceTest` 추가분, 이슈 #2 의 characterization 변경)을 별도 변경으로 분리한다 |
| 2 | 경고 | `characterization/lib/target.mjs:23`, `characterization/tests/item-bank.test.js:130-135`, `characterization/__snapshots__/item-bank.test.js.snap:3-41` | `CLAUDE.md` 3 · 5절은 이관 중 characterization 테스트 · 스냅샷을 바꾸기 전에 사람에게 알리고 묻도록 한다. `PATH_ALIASES['/units.php']` 가 `/api/units` 에서 `/api/units/item-counts` 로 바뀌었고, units 케이스와 스냅샷이 추가됐다. 기존 스냅샷 줄은 지우지 않았다. 승인 기록이 없다 | #1 의 결정에 맞춰 유지 또는 분리하고, 사람의 승인을 기록한다 |
| 3 | 제안 | `modern/api/src/test/java/com/example/item/UnitServiceTest.java:58` | `verify(unitRepository).countItemsByUnit("A")` 가 상태 값을 리터럴로 쓴다. `CLAUDE.md` 는 문항 상태 값에 `ItemStatus` 상수를 쓰도록 한다. 실제 값을 확인하려는 의도로 보인다 | `ItemStatus.ACTIVE` 로 바꾸거나, 의도라면 그대로 둔다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/UnitRepository.java:15-16` | JPQL `select new` 로 응답 DTO 를 직접 만들어, 저장소가 API 응답 모양에 묶인다. 계층 규칙 위반은 아니다 | 투영용 record 를 따로 두고 서비스에서 응답 DTO 로 바꾼다 |
| 5 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchService.java:61-63` | 첫 조회와 상세 재조회 사이에 행이 사라지면 `detailsById::get` 이 null 을 돌려줘 `ItemSearchRow.from(null)` 에서 NPE 가 난다. 읽기 전용 트랜잭션이라 가능성은 낮다 | null 을 걸러내거나 명시적 예외로 바꾼다 |

새 public 메서드별 테스트 (tester · reviewer 공통 확인)

| 메서드 | 테스트 |
|---|---|
| `ItemSearchController.search` | `ItemSearchControllerTest` 2건 (`@WebMvcTest`) |
| `ItemSearchService.search` | `ItemSearchServiceTest` 4건 (Mockito) |
| `UnitController.listUnitItemCounts` | `UnitControllerTest.listUnitItemCountsReturnsArray` |
| `UnitService.listUnitItemCounts` | `UnitServiceTest.listUnitItemCountsQueriesActiveStatusOnly` |
| `UnitRepository.countItemsByUnit` | `UnitRepositoryTest.countsActiveItemsPerUnitIncludingEmptyUnits` (`@DataJpaTest`) |
| `ItemRepository.findWithDetailsByIdIn` | `ItemSearchRepositoryTest.findWithDetailsByIdInLoadsUnitAndTagsInOrder` |
| `ItemSearchCondition.from` | `ItemSearchConditionTest` 9건 |

## 확인 필요

1. **`HEAD` 단독 테스트 결과.** tester 는 허용된 명령이 테스트 실행과 `git status` · `git diff` 뿐이라 `HEAD` 를 worktree 로 따로 꺼내지 못했다. 그래서 작업 트리(커밋 안 된 변경 2건 포함)에서 실행했다. 영향은 두 가지다.
   - `./gradlew test` 51건 중 `ItemSearchConditionTest` · `ItemSearchRepositoryTest`(18건)는 커밋 안 된 두 파일을 직접 검증하므로 `HEAD` 결과로 볼 수 없다.
   - 8080 서버가 `HEAD` 코드로 떠 있는지도 판별하지 못했다. 커밋 안 된 변경은 `name[키]` 배열 키와 키워드의 `\` 처리에만 닿는다. characterization 케이스(`item-bank.test.js:117`, `:121`)는 이 경로를 지나지 않아, 결과로도 어느 코드인지 구분되지 않는다.
   - 해소하려면 사람이 `HEAD` worktree 에서 `./gradlew test` 를 실행하고, 그 worktree 로 8080 을 다시 띄워 `TARGET_BASE_URL=http://localhost:8080 npm test` 를 다시 돌린다.
2. **심각도 분류.** 체크리스트의 심각도 정의(`templates/approval-checklist.md:13-19`)에서 "경고"는 반려 항목을 어기지 않은 문제다. 그런데 이슈 #1 · #2 는 "요청 범위 이탈 없음" 기준을 미충족으로 만든다. 정의에 맞는 칸이 없어 이전 리뷰와 같이 "경고"로 적었다. 반려 판정은 기준 표에 따른 것이다.
3. **DB 콜레이션에 기대는 동작.** `unit=m5-1` 이 5건을 돌려주는 것은 `utf8mb4_unicode_ci`(`db/mariadb/init/01-schema.sql:5`) 덕분이다(스냅샷 `item-bank.test.js.snap:1169-1227`). H2 단위 테스트는 이 경우를 다루지 않는다. 새 API 대상 characterization 이 통과했으므로 현재 8080 에서는 같다. 다만 확인 필요 1 때문에 `HEAD` 기준인지는 확정되지 않았다.
4. **스냅샷 경계값 밖의 입력.** `ItemSearchCondition` 의 PHP 흉내(trim, (int) 변환)가 `1e3`, 정수 범위를 넘는 난이도 같은 입력에서도 레거시와 같은지는 코드를 읽어서만 대조했다.
5. **작업 트리의 커밋 안 된 변경 2건.** 검색 동작을 바꾸는 변경이다. 커밋하면 다음 검증 대상이 되고, 그때 이 동작을 덮는 characterization 케이스(`\` 포함 키워드, `q[0]` 같은 인덱스 키)가 있는지도 확인해야 한다.

## 발견했지만 손대지 않은 것

- `characterization/lib/target.mjs:54` 의 빈 `.catch(() => {})` 는 원래 있던 코드라 이슈로 올리지 않았다.
- 브랜치 전체(`git diff --stat upstream/main...HEAD`)에는 `modern` · `characterization` 밖의 변경(`CLAUDE.md`, `.claude/**`, `docs/**`)도 있다. 이번 검증은 범위를 좁혀서 판정하지 않았다.
