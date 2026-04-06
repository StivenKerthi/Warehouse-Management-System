package com.example.wms.order.export;

import com.example.wms.order.dto.OrderSummaryDto;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring MVC {@link org.springframework.http.converter.HttpMessageConverter} that serialises
 * a {@code List<OrderSummaryDto>} as an RFC 4180 CSV file when the client sends
 * {@code Accept: text/csv}.
 *
 * <p>Registration is done in {@link com.example.wms.config.WebMvcConfig}. No controller
 * branching on the {@code Accept} header is needed — Spring MVC performs content negotiation
 * automatically and selects this converter when the client requests {@code text/csv}.
 *
 * <h2>Response headers set by this converter</h2>
 * <ul>
 *   <li>{@code Content-Type: text/csv; charset=UTF-8}</li>
 *   <li>{@code Content-Disposition: attachment; filename="orders.csv"}</li>
 * </ul>
 *
 * <p>The actual CSV writing is delegated to {@link OrderCsvExporter} so the serialisation
 * logic lives in one place and can be unit-tested independently.
 */
public class OrderCsvMessageConverter extends AbstractHttpMessageConverter<List<OrderSummaryDto>> {

    /** The media type handled by this converter, exposed for tests and registration. */
    public static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final OrderCsvExporter csvExporter;

    public OrderCsvMessageConverter(OrderCsvExporter csvExporter) {
        super(TEXT_CSV);
        this.csvExporter = csvExporter;
    }

    // -------------------------------------------------------------------------
    // AbstractHttpMessageConverter contract
    // -------------------------------------------------------------------------

    @Override
    protected boolean supports(Class<?> clazz) {
        // Matches any List subtype — the specific element type cannot be checked at
        // runtime due to erasure, but this converter is only registered for text/csv
        // so mis-selection by Spring MVC is not a concern.
        return List.class.isAssignableFrom(clazz);
    }

    @Override
    protected List<OrderSummaryDto> readInternal(
            Class<? extends List<OrderSummaryDto>> clazz, HttpInputMessage inputMessage) {
        throw new UnsupportedOperationException("CSV deserialisation is not supported");
    }

    @Override
    protected void writeInternal(List<OrderSummaryDto> orders, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {

        outputMessage.getHeaders().set(
                "Content-Disposition", "attachment; filename=\"orders.csv\"");

        try (Writer writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8)) {
            csvExporter.writeCsv(orders, writer);
        }
    }
}
