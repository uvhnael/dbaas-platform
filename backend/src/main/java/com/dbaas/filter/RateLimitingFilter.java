package com.dbaas.filter;

import com.dbaas.config.RateLimitingConfig;
import com.dbaas.config.RateLimitingConfig.RateLimitPlan;
import com.dbaas.model.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter that applies different limits based on endpoint.
 * Uses IP address as the rate limit key.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingConfig.RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitPlan plan = determinePlan(path, method);
        
        if (plan != null && !rateLimiter.tryConsume(clientIp, plan)) {
            log.warn("Rate limit exceeded for IP: {} on path: {} (plan: {})", clientIp, path, plan);
            sendRateLimitResponse(response, plan, clientIp);
            return;
        }

        // Add rate limit headers
        if (plan != null) {
            long remaining = rateLimiter.getAvailableTokens(clientIp, plan);
            response.setHeader("X-RateLimit-Limit", String.valueOf(plan.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Reset", String.valueOf(plan.getDuration().toSeconds()));
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitPlan determinePlan(String path, String method) {
        // Skip rate limiting for health checks and actuator
        if (path.startsWith("/actuator") || path.equals("/health")) {
            return null;
        }

        // Authentication endpoints
        if (path.startsWith("/auth") || path.startsWith("/api/v1/auth")) {
            return RateLimitPlan.AUTH;
        }

        // Cluster creation
        if (path.matches("/api/v1/clusters/?") && "POST".equals(method)) {
            return RateLimitPlan.CREATE_CLUSTER;
        }

        // Cluster deletion
        if (path.matches("/api/v1/clusters/[^/]+/?") && "DELETE".equals(method)) {
            return RateLimitPlan.DELETE_CLUSTER;
        }

        // Cluster scaling
        if (path.matches("/api/v1/clusters/[^/]+/scale/?")) {
            return RateLimitPlan.SCALE_CLUSTER;
        }

        // Metrics and monitoring
        if (path.contains("/metrics") || path.contains("/health") || path.contains("/stats")) {
            return RateLimitPlan.METRICS;
        }

        // General API
        return RateLimitPlan.GENERAL_API;
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

    private void sendRateLimitResponse(HttpServletResponse response, RateLimitPlan plan, String clientIp) 
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(plan.getDuration().toSeconds()));
        
        ApiResponse<Void> errorResponse = ApiResponse.error(
                "RATE_LIMIT_EXCEEDED",
                String.format("Too many requests. Limit: %d per %d seconds. Please try again later.",
                        plan.getCapacity(), plan.getDuration().toSeconds())
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Don't filter static resources, swagger, etc.
        return path.startsWith("/swagger") || 
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/h2-console") ||
               path.endsWith(".html") ||
               path.endsWith(".css") ||
               path.endsWith(".js");
    }
}
