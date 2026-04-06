package com.example.wms.inventory.controller;

import com.example.wms.common.dto.ApiResponse;
import com.example.wms.common.dto.PagedResponse;
import com.example.wms.inventory.dto.CreateInventoryItemRequest;
import com.example.wms.inventory.dto.InventoryItemDto;
import com.example.wms.inventory.dto.UpdateInventoryItemRequest;
import com.example.wms.inventory.service.InventoryService;
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
 * Inventory management — WAREHOUSE_MANAGER role only.
 *
 * <p>URL-level protection is enforced by {@code SecurityConfig}
 * ({@code /api/manager/**} → {@code WAREHOUSE_MANAGER}). No additional
 * {@code @PreAuthorize} annotations are needed here.
 */
@RestController
@RequestMapping("/api/manager/inventory")
@RequiredArgsConstructor
@Tag(name = "Manager — Inventory", description = "Inventory item CRUD (WAREHOUSE_MANAGER only)")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    // -------------------------------------------------------------------------
    // List (paginated)
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List all inventory items (paginated)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public PagedResponse<InventoryItemDto> listItems(
            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PagedResponse.of(inventoryService.findAll(pageable));
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new inventory item")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Item name already exists")
    })
    public ApiResponse<InventoryItemDto> createItem(@Valid @RequestBody CreateInventoryItemRequest request) {
        return ApiResponse.of(inventoryService.create(request));
    }

    // -------------------------------------------------------------------------
    // Get by ID
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Get inventory item by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Item not found")
    })
    public ApiResponse<InventoryItemDto> getItem(@PathVariable UUID id) {
        return ApiResponse.of(inventoryService.findById(id));
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @PutMapping("/{id}")
    @Operation(summary = "Update an inventory item")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Item not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Item name already exists")
    })
    public ApiResponse<InventoryItemDto> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ApiResponse.of(inventoryService.update(id, request));
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an inventory item")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Item not found")
    })
    public void deleteItem(@PathVariable UUID id) {
        inventoryService.delete(id);
    }
}
