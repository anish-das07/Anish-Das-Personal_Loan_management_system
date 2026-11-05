package com.example.plms.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class AuthDtos {

    /** 8-72 chars (BCrypt limit) with at least one letter and one digit. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE =
            "Password must be 8-72 characters and contain at least one letter and one digit";

    private AuthDtos() {
    }

    /**
     * Public registration. {@code role} is accepted only so that it can be explicitly ignored:
     * public registration always assigns CUSTOMER.
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[0-9+][0-9 -]{6,19}$", message = "Enter a valid mobile number") String mobile,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            Boolean acceptedTerms,
            String role) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record ResetRequest(@NotBlank @Email String email) {
    }

    public record ResetConfirm(@NotBlank String token,
                               @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
                               String newPassword) {
    }

    public record UserSummary(String id, String email, String fullName, Set<Role> roles, String status) {
        static UserSummary from(UserAccount u) {
            return new UserSummary(u.getId(), u.getEmail(), u.getFullName(), u.getRoles(), u.getStatus().name());
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                                UserSummary user) {
    }

    public record CreateStaffRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password,
            @NotEmpty Set<Role> roles) {
    }

    public record UpdateUserRequest(Set<Role> roles, UserAccount.Status status) {
    }

    public record MessageResponse(String message) {
    }
}
