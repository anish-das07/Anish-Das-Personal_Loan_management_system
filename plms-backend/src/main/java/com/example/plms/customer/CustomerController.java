package com.example.plms.customer;

import com.example.plms.common.Actor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerDtos.ProfileResponse me(Authentication auth) {
        return CustomerDtos.ProfileResponse.from(service.get(Actor.from(auth).userId()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerDtos.ProfileResponse update(@Valid @RequestBody CustomerDtos.UpdateProfileRequest request,
                                               Authentication auth) {
        return CustomerDtos.ProfileResponse.from(service.update(Actor.from(auth).userId(), request));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','CREDIT_ANALYST','LOAN_APPROVER')")
    public CustomerDtos.StaffProfileView staffView(@PathVariable String customerId, Authentication auth) {
        return CustomerDtos.StaffProfileView.from(service.getForStaff(Actor.from(auth).userId(), customerId));
    }
}
