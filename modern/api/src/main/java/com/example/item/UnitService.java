package com.example.item;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UnitService {

    private static final Logger log = LoggerFactory.getLogger(UnitService.class);

    private final UnitRepository unitRepository;

    public UnitService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    /** 단원 목록 — 학년, 코드 순. */
    public List<UnitResponse> listUnits() {
        List<UnitResponse> units = unitRepository.findAllByOrderByGradeAscCodeAsc().stream()
            .map(UnitResponse::from)
            .toList();
        log.debug("listed {} units", units.size());
        return units;
    }

    /** 단원별 공개({@code status='A'}) 문항 수 — 학년, 코드 순. 문항이 없는 단원은 0. */
    public List<UnitItemCountResponse> listUnitItemCounts() {
        List<UnitItemCountResponse> counts = unitRepository.countItemsByUnit(ItemStatus.ACTIVE);
        log.debug("counted items for {} units", counts.size());
        return counts;
    }
}
