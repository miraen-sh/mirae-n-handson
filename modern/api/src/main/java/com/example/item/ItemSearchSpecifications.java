package com.example.item;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * 문항 검색의 조회 조건. 조건은 모두 AND 로 결합한다.
 */
final class ItemSearchSpecifications {

    /** 난이도를 비웠을 때의 상한. 이 값 미만만 조회한다(레거시 동작 그대로). */
    static final int DEFAULT_LEVEL_UPPER_BOUND = 5;

    private static final String LIKE_WILDCARD = "%";
    /** MariaDB 기본 LIKE 이스케이프 문자. 레거시(mysqli)는 이 기본값으로 조회했다. */
    private static final char LIKE_ESCAPE = '\\';

    private ItemSearchSpecifications() {
    }

    static Specification<Item> matching(ItemSearchCondition condition) {
        Specification<Item> spec = hasStatus(ItemStatus.ACTIVE);
        if (!condition.keyword().isEmpty()) {
            spec = spec.and(titleOrStemContains(condition.keyword()));
        }
        if (!condition.unitCode().isEmpty()) {
            spec = spec.and(hasUnitCode(condition.unitCode()));
        }
        spec = spec.and(condition.level() == null
            ? levelLessThan(DEFAULT_LEVEL_UPPER_BOUND)
            : hasLevel(condition.level()));
        if (!condition.tag().isEmpty()) {
            spec = spec.and(hasTag(condition.tag()));
        }
        return spec;
    }

    private static Specification<Item> hasStatus(String status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * 키워드의 {@code %} · {@code _} 는 이스케이프하지 않는다 — 와일드카드로 동작한다.
     * {@code \} 는 레거시처럼 이스케이프 문자로 동작한다. ESCAPE 를 지정하지 않으면 Hibernate 가 {@code \} 를
     * 일반 문자로 바꿔 렌더링하므로 명시한다.
     */
    private static Specification<Item> titleOrStemContains(String keyword) {
        String pattern = LIKE_WILDCARD + keyword + LIKE_WILDCARD;
        return (root, query, cb) -> cb.or(
            cb.like(root.get("title"), pattern, LIKE_ESCAPE),
            cb.like(root.get("stem"), pattern, LIKE_ESCAPE));
    }

    private static Specification<Item> hasUnitCode(String unitCode) {
        return (root, query, cb) -> cb.equal(root.get("unit").get("code"), unitCode);
    }

    private static Specification<Item> levelLessThan(int upperBound) {
        return (root, query, cb) -> cb.lessThan(root.get("level"), upperBound);
    }

    private static Specification<Item> hasLevel(int level) {
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    private static Specification<Item> hasTag(String tagName) {
        return (root, query, cb) -> {
            Subquery<Integer> tagged = query.subquery(Integer.class);
            Root<Item> taggedItem = tagged.from(Item.class);
            Join<Item, Tag> tag = taggedItem.join("tags");
            tagged.select(taggedItem.get("id"))
                .where(cb.equal(taggedItem.get("id"), root.get("id")), cb.equal(tag.get("name"), tagName));
            return cb.exists(tagged);
        };
    }
}
