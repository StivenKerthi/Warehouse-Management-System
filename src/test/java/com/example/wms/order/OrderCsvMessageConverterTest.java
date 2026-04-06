package com.example.wms.order;

import com.example.wms.order.dto.OrderSummaryDto;
import com.example.wms.order.export.OrderCsvExporter;
import com.example.wms.order.export.OrderCsvMessageConverter;
import com.example.wms.order.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpOutputMessage;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderCsvMessageConverter}.
 *
 * <p>No Spring context is loaded — the converter and exporter are wired directly.
 * {@link MockHttpOutputMessage} captures the serialised bytes for assertion.
 */
class OrderCsvMessageConverterTest {

    private OrderCsvMessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new OrderCsvMessageConverter(new OrderCsvExporter());
    }

    // -------------------------------------------------------------------------
    // canWrite / media type
    // -------------------------------------------------------------------------

    @Test
    void canWrite_trueForListAndTextCsv() {
        assertThat(converter.canWrite(List.class, OrderCsvMessageConverter.TEXT_CSV)).isTrue();
    }

    @Test
    void canWrite_falseForJsonMediaType() {
        assertThat(converter.canWrite(List.class, MediaType.APPLICATION_JSON)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Header assertions
    // -------------------------------------------------------------------------

    @Test
    void write_setsContentDispositionHeader() throws IOException {
        MockHttpOutputMessage output = new MockHttpOutputMessage();

        converter.write(List.of(), OrderCsvMessageConverter.TEXT_CSV, output);

        assertThat(output.getHeaders().getFirst("Content-Disposition"))
                .isEqualTo("attachment; filename=\"orders.csv\"");
    }

    // -------------------------------------------------------------------------
    // CSV body assertions
    // -------------------------------------------------------------------------

    @Test
    void write_emptyList_producesHeaderRowOnly() throws IOException {
        MockHttpOutputMessage output = new MockHttpOutputMessage();

        converter.write(List.of(), OrderCsvMessageConverter.TEXT_CSV, output);

        String csv = output.getBodyAsString().trim();
        assertThat(csv).isEqualTo("id,orderNumber,clientUsername,status,submittedAt,createdAt");
    }

    @Test
    void write_singleDto_producesCorrectHeaderAndDataRow() throws IOException {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OffsetDateTime submitted = OffsetDateTime.of(2025, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime created   = OffsetDateTime.of(2025, 3, 1,  9, 0, 0, 0, ZoneOffset.UTC);

        OrderSummaryDto dto = new OrderSummaryDto(
                id, "ORD-2025-00001", "alice", OrderStatus.APPROVED, submitted, created);

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(List.of(dto), OrderCsvMessageConverter.TEXT_CSV, output);

        String[] lines = output.getBodyAsString().split("\r\n");

        // Header row
        assertThat(lines[0])
                .isEqualTo("id,orderNumber,clientUsername,status,submittedAt,createdAt");

        // Data row — check each field by position
        String[] fields = lines[1].split(",");
        assertThat(fields[0]).isEqualTo(id.toString());
        assertThat(fields[1]).isEqualTo("ORD-2025-00001");
        assertThat(fields[2]).isEqualTo("alice");
        assertThat(fields[3]).isEqualTo("APPROVED");
        assertThat(fields[4]).isEqualTo(submitted.toString());
        assertThat(fields[5]).isEqualTo(created.toString());
    }

    @Test
    void write_nullSubmittedAt_rendersEmptyField() throws IOException {
        OrderSummaryDto dto = new OrderSummaryDto(
                UUID.randomUUID(), "ORD-2025-00002", "bob",
                OrderStatus.CREATED, null,
                OffsetDateTime.of(2025, 4, 1, 8, 0, 0, 0, ZoneOffset.UTC));

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(List.of(dto), OrderCsvMessageConverter.TEXT_CSV, output);

        String[] lines = output.getBodyAsString().split("\r\n");
        String[] fields = lines[1].split(",", -1); // -1 keeps trailing empty tokens
        assertThat(fields[4]).isEmpty(); // submittedAt column
    }

    @Test
    void write_multipleRows_allPresent() throws IOException {
        List<OrderSummaryDto> orders = List.of(
                new OrderSummaryDto(UUID.randomUUID(), "ORD-2025-00001", "alice",
                        OrderStatus.APPROVED, null,
                        OffsetDateTime.now(ZoneOffset.UTC)),
                new OrderSummaryDto(UUID.randomUUID(), "ORD-2025-00002", "bob",
                        OrderStatus.CREATED, null,
                        OffsetDateTime.now(ZoneOffset.UTC))
        );

        MockHttpOutputMessage output = new MockHttpOutputMessage();
        converter.write(orders, OrderCsvMessageConverter.TEXT_CSV, output);

        String[] lines = output.getBodyAsString().split("\r\n");
        assertThat(lines).hasSize(3); // 1 header + 2 data rows
        assertThat(lines[1]).contains("ORD-2025-00001");
        assertThat(lines[2]).contains("ORD-2025-00002");
    }
}
