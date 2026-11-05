package com.example.plms.disbursement;

import com.example.plms.account.AccountDtos;
import com.example.plms.account.LoanAccountService;
import com.example.plms.common.Actor;
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
public class DisbursementController {

    private final DisbursementService service;
    private final LoanAccountService accounts;

    public DisbursementController(DisbursementService service, LoanAccountService accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping("/api/v1/finance/disbursements/eligible")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public List<AccountDtos.AccountResponse> eligible() {
        return accounts.awaitingDisbursement().stream().map(AccountDtos.AccountResponse::from).toList();
    }

    @PostMapping("/api/v1/loan-accounts/{id}/disbursements")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public ResponseEntity<DisbursementDtos.DisbursementResponse> create(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DisbursementDtos.CreateRequest request,
            Authentication auth) {
        DisbursementService.Outcome outcome = service.create(Actor.from(auth).userId(), id, idempotencyKey, request);
        return ResponseEntity.status(outcome.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(DisbursementDtos.DisbursementResponse.from(outcome.disbursement()));
    }

    @PostMapping("/api/v1/disbursements/{id}/reconcile")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public DisbursementDtos.DisbursementResponse reconcile(@PathVariable String id, Authentication auth) {
        return DisbursementDtos.DisbursementResponse.from(service.reconcile(Actor.from(auth).userId(), id));
    }

    @GetMapping("/api/v1/loan-accounts/{id}/disbursements")
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','LOAN_APPROVER','ADMIN')")
    public List<DisbursementDtos.DisbursementResponse> list(@PathVariable String id) {
        return service.forAccount(id).stream().map(DisbursementDtos.DisbursementResponse::from).toList();
    }
}
