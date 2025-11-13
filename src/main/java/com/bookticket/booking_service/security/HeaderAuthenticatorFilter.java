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
//       TODO String username = request.getHeader("X-User-Name");
        String roles = request.getHeader("X-User-Roles");

        if (id != null  && roles != null) {
            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .map(role ->new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            // Create a new authentication object
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(id, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("Successfully authenticated user {} with roles {}", id, authorities);
        } else {
            log.warn("HeaderAuthenticatorFilter - Missing headers. X-User-Id: {}, X-User-Roles: {}", id, roles);
        }
        filterChain.doFilter(request, response);
    }
}
