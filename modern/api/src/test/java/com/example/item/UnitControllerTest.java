package com.example.item;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스는 목. */
@WebMvcTest(UnitController.class)
@ActiveProfiles("test")
class UnitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitService unitService;

    @Test
    @DisplayName("GET /api/units/item-counts → 200, 단원별 문항 수 배열")
    void listUnitItemCountsReturnsArray() throws Exception {
        when(unitService.listUnitItemCounts()).thenReturn(List.of(
            new UnitItemCountResponse("M5-1", "분수의 덧셈과 뺄셈", 5, 3),
            new UnitItemCountResponse("M6-1", "분수의 나눗셈", 6, 0)));

        mockMvc.perform(get("/api/units/item-counts").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].code").value("M5-1"))
            .andExpect(jsonPath("$[0].name").value("분수의 덧셈과 뺄셈"))
            .andExpect(jsonPath("$[0].grade").value(5))
            .andExpect(jsonPath("$[0].id").doesNotExist())
            .andExpect(jsonPath("$[0].item_count").value(3))
            .andExpect(jsonPath("$[1].item_count").value(0));
    }
}
