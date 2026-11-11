package com.bookstore.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CONFIRMED;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "owner_session_id", length = 128)
    private String ownerSessionId;

    @Column(length = 30)
    private String phone;

    @Column(name = "address_line", nullable = false, length = 200)
    private String addressLine;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shipping;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public String getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public String getCustomerName() { return customerName; }
    public String getEmail() { return email; }
    public String getOwnerSessionId() { return ownerSessionId; }
    public String getPhone() { return phone; }
    public String getAddressLine() { return addressLine; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getShipping() { return shipping; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getTotal() { return total; }
    public List<OrderItem> getItems() { return items; }

    public void setId(String id) { this.id = id; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setEmail(String email) { this.email = email; }
    public void setOwnerSessionId(String ownerSessionId) { this.ownerSessionId = ownerSessionId; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }
    public void setCity(String city) { this.city = city; }
    public void setState(String state) { this.state = state; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public void setCountry(String country) { this.country = country; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public void setShipping(BigDecimal shipping) { this.shipping = shipping; }
    public void setTax(BigDecimal tax) { this.tax = tax; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public void transitionTo(OrderStatus nextStatus) {
        if (!status.canTransitionTo(nextStatus)) {
            throw new OrderException(
                409,
                "invalid_status_transition",
                "Order status cannot transition from " + status + " to " + nextStatus + "."
            );
        }

        status = nextStatus;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}