package io.github.jsbxyyx.jdbcdsl.dto;

import java.math.BigDecimal;

/**
 * Projection DTO combining fields from {@code t_user} and {@code t_order}.
 * Used by JOIN-projection integration tests.
 */
public class UserOrderDto {

    private String username;
    private String orderNo;
    private BigDecimal amount;
    private String orderStatus;

    public UserOrderDto() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
}
