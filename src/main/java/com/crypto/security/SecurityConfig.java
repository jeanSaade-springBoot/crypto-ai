package com.crypto.security;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // This is a private dashboard using session authentication. API calls are
            // protected by the same authenticated session.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/login.html",
                    "/css/login.css",
                    "/favicon.ico",
                    "/error"
                ).permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login.html?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    request -> request.getRequestURI().startsWith("/api/")
                )
            );

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager users = new JdbcUserDetailsManager(dataSource);
        users.setUsersByUsernameQuery("""
            SELECT username, password, enabled
            FROM app_user
            WHERE username = ?
            """);
        users.setAuthoritiesByUsernameQuery("""
            SELECT username, CONCAT('ROLE_', role_name)
            FROM app_user
            WHERE username = ? AND enabled = TRUE
            """);
        return users;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Database values may use {bcrypt}, {noop}, or another supported prefix.
        // Production users should always use {bcrypt} hashes.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
