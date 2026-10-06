# 머지 전 검증 — migration-review-2

- 검증 대상: `upstream/main...HEAD` (커밋 4개: 1차 검증 이후 `769fcb2 fix: 검증루프 1차 리뷰 반영` 추가)
- 요청 문장: 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 검증자는 코드를 수정하지 않았다. 1차 리뷰는 `docs/verify/migration-review-1.md`.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 미충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 51건, 통과 51 · 실패 0 · 오류 0 · 건너뜀 0. `cd characterization && npm test`(레거시 8081) → 37건 통과(1차 36건 + units 1건). 새 API 대상 `TARGET_BASE_URL=http://localhost:8080 npm test` 는 **실행하지 않음**(8080 응답 없음 `000`, 서버를 직접 띄우지 않음). "확인 필요" 1번 |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음 |
| 요청 범위 이탈 없음 | 미충족 | 요청은 엔드포인트 1개인데 search 와 units(`/api/units/item-counts`) 2개가 이관된 채로 남아 있다. 이슈 #1 |
| 컨벤션 준수 | 충족 | 위반 없음(수정분 포함 재확인) |

## 1차 이슈 처리 현황

| 1차 # | 상태 | 확인 |
|---|---|---|
| 1 (units 엔드포인트 범위 · 테스트 부재) | 부분 해결 | units 용 characterization 케이스와 스냅샷이 추가됐고(`characterization/tests/item-bank.test.js:130-135`) 응답 필드가 레거시 열(`code, name, grade, item_count`)에 맞춰졌다(`UnitItemCountResponse.java:9-13`). 그러나 엔드포인트가 2개라는 범위 문제 자체는 그대로이고, 사람의 범위 확정 기록이 없다 |
| 2 (문서 · Skill 이 같은 범위에 섞임) | 미해결 | 변경 36개 중 이관 코드 외 `CLAUDE.md`, `.claude/skills/*`, `docs/**` 그대로 |
| 3 (저장소가 DTO 생성) | 미해결(제안) | `UnitRepository.java:17` 여전히 `select new …UnitItemCountResponse` |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/UnitController.java:25-29` | 요청 "엔드포인트 1개"와 달리 `GET /api/units/item-counts` 가 함께 이관됐다. 1차 리뷰가 사람에게 범위 확정을 요청했지만 대화 · 커밋에 확정 기록이 없다(`769fcb2` 는 유지하는 쪽으로 코드만 보강) | 사람이 "2개 모두 요청 범위"라고 확정하거나, units 이관을 별도 변경으로 분리한다 |
| 2 | 경고 | `characterization/lib/target.mjs:23`, `characterization/__snapshots__/item-bank.test.js.snap:3-41`, `characterization/tests/item-bank.test.js:130-135` | `CLAUDE.md` 5절·3절은 characterization 의 테스트 코드 · 스냅샷을 이관 중 고치려면 멈추고 사람에게 묻도록 한다. 이번 커밋이 `PATH_ALIASES['/units.php']` 를 `/api/units` → `/api/units/item-counts` 로 바꾸고 테스트 케이스와 스냅샷을 추가했다. 기존 스냅샷 줄 삭제는 0건이라 기존 케이스를 깨지는 않았지만, 승인 없이 이뤄졌는지 확인할 수 없다 | 사람이 이 변경을 승인했는지 확인한다. 새 스냅샷은 레거시 8081 기준이므로 새 API 대상 실행으로 같은 값이 나오는지 검증한다 |
| 3 | 경고 | `docs/verify/migration-review-1.md` 외 `CLAUDE.md`, `.claude/skills/**`, `docs/**` | 요청 문장("엔드포인트 1개 이관")으로 설명되지 않는 문서 · 지침 변경이 같은 머지 범위에 포함됨(1차 #2 미해결) | 머지 단위를 나누거나 요청 문장을 "이관 + 문서화"로 확정한다 |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/UnitRepository.java:17` | 저장소 JPQL 이 응답 DTO 를 직접 생성해 계층 경계가 흐려진다(위반 규정은 아님) | 프로젝션 인터페이스나 튜플 반환 후 서비스에서 DTO 변환 |

## 확인 필요

1. 새 API 대상 characterization 미실행. `http://localhost:8080/` 이 꺼져 있다. `docker compose --profile modern up -d`, `cd modern/api && ./gradlew bootRun` 으로 기동한 뒤 `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test` 를 실행해야 한다. 특히 units 응답(`/api/units/item-counts`)이 정규화 후 스냅샷과 같은지(키 이름 · 순서 · 0건 단원) 이 실행으로만 확인된다. 이관 완료 기준(`CLAUDE.md` 5절)상 필수.
2. `ItemSearchCondition` 의 PHP 보정 흉내가 스냅샷 경계값 밖의 입력에서도 `search.php` 와 같은지는 확인하지 못했다(1차와 동일).
