package com.example.plms.customer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "^[0-9+][0-9 -]{6,19}$", message = "Enter a valid phone number") String phone,
            @NotBlank @Size(max = 200) String addressLine1,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 -]{3,12}$", message = "Enter a valid postal code") String postalCode,
            @Pattern(regexp = "^(SALARIED|SELF_EMPLOYED|OTHER)$", message = "Unsupported employment type")
            String employmentType,
            @Size(max = 120) String employerName,
            @PastOrPresent LocalDate employmentStartDate,
            @Size(max = 80) String occupation,
            @DecimalMin(value = "0.01", message = "Monthly income must be positive") @Digits(integer = 13, fraction = 2)
            BigDecimal monthlyIncome,
            @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2) BigDecimal existingMonthlyObligations) {
    }

    public record ProfileResponse(String customerId, String fullName, String email, LocalDate dateOfBirth,
                                  String phone, String addressLine1, String city, String state, String postalCode,
                                  String employmentType, String employerName, LocalDate employmentStartDate,
                                  String occupation, BigDecimal monthlyIncome, BigDecimal existingMonthlyObligations,
                                  int completeness, Instant updatedAt) {
        public static ProfileResponse from(CustomerProfile p) {
            return new ProfileResponse(p.getId(), p.getFullName(), p.getEmail(), p.getDateOfBirth(), p.getPhone(),
                    p.getAddressLine1(), p.getCity(), p.getState(), p.getPostalCode(), p.getEmploymentType(),
                    p.getEmployerName(), p.getEmploymentStartDate(), p.getOccupation(), p.getMonthlyIncome(),
                    p.getExistingMonthlyObligations(), p.completeness(), p.getUpdatedAt());
        }
    }

    /** Purpose-limited view for staff: no address or contact details. */
    public record StaffProfileView(String customerId, String fullName, LocalDate dateOfBirth, String employmentType,
                                   String employerName, BigDecimal monthlyIncome,
                                   BigDecimal existingMonthlyObligations) {
        public static StaffProfileView from(CustomerProfile p) {
            return new StaffProfileView(p.getId(), p.getFullName(), p.getDateOfBirth(), p.getEmploymentType(),
                    p.getEmployerName(), p.getMonthlyIncome(), p.getExistingMonthlyObligations());
        }
    }
}
