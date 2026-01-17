package com.dbaas.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication (login/register).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String type;
    private long expiresIn;
    private String username;
    private String role;

    /**
     * Create Bearer token response.
     */
    public static AuthResponse bearer(String token, long expiresIn, String username, String role) {
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .expiresIn(expiresIn)
                .username(username)
                .role(role)
                .build();
    }
}
