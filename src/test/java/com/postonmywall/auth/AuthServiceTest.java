package com.postonmywall.auth;

import com.postonmywall.auth.*;
import com.postonmywall.config.JwtTokenProvider;
import com.postonmywall.exception.ResourceAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("mouad");
        registerRequest.setEmail("mouad@test.com");
        registerRequest.setPassword("password123");
    }

    @Test
    void register_shouldSaveUserAndReturnResponse() {
        when(userRepository.existsByUsername("mouad")).thenReturn(false);
        when(userRepository.existsByEmail("mouad@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        UserResponse result = authService.register(registerRequest);

        assertThat(result.getUsername()).isEqualTo("mouad");
        assertThat(result.getEmail()).isEqualTo("mouad@test.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrow_whenUsernameAlreadyTaken() {
        when(userRepository.existsByUsername("mouad")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("mouad");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyRegistered() {
        when(userRepository.existsByUsername("mouad")).thenReturn(false);
        when(userRepository.existsByEmail("mouad@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("mouad@test.com");
    }

    @Test
    void login_shouldReturnToken_onValidCredentials() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("mouad")
                .email("mouad@test.com")
                .password("encoded-password")
                .build();

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("mouad");
        loginRequest.setPassword("password123");

        when(userRepository.findByUsername("mouad")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(jwtTokenProvider.generateToken(userId.toString(), "mouad")).thenReturn("jwt-token");

        AuthResponse result = authService.login(loginRequest);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getUsername()).isEqualTo("mouad");
        assertThat(result.getUserId()).isEqualTo(userId.toString());
    }

    @Test
    void login_shouldThrow_whenUserNotFound() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("unknown");
        loginRequest.setPassword("password123");

        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(RuntimeException.class);
    }
}
