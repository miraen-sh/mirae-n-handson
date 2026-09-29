package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/** 저장소 슬라이스 — 검색 조건 · 정렬 · 페이지를 H2(MariaDB 모드)에 실제 SQL 로 날린다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemSearchRepositoryTest {

    private static final int PAGE_SIZE = 20;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void seed() {
        Unit fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, word, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 분수 문항", 3, ItemStatus.REVIEWING, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "삭제된 분수 문항", 1, ItemStatus.DELETED));
        entityManager.persist(ItemFixtures.item(null, fraction, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 1, ItemStatus.ACTIVE));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈 문장제", 4, ItemStatus.ACTIVE, word));
        entityManager.flush();
        entityManager.clear();
    }

    private Page<Item> search(Map<String, List<String>> params) {
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        return itemRepository.findAll(
            ItemSearchSpecifications.matching(condition),
            PageRequest.of(condition.pageIndex(), PAGE_SIZE, condition.sort()));
    }

    @Test
    @DisplayName("조건 없음: 공개이고 난이도 5 미만인 문항만, level DESC · id ASC")
    void defaultSearchExcludesLevelFiveAndNonActive() {
        Page<Item> page = search(Map.of());

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(Item::getTitle)
            .containsExactly("분수 문장제", "소수 곱셈 문장제", "분모가 같은 분수의 덧셈", "소수 곱셈");
    }

    @Test
    @DisplayName("난이도 5 를 직접 지정하면 난이도 5 문항이 나온다")
    void levelFiveIsFoundWhenRequested() {
        assertThat(search(Map.of("level", List.of("5"))).getContent()).extracting(Item::getTitle)
            .containsExactly("난이도 5 분수");
    }

    @Test
    @DisplayName("범위 밖 난이도 · 없는 단원 · 없는 태그는 0건")
    void outOfRangeInputsReturnNothing() {
        assertThat(search(Map.of("level", List.of("0"))).getTotalElements()).isZero();
        assertThat(search(Map.of("level", List.of("-1"))).getTotalElements()).isZero();
        assertThat(search(Map.of("unit", List.of("M9-99"))).getTotalElements()).isZero();
        assertThat(search(Map.of("tag", List.of("없는태그"))).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("키워드 · 단원 · 난이도 · 태그는 AND 로 결합한다")
    void conditionsAreCombinedWithAnd() {
        Page<Item> page = search(Map.of(
            "q", List.of("문장제"),
            "unit", List.of("M5-1"),
            "level", List.of("4"),
            "tag", List.of("계산")));

        assertThat(page.getContent()).extracting(Item::getTitle).containsExactly("분수 문장제");
    }

    @Test
    @DisplayName("키워드는 제목 또는 지문의 부분 일치이고 % 는 와일드카드로 동작한다")
    void keywordMatchesTitleOrStemAndKeepsWildcard() {
        assertThat(search(Map.of("q", List.of("문제 본문"))).getTotalElements()).isEqualTo(4);
        assertThat(search(Map.of("q", List.of("%"))).getTotalElements()).isEqualTo(4);
        assertThat(search(Map.of("q", List.of("소수%문장제"))).getContent()).extracting(Item::getTitle)
            .containsExactly("소수 곱셈 문장제");
    }

    @Test
    @DisplayName("태그 조건은 그 태그가 달린 문항만 찾고 문항이 중복되지 않는다")
    void tagFilterDoesNotDuplicateItems() {
        Page<Item> page = search(Map.of("tag", List.of("문장제")));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Item::getTitle)
            .containsExactly("분수 문장제", "소수 곱셈 문장제");
    }

    @Test
    @DisplayName("단원 정렬: 단원 코드 방향만 바뀌고 난이도 내림차순 · id 오름차순은 고정")
    void unitSortKeepsSecondaryKeys() {
        assertThat(search(Map.of("sort", List.of("unit"), "dir", List.of("desc"))).getContent())
            .extracting(Item::getTitle)
            .containsExactly("소수 곱셈 문장제", "소수 곱셈", "분수 문장제", "분모가 같은 분수의 덧셈");
    }

    @Test
    @DisplayName("총 페이지를 넘는 페이지: 행은 없고 전체 건수는 그대로")
    void pageBeyondLastKeepsTotal() {
        Page<Item> page = search(Map.of("page", List.of("999")));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("findWithDetailsByIdIn: 단원 · 태그가 함께 로드되고 태그는 id 순이다")
    void findWithDetailsByIdInLoadsUnitAndTagsInOrder() {
        List<Integer> ids = search(Map.of("q", List.of("분수 문장제"))).getContent().stream()
            .map(Item::getId)
            .toList();
        entityManager.clear();

        List<Item> items = itemRepository.findWithDetailsByIdIn(ids);

        assertThat(items).hasSize(1);
        assertThat(Hibernate.isInitialized(items.get(0).getUnit())).isTrue();
        assertThat(Hibernate.isInitialized(items.get(0).getTags())).isTrue();
        assertThat(items.get(0).getTags()).extracting(Tag::getName).containsExactly("계산", "문장제");
    }
}
