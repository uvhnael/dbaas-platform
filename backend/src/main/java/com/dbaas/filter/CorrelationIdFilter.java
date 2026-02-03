package com.dbaas.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds correlation ID (request ID) to all requests.
 * The correlation ID is:
 * 1. Added to MDC for logging
 * 2. Added to response headers
 * 3. Propagated to downstream services
 */
@Component
@Order(0) // Run before rate limiting
@Slf4j
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_REQUEST_PATH = "requestPath";
    public static final String MDC_CLIENT_IP = "clientIp";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        
        try {
            // Get or generate correlation ID
            String correlationId = getOrGenerateCorrelationId(request);
            
            // Set MDC values for logging
            MDC.put(MDC_CORRELATION_ID, correlationId);
            MDC.put(MDC_REQUEST_PATH, request.getMethod() + " " + request.getRequestURI());
            MDC.put(MDC_CLIENT_IP, getClientIp(request));
            
            // Add correlation ID to response
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            response.setHeader(REQUEST_ID_HEADER, correlationId);

            // Log request start
            log.info("Request started: {} {} from {}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    getClientIp(request));

            filterChain.doFilter(request, response);

        } finally {
            // Log request completion with duration
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request completed: {} {} - Status: {} - Duration: {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
            
            // Clear MDC
            MDC.clear();
        }
    }

    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        // Check for existing correlation ID from upstream service
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(REQUEST_ID_HEADER);
        }
        
        // Generate new one if not present
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = generateCorrelationId();
        }
        
        return correlationId;
    }

    private String generateCorrelationId() {
        // Format: timestamp-random (e.g., 1705593600000-a1b2c3d4)
        return System.currentTimeMillis() + "-" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Get current correlation ID from MDC.
     * Useful for propagating to async tasks or downstream services.
     */
    public static String getCurrentCorrelationId() {
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        return correlationId != null ? correlationId : "unknown";
    }

    /**
     * Set correlation ID in MDC (for async tasks).
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
        }
    }
}
