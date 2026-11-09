package com.bookstore.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderTotalsCalculator {
    @Value("${bookstore.shipping:0}")
    private BigDecimal shipping;

    @Value("${bookstore.tax-rate:0}")
    private BigDecimal taxRate;

    @Value("${bookstore.discount:0}")
    private BigDecimal configuredDiscount;

    public Totals calculate(BigDecimal rawSubtotal) {
        BigDecimal subtotal = money(rawSubtotal);
        BigDecimal shippingAmount = money(shipping);
        BigDecimal tax = money(subtotal.multiply(taxRate));
        BigDecimal gross = money(subtotal.add(shippingAmount).add(tax));
        BigDecimal discount = money(configuredDiscount.min(gross));
        BigDecimal total = money(gross.subtract(discount));
        return new Totals(subtotal, shippingAmount, tax, discount, total);
    }

    private BigDecimal money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP); }

    public record Totals(BigDecimal subtotal, BigDecimal shipping, BigDecimal tax, BigDecimal discount, BigDecimal total) {}
}
