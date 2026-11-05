package com.example.plms.common;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Pagination convention: zero-based page, bounded size, allow-listed sort fields. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int MAX_SIZE = 100;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static Pageable pageable(Integer page, Integer size, String sort, Set<String> allowedSort,
                                    String defaultSort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size <= 0 ? 20 : Math.min(size, MAX_SIZE);
        String field = defaultSort;
        Sort.Direction dir = Sort.Direction.DESC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (!allowedSort.contains(parts[0])) {
                throw ApiException.badRequest("INVALID_SORT", "Sorting by '" + parts[0] + "' is not supported.");
            }
            field = parts[0];
            if (parts.length > 1 && parts[1].equalsIgnoreCase("asc")) {
                dir = Sort.Direction.ASC;
            }
        }
        return PageRequest.of(p, s, Sort.by(dir, field));
    }
}
