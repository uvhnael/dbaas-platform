package com.dbaas.repository;

import com.dbaas.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Check if username exists.
     */
    boolean existsByUsername(String username);

    /**
     * Find user by email.
     */
    Optional<User> findByEmail(String email);
}
