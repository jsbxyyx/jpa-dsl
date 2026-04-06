package io.github.jsbxyyx.jdbcdsl.dto;

import java.math.BigDecimal;

/**
 * Simple DTO for jdbc-dsl order projections.
 *
 * <p>Uses JavaBean style (no-arg constructor + setters) for setter-based mapping.
 */
public class OrderDto {

    private Long id;
    private String orderNo;
    private BigDecimal amount;

    public OrderDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
