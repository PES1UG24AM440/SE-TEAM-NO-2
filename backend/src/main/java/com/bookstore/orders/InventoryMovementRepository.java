package com.bookstore.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMovementRepository
        extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByOrder_Id(String orderId);
}
