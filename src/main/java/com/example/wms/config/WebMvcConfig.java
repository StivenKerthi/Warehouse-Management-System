package com.example.wms.config;

import com.example.wms.order.export.OrderCsvExporter;
import com.example.wms.order.export.OrderCsvMessageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC customisation — registers additional {@link HttpMessageConverter}s.
 *
 * <p>The {@link OrderCsvMessageConverter} is prepended to the converter list so that
 * it is evaluated before Jackson. Spring MVC then selects it automatically when a
 * client sends {@code Accept: text/csv} on an endpoint that produces a
 * {@code List<OrderSummaryDto>}.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final OrderCsvExporter csvExporter;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Append after Jackson. Jackson does not handle text/csv, so this converter
        // is still selected for Accept: text/csv without stealing Accept: */* from Jackson.
        converters.add(new OrderCsvMessageConverter(csvExporter));
    }
}
