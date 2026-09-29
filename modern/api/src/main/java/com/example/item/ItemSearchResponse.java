package com.example.item;

import java.util.List;

/**
 * {@code GET /api/items/search} 응답. {@code count} 는 현재 페이지의 행 수가 아니라 조건에 맞는 전체 건수,
 * {@code message} 는 전체 건수가 0 일 때만 채운다.
 */
public record ItemSearchResponse(List<ItemSearchRow> rows, long count, String message) {
}
