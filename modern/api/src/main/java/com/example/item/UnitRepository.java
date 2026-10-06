package com.example.item;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnitRepository extends JpaRepository<Unit, Integer> {

    Optional<Unit> findByCode(String code);

    List<Unit> findAllByOrderByGradeAscCodeAsc();

    /** 단원별 문항 수 — 주어진 상태의 문항만 센다. 문항이 없는 단원도 0 으로 포함. 학년, 코드 순. */
    @Query("""
        select new com.example.item.UnitItemCountResponse(u.code, u.name, u.grade, count(i))
        from Unit u
        left join Item i on i.unit = u and i.status = :status
        group by u.id, u.code, u.name, u.grade
        order by u.grade asc, u.code asc
        """)
    List<UnitItemCountResponse> countItemsByUnit(@Param("status") String status);
}
