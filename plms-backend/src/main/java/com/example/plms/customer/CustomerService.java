package com.example.plms.customer;

import com.example.plms.audit.AuditService;
import com.example.plms.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class CustomerService {

    private final CustomerProfileRepository repository;
    private final AuditService audit;
    private final Clock clock;

    public CustomerService(CustomerProfileRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void createForUser(String userId, String fullName, String email, String phone) {
        repository.save(new CustomerProfile(userId, fullName, email, phone, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public CustomerProfile get(String customerId) {
        return repository.findById(customerId).orElseThrow(() -> ApiException.notFound("Customer profile"));
    }

    @Transactional
    public CustomerProfile update(String customerId, CustomerDtos.UpdateProfileRequest request) {
        CustomerProfile profile = get(customerId);
        LocalDate today = LocalDate.now(clock);
        if (request.employmentStartDate() != null && request.employmentStartDate().isBefore(request.dateOfBirth())) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Employment start date must be after date of birth.");
        }
        if (request.dateOfBirth().isAfter(today.minusYears(16))) {
            throw ApiException.badRequest("VALIDATION_FAILED", "Date of birth is not valid for a loan applicant.");
        }
        profile.update(request, Instant.now(clock));
        audit.record(customerId, "PROFILE_UPDATED", "CUSTOMER_PROFILE", customerId, "SUCCESS", Map.of());
        return profile;
    }

    @Transactional
    public CustomerProfile getForStaff(String staffUserId, String customerId) {
        CustomerProfile profile = get(customerId);
        audit.record(staffUserId, "PROFILE_VIEWED_BY_STAFF", "CUSTOMER_PROFILE", customerId, "SUCCESS", Map.of());
        return profile;
    }

    public Integer ageYears(CustomerProfile p) {
        return p.getDateOfBirth() == null ? null : Period.between(p.getDateOfBirth(), LocalDate.now(clock)).getYears();
    }

    public Integer employmentMonths(LocalDate start) {
        return start == null ? null : (int) ChronoUnit.MONTHS.between(start, LocalDate.now(clock));
    }
}
