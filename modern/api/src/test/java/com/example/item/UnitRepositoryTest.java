package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/** 저장소 슬라이스 — H2(MariaDB 모드)에 실제 SQL 을 날린다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UnitRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UnitRepository unitRepository;

    @BeforeEach
    void seed() {
        Unit division = entityManager.persist(ItemFixtures.unit(4, "M6-1", "분수의 나눗셈", 6));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Unit fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));

        entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE));
        entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE));
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, fraction, "삭제된 문항", 5, ItemStatus.DELETED));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 1, ItemStatus.ACTIVE));
        entityManager.persist(ItemFixtures.item(null, division, "삭제된 나눗셈 문항", 2, ItemStatus.DELETED));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("countItemsByUnit: 공개 문항만 세고, 문항 없는 단원은 0, 학년 · 코드 순")
    void countsActiveItemsPerUnitIncludingEmptyUnits() {
        List<UnitItemCountResponse> counts = unitRepository.countItemsByUnit(ItemStatus.ACTIVE);

        assertThat(counts).containsExactly(
            new UnitItemCountResponse("M5-1", "분수의 덧셈과 뺄셈", 5, 2),
            new UnitItemCountResponse("M5-3", "소수의 곱셈", 5, 1),
            new UnitItemCountResponse("M6-1", "분수의 나눗셈", 6, 0));
    }
}
