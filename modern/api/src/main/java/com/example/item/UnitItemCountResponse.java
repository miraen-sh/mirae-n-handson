package com.example.item;

/** {@code GET /api/units/item-counts} 응답 항목. {@code itemCount} 는 공개({@code status='A'}) 문항 수. */
public record UnitItemCountResponse(Integer id, String code, String name, Integer grade, long itemCount) {
}
