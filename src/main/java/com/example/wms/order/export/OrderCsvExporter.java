package com.example.wms.order.export;

import com.example.wms.order.dto.OrderSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes a list of {@link OrderSummaryDto} records as RFC 4180 CSV to an arbitrary {@link Writer}.
 *
 * <p>Transport concerns (Content-Type, Content-Disposition, charset) are handled by the
 * caller — either {@link OrderCsvMessageConverter} for Spring MVC content negotiation or
 * a test directly passing a {@link java.io.StringWriter}.
 *
 * <h2>Column order</h2>
 * <pre>id, orderNumber, clientUsername, status, submittedAt, createdAt</pre>
 *
 * <h2>Format</h2>
 * RFC 4180 (Commons CSV default). A header row is always emitted first.
 * Date-times render as ISO-8601 strings via {@link Object#toString()} on
 * {@link java.time.OffsetDateTime} — consistent with the Jackson JSON serialisation.
 */
@Slf4j
@Component
public class OrderCsvExporter {

    static final String[] HEADERS = {
            "id", "orderNumber", "clientUsername", "status", "submittedAt", "createdAt"
    };

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader(HEADERS)
            .build();

    /**
     * Writes the given orders to {@code writer} in CSV format.
     *
     * <p>The writer is NOT closed — the caller owns the lifecycle.
     *
     * @param orders the orders to export (may be empty)
     * @param writer the target writer to append CSV text to
     * @throws IOException if writing fails
     */
    public void writeCsv(List<OrderSummaryDto> orders, Writer writer) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(writer, FORMAT)) {
            for (OrderSummaryDto order : orders) {
                printer.printRecord(
                        order.id(),
                        order.orderNumber(),
                        order.clientUsername(),
                        order.status(),
                        order.submittedAt(),
                        order.createdAt()
                );
            }
            printer.flush();
        }
        log.debug("Exported {} orders to CSV", orders.size());
    }
}
