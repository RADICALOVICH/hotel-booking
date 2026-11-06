package ru.hotelbooking.booking.user.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.booking.common.web.Pages;
import ru.hotelbooking.booking.user.dto.CreateUserRequest;
import ru.hotelbooking.booking.user.dto.PatchUserRequest;
import ru.hotelbooking.booking.user.dto.UserResponse;
import ru.hotelbooking.booking.user.service.UserAdminService;

import java.net.URI;
import java.util.List;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final UserAdminService admin;

    public UserAdminController(UserAdminService admin) {
        this.admin = admin;
    }

    @PostMapping("/user")
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        UserResponse user = admin.create(request);
        return ResponseEntity.created(URI.create("/user/" + user.id())).body(user);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Pages.response(admin.list(Pages.request(page, size, Sort.by("id"))));
    }

    @GetMapping("/user/{id}")
    public UserResponse get(@PathVariable long id) {
        return admin.get(id);
    }

    @PatchMapping("/user/{id}")
    public UserResponse update(@PathVariable long id, @RequestBody PatchUserRequest request) {
        return admin.update(id, request);
    }

    @DeleteMapping("/user/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        admin.delete(id);
        return ResponseEntity.noContent().build();
    }
}
