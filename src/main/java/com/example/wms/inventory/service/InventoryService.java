package com.example.wms.inventory.service;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.inventory.dto.CreateInventoryItemRequest;
import com.example.wms.inventory.dto.InventoryItemDto;
import com.example.wms.inventory.dto.UpdateInventoryItemRequest;
import com.example.wms.inventory.mapper.InventoryItemMapper;
import com.example.wms.inventory.model.InventoryItem;
import com.example.wms.inventory.repository.InventoryItemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository repository;
    private final InventoryItemMapper     mapper;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public InventoryItemDto create(CreateInventoryItemRequest request) {
        if (repository.existsByName(request.name())) {
            throw new BusinessException("ITEM_NAME_TAKEN",
                    "An inventory item named '" + request.name() + "' already exists.",
                    HttpStatus.CONFLICT);
        }

        InventoryItem item = InventoryItem.builder()
                .name(request.name())
                .quantity(request.quantity())
                .unitPrice(request.unitPrice())
                .packageVolume(request.packageVolume())
                .build();

        return mapper.toDto(repository.save(item));
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public InventoryItemDto findById(UUID id) {
        return mapper.toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    public InventoryItemDto update(UUID id, UpdateInventoryItemRequest request) {
        InventoryItem item = getOrThrow(id);

        if (request.name() != null) {
            if (!request.name().equals(item.getName())
                    && repository.existsByNameAndIdNot(request.name(), id)) {
                throw new BusinessException("ITEM_NAME_TAKEN",
                        "An inventory item named '" + request.name() + "' already exists.",
                        HttpStatus.CONFLICT);
            }
            item.setName(request.name());
        }

        if (request.quantity() != null) {
            if (request.quantity() < 0) {
                throw new BusinessException("INVALID_QUANTITY",
                        "Quantity cannot be negative.");
            }
            item.setQuantity(request.quantity());
        }

        if (request.unitPrice() != null) {
            item.setUnitPrice(request.unitPrice());
        }

        if (request.packageVolume() != null) {
            item.setPackageVolume(request.packageVolume());
        }

        return mapper.toDto(repository.save(item));
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Inventory item not found: " + id);
        }
        repository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Stock adjustments (called by DeliveryService)
    // -------------------------------------------------------------------------

    /**
     * Atomically decrements stock by {@code quantity}.
     *
     * <p>Uses a conditional UPDATE that only fires when {@code current >= quantity},
     * preventing negative stock without a separate read-then-write race condition.
     *
     * @throws BusinessException (422) if stock would go negative or item not found
     */
    @Transactional
    public void decrementStock(UUID itemId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_QUANTITY",
                    "Decrement quantity must be positive.");
        }
        // existsById check first to return a clear 404 vs 422
        getOrThrow(itemId);

        int updated = repository.decrementIfSufficient(itemId, quantity);
        if (updated == 0) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock for item " + itemId
                            + ": requested " + quantity + " but available stock is too low.");
        }
    }

    /**
     * Atomically increments stock by {@code quantity}.
     *
     * @throws BusinessException (422) if quantity is not positive or item not found
     */
    @Transactional
    public void incrementStock(UUID itemId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("INVALID_QUANTITY",
                    "Increment quantity must be positive.");
        }
        getOrThrow(itemId);
        repository.increment(itemId, quantity);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InventoryItem getOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Inventory item not found: " + id));
    }
}
