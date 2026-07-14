package com.praxedo.upload.domain.models;

import java.util.List;

/** Resultat pagine generique (immuable). */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
        return new PageResult<>(items, page, size, totalElements);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
