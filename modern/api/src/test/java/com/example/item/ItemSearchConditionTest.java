package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
class ItemSearchConditionTest {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));

    private static ItemSearchCondition of(String name, String... values) {
        return ItemSearchCondition.from(Map.of(name, List.of(values)));
    }

    @Test
    @DisplayName("파라미터가 없으면 조건 없음 · 난이도 비움 · 기본 정렬 · 1페이지")
    void defaultsWhenNoParams() {
        ItemSearchCondition condition = ItemSearchCondition.from(Map.of());

        assertThat(condition.keyword()).isEmpty();
        assertThat(condition.unitCode()).isEmpty();
        assertThat(condition.level()).isNull();
        assertThat(condition.tag()).isEmpty();
        assertThat(condition.sort()).isEqualTo(DEFAULT_SORT);
        assertThat(condition.page()).isEqualTo(1);
        assertThat(condition.pageIndex()).isZero();
    }

    @Test
    @DisplayName("키워드: 앞뒤 공백을 지우고 100자를 넘으면 앞 100자만 쓴다")
    void keywordIsTrimmedAndTruncated() {
        assertThat(of("q", "  곱셈 ").keyword()).isEqualTo("곱셈");
        assertThat(of("q", "가".repeat(100)).keyword()).hasSize(100);
        assertThat(of("q", "가".repeat(101)).keyword()).isEqualTo("가".repeat(100));
    }

    @Test
    @DisplayName("태그: 50자를 넘으면 앞 50자만 쓴다")
    void tagIsTruncated() {
        assertThat(of("tag", "가".repeat(50)).tag()).hasSize(50);
        assertThat(of("tag", "가".repeat(51)).tag()).isEqualTo("가".repeat(50));
    }

    @Test
    @DisplayName("단원: 앞뒤 공백만 지우고 소문자 · 없는 코드도 그대로 둔다")
    void unitCodeIsPassedThrough() {
        assertThat(of("unit", " m5-1 ").unitCode()).isEqualTo("m5-1");
        assertThat(of("unit", "M9-99").unitCode()).isEqualTo("M9-99");
    }

    @Test
    @DisplayName("난이도: 빈 값은 null, 1~5 는 그 값, 그 밖은 정수로 바꾼 값(숫자가 아니면 0)")
    void levelFollowsLegacyIntCast() {
        assertThat(of("level", "").level()).isNull();
        assertThat(of("level", " 3 ").level()).isEqualTo(3);
        assertThat(of("level", "5").level()).isEqualTo(5);
        assertThat(of("level", "0").level()).isZero();
        assertThat(of("level", "-1").level()).isEqualTo(-1);
        assertThat(of("level", "abc").level()).isZero();
        assertThat(of("level", "2abc").level()).isEqualTo(2);
        assertThat(of("level", "2.7").level()).isEqualTo(2);
    }

    @Test
    @DisplayName("배열 파라미터는 첫 값, 같은 이름 반복은 마지막 값을 쓴다")
    void arrayParamUsesFirstAndRepeatedParamUsesLast() {
        assertThat(of("level[]", "2", "3").level()).isEqualTo(2);
        assertThat(of("tag[]", "개념", "계산").tag()).isEqualTo("개념");
        assertThat(of("level", "2", "3").level()).isEqualTo(3);
    }

    @Test
    @DisplayName("정렬: 기준별 기본 방향과 고정된 2차 정렬 키")
    void sortFollowsLegacyTable() {
        assertThat(sort("id", "")).isEqualTo(Sort.by(Sort.Order.asc("id")));
        assertThat(sort("id", "desc")).isEqualTo(Sort.by(Sort.Order.desc("id")));
        assertThat(sort("title", "desc")).isEqualTo(Sort.by(Sort.Order.desc("title"), Sort.Order.asc("id")));
        assertThat(sort("unit", "asc")).isEqualTo(
            Sort.by(Sort.Order.asc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(sort("unit", "desc")).isEqualTo(
            Sort.by(Sort.Order.desc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id")));
        assertThat(sort("level", "")).isEqualTo(DEFAULT_SORT);
        assertThat(sort("level", "asc")).isEqualTo(Sort.by(Sort.Order.asc("level"), Sort.Order.asc("id")));
        assertThat(sort("created", "")).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        assertThat(sort("created", "asc")).isEqualTo(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("정렬: 공백 · 대문자는 보정하고, 모르는 기준 · 방향은 기본 정렬로 돌린다")
    void sortNormalizesInput() {
        assertThat(sort(" TITLE ", " DESC")).isEqualTo(Sort.by(Sort.Order.desc("title"), Sort.Order.asc("id")));
        assertThat(sort("DROP", "UP")).isEqualTo(DEFAULT_SORT);
        assertThat(sort("", "asc")).isEqualTo(DEFAULT_SORT);
        assertThat(sort("title", "UP")).isEqualTo(Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("페이지: 숫자가 아니거나 1 보다 작으면 1, 999 보다 크면 999")
    void pageIsClamped() {
        assertThat(of("page", "").page()).isEqualTo(1);
        assertThat(of("page", "0").page()).isEqualTo(1);
        assertThat(of("page", "-5").page()).isEqualTo(1);
        assertThat(of("page", "abc").page()).isEqualTo(1);
        assertThat(of("page", " 2").page()).isEqualTo(1);
        assertThat(of("page", "2").page()).isEqualTo(2);
        assertThat(of("page", "999").page()).isEqualTo(999);
        assertThat(of("page", "1000").page()).isEqualTo(999);
        assertThat(of("page", "99999999999999999999").page()).isEqualTo(999);
        assertThat(of("page", "999").pageIndex()).isEqualTo(998);
    }

    private static Sort sort(String sort, String dir) {
        return ItemSearchCondition.from(Map.of("sort", List.of(sort), "dir", List.of(dir))).sort();
    }
}
