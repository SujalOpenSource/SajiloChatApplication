
package com.sajilochat.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HttpSessionSecurityContextRepository securityContextRepository
    ) throws Exception {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse()
                        )
                        .csrfTokenRequestHandler(
                                new CsrfTokenRequestAttributeHandler()
                        )
                )

                .httpBasic(basic -> basic.disable())

                .formLogin(form -> form.disable())

                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        response.sendError(
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "Authentication required"
                                        )
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Allow error dispatches.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        // Public frontend pages and static resources.
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login.html",
                                "/register.html",
                                "/error",
                                "/css/**",
                                "/chat.html",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()

                        // Public authentication endpoints.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/csrf"
                        ).permitAll()

                        // Public health endpoint.
                        .requestMatchers("/actuator/health")
                        .permitAll()

                        // WebSocket handshake.
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // Everything else requires authentication.
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
