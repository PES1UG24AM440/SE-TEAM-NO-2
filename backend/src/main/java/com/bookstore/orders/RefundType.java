package com.bookstore.orders;

/** FULL: the whole amount paid comes back. PARTIAL: shipping and/or a cancellation fee was withheld. */
public enum RefundType {
    FULL,
    PARTIAL
}
