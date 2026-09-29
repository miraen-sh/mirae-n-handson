# 문항 은행(legacy/item-bank-php) 비즈니스 규칙 후보

분석 기준일: 2026-09-29. 선행 문서는 [ARCHITECTURE.md](ARCHITECTURE.md), [ERD.md](ERD.md) 다.
근거의 파일 경로는 저장소 루트 기준으로 적는다. 줄번호 없이 쓴 PHP 파일 이름은 `legacy/item-bank-php/` 기준이다.
소스를 읽어서 정리했다. 화면을 띄워 동작을 확인하지는 않았다.

- 코드(조건문 · SQL)에 있는 것만 규칙으로 올렸다. 주석뿐인 내용은 5절에 따로 모았다.
- 확신도 "확실"은 조건문 · SQL 로 확인한 것, "추정"은 이름이나 주석으로 짐작한 것이다.
- **이 모듈에는 문항 수정 기능이 없다.** UPDATE · DELETE 문이 없고 (`ERD.md` 3절), 수정 화면 파일도 없다. 그래서 "수정 시의 검증" 규칙은 없다.

## 1. 문항 검색 (`search.php`)

### 1.1 조회 대상 · 조건 조합

#### BR-01
- 규칙: 문항의 상태가 공개(`status = 'A'`)가 아니면 검색 결과에서 제외한다.
- 근거: legacy/item-bank-php/search.php:522, db/mariadb/init/01-schema.sql:68-70
- 근거 코드:
  ```php
  $from   = " FROM v_item_public";
  ```
  ```sql
  WHERE i.status = 'A';
  ```
- 확신도: 확실
- 비고: 조건이 PHP 가 아니라 뷰 정의 안에 있다. 같은 공개 조건이 legacy/item-bank-php/units.php:17 에 따로 구현돼 있다(BR-32).

#### BR-02
- 규칙: 키워드 · 단원 · 난이도 · 태그 조건이 여러 개 입력되면 모두 AND 로 결합한다.
- 근거: legacy/item-bank-php/search.php:44, legacy/item-bank-php/search.php:98, legacy/item-bank-php/search.php:118, legacy/item-bank-php/search.php:215, legacy/item-bank-php/search.php:218, legacy/item-bank-php/search.php:244
- 근거 코드:
  ```php
  $where    = " WHERE 1=1";
  $where .= " AND unit_code = ?";
  $where .= " AND level = ?";
  ```
- 확신도: 확실
- 비고: OR 결합은 키워드 조건 안쪽(제목 OR 지문)에만 있다(BR-04). 난이도는 비워도 조건이 붙는다(BR-09).

#### BR-03
- 규칙: 파라미터가 배열로 들어오면 첫 값만 사용한다.
- 근거: legacy/item-bank-php/search.php:55, legacy/item-bank-php/search.php:59, legacy/item-bank-php/search.php:63, legacy/item-bank-php/search.php:67, legacy/item-bank-php/search.php:71, legacy/item-bank-php/search.php:75, legacy/item-bank-php/search.php:79
- 근거 코드:
  ```php
  $q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
  ```
- 확신도: 확실
- 비고: 태그도 한 개만 조건으로 쓸 수 있다. 여러 태그 동시 검색은 없다.

### 1.2 키워드

#### BR-04
- 규칙: 키워드가 있으면 제목 또는 지문에 키워드가 포함된(부분 일치) 문항만 조회한다.
- 근거: legacy/item-bank-php/search.php:90, legacy/item-bank-php/search.php:97-98
- 근거 코드:
  ```php
  $like = '%' . $q . '%';
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```
- 확신도: 확실
- 비고: 앞뒤 공백은 제거한 뒤 비교한다 (legacy/item-bank-php/search.php:85).

#### BR-05
- 규칙: 키워드가 100자를 넘으면 앞 100자만 사용하고 경고를 표시한다.
- 근거: legacy/item-bank-php/search.php:86-89
- 근거 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
  ```
- 확신도: 확실
- 비고: 매직 넘버 100. 폼의 `maxlength="100"` 에도 같은 숫자가 따로 있다 (legacy/item-bank-php/search.php:494).

#### BR-06
- 규칙: 키워드가 한 글자이거나 `%` · `_` 를 포함하면 경고만 표시하고 검색은 그대로 실행한다.
- 근거: legacy/item-bank-php/search.php:91-96
- 근거 코드:
  ```php
  if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
      $warnings[] = '키워드의 % 와 _ 는 와일드카드로 처리됩니다.';
  ```
- 확신도: 확실
- 비고: `%` · `_` 를 이스케이프하지 않으므로 사용자가 넣은 와일드카드가 LIKE 에서 그대로 동작한다.

### 1.3 단원

#### BR-07
- 규칙: 단원 코드가 있으면 단원 코드가 입력값과 일치하는 문항만 조회한다.
- 근거: legacy/item-bank-php/search.php:109-110, legacy/item-bank-php/search.php:118-120
- 근거 코드:
  ```php
  $where .= " AND unit_code = ?";
  $types .= 's';
  $values[] = $unit;
  ```
- 확신도: 확실
- 비고: 부분 일치 · 학년 단위 검색은 없다. `unit.code` 의 콜레이션이 `utf8mb4_unicode_ci` 라서 (db/mariadb/init/01-schema.sql:18) 소문자 입력도 일치로 처리될 수 있다 — 실행으로는 확인하지 않음(확인 필요).

#### BR-08
- 규칙: 단원 코드가 형식(`영문 1자 + 숫자 1~2자 + '-' + 숫자 1~2자`)에 맞지 않거나, 대문자가 아니거나, 등록되지 않은 코드이면 경고만 표시하고 입력값 그대로 조회한다.
- 근거: legacy/item-bank-php/search.php:111-117, legacy/item-bank-php/search.php:141-143
- 근거 코드:
  ```php
  if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
      // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
      $warnings[] = '단원 코드 형식이 올바르지 않습니다. (예: M5-1)';
  ```
- 확신도: 확실
- 비고: 검증 실패가 조회를 막지 않는다. 형식 정규식은 소문자를 허용하면서 (legacy/item-bank-php/search.php:111) 바로 아래에서 대문자가 아니면 경고한다 (legacy/item-bank-php/search.php:115).

### 1.4 난이도

#### BR-09
- 규칙: 난이도를 비우면 난이도 5 미만(1~4)인 문항만 조회한다.
- 근거: legacy/item-bank-php/search.php:213-216
- 근거 코드:
  ```php
  // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
  if ($level == '') {
      $where .= " AND level < 5";
  ```
- 확신도: 확실
- 비고: **주석과 코드 불일치.** 주석은 "1~5 모두 포함"인데 코드는 난이도 5 를 제외한다. 폼의 선택지 이름도 "전체"다 (legacy/item-bank-php/search.php:192). 시드 주석은 `status='A' AND level<5` 가 20건이라고 적고 있다 (db/mariadb/init/02-seed.sql:34). 의도된 규칙인지 확인 필요. 매직 넘버 5. 난이도 5 문항은 난이도 5 를 직접 고를 때만 나온다(BR-10).

#### BR-10
- 규칙: 난이도가 1~5 한 자리 숫자이면 난이도가 그 값과 같은 문항만 조회한다.
- 근거: legacy/item-bank-php/search.php:217-220
- 근거 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
  ```
- 확신도: 확실
- 비고: 범위 검색(이상 · 이하)은 없다.

#### BR-11
- 규칙: 난이도가 비어 있지 않고 1~5 도 아니면 경고를 표시하고 정수로 변환한 값과 같은 문항을 조회한다.
- 근거: legacy/item-bank-php/search.php:223-229
- 근거 코드:
  ```php
  $warnings[] = '난이도는 1~5 사이여야 합니다.';
  $where .= " AND level = ?";
  $values[] = (int)$level;
  ```
- 확신도: 확실
- 비고: 숫자가 아닌 문자열은 `(int)` 변환으로 0 이 된다. 같은 "1~5" 검증이 등록에서는 거부(BR-26), 검색에서는 경고 후 진행으로 다르게 구현돼 있다.

### 1.5 태그

#### BR-12
- 규칙: 태그가 있으면 이름이 입력값과 일치하는 태그가 달린 문항만 조회한다.
- 근거: legacy/item-bank-php/search.php:235-236, legacy/item-bank-php/search.php:244-247
- 근거 코드:
  ```php
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```
- 확신도: 확실
- 비고: 부분 일치는 없다. `tag.name` 도 콜레이션이 `utf8mb4_unicode_ci` 다 (db/mariadb/init/01-schema.sql:40).

#### BR-13
- 규칙: 태그가 50자를 넘으면 앞 50자만 사용하고, `%` · `_` 를 포함하거나 등록되지 않은 태그이면 경고만 표시하고 그대로 조회한다.
- 근거: legacy/item-bank-php/search.php:237-243, legacy/item-bank-php/search.php:251-260
- 근거 코드:
  ```php
  if (mb_strlen($tag, 'UTF-8') > 50) {
      $tag = mb_substr($tag, 0, 50, 'UTF-8');
  ```
  ```php
  if (!$tagKnown) {
      $warnings[] = '등록되지 않은 태그입니다: ' . $tag;
  ```
- 확신도: 확실
- 비고: 매직 넘버 50. `tag.name VARCHAR(50)` 과 같은 값이지만 (db/mariadb/init/01-schema.sql:37) 코드에서 스키마를 참조하지는 않는다. 등록 여부 비교는 PHP 의 `===` 라서 대소문자를 구분한다 (legacy/item-bank-php/search.php:253).

### 1.6 정렬

#### BR-14
- 규칙: 정렬 기준을 비우면 난이도 내림차순, 같은 난이도에서는 id 오름차순으로 정렬한다.
- 근거: legacy/item-bank-php/search.php:317-320
- 근거 코드:
  ```php
  case '':
      // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
      $orderBy = " ORDER BY level DESC, id ASC";
  ```
- 확신도: 확실
- 비고: 같은 ORDER BY 문자열이 세 곳에 따로 있다 (legacy/item-bank-php/search.php:305, legacy/item-bank-php/search.php:319, legacy/item-bank-php/search.php:325). `characterization/` 이 행 순서를 비교하므로 이관 시 그대로 옮겨야 한다.

#### BR-15
- 규칙: 정렬 기준이 `id` · `title` · `unit` · `level` · `created` 가 아니면 경고를 표시하고 기본 정렬을 사용한다.
- 근거: legacy/item-bank-php/search.php:322-326
- 근거 코드:
  ```php
  default:
      $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
      $sort = '';
  ```
- 확신도: 확실
- 비고: 정렬 기준은 앞뒤 공백을 제거하고 소문자로 바꾼 뒤 비교한다 (legacy/item-bank-php/search.php:266). 정렬 방향도 같다(BR-17). `created` 는 허용되지만 열 머리글 정렬 링크에는 없다 (legacy/item-bank-php/search.php:392-397).

#### BR-16
- 규칙: 정렬 기준이 주어지면 아래 표의 순서로 정렬하고, 2차 이후 정렬 키의 방향은 `dir` 과 상관없이 고정이다.
- 근거: legacy/item-bank-php/search.php:277-315
- 근거 코드:
  ```php
  $orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
  ```
  ```php
  $orderBy = " ORDER BY created_at DESC, id ASC";
  ```
- 확신도: 확실
- 비고: `unit_code` 는 문자열 정렬이라 `M5-10` 이 `M5-2` 보다 앞에 온다(코드에 숫자 정렬 처리가 없음).

  | sort | `dir` 을 비웠을 때 | 반대 방향 | 근거 |
  |---|---|---|---|
  | `id` | `id ASC` | `id DESC` | legacy/item-bank-php/search.php:277-283 |
  | `title` | `title ASC, id ASC` | `title DESC, id ASC` | legacy/item-bank-php/search.php:285-291 |
  | `unit` | `unit_code ASC, level DESC, id ASC` | `unit_code DESC, level DESC, id ASC` | legacy/item-bank-php/search.php:293-299 |
  | `level` | `level DESC, id ASC` | `level ASC, id ASC` | legacy/item-bank-php/search.php:301-307 |
  | `created` | `created_at DESC, id ASC` | `created_at ASC, id ASC` | legacy/item-bank-php/search.php:309-315 |

#### BR-17
- 규칙: 정렬 방향은 앞뒤 공백을 제거하고 소문자로 바꾼 뒤 비교한다. `asc` · `desc` 가 아니면 방향을 비운 것으로 처리하고, 비어 있지 않은 값이었다면 경고를 표시한다.
- 근거: legacy/item-bank-php/search.php:267-273
- 근거 코드:
  ```php
  $dir  = strtolower(trim($dir));
  if ($dir !== 'asc' && $dir !== 'desc') {
      if ($dir !== '') {
          $warnings[] = '정렬 방향은 asc 또는 desc 만 가능합니다.';
  ```
- 확신도: 확실
- 비고: 방향을 비웠을 때의 기본 방향이 두 곳에 따로 구현돼 있다. 실제 SQL 은 `switch` 안의 `else` 분기 (legacy/item-bank-php/search.php:277-315), 화면 표시는 legacy/item-bank-php/search.php:328-331. 지금은 두 곳의 결과가 같다.

### 1.7 페이지 · 출력

#### BR-18
- 규칙: 검색 결과는 한 페이지에 20건씩 조회한다.
- 근거: legacy/item-bank-php/search.php:349, legacy/item-bank-php/search.php:523, legacy/item-bank-php/search.php:553
- 근거 코드:
  ```php
  $offset = ($page - 1) * 20;
  $limit  = " LIMIT 20 OFFSET " . (int)$offset;
  ```
- 확신도: 확실
- 비고: 매직 넘버 20 이 세 곳에 숫자로 들어 있다. 페이지 크기를 바꾸는 파라미터는 없다.

#### BR-19
- 규칙: 페이지 번호가 숫자가 아니거나 1 보다 작으면 1페이지를, 999 보다 크면 999페이지를 조회한다.
- 근거: legacy/item-bank-php/search.php:336-348
- 근거 코드:
  ```php
  if ($page > 999) {
      $page = 999;
      $warnings[] = '페이지 번호는 999 를 넘을 수 없습니다.';
  ```
- 확신도: 확실
- 비고: 매직 넘버 999. `page=0` 은 경고 없이 1페이지가 되고 (legacy/item-bank-php/search.php:342-344), `page=-1` · `page=abc` 는 경고와 함께 1페이지가 된다 (legacy/item-bank-php/search.php:339-341). `page` 를 빈 문자열로 보내면 경고 없이 1페이지가 된다 (legacy/item-bank-php/search.php:339). 총 페이지 수를 넘는 번호는 막지 않는다 — 건수는 그대로 나오고 표의 행만 비게 된다 (legacy/item-bank-php/search.php:657, legacy/item-bank-php/search.php:672).

#### BR-20
- 규칙: 검색 결과가 0건이면 표 대신 "검색 결과가 없습니다" 문구를 표시한다.
- 근거: legacy/item-bank-php/search.php:655-660
- 근거 코드:
  ```php
  if ($total === 0) {
      echo '<p id="message">검색 결과가 없습니다</p>' . "\n";
      return;
  ```
- 확신도: 확실
- 비고: 건수 문구 `검색 결과 N건` 은 0건일 때도 출력한다 (legacy/item-bank-php/search.php:655).

#### BR-21
- 규칙: 문항에 태그가 여러 개이면 태그 id 오름차순으로 쉼표로 이어 표시하고, 태그가 없으면 빈 문자열로 표시한다.
- 근거: db/mariadb/init/01-schema.sql:64-67, legacy/item-bank-php/search.php:678
- 근거 코드:
  ```sql
  (SELECT GROUP_CONCAT(t.name ORDER BY t.id SEPARATOR ',')
  ```
  ```php
  echo '<td>' . h($r['tag_names'] === null ? '' : $r['tag_names']) . '</td>';
  ```
- 확신도: 확실
- 비고: 태그 순서도 `characterization/` 비교 대상이다. 결과 열 순서는 id · 제목 · 단원 코드 · 난이도 · 태그다 (legacy/item-bank-php/search.php:674-678).

### 1.8 경고

#### BR-35
- 규칙: 입력값 경고가 있어도 검색은 그대로 실행하고, 경고는 목록으로 모아 검색 결과 위에 표시한다.
- 근거: legacy/item-bank-php/search.php:510-516, legacy/item-bank-php/search.php:652
- 근거 코드:
  ```php
  if (!empty($warnings)) {
      $warningHtml = '<ul id="warnings" class="error">' . "\n";
      foreach ($warnings as $w) {
          $warningHtml .= '  <li>' . h($w) . '</li>' . "\n";
  ```
  ```php
  echo $built['warning_html'];
  ```
- 확신도: 확실
- 비고: 교차 검증(CROSS-CHECK 의 CX-09)으로 추가했다. 경고를 내는 규칙은 BR-05, BR-06, BR-08, BR-11, BR-13, BR-15, BR-17, BR-19 다. 경고는 조건 요약 · 건수 문구보다 먼저 출력한다 (legacy/item-bank-php/search.php:652-655). 등록은 같은 종류의 검증 실패를 거부한다(BR-28).

## 2. 문항 등록 (`register.php`)

### 2.1 검증

#### BR-22
- 규칙: 제목이 앞뒤 공백을 제거한 뒤 5자 미만이면 등록을 거부한다.
- 근거: legacy/item-bank-php/register.php:41, legacy/item-bank-php/register.php:49-51
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') < 5) {
      $errors[] = '제목은 5자 이상 입력해야 합니다.';
  ```
- 확신도: 확실
- 비고: 매직 넘버 5. 글자 수는 바이트가 아니라 UTF-8 문자 수이고, 가운데 공백은 글자로 센다. 빈 제목도 이 조건에 걸린다(별도 필수 검사는 없음).

#### BR-23
- 규칙: 제목이 200자를 넘으면 등록을 거부한다.
- 근거: legacy/item-bank-php/register.php:52-54
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') > 200) {
      $errors[] = '제목은 200자를 넘을 수 없습니다.';
  ```
- 확신도: 확실
- 비고: 매직 넘버 200. `item.title VARCHAR(200)` 과 같은 값이다 (db/mariadb/init/01-schema.sql:23). 검색의 키워드 · 태그는 잘라서 쓰지만(BR-05, BR-13) 제목은 거부한다.

#### BR-24
- 규칙: 지문이 앞뒤 공백을 제거한 뒤 비어 있으면 등록을 거부한다.
- 근거: legacy/item-bank-php/register.php:42, legacy/item-bank-php/register.php:56-58
- 근거 코드:
  ```php
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  ```
- 확신도: 확실
- 비고: 지문의 최소 · 최대 길이 검사는 없다.

#### BR-25
- 규칙: 단원 id 가 `unit` 테이블에 있는 id 와 일치하지 않으면 등록을 거부한다.
- 근거: legacy/item-bank-php/register.php:60-68
- 근거 코드:
  ```php
  if ((string)$u['id'] === $unitId) {
      $unitOk = true;
  ```
- 확신도: 확실
- 비고: 오류 문구는 "단원을 선택해야 합니다." 하나라서 선택하지 않은 경우와 없는 id 를 보낸 경우가 구분되지 않는다. 등록은 단원 **id**, 검색은 단원 **코드**를 받는다.

#### BR-26
- 규칙: 난이도가 1~5 한 자리 숫자가 아니면 등록을 거부한다.
- 근거: legacy/item-bank-php/register.php:70-72
- 근거 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  ```
- 확신도: 확실
- 비고: 같은 정규식이 legacy/item-bank-php/search.php:217 에 따로 있고, 실패 시 처리가 다르다(BR-11). 스키마에는 `level` 범위 제약이 없다 (db/mariadb/init/01-schema.sql:25, 주석만 있음).

#### BR-27
- 규칙: 선택한 태그 id 가 `tag` 테이블에 없으면 오류 없이 그 태그만 버리고 등록한다.
- 근거: legacy/item-bank-php/register.php:74-81, legacy/item-bank-php/register.php:104
- 근거 코드:
  ```php
  if ((string)$t['id'] === (string)$tid) {
      $validTagIds[] = (int)$tid;
  ```
- 확신도: 확실
- 비고: 태그는 선택 사항이다(0개 허용, legacy/item-bank-php/register.php:104). 태그 개수 상한은 없다. 같은 id 를 두 번 보내면 중복 제거 없이 두 번 담겨 `item_tag` 의 PK (db/mariadb/init/01-schema.sql:45) 위반으로 등록 전체가 롤백될 것으로 보인다 — 실행으로는 확인하지 않음(확인 필요).

#### BR-28
- 규칙: 검증 오류가 하나라도 있으면 저장하지 않고, 걸린 오류를 모두 표시한다.
- 근거: legacy/item-bank-php/register.php:83, legacy/item-bank-php/register.php:133-139
- 근거 코드:
  ```php
  if (empty($errors)) {
  ```
- 확신도: 확실
- 비고: 첫 오류에서 멈추지 않고 모든 검증을 실행한다. 오류가 있으면 입력값은 폼에 그대로 남는다 (legacy/item-bank-php/register.php:144, legacy/item-bank-php/register.php:148).

### 2.2 저장

#### BR-29
- 규칙: 문항을 등록하면 상태를 검수중(`'R'`)으로, 등록 시각과 수정 시각을 DB 의 현재 시각으로 저장한다.
- 근거: legacy/item-bank-php/register.php:92-95
- 근거 코드:
  ```php
  "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
   VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
  ```
- 확신도: 확실
- 비고: 스키마의 `status` 기본값은 `'A'` 인데 (db/mariadb/init/01-schema.sql:26) 등록은 `'R'` 을 직접 넣는다. BR-01 과 합치면 새로 등록한 문항은 검색에 나오지 않는다. `'R'` 을 `'A'` 로 바꾸는 코드는 이 모듈에 없다(5절).

#### BR-30
- 규칙: 문항을 등록하면 id 는 기존 문항 id 의 최댓값 + 1 로 정하고, 문항이 하나도 없으면 1 로 정한다.
- 근거: legacy/item-bank-php/register.php:87-90
- 근거 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  ```
- 확신도: 확실
- 비고: 상태와 상관없이 전체 문항에서 최댓값을 구한다(삭제 · 검수중 포함). `item.id` 에 `AUTO_INCREMENT` 가 없다 (db/mariadb/init/01-schema.sql:21).

#### BR-31
- 규칙: 문항 저장이나 태그 저장이 실패하면 문항과 태그 연결을 모두 롤백하고 "저장 중 오류가 발생했습니다." 를 표시한다.
- 근거: legacy/item-bank-php/register.php:85, legacy/item-bank-php/register.php:99-101, legacy/item-bank-php/register.php:108-110, legacy/item-bank-php/register.php:114, legacy/item-bank-php/register.php:119-123
- 근거 코드:
  ```php
  } catch (Exception $e) {
      $conn->rollback();
      Log::error('item register failed', array('msg' => $e->getMessage()));
  ```
- 확신도: 확실
- 비고: 성공하면 입력값을 비우고 안내 문구를 표시한다 (legacy/item-bank-php/register.php:116-118).

## 3. 단원 목록 (`units.php`) · 선택 목록

#### BR-32
- 규칙: 단원 목록의 문항 수는 상태가 공개(`'A'`)인 문항만 센다.
- 근거: legacy/item-bank-php/units.php:17
- 근거 코드:
  ```php
  (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
  ```
- 확신도: 확실
- 비고: **같은 규칙이 다르게 구현됨.** 검색은 뷰로 (legacy/item-bank-php/search.php:522), 단원 목록은 `item` 을 직접 세며 공개 조건을 따로 쓴다. 또 단원 목록에는 `level < 5` 조건이 없어서, 난이도 5 문항이 있는 단원은 이 숫자와 단원만 지정한 검색의 건수(BR-09)가 다르다.

#### BR-33
- 규칙: 단원 목록은 공개 문항이 없는 단원을 포함한 모든 단원을 학년 오름차순, 같은 학년에서는 단원 코드 오름차순으로 표시한다.
- 근거: legacy/item-bank-php/units.php:16-19
- 근거 코드:
  ```php
    FROM unit u
   ORDER BY u.grade ASC, u.code ASC";
  ```
- 확신도: 확실
- 비고: WHERE 절이 없고 문항 수는 서브쿼리라서 0건 단원도 나온다. 단원 코드는 문자열 정렬이다. 검색 · 등록의 단원 선택 목록도 같은 정렬을 따로 쓴다 (legacy/item-bank-php/search.php:156, legacy/item-bank-php/register.php:27).

#### BR-34
- 규칙: 검색 · 등록 화면의 태그 선택 목록은 태그 id 오름차순으로 표시한다.
- 근거: legacy/item-bank-php/search.php:178, legacy/item-bank-php/register.php:34
- 근거 코드:
  ```php
  $res = $conn->query("SELECT name FROM tag ORDER BY id ASC");
  ```
  ```php
  $res = $conn->query("SELECT id, name FROM tag ORDER BY id ASC");
  ```
- 확신도: 확실
- 비고: 검색의 단원 선택 목록은 학년별로 묶어 보여 준다 (legacy/item-bank-php/search.php:452-462). 등록 화면은 묶지 않는다 (legacy/item-bank-php/register.php:154-156).

## 4. 매직 넘버

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `100` | legacy/item-bank-php/search.php:86, legacy/item-bank-php/search.php:87, legacy/item-bank-php/search.php:494 | 검색 키워드 최대 글자 수 |
| `1` | legacy/item-bank-php/search.php:91 | 결과가 많다고 경고하는 키워드 길이 |
| `{1,2}` | legacy/item-bank-php/search.php:111 | 단원 코드의 학년 · 순번 자릿수 (1~2자리) |
| `5` | legacy/item-bank-php/search.php:215 | 난이도를 비웠을 때 제외하는 난이도(최상) 경계. 의도 확인 필요 |
| `[1-5]` | legacy/item-bank-php/search.php:217, legacy/item-bank-php/register.php:70, legacy/item-bank-php/register.php:163 | 난이도 허용 범위 1~5 |
| `1`~`5` 이름표 | legacy/item-bank-php/search.php:193-199 | 난이도 표시 이름 (매우 쉬움 · 쉬움 · 보통 · 어려움 · 최상) |
| `50` | legacy/item-bank-php/search.php:240, legacy/item-bank-php/search.php:241 | 태그 이름 최대 글자 수 (`tag.name VARCHAR(50)` 과 같은 값) |
| `20` | legacy/item-bank-php/search.php:349, legacy/item-bank-php/search.php:523, legacy/item-bank-php/search.php:553 | 페이지 크기 |
| `999` | legacy/item-bank-php/search.php:345, legacy/item-bank-php/search.php:346 | 페이지 번호 상한 |
| `5` | legacy/item-bank-php/register.php:49 | 제목 최소 글자 수 |
| `200` | legacy/item-bank-php/register.php:52 | 제목 최대 글자 수 (`item.title VARCHAR(200)` 과 같은 값) |
| `'R'` | legacy/item-bank-php/register.php:94 | 등록 직후 상태 = 검수중 |
| `'A'` | legacy/item-bank-php/units.php:17, db/mariadb/init/01-schema.sql:70 | 공개 상태 |
| `0` | legacy/item-bank-php/register.php:87 | 문항이 없을 때 id 채번의 시작 기준 (첫 id = 1) |

## 5. 규칙으로 올리지 않은 것 (주석 · 문구만 있음)

| 내용 | 위치 | 올리지 않은 이유 |
|---|---|---|
| "전체 난이도 검색 (1~5 모두 포함)" | legacy/item-bank-php/search.php:213 | 주석만 있음. 코드는 `level < 5` 다(BR-09) |
| "검수 완료 후 검색에 노출됩니다" | legacy/item-bank-php/register.php:84, legacy/item-bank-php/register.php:116 | 주석 · 안내 문구만 있음. 검수 완료 처리(`'R'` → `'A'`) 코드가 이 모듈에 없다 |
| 상태 `D` = 삭제 | db/mariadb/init/01-schema.sql:26, legacy/item-bank-php/units.php:15 | 주석만 있음. `'D'` 를 저장하거나 비교하는 코드가 이 모듈에 없다. 제외되는 것은 "`'A'` 가 아닌 모든 상태"다(BR-01, BR-32) |
| "등록 화면이 이 뷰를 기준으로 삼는다" | db/mariadb/init/01-schema.sql:51 | 주석만 있음. `register.php` 에 `v_item_public` 을 쓰는 SQL 이 없다 |
| 난이도는 1~5 | db/mariadb/init/01-schema.sql:25 | 스키마에서는 주석만 있음(CHECK 제약 없음). 규칙은 PHP 검증으로 올렸다(BR-26) |
| 문항 수정 · 삭제 시의 검증 | 없음 | 해당 코드가 없다 |
| 단원 · 태그의 등록 · 수정 | 없음 | 이 모듈은 `unit` · `tag` 에 쓰지 않는다 (`ERD.md` 3.1절) |
| 로그인 · 권한에 따른 제한 | 없음 | 세션 · 권한을 확인하는 코드가 없다 |

## 6. 미확인 목록

| 항목 | 미확인 사유 |
|---|---|
| 난이도를 비웠을 때 난이도 5 를 제외하는 것이 의도된 규칙인지 (BR-09) | 주석과 코드가 다르다. 코드만으로는 의도를 알 수 없다 |
| 단원 코드 · 태그 이름 비교가 실제로 대소문자를 구분하지 않는지 (BR-07, BR-12) | 콜레이션 선언만 확인했고 DB 를 띄워 실행하지 않았다 |
| 같은 태그 id 를 중복 제출하면 등록이 롤백되는지 (BR-27) | 코드와 PK 선언으로 짐작했고 실행하지 않았다 |
| `characterization/` 스냅샷이 위 규칙 중 무엇을 고정하고 있는지 | 내용을 읽지 않았다 |
| `'R'` → `'A'`, `'A'` → `'D'` 로 상태를 바꾸는 주체 | 이 모듈에 해당 SQL 이 없다. 다른 모듈은 읽지 않았다 |
| `unit_code` 정렬에서 `M5-10` 이 `M5-2` 보다 실제로 앞에 오는지 (BR-16) | 컬럼 타입과 ORDER BY 로 짐작했고 실행하지 않았다 |

## 7. 검증 이력

2026-09-29 에 [CROSS-CHECK.md](CROSS-CHECK.md) 와 교차 검증했다. 불일치 항목은 코드로 다시 확인했고, 실행으로 확인하지는 않았다.

| 항목 | 바뀐 내용 | 근거 |
|---|---|---|
| BR-35 (신규) | 추가. 경고는 검색을 막지 않고 결과 위에 표시한다. CX-09 에만 있던 규칙 | legacy/item-bank-php/search.php:510-516, legacy/item-bank-php/search.php:652 |
| BR-17 | 수정. 정렬 방향도 소문자로 바꾼 뒤 비교한다는 내용을 규칙 · 근거 코드에 추가 (CX-13 쪽이 맞음) | legacy/item-bank-php/search.php:267 |
| BR-15 | 수정. 비고에 정렬 방향도 같은 방식이라는 참조 추가 | legacy/item-bank-php/search.php:266-267 |
| BR-19 | 수정. 비고에 `page` 가 빈 문자열일 때의 처리 추가 (두 문서 모두 빠져 있던 내용) | legacy/item-bank-php/search.php:339 |
| BR-11 | 수정. 비고의 참조 번호 BR-24 → BR-26 | legacy/item-bank-php/register.php:70-72 |
| 6절 미확인 목록 | 추가. `unit_code` 문자열 정렬의 실제 순서 (BR-16) | legacy/item-bank-php/search.php:293-299, db/mariadb/init/01-schema.sql:13 |
| BR-01, BR-17, BR-19, BR-29 | 변경 없음. CX-10 · CX-13 · CX-14 · CX-15 와 달랐지만 이 문서 쪽이 맞음 | db/mariadb/init/01-schema.sql:70, legacy/item-bank-php/search.php:268-273, legacy/item-bank-php/search.php:337-344, legacy/item-bank-php/register.php:94 |
| `inc/db.php` | 변경 없음. 읽어 보니 DB 연결과 HTML 이스케이프뿐이고 규칙으로 올릴 조건문이 없다 | legacy/item-bank-php/inc/db.php:7-37 |

## 이관 회고

대상: 문항 검색 `search.php` → `modern/api` 의 `GET /api/items/search` (2026-09-29).
동작 보존 테스트 22케이스는 모두 통과했다. 아래는 통과한 뒤 규칙별 대조와 두 서버에 같은 요청을 보내 찾은 차이다.
새 구현 경로는 `modern/api/src/main/java/com/example/item/` 기준이다.

| 바뀐 지점 | 레거시 동작 | AI가 만든 동작 | 테스트가 잡았나 | 다음에 막을 방법 |
|---|---|---|---|---|
| 배열과 일반 파라미터를 함께 보냄 (`level[]=3&level=2`, BR-03) | 뒤에 온 값(2)으로 검색 (legacy/item-bank-php/search.php:61-64) | 순서와 상관없이 `level[]` 값(3)으로 검색 (ItemSearchCondition.java:88-91) | 못 잡음 | 배열 케이스를 세울 때 일반 파라미터와 섞은 순서 두 가지를 함께 세운다 |
| 키가 있는 배열 파라미터 (`level[0]=2`, `level[a]=5`, BR-03) | 첫 값으로 검색 (legacy/item-bank-php/search.php:63) | 파라미터를 못 읽어 난이도를 비운 것으로 처리, 20건 (ItemSearchCondition.java:88) | 못 잡음 | 규칙 비고에 PHP 가 배열로 받는 표기(`[]`, `[0]`, `[키]`)를 모두 적고 표기마다 케이스를 세운다 |
| 키워드에 `\` 포함 (`q=\%&level=5`, BR-04 · BR-06) | `\` 가 이스케이프로 동작해 글자 `%` 를 찾음, 1건 (legacy/item-bank-php/search.php:97-98) | 0건 (ItemSearchSpecifications.java:46-47) | 못 잡음 | 와일드카드 케이스에 `\` 를 넣은 케이스를 추가하고, 비고에 DB 의 LIKE 기본 이스케이프 문자를 적는다 |
| GET 이 아닌 요청 (POST) | 200 으로 검색 실행 (legacy/item-bank-php/search.php:722) | 500 (ItemSearchController.java:23) | 못 잡음 | 계획 단계에서 허용할 HTTP 메서드와 그 밖의 메서드의 상태 코드를 정하고 컨트롤러 테스트로 고정한다 |
| DB · 조회 오류 | 200 에 오류 문구 (legacy/item-bank-php/search.php:714-719, 726-729) | 500 에 JSON 오류 본문 | 못 잡음 (스냅샷은 정상 응답만 찍음) | 오류 시 응답을 규칙으로 올리고, 레거시와 다르게 갈지 계획 단계에서 결정해 적는다 |
| 입력값 경고 (BR-05 · 06 · 08 · 11 · 13 · 15 · 17 · 19 · 35) | 경고 목록을 결과 위에 표시 (legacy/item-bank-php/search.php:510-516, 652) | 응답에 경고 없음 (계획에서 넣지 않기로 결정) | 못 잡음 (정규화 결과에 경고가 없음) | 이관 범위에 넣을 규칙이면 정규화 함수가 경고를 읽게 한 뒤 베이스라인을 찍는다 |
| 선택 목록 · 조건 요약 · 페이지 링크 (BR-34 외) | 화면에 함께 출력 (legacy/item-bank-php/search.php:354-366, 392-504, 684-703) | 응답에 없음 | 못 잡음 (정규화가 표 · 건수 · 문구만 읽음) | 계획에 "옮기지 않는 것" 목록을 따로 두고 화면 쪽 대체 방법을 함께 적는다 |
| 태그 표시 (BR-21) | 쉼표로 이은 문자열. 이름에 쉼표가 있으면 쪼개지고 길면 잘릴 수 있음 (db/mariadb/init/01-schema.sql:64-67) | 태그 이름 배열 (ItemSearchRow.java:21) | 못 잡음 (시드에 해당 데이터 없음) — 확인 필요 | 확인 필요 항목으로 남기고, 시드에 없는 데이터 모양은 규칙 비고에 적는다 |
| 소문자 단원 코드 (`m5-1`, BR-07) | DB 콜레이션으로 일치, 5건 | 같음 | 잡음 (H2 단위 테스트로는 확인 불가, MariaDB 대상 실행에서만 확인) | DB 설정에 기대는 동작은 비고에 "코드가 아니라 DB 동작"이라고 적고 실제 DB 대상 케이스를 세운다 |
| 난이도 미지정 검색 (BR-09) | 난이도 5 를 빼고 검색 (legacy/item-bank-php/search.php:213-216) | 같음 (ItemSearchSpecifications.java:29-31) | 잡음 (차이 없음) | 규칙 비고에 "주석과 다름"을 적어 두고 경계 케이스를 세운다 |
