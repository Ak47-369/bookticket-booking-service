package com.bookticket.booking_service.security;


import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

public class HeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            String userId = attributes.getRequest().getHeader("X-User-Id");
            String userRoles = attributes.getRequest().getHeader("X-User-Roles");
            userRoles += ",ROLE_SERVICE_ACCOUNT"; // Service To Service communication

            if (userId != null) {
                request.getHeaders().add("X-User-Id", userId);
            }

            if (userRoles != null) {
                request.getHeaders().add("X-User-Roles", userRoles);
            }
        }

        return execution.execute(request, body);
    }
}
