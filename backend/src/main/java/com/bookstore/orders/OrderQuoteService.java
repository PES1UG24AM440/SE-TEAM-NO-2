package com.bookstore.orders;

import com.bookstore.books.Book;
import com.bookstore.books.BookRepository;
import com.bookstore.orders.dto.OrderLineRequest;
import com.bookstore.orders.dto.OrderQuoteRequest;
import com.bookstore.orders.dto.OrderQuoteResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQuoteService {
    private final BookRepository books;
    private final OrderTotalsCalculator totalsCalculator;

    public OrderQuoteService(BookRepository books, OrderTotalsCalculator totalsCalculator) {
        this.books = books;
        this.totalsCalculator = totalsCalculator;
    }

    @Transactional(readOnly = true)
    public OrderQuoteResponse quote(OrderQuoteRequest request) {
        Set<Long> seen = new HashSet<>();
        List<OrderQuoteResponse.Item> items = new ArrayList<>();
        int itemCount = 0;
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderLineRequest line : request.items()) {
            if (!seen.add(line.book_id())) {
                throw new OrderException(400, "duplicate_book", "Include each book once and set its quantity.");
            }
            Book book = books.findBookById(line.book_id()).orElseThrow(() -> new OrderException(
                404, "book_not_found", "Book ID " + line.book_id() + " was not found."
            ));
            if (line.quantity() > book.getStock()) {
                int available = book.getStock();
                throw new OrderException(409, "insufficient_stock", "Only " + available + " " +
                    (available == 1 ? "copy" : "copies") + " of '" + book.getTitle() + "' remain.",
                    java.util.Map.of("book_id", book.getId(), "available", available));
            }
            BigDecimal lineTotal = money(book.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
            items.add(new OrderQuoteResponse.Item(book.getId(), book.getTitle(), book.getAuthor(), line.quantity(), book.getPrice(), lineTotal));
            subtotal = subtotal.add(lineTotal);
            itemCount += line.quantity();
        }

        OrderTotalsCalculator.Totals totals = totalsCalculator.calculate(subtotal);
        return new OrderQuoteResponse(items, itemCount, totals.subtotal(), totals.shipping(), totals.tax(), totals.discount(), totals.total());
    }

    private BigDecimal money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP); }
}
