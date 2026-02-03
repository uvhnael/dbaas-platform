package com.dbaas.util;

import com.dbaas.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Utility class for authentication-related helper methods.
 * 
 * <p>
 * Provides common helper methods used across multiple controllers
 * to avoid code duplication.
 * </p>
 */
@Component
public class AuthHelper {

    private static final String ANONYMOUS_USER_ID = "anonymous";

    /**
     * Extract user ID from Authentication object.
     * 
     * @param authentication Spring Security Authentication object
     * @return User ID or "anonymous" if not authenticated
     */
    public String getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return ANONYMOUS_USER_ID;
    }

    /**
     * Extract User object from Authentication.
     * 
     * @param authentication Spring Security Authentication object
     * @return User object or null if not authenticated
     */
    public User getUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    /**
     * Check if the authentication represents a valid authenticated user.
     * 
     * @param authentication Spring Security Authentication object
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof User;
    }
}
