package com.dbaas.service;

import com.dbaas.model.User;
import com.dbaas.model.UserRole;
import com.dbaas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for user management and authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Create a new user.
     */
    @Transactional
    public User createUser(String username, String password, UserRole role) {
        return createUser(username, password, null, role);
    }

    /**
     * Create a new user with email.
     */
    @Transactional
    public User createUser(String username, String password, String email, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Created user: {} with role: {}", username, role);
        return saved;
    }

    /**
     * Find user by username.
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Check if user exists.
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Get user by ID.
     */
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    /**
     * Update user password.
     */
    @Transactional
    public void updatePassword(String userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password updated for user: {}", user.getUsername());
    }

    /**
     * Validate user's current password.
     */
    public boolean validatePassword(String userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return passwordEncoder.matches(password, user.getPasswordHash());
    }

    /**
     * Update user profile (email, displayName).
     */
    @Transactional
    public User updateProfile(String userId, String email, String displayName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (email != null && !email.isBlank()) {
            user.setEmail(email);
        }
        // Note: displayName field can be added to User entity if needed

        User saved = userRepository.save(user);
        log.info("Profile updated for user: {}", user.getUsername());
        return saved;
    }

    /**
     * Change user password with validation of current password.
     */
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        if (!validatePassword(userId, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        updatePassword(userId, newPassword);
    }
}
