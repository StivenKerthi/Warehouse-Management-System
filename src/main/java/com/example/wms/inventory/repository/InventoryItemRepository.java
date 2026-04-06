package com.example.wms.inventory.repository;

import com.example.wms.inventory.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    Optional<InventoryItem> findByName(String name);

    /**
     * Atomic decrement — only succeeds when current quantity >= delta.
     * Returns number of rows updated (1 = success, 0 = insufficient stock).
     */
    @Modifying
    @Query("""
            UPDATE InventoryItem i
               SET i.quantity = i.quantity - :delta
             WHERE i.id = :id
               AND i.quantity >= :delta
            """)
    int decrementIfSufficient(@Param("id") UUID id, @Param("delta") int delta);

    /**
     * Atomic increment — no guard needed (adding stock never violates constraints).
     */
    @Modifying
    @Query("""
            UPDATE InventoryItem i
               SET i.quantity = i.quantity + :delta
             WHERE i.id = :id
            """)
    int increment(@Param("id") UUID id, @Param("delta") int delta);
}
