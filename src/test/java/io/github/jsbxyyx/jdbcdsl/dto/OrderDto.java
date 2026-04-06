package io.github.jsbxyyx.jdbcdsl.dto;

import java.math.BigDecimal;

/**
 * Simple DTO for jdbc-dsl order projections.
 */
public class OrderDto {

    private final Long id;
    private final String orderNo;
    private final BigDecimal amount;

    public OrderDto(Long id, String orderNo, BigDecimal amount) {
        this.id = id;
        this.orderNo = orderNo;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public BigDecimal getAmount() { return amount; }
}
