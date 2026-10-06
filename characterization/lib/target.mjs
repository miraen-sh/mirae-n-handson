// 테스트 대상 주소 결정.
//
// - 환경 변수 TARGET_BASE_URL 이 있으면 그 주소를 쓴다(예: 이관 후 새 API 의 8080 포트 주소).
// - 없으면 모듈별 레거시 기본 주소를 쓴다. 기본 주소는 이 파일 한 곳에만 적는다.
import { normalize } from './normalize.mjs';

/** 모듈명 → 레거시 기본 주소. 테스트 코드에는 주소를 직접 적지 않는다. */
export const DEFAULT_BASE_URLS = {
  'item-bank': 'http://localhost:8081',
  assignment: 'http://localhost:8082',
  grade: 'http://localhost:8083',
};

export const MODULES = Object.keys(DEFAULT_BASE_URLS);

/**
 * 레거시 경로 → 이관 후 경로 대응표.
 * 테스트는 레거시 경로로 적는다. 대상 서버가 그 경로를 404 로 돌려주면 여기 적힌 새 경로로 한 번 더 요청한다.
 * 이관하면서 경로가 바뀐 화면이 있으면 여기에 한 줄 추가한다(테스트 코드와 스냅샷은 그대로).
 */
export const PATH_ALIASES = {
  '/search.php': '/api/items/search',
  '/units.php': '/api/units/item-counts',
};

/** 모듈의 대상 주소(끝에 슬래시 없음). */
export function baseUrl(module) {
  const fromEnv = (process.env.TARGET_BASE_URL || '').trim();
  if (fromEnv.length > 0) return fromEnv.replace(/\/+$/, '');
  const fallback = DEFAULT_BASE_URLS[module];
  if (!fallback) {
    throw new Error(`알 수 없는 모듈명 "${module}" — ${MODULES.join(' | ')} 중 하나여야 합니다`);
  }
  return fallback;
}

/** 경로 + 파라미터 → 전체 URL. 값이 undefined/null 인 파라미터는 빼고, 빈 문자열은 그대로 보낸다(빈값 케이스용). */
export function buildUrl(module, path, params = {}) {
  const url = new URL(path.startsWith('/') ? path : `/${path}`, `${baseUrl(module)}/`);
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    if (Array.isArray(value)) value.forEach((v) => url.searchParams.append(key, String(v)));
    else url.searchParams.append(key, String(value));
  }
  return url.toString();
}

/** 원 응답이 필요할 때 쓰는 낮은 수준의 요청. 404 이고 대응표에 새 경로가 있으면 그 경로로 다시 요청한다. */
export async function fetchRaw(module, path, params = {}) {
  const headers = { accept: 'application/json, text/html;q=0.9, */*;q=0.8' };
  let response = await fetch(buildUrl(module, path, params), { headers, redirect: 'manual' });
  const alias = PATH_ALIASES[path];
  if (response.status === 404 && alias) {
    await response.arrayBuffer().catch(() => {});
    response = await fetch(buildUrl(module, alias, params), { headers, redirect: 'manual' });
  }
  return response;
}

/**
 * 요청하고 바로 정규화한다. 테스트에서는 보통 이것만 쓰면 된다.
 *   const result = await fetchNormalized('item-bank', '/search.php', { q: '분수', level: 3 });
 *   expect(result).toMatchSnapshot();
 */
export async function fetchNormalized(module, path, params = {}) {
  const response = await fetchRaw(module, path, params);
  return normalize(response);
}
