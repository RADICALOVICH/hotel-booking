package ru.hotelbooking.booking.user;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody CredentialsRequest request) {
        UserResponse user = users.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(user);
    }

    @PostMapping("/auth")
    public ResponseEntity<UserResponse> authenticate(@RequestBody CredentialsRequest request) {
        UserResponse user = users.authenticate(request.username(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }
}
