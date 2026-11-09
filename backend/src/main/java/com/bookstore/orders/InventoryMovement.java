package com.bookstore.orders;

import com.bookstore.books.Book;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "inventory_movements",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_movement_order_book_type",
        columnNames = {"order_id", "book_id", "movement_type"}
    )
)
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 32)
    private InventoryMovementType movementType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryMovement() {}

    public InventoryMovement(
            Book book,
            Order order,
            int quantityChange,
            InventoryMovementType movementType) {
        this.book = book;
        this.order = order;
        this.quantityChange = quantityChange;
        this.movementType = movementType;
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public Order getOrder() { return order; }
    public Integer getQuantityChange() { return quantityChange; }
    public InventoryMovementType getMovementType() { return movementType; }
    public Instant getCreatedAt() { return createdAt; }
}
