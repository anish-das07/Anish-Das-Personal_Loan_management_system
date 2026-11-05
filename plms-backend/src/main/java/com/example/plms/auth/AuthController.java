package com.example.plms.auth;

import com.example.plms.common.Actor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<AuthDtos.UserSummary> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/api/v1/auth/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.LogoutRequest request,
                                       Authentication auth) {
        service.logout(Actor.from(auth).userId(), request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/password-reset/request")
    public ResponseEntity<AuthDtos.MessageResponse> requestReset(@Valid @RequestBody AuthDtos.ResetRequest request) {
        service.requestPasswordReset(request.email());
        return ResponseEntity.accepted().body(new AuthDtos.MessageResponse(
                "If the address is registered, reset instructions have been sent."));
    }

    @PostMapping("/api/v1/auth/password-reset/confirm")
    public AuthDtos.MessageResponse confirmReset(@Valid @RequestBody AuthDtos.ResetConfirm request) {
        service.confirmPasswordReset(request.token(), request.newPassword());
        return new AuthDtos.MessageResponse("Your password has been updated. Sign in with the new password.");
    }

    @GetMapping("/api/v1/auth/me")
    public Actor me(Authentication auth) {
        return Actor.from(auth);
    }

    // ---- User and role administration (ADMIN only) ----

    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuthDtos.UserSummary> users() {
        return service.listUsers();
    }

    @PostMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuthDtos.UserSummary> createStaff(@Valid @RequestBody AuthDtos.CreateStaffRequest request,
                                                            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createStaffUser(Actor.from(auth).userId(), request));
    }

    @PutMapping("/api/v1/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthDtos.UserSummary updateUser(@PathVariable String userId,
                                           @RequestBody AuthDtos.UpdateUserRequest request, Authentication auth) {
        return service.updateUser(Actor.from(auth).userId(), userId, request);
    }
}
