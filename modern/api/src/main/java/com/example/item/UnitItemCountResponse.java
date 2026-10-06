package com.example.item;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code GET /api/units/item-counts} 응답 항목. {@code itemCount} 는 공개({@code status='A'}) 문항 수.
 * 필드 구성과 JSON 이름({@code item_count})은 레거시 {@code units.php} 표의 열과 맞춘다(동작 보존 비교 대상).
 */
public record UnitItemCountResponse(
    String code,
    String name,
    Integer grade,
    @JsonProperty("item_count") long itemCount) {
}
