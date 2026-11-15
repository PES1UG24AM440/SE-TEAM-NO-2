package com.bookstore.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByOrder_Id(String orderId);

    boolean existsByOrder_Id(String orderId);

    @Query("select coalesce(sum(r.amount), 0) from Refund r")
    BigDecimal sumAmounts();
}
