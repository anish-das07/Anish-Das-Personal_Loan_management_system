package com.example.plms.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Customer profile (PII). The primary key equals the owning user's id, so the authenticated
 * subject is also the customerId used by every other module.
 */
@Entity
@Table(name = "customer_profile")
public class CustomerProfile {

    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;
    @Column(length = 254)
    private String email;
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(length = 20)
    private String phone;
    @Column(name = "address_line1", length = 200)
    private String addressLine1;
    @Column(length = 80)
    private String city;
    @Column(length = 80)
    private String state;
    @Column(name = "postal_code", length = 12)
    private String postalCode;
    @Column(name = "employment_type", length = 30)
    private String employmentType;
    @Column(name = "employer_name", length = 120)
    private String employerName;
    @Column(name = "employment_start_date")
    private LocalDate employmentStartDate;
    @Column(length = 80)
    private String occupation;
    @Column(name = "monthly_income", precision = 15, scale = 2)
    private BigDecimal monthlyIncome;
    @Column(name = "existing_monthly_obligations", precision = 15, scale = 2)
    private BigDecimal existingMonthlyObligations;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected CustomerProfile() {
    }

    public CustomerProfile(String userId, String fullName, String email, String phone, Instant now) {
        this.id = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.updatedAt = now;
    }

    public void update(CustomerDtos.UpdateProfileRequest r, Instant now) {
        this.fullName = r.fullName();
        this.dateOfBirth = r.dateOfBirth();
        this.phone = r.phone();
        this.addressLine1 = r.addressLine1();
        this.city = r.city();
        this.state = r.state();
        this.postalCode = r.postalCode();
        this.employmentType = r.employmentType();
        this.employerName = r.employerName();
        this.employmentStartDate = r.employmentStartDate();
        this.occupation = r.occupation();
        this.monthlyIncome = r.monthlyIncome();
        this.existingMonthlyObligations = r.existingMonthlyObligations();
        this.updatedAt = now;
    }

    /** Percentage of profile sections that are complete (personal, address, employment, income). */
    public int completeness() {
        int done = 0;
        if (fullName != null && dateOfBirth != null && phone != null) {
            done++;
        }
        if (addressLine1 != null && city != null && postalCode != null) {
            done++;
        }
        if (employmentType != null) {
            done++;
        }
        if (monthlyIncome != null && monthlyIncome.signum() > 0) {
            done++;
        }
        return done * 25;
    }

    public boolean isReadyForApplication() {
        return fullName != null && dateOfBirth != null && phone != null && addressLine1 != null
                && city != null && postalCode != null;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getEmployerName() {
        return employerName;
    }

    public LocalDate getEmploymentStartDate() {
        return employmentStartDate;
    }

    public String getOccupation() {
        return occupation;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public BigDecimal getExistingMonthlyObligations() {
        return existingMonthlyObligations;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
