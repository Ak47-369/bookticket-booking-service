package com.bookticket.booking_service.security;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

@Slf4j
public class HeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            String userId = attributes.getRequest().getHeader("X-User-Id");
            String userRoles = attributes.getRequest().getHeader("X-User-Roles");
            String username = attributes.getRequest().getHeader("X-User-Name");

            // Fix: Only append SERVICE_ACCOUNT if userRoles is not null
            if (userRoles != null) {
                userRoles = userRoles + ",SERVICE_ACCOUNT"; // Service To Service communication
            } else {
                log.warn("HeaderPropagationInterceptor - X-User-Roles is null, cannot add SERVICE_ACCOUNT");
            }

            if (userId != null) {
                request.getHeaders().set("X-User-Id", userId);  // Use set() instead of add() to avoid duplicates
            } else {
                log.warn("HeaderPropagationInterceptor - X-User-Id is null, not adding to outgoing request");
            }

            if (username != null) {
                request.getHeaders().set("X-User-Name", username);  // Use set() instead of add() to avoid duplicates
            } else {
                log.warn("HeaderPropagationInterceptor - X-User-Name is null, not adding to outgoing request");
            }

            if (userRoles != null) {
                request.getHeaders().set("X-User-Roles", userRoles);  // Use set() instead of add() to avoid duplicates
            } else {
                log.warn("HeaderPropagationInterceptor - X-User-Roles is null, not adding to outgoing request");
            }
        } else {
            log.warn("HeaderPropagationInterceptor - RequestContextHolder.getRequestAttributes() returned null - no request context available");
        }

        log.info("HeaderPropagationInterceptor - Final outgoing headers: {}", request.getHeaders());
        return execution.execute(request, body);
    }
}
