package com.dbaas.model.dto;

import com.dbaas.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for user details.
 * Does not expose sensitive information like password hash.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private String id;
    private String username;
    private String email;
    private UserRole role;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Optional: count of clusters owned by user.
     */
    private Integer clusterCount;
}
