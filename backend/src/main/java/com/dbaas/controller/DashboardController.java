package com.dbaas.controller;

import com.dbaas.model.dto.ApiResponse;
import com.dbaas.model.dto.DashboardSummaryResponse;
import com.dbaas.service.DashboardService;
import com.dbaas.util.AuthHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for dashboard summary.
 * Provides overall system overview and aggregated statistics.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard Summary API")
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthHelper authHelper;

    /**
     * Get overall dashboard summary with cluster counts, node health, and resource
     * usage.
     */
    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary with cluster and node statistics")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary(
            Authentication authentication) {

        String userId = authHelper.getUserId(authentication);
        DashboardSummaryResponse response = dashboardService.getDashboardSummary(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
