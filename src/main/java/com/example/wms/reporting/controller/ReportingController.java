package com.example.wms.reporting.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.reporting.dto.SlaReportDto;
import com.example.wms.reporting.dto.ThroughputReportDto;
import com.example.wms.reporting.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Admin reporting endpoints — SYSTEM_ADMIN role only.
 *
 * <p>URL-level protection is enforced by {@code SecurityConfig}
 * ({@code /api/admin/**} → {@code SYSTEM_ADMIN}). No additional
 * {@code @PreAuthorize} is needed.
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin — Reports", description = "SLA and throughput reporting (SYSTEM_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class ReportingController {

    private final ReportingService reportingService;

    /**
     * Returns the SLA report, optionally scoped to a calendar date range.
     *
     * <p>The report contains four sections:
     * <ol>
     *   <li>Order counts broken down by all statuses.</li>
     *   <li>Average hours from {@code AWAITING_APPROVAL} to {@code FULFILLED}.</li>
     *   <li>The 5 most recently fulfilled orders.</li>
     *   <li>Active truck count and total available container volume (m³).</li>
     * </ol>
     *
     * <p>Results are cached in Redis (5-min TTL) per date-range combination and
     * evicted automatically on any order status change.
     *
     * @param from optional inclusive start date ({@code YYYY-MM-DD}); no lower bound when absent
     * @param to   optional inclusive end date  ({@code YYYY-MM-DD}); no upper bound when absent
     * @throws ResponseStatusException HTTP 400 when {@code from} is not strictly before {@code to}
     */
    @GetMapping("/sla")
    @Operation(
        summary = "SLA report",
        description = """
                Returns order counts by status, average fulfilment time, the 5 most recently \
                fulfilled orders, and active fleet stats. \
                Optionally scoped to a date range via `from` / `to` query params (inclusive, YYYY-MM-DD). \
                Results are cached for 5 minutes and evicted on every order status change."""
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
            description = "Invalid date range — `from` must be strictly before `to`"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — SYSTEM_ADMIN role required")
    })
    public ApiResponse<SlaReportDto> getSlaReport(
            @Parameter(description = "Inclusive start date (YYYY-MM-DD). Omit for no lower bound.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Nullable LocalDate from,

            @Parameter(description = "Inclusive end date (YYYY-MM-DD). Omit for no upper bound.")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Nullable LocalDate to) {

        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "'from' must be strictly before 'to'");
        }

        return ApiResponse.of(reportingService.getSlaReport(from, to));
    }

    /**
     * Returns daily order submission counts for the last 30 calendar days (UTC).
     *
     * <p>Every day in the window is present — days with no submissions carry a
     * count of {@code 0}, making the result directly usable for charting.
     * Results are cached for 5 minutes.
     */
    @GetMapping("/throughput")
    @Operation(
        summary = "Throughput report (last 30 days)",
        description = """
                Returns the number of orders submitted (entered AWAITING_APPROVAL) \
                per calendar day over the last 30 days (UTC). \
                All 30 days are present in the response — days with no activity carry count 0. \
                Results are cached for 5 minutes."""
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Report returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — SYSTEM_ADMIN role required")
    })
    public ApiResponse<ThroughputReportDto> getThroughputReport() {
        return ApiResponse.of(reportingService.getThroughputReport());
    }
}
