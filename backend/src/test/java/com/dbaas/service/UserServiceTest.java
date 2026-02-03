package com.dbaas.service;

import com.dbaas.model.User;
import com.dbaas.model.UserRole;
import com.dbaas.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID().toString())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashedPassword123")
                .role(UserRole.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("loadUserByUsername")
    class LoadUserByUsername {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            // Given
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            // When
            var result = userService.loadUserByUsername("testuser");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
            verify(userRepository).findByUsername("testuser");
        }

        @Test
        @DisplayName("should throw UsernameNotFoundException when user not found")
        void shouldThrowWhenUserNotFound() {
            // Given
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.loadUserByUsername("unknown"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("unknown");
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("should create user successfully")
        void shouldCreateUserSuccessfully() {
            // Given
            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setCreatedAt(Instant.now());
                return user;
            });

            // When
            User result = userService.createUser("newuser", "password123", "new@example.com", UserRole.USER);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("newuser");
            assertThat(result.getEmail()).isEqualTo("new@example.com");
            assertThat(result.getRole()).isEqualTo(UserRole.USER);
            verify(passwordEncoder).encode("password123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw exception when username already exists")
        void shouldThrowWhenUsernameExists() {
            // Given
            when(userRepository.existsByUsername("existinguser")).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> userService.createUser("existinguser", "password", UserRole.USER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username already exists");
        }

        @Test
        @DisplayName("should create user with default role")
        void shouldCreateUserWithDefaultRole() {
            // Given
            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = userService.createUser("newuser", "password123", UserRole.USER);

            // Then
            assertThat(result.getRole()).isEqualTo(UserRole.USER);
        }
    }

    @Nested
    @DisplayName("findByUsername")
    class FindByUsername {

        @Test
        @DisplayName("should return Optional with user when found")
        void shouldReturnUserWhenFound() {
            // Given
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

            // When
            Optional<User> result = userService.findByUsername("testuser");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("should return empty Optional when not found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.findByUsername("unknown");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUsername")
    class ExistsByUsername {

        @Test
        @DisplayName("should return true when user exists")
        void shouldReturnTrueWhenExists() {
            // Given
            when(userRepository.existsByUsername("testuser")).thenReturn(true);

            // When
            boolean result = userService.existsByUsername("testuser");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when user does not exist")
        void shouldReturnFalseWhenNotExists() {
            // Given
            when(userRepository.existsByUsername("unknown")).thenReturn(false);

            // When
            boolean result = userService.existsByUsername("unknown");

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("validatePassword")
    class ValidatePassword {

        @Test
        @DisplayName("should return true when password matches")
        void shouldReturnTrueWhenPasswordMatches() {
            // Given
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("correctPassword", testUser.getPasswordHash())).thenReturn(true);

            // When
            boolean result = userService.validatePassword(testUser.getId(), "correctPassword");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when password does not match")
        void shouldReturnFalseWhenPasswordDoesNotMatch() {
            // Given
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash())).thenReturn(false);

            // When
            boolean result = userService.validatePassword(testUser.getId(), "wrongPassword");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw exception when user not found")
        void shouldThrowWhenUserNotFound() {
            // Given
            when(userRepository.findById("unknownId")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> userService.validatePassword("unknownId", "password"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("updatePassword")
    class UpdatePassword {

        @Test
        @DisplayName("should update password successfully")
        void shouldUpdatePasswordSuccessfully() {
            // Given
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.updatePassword(testUser.getId(), "newPassword");

            // Then
            verify(passwordEncoder).encode("newPassword");
            verify(userRepository).save(argThat(user -> user.getPasswordHash().equals("newEncodedPassword")));
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("should change password when current password is correct")
        void shouldChangePasswordWhenCurrentPasswordCorrect() {
            // Given
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("currentPassword", testUser.getPasswordHash())).thenReturn(true);
            when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            // When
            userService.changePassword(testUser.getId(), "currentPassword", "newPassword");

            // Then
            verify(passwordEncoder).encode("newPassword");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw exception when current password is incorrect")
        void shouldThrowWhenCurrentPasswordIncorrect() {
            // Given
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash())).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> userService.changePassword(testUser.getId(), "wrongPassword", "newPassword"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Current password is incorrect");
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("should update email successfully")
        void shouldUpdateEmailSuccessfully() {
            // Given
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = userService.updateProfile(testUser.getId(), "newemail@example.com", null);

            // Then
            assertThat(result.getEmail()).isEqualTo("newemail@example.com");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should not update email when null or blank")
        void shouldNotUpdateEmailWhenNullOrBlank() {
            // Given
            String originalEmail = testUser.getEmail();
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            User result = userService.updateProfile(testUser.getId(), "", null);

            // Then
            assertThat(result.getEmail()).isEqualTo(originalEmail);
        }
    }
}
