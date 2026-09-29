package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    private final Unit unit = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);

    @Test
    @DisplayName("search: 검색 결과의 순서대로 행을 만들고 건수는 전체 건수다")
    void searchKeepsOrderAndReturnsTotal() {
        Item hard = ItemFixtures.item(24, unit, "분수 덧셈과 뺄셈 혼합 계산", 5, ItemStatus.ACTIVE,
            ItemFixtures.tag(1, "계산"), ItemFixtures.tag(4, "오답률높음"));
        Item easy = ItemFixtures.item(1, unit, "분모가 같은 분수의 덧셈", 1, ItemStatus.ACTIVE);
        Pageable pageable = PageRequest.of(0, ItemSearchService.PAGE_SIZE);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(hard, easy), pageable, 25));
        when(itemRepository.findWithDetailsByIdIn(List.of(24, 1))).thenReturn(List.of(easy, hard));

        ItemSearchResponse response = itemSearchService.search(Map.of());

        assertThat(response.count()).isEqualTo(25);
        assertThat(response.message()).isNull();
        assertThat(response.rows()).extracting(ItemSearchRow::id).containsExactly(24, 1);
        assertThat(response.rows().get(0).unit()).isEqualTo("M5-1");
        assertThat(response.rows().get(0).tags()).containsExactly("계산", "오답률높음");
        assertThat(response.rows().get(1).tags()).isEmpty();
    }

    @Test
    @DisplayName("search: 전체 건수가 0 이면 안내 문구를 채우고 상세 조회는 하지 않는다")
    void searchReturnsMessageWhenTotalIsZero() {
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, ItemSearchService.PAGE_SIZE), 0));

        ItemSearchResponse response = itemSearchService.search(Map.of("unit", List.of("M9-99")));

        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isZero();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
        verify(itemRepository, never()).findWithDetailsByIdIn(anyList());
    }

    @Test
    @DisplayName("search: 총 페이지를 넘으면 행은 비고 건수는 그대로이며 안내 문구는 없다")
    void searchBeyondLastPageKeepsTotalWithoutMessage() {
        Pageable pageable = PageRequest.of(998, ItemSearchService.PAGE_SIZE);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), pageable, 20));

        ItemSearchResponse response = itemSearchService.search(Map.of("page", List.of("1000")));

        assertThat(response.rows()).isEmpty();
        assertThat(response.count()).isEqualTo(20);
        assertThat(response.message()).isNull();
    }

    @Test
    @DisplayName("search: 보정한 페이지 · 정렬과 페이지 크기 20 으로 저장소에 묻는다")
    void searchQueriesWithPageAndSort() {
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, ItemSearchService.PAGE_SIZE), 0));

        itemSearchService.search(Map.of("page", List.of("2"), "sort", List.of("id"), "dir", List.of("desc")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(itemRepository).findAll(ArgumentMatchers.<Specification<Item>>any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
    }
}
