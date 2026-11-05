package com.example.plms.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface LoanProductRepository extends JpaRepository<LoanProduct, String> {

    Optional<LoanProduct> findByCode(String code);

    @Query("""
            select p from LoanProduct p
            where p.active = true and p.activeFrom <= :today and (p.activeTo is null or p.activeTo >= :today)
              and (lower(p.name) like lower(concat('%', :q, '%'))
                   or lower(p.purpose) like lower(concat('%', :q, '%')))
            """)
    Page<LoanProduct> findAvailable(@Param("today") LocalDate today, @Param("q") String q, Pageable pageable);
}
