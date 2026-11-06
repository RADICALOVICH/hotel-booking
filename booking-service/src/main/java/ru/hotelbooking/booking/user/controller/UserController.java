package ru.hotelbooking.booking.user.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ru.hotelbooking.booking.security.dto.AuthenticationResponse;
import ru.hotelbooking.booking.security.service.JwtTokenService;
import ru.hotelbooking.booking.user.dto.CredentialsRequest;
import ru.hotelbooking.booking.user.dto.UserSession;
import ru.hotelbooking.booking.user.service.UserService;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService users;
    private final JwtTokenService tokens;

    public UserController(UserService users, JwtTokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody CredentialsRequest request) {
        UserSession session = users.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(tokens.issue(session));
    }

    @PostMapping("/auth")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody CredentialsRequest request) {
        UserSession session = users.authenticate(request.username(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.issue(session));
    }
}
