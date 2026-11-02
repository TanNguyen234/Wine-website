package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.entity.User;

public record UserDto(Long id, String username, String email, String role) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
