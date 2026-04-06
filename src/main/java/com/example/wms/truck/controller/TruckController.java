package com.example.wms.truck.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.common.dto.PagedResponse;
import com.example.wms.truck.dto.CreateTruckRequest;
import com.example.wms.truck.dto.TruckDto;
import com.example.wms.truck.dto.UpdateTruckRequest;
import com.example.wms.truck.service.TruckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Truck fleet management — WAREHOUSE_MANAGER role only.
 *
 * <p>URL-level protection is enforced by {@code SecurityConfig}
 * ({@code /api/manager/**} → {@code WAREHOUSE_MANAGER}).
 *
 * <p>Update and delete operations evict the {@code delivery-eligible} Redis cache
 * (handled in {@link TruckService}) because any fleet change invalidates previously
 * computed eligible delivery dates for pending orders.
 */
@RestController
@RequestMapping("/api/manager/trucks")
@RequiredArgsConstructor
@Tag(name = "Manager — Trucks", description = "Truck fleet CRUD (WAREHOUSE_MANAGER only)")
@SecurityRequirement(name = "bearerAuth")
public class TruckController {

    private final TruckService truckService;

    // -------------------------------------------------------------------------
    // List (paginated)
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List all trucks (paginated)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public PagedResponse<TruckDto> listTrucks(
            @ParameterObject @PageableDefault(size = 20, sort = "licensePlate") Pageable pageable) {
        return PagedResponse.of(truckService.findAll(pageable));
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new truck")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Chassis number or license plate already registered")
    })
    public ApiResponse<TruckDto> createTruck(@Valid @RequestBody CreateTruckRequest request) {
        return ApiResponse.of(truckService.create(request));
    }

    // -------------------------------------------------------------------------
    // Get by ID
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Get truck by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Truck not found")
    })
    public ApiResponse<TruckDto> getTruck(@PathVariable UUID id) {
        return ApiResponse.of(truckService.findById(id));
    }

    // -------------------------------------------------------------------------
    // Update — also evicts delivery-eligible cache
    // -------------------------------------------------------------------------

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a truck",
        description = "Evicts all delivery-eligible-days cache entries — any fleet change " +
                      "invalidates previously computed eligible dates for pending orders."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Truck not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Chassis number or license plate already registered")
    })
    public ApiResponse<TruckDto> updateTruck(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTruckRequest request) {
        return ApiResponse.of(truckService.update(id, request));
    }

    // -------------------------------------------------------------------------
    // Delete (soft) — also evicts delivery-eligible cache
    // -------------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Deactivate a truck (soft delete)",
        description = "Sets active=false. Evicts all delivery-eligible-days cache entries. " +
                      "Historical delivery records referencing this truck are preserved."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Deactivated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Truck not found")
    })
    public void deleteTruck(@PathVariable UUID id) {
        truckService.delete(id);
    }
}
