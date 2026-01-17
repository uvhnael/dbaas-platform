package com.dbaas.controller;

import com.dbaas.model.User;
import com.dbaas.model.UserRole;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.AuthResponse;
import com.dbaas.model.dto.LoginRequest;
import com.dbaas.model.dto.RegisterRequest;
import com.dbaas.model.dto.UserDto;
import com.dbaas.security.JwtService;
import com.dbaas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication API")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            User user = (User) authentication.getPrincipal();
            String token = jwtService.generateToken(user);

            log.info("User logged in: {}", user.getUsername());

            AuthResponse authResponse = AuthResponse.bearer(
                    token,
                    jwtService.getExpirationTime(),
                    user.getUsername(),
                    user.getRole().name());

            return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));

        } catch (AuthenticationException e) {
            log.warn("Login failed for user: {}", request.getUsername());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("INVALID_CREDENTIALS", "Invalid username or password"));
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            if (userService.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("USERNAME_EXISTS", "Username already exists"));
            }

            User user = userService.createUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    UserRole.USER);

            String token = jwtService.generateToken(user);

            log.info("User registered: {}", user.getUsername());

            AuthResponse authResponse = AuthResponse.bearer(
                    token,
                    jwtService.getExpirationTime(),
                    user.getUsername(),
                    user.getRole().name());

            return ResponseEntity.ok(ApiResponse.success(authResponse, "Registration successful"));

        } catch (Exception e) {
            log.error("Registration failed", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("REGISTRATION_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Not authenticated"));
        }

        User user = (User) authentication.getPrincipal();
        UserDto userDto = UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(userDto));
    }
}
