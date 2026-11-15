package com.bookstore.cart;

import com.bookstore.books.Book;
import jakarta.persistence.*;

@Entity
@Table(name = "cart_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"cart_key", "book_id"}))
public class CartLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cart_key", nullable = false, length = 36)
    private String cartKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;
    @Column(nullable = false)
    private Integer quantity;

    protected CartLine() {}
    public CartLine(String cartKey, Book book, Integer quantity) { this.cartKey = cartKey; this.book = book; this.quantity = quantity; }
    public Long getId() { return id; }
    public String getCartKey() { return cartKey; }
    public Book getBook() { return book; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
