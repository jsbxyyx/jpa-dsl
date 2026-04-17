package io.github.jsbxyyx.jdbcdsl.dto;

/**
 * Projection DTO for scalar-subquery tests that return a username and an order count.
 */
public class UserOrderCountDto {

    private String username;
    private Long orderCount;

    public UserOrderCountDto() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
}
