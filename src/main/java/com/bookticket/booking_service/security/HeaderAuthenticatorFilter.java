package com.bookticket.booking_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class HeaderAuthenticatorFilter  extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String id = request.getHeader("X-User-Id");
        String username = request.getHeader("X-User-Name");
        String roles = request.getHeader("X-User-Roles");

        if (id != null && username != null && roles != null) {
            try {
                Long userId = Long.parseLong(id);

                List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                // Create UserPrincipal with userId and username
                UserPrincipal userPrincipal = new UserPrincipal(userId, username);

                // Create authentication object with UserPrincipal as principal
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userPrincipal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.info("Successfully authenticated user {} (ID: {}) with roles {}", username, userId, authorities);
            } catch (NumberFormatException e) {
                log.error("Invalid X-User-Id format: {}. Must be a valid Long.", id);
            }
        } else {
            log.warn("HeaderAuthenticatorFilter - Missing headers. X-User-Id: {}, X-User-Name: {}, X-User-Roles: {}",
                    id, username, roles);
        }
        filterChain.doFilter(request, response);
    }
}
