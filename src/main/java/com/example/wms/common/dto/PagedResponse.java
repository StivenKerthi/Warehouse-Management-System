package com.example.wms.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Uniform pagination envelope for all list endpoints.
 *
 * <p>Wraps Spring's {@link Page} into a stable JSON contract so callers
 * are never coupled to Spring's internal {@code Page} serialisation format,
 * which is verbose and subject to change.
 *
 * <pre>
 * {
 *   "content":       [...],
 *   "page":          0,
 *   "size":          20,
 *   "totalElements": 42,
 *   "totalPages":    3,
 *   "last":          false
 * }
 * </pre>
 *
 * @param <T> the element type
 */
public record PagedResponse<T>(
        List<T> content,
        int     page,
        int     size,
        long    totalElements,
        int     totalPages,
        boolean last
) {

    /**
     * Constructs a {@code PagedResponse} from a Spring {@link Page}.
     *
     * @param springPage the page returned by a repository or service
     * @param <T>        the element type
     * @return a serialisation-stable wrapper
     */
    public static <T> PagedResponse<T> of(Page<T> springPage) {
        return new PagedResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
