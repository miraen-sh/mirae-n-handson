package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과의 한 행. {@code unit} 은 단원 코드({@code M5-1}), {@code tags} 는 태그 이름 목록(id 순).
 */
public record ItemSearchRow(
    Integer id,
    String title,
    String unit,
    Integer level,
    List<String> tags) {

    static ItemSearchRow from(Item item) {
        return new ItemSearchRow(
            item.getId(),
            item.getTitle(),
            item.getUnit().getCode(),
            item.getLevel(),
            item.getTags().stream().map(Tag::getName).toList());
    }
}
