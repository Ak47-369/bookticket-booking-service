package com.bookticket.booking_service.configuration;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 1. Read the header passed by API Gateway
            String requestId = request.getHeader(HEADER_NAME);

            // (Optional: Generate if missing, useful for local testing without Gateway)
            if (requestId == null || requestId.isEmpty()) {
                requestId = "generated-" + UUID.randomUUID();
            }

            // 2. Add to MDC for logging
            MDC.put(MDC_KEY, requestId);

            // 3. Continue
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
