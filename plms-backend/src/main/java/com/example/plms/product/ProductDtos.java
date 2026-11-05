package com.example.plms.product;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,40}$") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Size(max = 60) String purpose,
            @NotNull @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal minAmount,
            @NotNull @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal maxAmount,
            @Min(1) @Max(600) int minMonths,
            @Min(1) @Max(600) int maxMonths,
            @NotNull @DecimalMin("0.00") @DecimalMax("99.99") BigDecimal annualRate,
            @NotNull @DecimalMin("0.00") @DecimalMax("20.00") BigDecimal processingFeePercent,
            @NotNull LocalDate activeFrom,
            LocalDate activeTo,
            boolean active,
            @NotBlank @Size(max = 40) String policyVersion) {
    }

    public record ProductResponse(String id, String code, String name, String description, String purpose,
                                  BigDecimal minAmount, BigDecimal maxAmount, int minMonths, int maxMonths,
                                  BigDecimal annualRate, String interestMethod, BigDecimal processingFeePercent,
                                  LocalDate activeFrom, LocalDate activeTo, boolean active, boolean availableNow,
                                  String policyVersion, String disclaimer) {
        static ProductResponse from(LoanProduct p, LocalDate today) {
            return new ProductResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPurpose(),
                    p.getMinAmount(), p.getMaxAmount(), p.getMinMonths(), p.getMaxMonths(), p.getAnnualRate(),
                    p.getInterestMethod(), p.getProcessingFeePercent(), p.getActiveFrom(), p.getActiveTo(),
                    p.isActive(), p.isAvailableOn(today), p.getPolicyVersion(),
                    "Illustrative terms; final terms are subject to approved policy and review.");
        }
    }
}
