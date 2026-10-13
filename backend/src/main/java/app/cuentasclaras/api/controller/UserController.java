package app.cuentasclaras.api.controller;

import app.cuentasclaras.api.dto.UpdateUserRequest;
import app.cuentasclaras.api.dto.UserResponse;
import app.cuentasclaras.application.security.AuthenticatedUser;
import app.cuentasclaras.application.user.UserService;
import app.cuentasclaras.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userService.getProfile(principal.id()));
    }

    @PatchMapping("/me")
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody UpdateUserRequest request) {
        User updated = userService.updateProfile(
                principal.id(),
                request.displayName(),
                request.defaultCurrency(),
                request.locale()
        );
        return UserResponse.from(updated);
    }
}
