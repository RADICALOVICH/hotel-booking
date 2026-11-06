package ru.hotelbooking.booking.common.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

public final class Pages {
    private Pages() {}

    public static PageRequest request(int page, int size, Sort sort) {
        PageRequest request = PageRequest.of(page, size, sort);
        if (request.getOffset() > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Pagination offset is too large");
        }
        return request;
    }

    public static <T> ResponseEntity<List<T>> response(Page<T> page) {
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.getContent());
    }
}
