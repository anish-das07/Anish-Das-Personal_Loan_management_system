package com.example.plms.repayment;

import com.example.plms.account.LoanAccountService;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RepaymentController {

    private final RepaymentService service;
    private final LoanAccountService accounts;

    public RepaymentController(RepaymentService service, LoanAccountService accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @PostMapping("/api/v1/repayments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<RepaymentDtos.RepaymentResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RepaymentDtos.CreateRequest request,
            Authentication auth) {
        RepaymentService.Outcome outcome = service.initiate(Actor.from(auth).userId(), idempotencyKey, request);
        return ResponseEntity.status(outcome.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(RepaymentDtos.RepaymentResponse.from(outcome.repayment()));
    }

    @PostMapping("/api/v1/repayments/{id}/reconcile")
    @PreAuthorize("hasAnyRole('CUSTOMER','FINANCE_OFFICER')")
    public RepaymentDtos.RepaymentResponse reconcile(@PathVariable String id, Authentication auth) {
        return RepaymentDtos.RepaymentResponse.from(service.reconcile(Actor.from(auth), id));
    }

    @GetMapping("/api/v1/repayments/{id}/receipt")
    public RepaymentDtos.Receipt receipt(@PathVariable String id, Authentication auth) {
        Repayment r = service.getAccessible(Actor.from(auth), id);
        if (!r.is(Repayment.Status.SUCCESS)) {
            throw ApiException.conflict("RECEIPT_UNAVAILABLE", "A receipt is only issued for a confirmed payment.");
        }
        RepaymentAllocation a = service.allocation(id)
                .orElseThrow(() -> ApiException.conflict("RECEIPT_UNAVAILABLE", "Allocation not recorded."));
        return new RepaymentDtos.Receipt("RCPT-" + r.getId().substring(0, 8).toUpperCase(), r.getId(),
                r.getLoanAccountId(), r.getInstallmentNo(), r.getAmount(), a.getFee(), a.getInterest(),
                a.getPrincipal(), r.getReference(), r.getStatus(), r.getUpdatedAt(),
                "Simulated payment - no real money movement");
    }

    @GetMapping("/api/v1/loan-accounts/{id}/repayments")
    public List<RepaymentDtos.RepaymentResponse> forAccount(@PathVariable String id, Authentication auth) {
        accounts.getAccessible(Actor.from(auth), id);
        return service.forAccount(id).stream().map(RepaymentDtos.RepaymentResponse::from).toList();
    }
}
