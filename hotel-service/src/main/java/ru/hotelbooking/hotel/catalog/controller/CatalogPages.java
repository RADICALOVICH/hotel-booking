package ru.hotelbooking.hotel.catalog.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

final class CatalogPages {
    private CatalogPages() {}

    static PageRequest byId(int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by("id"));
        if (request.getOffset() > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Pagination offset is too large");
        }
        return request;
    }

    static <T> ResponseEntity<List<T>> response(Page<T> page) {
        return ResponseEntity.ok()
                .header("X-Total-Count", Long.toString(page.getTotalElements()))
                .body(page.getContent());
    }
}
