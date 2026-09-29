# 문항 은행(legacy/item-bank-php) 비즈니스 규칙 — 코드 기준 교차 검증본

- 읽은 파일: `search.php`, `register.php`, `units.php`, `inc/db.php` (`vendor/`, `docs/` 는 읽지 않음)
- 규칙은 코드 동작 기준이다. 주석과 코드가 다르면 코드를 따랐고, 차이는 해당 규칙에 적었다.
- `v_item_public` 뷰 정의는 이 폴더 밖(DB 초기화 스크립트)에 있어 읽지 않았다. 뷰가 걸러내는 조건은 **확인 필요**.
- 수정(update) 화면 · 코드는 이 폴더에 없다. 등록(`register.php`)만 있다.

## A. 검색 조건 조합

### CX-01 조건은 모두 AND 로 결합한다
- 근거: `search.php:44`, `search.php:98`, `search.php:118`
```php
$where    = " WHERE 1=1";
$where .= " AND (title LIKE ? OR stem LIKE ?)";
$where .= " AND unit_code = ?";
```

### CX-02 키워드는 앞뒤 공백을 제거하고 100자로 자른 뒤 제목 또는 지문의 부분 일치로 찾는다
- 근거: `search.php:85-98`
```php
$q = trim($q);
if (mb_strlen($q, 'UTF-8') > 100) { $q = mb_substr($q, 0, 100, 'UTF-8'); ...
$where .= " AND (title LIKE ? OR stem LIKE ?)";
```
- `%` · `_` 는 이스케이프하지 않아 와일드카드로 동작한다(경고만 표시, `search.php:94-96`).

### CX-03 단원은 코드 문자열과 정확히 일치하는 것만 찾는다
- 근거: `search.php:109-120`
```php
$unit = trim($unit);
...
$where .= " AND unit_code = ?";
```
- 형식 오류 · 소문자 · 미등록 코드여도 조회는 그대로 하고 경고만 남긴다(`search.php:111-117`, `141-143`). 대소문자 구분 여부는 DB 콜레이션에 달려 있어 **확인 필요**.

### CX-04 난이도를 비우면 5 미만(1~4)만 조회한다 — 주석과 코드가 다름
- 근거: `search.php:213-216`
```php
// 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
if ($level == '') {
    $where .= " AND level < 5";
```
- 주석은 1~5 전체라고 하지만 코드는 난이도 5 문항을 제외한다. 난이도를 명시해야만(`level=5`) 5 가 나온다.

### CX-05 난이도가 1~5 한 자리 숫자면 그 값과 같은 문항만 찾는다
- 근거: `search.php:217-221`
```php
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
    $values[] = (int)$level;
```

### CX-06 난이도가 1~5 밖의 값이면 경고를 내고 정수로 바꿔 그대로 비교한다
- 근거: `search.php:223-229`
```php
$warnings[] = '난이도는 1~5 사이여야 합니다.';
$where .= " AND level = ?";
$values[] = (int)$level;
```
- 숫자가 아닌 값은 `(int)` 로 0 이 된다.

### CX-07 태그는 이름이 정확히 일치하는 태그가 붙은 문항만 찾는다 (50자 초과는 자른다)
- 근거: `search.php:235-248`
```php
if (mb_strlen($tag, 'UTF-8') > 50) { $tag = mb_substr($tag, 0, 50, 'UTF-8'); ...
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```
- 태그는 하나만 받는다. 미등록 태그는 경고만 표시하고 조회한다(`search.php:258-260`).

### CX-08 같은 이름의 파라미터가 배열로 오면 첫 값만 쓴다
- 근거: `search.php:53-56`
```php
if (isset($params['q'])) {
    $q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
```
- q · unit · level · tag · sort · dir · page 모두 같은 방식이다(`search.php:57-80`).

### CX-09 입력값 경고는 검색을 막지 않는다
- 근거: `search.php:113`, `225`, `259`, `510-516`
```php
$warnings[] = '단원 코드 형식이 올바르지 않습니다. (예: M5-1)';
```
- 경고는 `warnings` 목록에 모아 결과 위에 표시할 뿐, 쿼리는 그대로 실행된다.

## B. 목록의 제외 조건 · 정렬 · 페이지

### CX-10 검색은 공개 문항 뷰 `v_item_public` 에서만 조회한다
- 근거: `search.php:519-526`
```php
$from   = " FROM v_item_public";
$sql      = $select . $from . $where . $orderBy . $limit;
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```
- 뷰가 어떤 문항을 제외하는지는 이 폴더에서 알 수 없다 → **확인 필요**. 등록 문항이 검색에 안 나오는 이유는 CX-15 참고.

### CX-11 정렬을 지정하지 않으면 난이도 내림차순, 같은 난이도는 id 오름차순이다
- 근거: `search.php:317-319`
```php
case '':
    // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-12 알 수 없는 sort 값은 경고 후 기본 정렬(CX-11)을 쓴다
- 근거: `search.php:322-326`
```php
default:
    $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-13 정렬 기준별 SQL 과 동순위 처리, 방향 기본값
- 근거: `search.php:277-315`, `266-273`, `328-331`
```php
case 'title': ... " ORDER BY title ASC, id ASC"
case 'unit':  ... " ORDER BY unit_code ASC, level DESC, id ASC"
case 'created': ... " ORDER BY created_at DESC, id ASC"
```
- `id`: asc 기본, `desc` 일 때만 내림차순(동순위 없음).
- `title`: asc 기본, `desc` 이면 `title DESC, id ASC`.
- `unit`: asc 기본, `desc` 이면 `unit_code DESC, level DESC, id ASC` (난이도는 항상 내림차순).
- `level`: **desc 기본**, `asc` 일 때만 오름차순. 동순위는 `id ASC`.
- `created`: **desc 기본**, `asc` 일 때만 오름차순. 동순위는 `id ASC`.
- `dir` 이 asc · desc 가 아니면 경고 후 비운다(`search.php:268-273`). sort 와 dir 은 소문자로 바꿔 비교한다(`search.php:266-267`).

### CX-14 한 페이지는 20건이며 페이지 번호는 1~999 로 보정한다
- 근거: `search.php:336-349`, `523`
```php
if ($page > 999) { $page = 999; ...
$offset = ($page - 1) * 20;
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
```
- 숫자가 아니면 1페이지 + 경고(`search.php:339-341`), 0 이하는 조용히 1로 보정한다(`search.php:342-344`).

## C. 등록 시 검증

### CX-15 새로 등록한 문항은 검수중(`R`) 상태로 저장된다
- 근거: `register.php:84`, `92-94`
```php
// 등록 직후에는 검수중(R) 상태로 들어간다 → 검수 완료 전까지 검색 화면에 나오지 않는다.
"INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
```
- 검색에 나오지 않는 것은 `v_item_public` 이 `R` 을 제외한다는 전제이며, 뷰 정의는 **확인 필요**. 이 코드가 확정하는 것은 `R` 저장뿐이다.

### CX-16 제목은 앞뒤 공백 제거 후 5자 이상 200자 이하여야 한다
- 근거: `register.php:41`, `49-54`
```php
$title  = isset($_POST['title']) ? trim((string)$_POST['title']) : '';
if (mb_strlen($title, 'UTF-8') < 5) {
if (mb_strlen($title, 'UTF-8') > 200) {
```
- 길이는 `mb_strlen` 문자 수다. 공백을 제외하고 세지 않는다.

### CX-17 지문은 앞뒤 공백 제거 후 비어 있으면 안 된다
- 근거: `register.php:42`, `56-58`
```php
$stem   = isset($_POST['stem']) ? trim((string)$_POST['stem']) : '';
if ($stem === '') {
```
- 지문 길이 상한은 코드에 없다.

### CX-18 단원은 단원 목록에 있는 id 여야 한다
- 근거: `register.php:60-68`
```php
if ((string)$u['id'] === $unitId) { $unitOk = true; }
if (!$unitOk) { $errors[] = '단원을 선택해야 합니다.';
```
- 등록은 단원 **id**(숫자)를 받고, 검색은 단원 **코드**(`M5-1`)를 받는다(`register.php:43` ↔ `search.php:118`).

### CX-19 난이도는 1~5 한 자리 숫자여야 한다
- 근거: `register.php:70-72`
```php
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
```
- 검색(CX-06)과 달리 범위 밖이면 저장을 거부한다.

### CX-20 태그는 선택 사항이고, 목록에 없는 태그 id 는 오류 없이 버린다
- 근거: `register.php:74-81`, `104`
```php
if ((string)$t['id'] === (string)$tid) { $validTagIds[] = (int)$tid; }
if (!empty($validTagIds)) {
```
- 잘못된 태그 id 가 섞여도 검증 오류는 나지 않고 유효한 것만 저장한다. 개수 상한은 코드에 없다.

### CX-21 검증 오류가 하나라도 있으면 저장하지 않고, 저장은 한 트랜잭션으로 한다
- 근거: `register.php:83-85`, `114`, `119-123`
```php
if (empty($errors)) {
    $conn->begin_transaction();
    ...
} catch (Exception $e) { $conn->rollback();
```
- 오류는 모두 모아서 한 번에 보여 준다(조기 종료 없음, `register.php:49-72`). 문항 · 태그 저장 중 하나라도 실패하면 전체를 롤백한다.

### CX-22 새 문항 id 는 `MAX(id)+1` 로 직접 부여한다
- 근거: `register.php:87-90`
```php
$res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
$newId = (int)$row['next_id'];
```

## D. 단원 목록의 제외 조건 (참고)

### CX-23 단원별 문항 수는 공개(`A`) 문항만 센다
- 근거: `units.php:15-17`
```php
// 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
```
- 단원 목록은 `grade ASC, code ASC` 순이다(`units.php:19`). 검색 화면의 단원 선택 목록도 같은 순서다(`search.php:156`).

## 확인 필요 요약
| 항목 | 이유 |
|---|---|
| `v_item_public` 의 제외 조건 (CX-10, CX-15) | 뷰 정의가 이 폴더 밖 |
| 단원 코드 대소문자 비교 (CX-03) | DB 콜레이션 의존 |
| 문항 수정 시 검증 | 수정 코드가 이 폴더에 없음 |
