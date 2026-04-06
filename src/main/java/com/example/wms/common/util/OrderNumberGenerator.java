package com.example.wms.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Generates human-readable, unique order numbers in the format {@code ORD-YYYY-NNNNN}.
 *
 * <p>The sequence resets each calendar year. Concurrent inserts are protected by the
 * {@code UNIQUE} constraint on {@code orders.order_number}: if two threads race on the same
 * sequence number, one will get a constraint violation and must retry at the service layer.
 * This is acceptable for a single-instance monolith with normal order volumes.
 *
 * <p>Example: {@code ORD-2025-00042}
 */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Returns the next order number for the current year.
     *
     * <p>Queries the highest existing sequence in the {@code orders} table for the
     * current year and increments it by one. Returns {@code ORD-YYYY-00001} when no
     * orders exist yet for the year.
     */
    public String next() {
        int year = Year.now().getValue();
        String prefix = "ORD-" + year + "-%";

        Integer maxSeq = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(CAST(SPLIT_PART(order_number, '-', 3) AS INTEGER)), 0) " +
                "FROM orders WHERE order_number LIKE ?",
                Integer.class,
                prefix
        );

        int next = (maxSeq == null ? 0 : maxSeq) + 1;
        return String.format("ORD-%d-%05d", year, next);
    }
}
