package ru.hotelbooking.booking.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TokenValidationController {
    @GetMapping("/internal/auth/validate")
    public ResponseEntity<Void> validate() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
