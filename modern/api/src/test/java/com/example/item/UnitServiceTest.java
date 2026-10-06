package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UnitServiceTest {

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private UnitService unitService;

    @Test
    @DisplayName("listUnits: 저장소 순서를 유지하며 DTO 로 변환한다")
    void listUnitsMapsInOrder() {
        when(unitRepository.findAllByOrderByGradeAscCodeAsc()).thenReturn(List.of(
            ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5),
            ItemFixtures.unit(4, "M6-1", "분수의 나눗셈", 6)));

        List<UnitResponse> units = unitService.listUnits();

        assertThat(units).extracting(UnitResponse::code).containsExactly("M5-1", "M6-1");
        assertThat(units.get(1)).isEqualTo(new UnitResponse(4, "M6-1", "분수의 나눗셈", 6));
    }

    @Test
    @DisplayName("listUnits: 단원이 없으면 빈 목록")
    void listUnitsEmpty() {
        when(unitRepository.findAllByOrderByGradeAscCodeAsc()).thenReturn(List.of());

        assertThat(unitService.listUnits()).isEmpty();
    }

    @Test
    @DisplayName("listUnitItemCounts: status='A' 로만 저장소에 묻고 순서를 유지한다")
    void listUnitItemCountsQueriesActiveStatusOnly() {
        when(unitRepository.countItemsByUnit(ItemStatus.ACTIVE)).thenReturn(List.of(
            new UnitItemCountResponse("M5-1", "분수의 덧셈과 뺄셈", 5, 3),
            new UnitItemCountResponse("M6-1", "분수의 나눗셈", 6, 0)));

        List<UnitItemCountResponse> counts = unitService.listUnitItemCounts();

        assertThat(counts).extracting(UnitItemCountResponse::code).containsExactly("M5-1", "M6-1");
        assertThat(counts).extracting(UnitItemCountResponse::itemCount).containsExactly(3L, 0L);
        verify(unitRepository).countItemsByUnit("A");
    }
}
