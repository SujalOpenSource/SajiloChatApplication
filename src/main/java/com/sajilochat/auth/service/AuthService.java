
package com.sajilochat.auth.service;

import com.sajilochat.auth.dto.AuthResponse;
import com.sajilochat.auth.dto.LoginRequest;
import com.sajilochat.auth.dto.RegisterRequest;
import com.sajilochat.common.exception.BadRequestException;
import com.sajilochat.security.JwtService;
import com.sajilochat.user.entity.User;
import com.sajilochat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public User register(RegisterRequest request) {

        String username = request.username()
                .trim()
                .toLowerCase(Locale.ROOT);

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        String phoneNumber = request.phoneNumber().trim();

        int passwordBytes = request.password()
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (passwordBytes > 72) {
            throw new BadRequestException(
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException(
                    "Username is already taken"
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException(
                    "Email is already registered"
            );
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BadRequestException(
                    "Phone number is already registered"
            );
        }

        String passwordHash = passwordEncoder.encode(
                request.password()
        );

        User user = User.register(
                username,
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                phoneNumber,
                passwordHash
        );

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String identifier = request.identifier()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid username/email or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username/email or password"
            );
        }

        if (user.getStatus() !=
                com.sajilochat.user.enums.UserStatus.ACTIVE) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is not active"
            );
        }

        String accessToken = jwtService.generateToken(
                new com.sajilochat.security.UserPrincipal(user)
        );

        AuthResponse.AuthenticatedUser authenticatedUser =
                new AuthResponse.AuthenticatedUser(
                        user.getId(),
                        user.getUsername(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmail(),
                        user.getStatus()
                );

        return new AuthResponse(
                "Bearer",
                accessToken,
                jwtService.getExpirationMs() / 1000,
                authenticatedUser
        );
    }
}
