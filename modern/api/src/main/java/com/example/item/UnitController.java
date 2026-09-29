package com.example.item;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 단원 API. 컨트롤러는 서비스만 호출한다. */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    /** {@code GET /api/units} — 단원 목록. */
    @GetMapping
    public List<UnitResponse> listUnits() {
        return unitService.listUnits();
    }

    /** {@code GET /api/units/item-counts} — 단원별 공개 문항 수. */
    @GetMapping("/item-counts")
    public List<UnitItemCountResponse> listUnitItemCounts() {
        return unitService.listUnitItemCounts();
    }
}
