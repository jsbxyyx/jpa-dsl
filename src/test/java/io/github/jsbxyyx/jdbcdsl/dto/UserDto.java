package io.github.jsbxyyx.jdbcdsl.dto;

/**
 * Simple DTO for jdbc-dsl test projections.
 */
public class UserDto {

    private final Long id;
    private final String username;

    public UserDto(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
}
