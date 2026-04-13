package io.github.jsbxyyx.jdbcdsl.dto;

/**
 * Simple DTO for jdbc-dsl test projections.
 *
 * <p>Uses JavaBean style (no-arg constructor + setters) for setter-based mapping.
 */
public class UserDto {

    private Long id;
    private String username;

    public UserDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
