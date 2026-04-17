package io.github.jsbxyyx.jdbcdsl.dto;

/**
 * Projection DTO for window-function tests that return a username and a row-number / rank.
 */
public class UserRnDto {

    private String username;
    private Long rn;

    public UserRnDto() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getRn() { return rn; }
    public void setRn(Long rn) { this.rn = rn; }
}
