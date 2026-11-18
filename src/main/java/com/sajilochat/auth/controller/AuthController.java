
package com.sajilochat.auth.controller;

import com.sajilochat.auth.dto.AuthResponse;
import com.sajilochat.auth.dto.LoginRequest;
import com.sajilochat.auth.dto.RegisterRequest;
import com.sajilochat.auth.dto.RegisterResponse;
import com.sajilochat.auth.service.AuthService;
import com.sajilochat.security.UserPrincipal;
import com.sajilochat.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final HttpSessionSecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {

        User user = authService.register(request);

        RegisterResponse response = new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.getCreatedAt()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(
                new CsrfResponse(csrfToken.getToken())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {

        User user = authService.authenticate(request);

        UserPrincipal principal = new UserPrincipal(user);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        // Create a session and rotate its ID.
        httpRequest.getSession(true);
        httpRequest.changeSessionId();

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);

        SecurityContextHolder.setContext(context);

        // Explicitly persist authentication in the HTTP session.
        securityContextRepository.saveContext(
                context,
                httpRequest,
                httpResponse
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

        return ResponseEntity.ok(
                new AuthResponse(authenticatedUser)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> currentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            UserPrincipal principal
    ) {

        AuthResponse.AuthenticatedUser user =
                new AuthResponse.AuthenticatedUser(
                        principal.getUserId(),
                        principal.getUsername(),
                        principal.getFirstName(),
                        principal.getLastName(),
                        principal.getEmail(),
                        principal.getStatus()
                );

        return ResponseEntity.ok(new AuthResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {

        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);

        return ResponseEntity.noContent().build();
    }

    public record CsrfResponse(String token) {
    }
}
