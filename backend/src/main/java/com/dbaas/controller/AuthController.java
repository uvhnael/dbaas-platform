package com.dbaas.controller;

import com.dbaas.exception.InvalidPasswordException;
import com.dbaas.exception.ProfileUpdateException;
import com.dbaas.exception.RegistrationException;
import com.dbaas.exception.UsernameAlreadyExistsException;
import com.dbaas.model.User;
import com.dbaas.model.UserRole;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.AuthResponse;
import com.dbaas.model.dto.ChangePasswordRequest;
import com.dbaas.model.dto.LoginRequest;
import com.dbaas.model.dto.RegisterRequest;
import com.dbaas.model.dto.UpdateProfileRequest;
import com.dbaas.model.dto.UserDto;
import com.dbaas.mapper.UserMapper;
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
    private final UserMapper userMapper;

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        // BadCredentialsException will be handled by GlobalExceptionHandler
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        User user = (User) authentication.getPrincipal();
        String token = jwtService.generateToken(user);

        AuthResponse authResponse = AuthResponse.bearer(
                token,
                jwtService.getExpirationTime(),
                user.getUsername(),
                user.getRole().name());

        return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        try {
            User user = userService.createUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    UserRole.USER);

            String token = jwtService.generateToken(user);

            AuthResponse authResponse = AuthResponse.bearer(
                    token,
                    jwtService.getExpirationTime(),
                    user.getUsername(),
                    user.getRole().name());

            return ResponseEntity.ok(ApiResponse.success(authResponse, "Registration successful"));
        } catch (Exception e) {
            throw new RegistrationException(request.getUsername(), e.getMessage(), e);
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(Authentication authentication) {
        // Return 401 if not authenticated
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "Authentication required"));
        }

        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(user)));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserDto>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        User currentUser = (User) authentication.getPrincipal();

        try {
            User updatedUser = userService.updateProfile(
                    currentUser.getId(),
                    request.getEmail(),
                    request.getDisplayName());

            return ResponseEntity
                    .ok(ApiResponse.success(userMapper.toDto(updatedUser), "Profile updated successfully"));
        } catch (Exception e) {
            throw new ProfileUpdateException(currentUser.getId(), e.getMessage(), e);
        }
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        // Validate confirm password matches
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidPasswordException("New password and confirm password do not match");
        }

        User currentUser = (User) authentication.getPrincipal();

        try {
            userService.changePassword(
                    currentUser.getId(),
                    request.getCurrentPassword(),
                    request.getNewPassword());

            return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            throw new InvalidPasswordException(e.getMessage(), e);
        }
    }
}
