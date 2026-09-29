package com.example.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스는 목. 문항 단건 경로(/api/items/{id})와 함께 올려 경로 충돌도 확인한다. */
@WebMvcTest({ItemSearchController.class, ItemController.class})
@ActiveProfiles("test")
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @MockBean
    private ItemService itemService;

    @Test
    @DisplayName("GET /api/items/search → 200, rows · count · message 와 행의 다섯 필드만")
    void searchReturnsRowsCountMessage() throws Exception {
        when(itemSearchService.search(any())).thenReturn(new ItemSearchResponse(
            List.of(new ItemSearchRow(12, "대분수의 덧셈", "M5-1", 3, List.of("계산", "오답률높음"))), 1, null));

        mockMvc.perform(get("/api/items/search").param("q", "분수"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.message").value((Object) null))
            .andExpect(jsonPath("$.rows.length()").value(1))
            .andExpect(jsonPath("$.rows[0].length()").value(5))
            .andExpect(jsonPath("$.rows[0].id").value(12))
            .andExpect(jsonPath("$.rows[0].title").value("대분수의 덧셈"))
            .andExpect(jsonPath("$.rows[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.rows[0].level").value(3))
            .andExpect(jsonPath("$.rows[0].tags[1]").value("오답률높음"));
    }

    @Test
    @DisplayName("GET /api/items/search 숫자가 아닌 난이도 · 페이지, 배열 파라미터 → 400 이 아니라 200 이고 값이 그대로 서비스에 간다")
    void searchPassesRawParamsToService() throws Exception {
        when(itemSearchService.search(any())).thenReturn(
            new ItemSearchResponse(List.of(), 0, ItemSearchService.EMPTY_MESSAGE));

        mockMvc.perform(get("/api/items/search")
                .param("level", "abc")
                .param("page", "-5")
                .param("tag[]", "개념", "계산"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.rows.length()").value(0))
            .andExpect(jsonPath("$.message").value("검색 결과가 없습니다"));

        verify(itemSearchService).search(argThat(params ->
            List.of("abc").equals(params.get("level"))
                && List.of("-5").equals(params.get("page"))
                && List.of("개념", "계산").equals(params.get("tag[]"))));
    }
}
