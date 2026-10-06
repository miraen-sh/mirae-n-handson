// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 search.php · 단원 목록 units.php
//
// 케이스는 docs/item-bank/BUSINESS-RULES.md 의 규칙 ID(BR-xx)를 겨냥해 설계했다.
// 케이스 이름 앞의 [BR-xx] 는 겨냥한 규칙이다. 이름이 스냅샷의 키이므로 바꾸지 않는다.
//
// 규칙
// - 대상 주소는 lib/target.mjs 가 정한다(환경 변수 TARGET_BASE_URL, 없으면 레거시 기본 주소).
// - 응답은 fetchNormalized 로 {status, rows, count, message} 모양으로 바꾼 뒤 스냅샷과 비교한다.
// - 기대값을 손으로 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 레거시와 새 API 양쪽에서 같은 결과가 나와야 하므로 건너뛰기를 두지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

// 경계값 (legacy/item-bank-php/search.php:86-89, :237-243, :336-348)
const KEYWORD_MAX_LENGTH = 100;
const TAG_MAX_LENGTH = 50;
const PAGE_MAX = 999;

/** 멀티바이트 문자로 길이를 채운다 — 바이트가 아니라 글자 수로 세는지도 함께 드러난다. */
function repeatChar(length) {
  return '가'.repeat(length);
}

async function search(params) {
  return fetchNormalized(MODULE, PATH, params);
}

describe('item-bank · 문항 검색(search.php)', () => {
  describe('정상 입력', () => {
    it('[BR-01 BR-09 BR-14 BR-18 BR-21] 조건 없는 기본 검색 — 공개 · 난이도 5 미만 · 기본 정렬 · 20건', async () => {
      expect(await search({})).toMatchSnapshot();
    });

    it('[BR-02 BR-04 BR-07 BR-10 BR-12] 키워드 · 단원 · 난이도 · 태그 네 조건 AND 결합', async () => {
      expect(await search({ q: '분수', unit: 'M5-1', level: '3', tag: '계산' })).toMatchSnapshot();
    });

    it('[BR-10 BR-16 BR-21] 난이도 5 직접 지정 + 단원 오름차순 정렬', async () => {
      expect(await search({ level: '5', sort: 'unit', dir: 'asc' })).toMatchSnapshot();
    });
  });

  describe('경계값', () => {
    it('[BR-04 BR-06 BR-35] 키워드 한 글자 — 경고만 하고 검색은 실행', async () => {
      expect(await search({ q: '비' })).toMatchSnapshot();
    });

    it('[BR-05] 키워드 100자 — 자르기 직전', async () => {
      expect(await search({ q: repeatChar(KEYWORD_MAX_LENGTH) })).toMatchSnapshot();
    });

    it('[BR-05] 키워드 101자 — 앞 100자로 잘림', async () => {
      expect(await search({ q: repeatChar(KEYWORD_MAX_LENGTH + 1) })).toMatchSnapshot();
    });

    it('[BR-09 BR-10] 난이도 4 — 기본 조건 level<5 의 안쪽', async () => {
      expect(await search({ level: '4' })).toMatchSnapshot();
    });

    it('[BR-09 BR-10] 난이도 5 — 기본 조건 level<5 의 바깥', async () => {
      expect(await search({ level: '5' })).toMatchSnapshot();
    });

    it('[BR-11] 난이도 0 — 1~5 범위 아래쪽 바깥', async () => {
      expect(await search({ level: '0' })).toMatchSnapshot();
    });

    it('[BR-19 BR-20] 페이지 999 — 상한, 총 페이지 수 초과', async () => {
      expect(await search({ page: String(PAGE_MAX) })).toMatchSnapshot();
    });

    it('[BR-19] 페이지 1000 — 상한 초과, 999 로 보정', async () => {
      expect(await search({ page: String(PAGE_MAX + 1) })).toMatchSnapshot();
    });

    it('[BR-19] 페이지 0 — 경고 없이 1 페이지로 보정', async () => {
      expect(await search({ page: '0' })).toMatchSnapshot();
    });

    it('[BR-13] 태그 50자 — 자르기 직전, 미등록 태그', async () => {
      expect(await search({ tag: repeatChar(TAG_MAX_LENGTH) })).toMatchSnapshot();
    });

    it('[BR-13] 태그 51자 — 앞 50자로 잘림, 미등록 태그', async () => {
      expect(await search({ tag: repeatChar(TAG_MAX_LENGTH + 1) })).toMatchSnapshot();
    });
  });

  describe('빈 값 · 누락', () => {
    it('[BR-09 BR-14 BR-19] 모든 파라미터 빈 문자열', async () => {
      const params = { q: '', unit: '', level: '', tag: '', sort: '', dir: '', page: '' };
      expect(await search(params)).toMatchSnapshot();
    });

    it('[BR-14 BR-17] 정렬 기준 없이 방향만 지정', async () => {
      expect(await search({ dir: 'asc' })).toMatchSnapshot();
    });
  });

  describe('이상한 값', () => {
    it('[BR-11 BR-19 BR-35] 음수 난이도 · 음수 페이지', async () => {
      expect(await search({ level: '-1', page: '-5' })).toMatchSnapshot();
    });

    it('[BR-07 BR-08] 형식은 맞지만 없는 단원 코드', async () => {
      expect(await search({ unit: 'M9-99' })).toMatchSnapshot();
    });

    it('[BR-07 BR-08] 소문자 단원 코드', async () => {
      expect(await search({ unit: 'm5-1' })).toMatchSnapshot();
    });

    it('[BR-06 BR-15 BR-17] 와일드카드 키워드 · 허용되지 않는 정렬 기준 · 방향', async () => {
      expect(await search({ q: '%', sort: 'DROP', dir: 'UP' })).toMatchSnapshot();
    });

    it('[BR-03 BR-10 BR-12] 배열 파라미터 — 첫 값만 사용', async () => {
      expect(await search({ 'level[]': ['2', '3'], 'tag[]': ['개념', '계산'] })).toMatchSnapshot();
    });

    it('[BR-04 BR-15 BR-17] 앞뒤 공백 · 대문자가 섞인 키워드 · 정렬 입력', async () => {
      expect(await search({ q: '  곱셈 ', sort: ' TITLE ', dir: ' DESC' })).toMatchSnapshot();
    });
  });
});

// 단원 목록 units.php — 새 API 에서는 /api/units/item-counts 로 대응한다(lib/target.mjs PATH_ALIASES).
describe('item-bank · 단원 목록(units.php)', () => {
  it('[BR-32 BR-33] 단원 전체 — 공개 문항 수 · 0건 단원 포함 · 학년 · 코드 순', async () => {
    expect(await fetchNormalized(MODULE, '/units.php')).toMatchSnapshot();
  });
});
