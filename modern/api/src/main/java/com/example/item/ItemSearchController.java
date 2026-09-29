package com.example.item;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 문항 검색 API. 파라미터는 모두 문자열로 받아 서비스가 보정한다(숫자가 아닌 값도 400 이 아니다).
 */
@RestController
@RequestMapping("/api/items")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /** {@code GET /api/items/search} — 파라미터 q, unit, level, tag, sort, dir, page. */
    @GetMapping("/search")
    public ItemSearchResponse search(@RequestParam MultiValueMap<String, String> params) {
        return itemSearchService.search(params);
    }
}
