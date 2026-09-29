package com.example.item;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스. 레거시 검색 화면(search.php)의 동작을 그대로 옮겼다.
 * 없는 단원 · 범위 밖 난이도도 오류가 아니라 0건으로 응답한다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    static final int PAGE_SIZE = 20;
    static final String EMPTY_MESSAGE = "검색 결과가 없습니다";

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;

    public ItemSearchService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /** 공개({@code status='A'}) 문항 검색. 한 페이지 20건, 건수는 조건에 맞는 전체 건수. */
    public ItemSearchResponse search(Map<String, List<String>> params) {
        ItemSearchCondition condition = ItemSearchCondition.from(params);
        Page<Item> page = itemRepository.findAll(
            ItemSearchSpecifications.matching(condition),
            PageRequest.of(condition.pageIndex(), PAGE_SIZE, condition.sort()));

        List<ItemSearchRow> rows = toRows(page.getContent());
        long total = page.getTotalElements();
        log.debug("searched items: total={}, page={}, rows={}", total, condition.page(), rows.size());
        return new ItemSearchResponse(rows, total, total == 0 ? EMPTY_MESSAGE : null);
    }

    /** 단원 · 태그를 한 번에 읽어 온 뒤 검색 결과의 순서대로 행을 만든다. */
    private List<ItemSearchRow> toRows(List<Item> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = items.stream().map(Item::getId).toList();
        Map<Integer, Item> detailsById = itemRepository.findWithDetailsByIdIn(ids).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity(), (first, second) -> first));
        return ids.stream()
            .map(detailsById::get)
            .map(ItemSearchRow::from)
            .toList();
    }
}
