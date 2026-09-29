package com.example.item;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;

/**
 * 보정이 끝난 문항 검색 조건. 레거시 검색 화면(search.php)의 입력 처리를 그대로 옮겼다.
 * 입력값이 이상해도 거부하지 않고 보정해서 조회한다.
 *
 * @param keyword  키워드. 없으면 빈 문자열
 * @param unitCode 단원 코드. 없으면 빈 문자열
 * @param level    난이도. 입력을 비웠으면 {@code null}
 * @param tag      태그 이름. 없으면 빈 문자열
 * @param sort     정렬
 * @param page     페이지 번호(1부터)
 */
public record ItemSearchCondition(
    String keyword,
    String unitCode,
    Integer level,
    String tag,
    Sort sort,
    int page) {

    static final int KEYWORD_MAX_LENGTH = 100;
    static final int TAG_MAX_LENGTH = 50;
    static final int PAGE_MIN = 1;
    static final int PAGE_MAX = 999;

    private static final String PARAM_KEYWORD = "q";
    private static final String PARAM_UNIT = "unit";
    private static final String PARAM_LEVEL = "level";
    private static final String PARAM_TAG = "tag";
    private static final String PARAM_SORT = "sort";
    private static final String PARAM_DIR = "dir";
    private static final String PARAM_PAGE = "page";
    private static final String ARRAY_SUFFIX = "[]";

    private static final String SORT_ID = "id";
    private static final String SORT_TITLE = "title";
    private static final String SORT_UNIT = "unit";
    private static final String SORT_LEVEL = "level";
    private static final String SORT_CREATED = "created";
    private static final String DIR_ASC = "asc";
    private static final String DIR_DESC = "desc";

    private static final String FIELD_ID = "id";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_UNIT_CODE = "unit.code";
    private static final String FIELD_LEVEL = "level";
    private static final String FIELD_CREATED_AT = "createdAt";

    /** PHP trim() 이 지우는 문자: 공백, 탭, 줄바꿈, CR, NUL, 수직 탭. */
    private static final String PHP_TRIM_CHARS = " \t\n\r\0\u000B";
    private static final Pattern LEVEL_IN_RANGE = Pattern.compile("[1-5]");
    /** PHP 의 {@code $} 처럼 끝의 줄바꿈 하나는 허용한다. */
    private static final Pattern PAGE_DIGITS = Pattern.compile("^([0-9]+)\\n?$", Pattern.UNIX_LINES);
    /** PHP (int) 변환이 읽는 앞부분: 공백, 부호, 숫자, 소수부, 지수부. */
    private static final Pattern PHP_NUMERIC_PREFIX =
        Pattern.compile("^[ \\t\\n\\r\\u000B\\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");

    /** 요청 파라미터에서 검색 조건을 만든다. */
    static ItemSearchCondition from(Map<String, List<String>> params) {
        String keyword = truncate(phpTrim(param(params, PARAM_KEYWORD, "")), KEYWORD_MAX_LENGTH);
        String unitCode = phpTrim(param(params, PARAM_UNIT, ""));
        String tag = truncate(phpTrim(param(params, PARAM_TAG, "")), TAG_MAX_LENGTH);
        return new ItemSearchCondition(
            keyword,
            unitCode,
            parseLevel(phpTrim(param(params, PARAM_LEVEL, ""))),
            tag,
            parseSort(param(params, PARAM_SORT, ""), param(params, PARAM_DIR, "")),
            parsePage(param(params, PARAM_PAGE, String.valueOf(PAGE_MIN))));
    }

    /** 조회 시작 위치를 뺀 0 기반 페이지 번호. */
    int pageIndex() {
        return page - PAGE_MIN;
    }

    /**
     * 파라미터 값 하나를 꺼낸다. 배열({@code name[]})로 오면 첫 값, 같은 이름이 반복되면 마지막 값을 쓴다(PHP 와 같다).
     */
    private static String param(Map<String, List<String>> params, String name, String defaultValue) {
        List<String> array = params.get(name + ARRAY_SUFFIX);
        if (array != null && !array.isEmpty()) {
            return nullToEmpty(array.get(0));
        }
        List<String> values = params.get(name);
        if (values != null && !values.isEmpty()) {
            return nullToEmpty(values.get(values.size() - 1));
        }
        return defaultValue;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String phpTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && PHP_TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && PHP_TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    /** 글자 수(코드 포인트)가 최대 길이를 넘으면 앞부분만 쓴다. */
    private static String truncate(String value, int maxLength) {
        if (value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    /** 빈 값은 {@code null}, 1~5 는 그 값, 그 밖은 PHP (int) 변환 결과(숫자가 아니면 0). */
    private static Integer parseLevel(String level) {
        if (level.isEmpty()) {
            return null;
        }
        if (LEVEL_IN_RANGE.matcher(level).matches()) {
            return Integer.valueOf(level);
        }
        return phpIntValue(level);
    }

    private static int phpIntValue(String value) {
        Matcher matcher = PHP_NUMERIC_PREFIX.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        double number = Double.parseDouble(matcher.group(1));
        // double → int 변환은 범위를 넘으면 int 의 최솟값 · 최댓값으로 맞춰진다. 난이도와는 어차피 일치하지 않는다.
        return (int) number;
    }

    private static Sort parseSort(String sortRaw, String dirRaw) {
        String sort = asciiLowerCase(phpTrim(sortRaw));
        String dir = asciiLowerCase(phpTrim(dirRaw));
        boolean asc = DIR_ASC.equals(dir);
        boolean desc = DIR_DESC.equals(dir);
        Sort.Order idAsc = Sort.Order.asc(FIELD_ID);
        return switch (sort) {
            case SORT_ID -> Sort.by(desc ? Sort.Order.desc(FIELD_ID) : idAsc);
            case SORT_TITLE -> Sort.by(desc ? Sort.Order.desc(FIELD_TITLE) : Sort.Order.asc(FIELD_TITLE), idAsc);
            case SORT_UNIT -> Sort.by(
                desc ? Sort.Order.desc(FIELD_UNIT_CODE) : Sort.Order.asc(FIELD_UNIT_CODE),
                Sort.Order.desc(FIELD_LEVEL),
                idAsc);
            case SORT_LEVEL -> Sort.by(asc ? Sort.Order.asc(FIELD_LEVEL) : Sort.Order.desc(FIELD_LEVEL), idAsc);
            case SORT_CREATED -> Sort.by(
                asc ? Sort.Order.asc(FIELD_CREATED_AT) : Sort.Order.desc(FIELD_CREATED_AT), idAsc);
            // 비웠거나 모르는 정렬 기준: 어려운 문항부터, 같은 난이도면 번호 순
            default -> Sort.by(Sort.Order.desc(FIELD_LEVEL), idAsc);
        };
    }

    /** PHP strtolower() 처럼 ASCII 대문자만 소문자로 바꾼다. */
    private static String asciiLowerCase(String value) {
        StringBuilder lowered = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            lowered.append(c >= 'A' && c <= 'Z' ? Character.toLowerCase(c) : c);
        }
        return lowered.toString();
    }

    /** 숫자가 아니면 1, 1 보다 작으면 1, 999 보다 크면 999. 앞뒤 공백은 지우지 않는다. */
    private static int parsePage(String pageRaw) {
        Matcher matcher = PAGE_DIGITS.matcher(pageRaw);
        if (!matcher.matches()) {
            return PAGE_MIN;
        }
        String digits = matcher.group(1).replaceFirst("^0+", "");
        if (digits.isEmpty()) {
            return PAGE_MIN;
        }
        if (digits.length() > String.valueOf(PAGE_MAX).length()) {
            return PAGE_MAX;
        }
        return Math.min(Integer.parseInt(digits), PAGE_MAX);
    }
}
