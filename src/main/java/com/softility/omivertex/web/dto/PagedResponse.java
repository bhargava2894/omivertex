package com.softility.omivertex.web.dto;

import java.util.List;

/**
 * A single page of results. Returned by list endpoints only when the caller opts in
 * with a {@code page} parameter; without it they return a plain array (backward
 * compatible). Bounds the payload sent to the browser for large rosters.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> of(List<T> all, int page, int size) {
        int safeSize = Math.max(1, size);
        int totalPages = (int) Math.ceil((double) all.size() / safeSize);
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PagedResponse<>(List.copyOf(all.subList(from, to)), safePage, safeSize, all.size(), totalPages);
    }
}
