package com.example.plms.report;

import com.example.plms.account.AccountDtos;
import com.example.plms.account.LoanAccount;
import com.example.plms.account.LoanAccountRepository;
import com.example.plms.account.LoanAccountService;
import com.example.plms.application.LoanApplicationRepository;
import com.example.plms.approval.LoanDecisionRepository;
import com.example.plms.audit.AuditService;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import com.example.plms.common.PageResponse;
import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.disbursement.DisbursementRepository;
import com.example.plms.document.DocumentMetadataRepository;
import com.example.plms.repayment.RepaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reporting. Reports are aggregate counts and operational rows only; they do not export
 * customer PII. Every report run is audited.
 */
@RestController
public class ReportController {

    private final LoanAccountService accounts;
    private final ClosureService closure;
    private final LoanApplicationRepository applications;
    private final LoanAccountRepository accountRepository;
    private final LoanDecisionRepository decisions;
    private final DisbursementRepository disbursements;
    private final RepaymentRepository repayments;
    private final DocumentMetadataRepository documents;
    private final AuditService audit;
    private final Clock clock;

    public ReportController(LoanAccountService accounts, ClosureService closure,
                            LoanApplicationRepository applications, LoanAccountRepository accountRepository,
                            LoanDecisionRepository decisions, DisbursementRepository disbursements,
                            RepaymentRepository repayments, DocumentMetadataRepository documents,
                            AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.closure = closure;
        this.applications = applications;
        this.accountRepository = accountRepository;
        this.decisions = decisions;
        this.disbursements = disbursements;
        this.repayments = repayments;
        this.documents = documents;
        this.audit = audit;
        this.clock = clock;
    }

    public record ClosureCheck(String loanAccountId, String status, boolean closable, List<String> blockers) {
    }

    public record PortfolioReport(Instant generatedAt, Map<String, Long> applicationsByStatus,
                                  Map<String, Long> loanAccountsByStatus, Map<String, Long> decisions,
                                  Map<String, Long> disbursementsByStatus, Map<String, Long> repaymentsByStatus) {
    }

    @GetMapping("/api/v1/reports/overdue")
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','ADMIN')")
    public PageResponse<AccountDtos.OverdueRow> overdue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            Authentication auth) {
        audit.record(Actor.from(auth).userId(), "REPORT_RUN", "REPORT", "overdue", "SUCCESS",
                Map.of("asOf", String.valueOf(asOf)));
        return accounts.overdue(asOf, page, size);
    }

    @GetMapping("/api/v1/reports/portfolio")
    @PreAuthorize("hasAnyRole('FINANCE_OFFICER','LOAN_APPROVER','ADMIN')")
    @Transactional
    public PortfolioReport portfolio(Authentication auth) {
        Map<String, Long> apps = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            apps.put(s.name(), applications.countByStatus(s));
        }
        Map<String, Long> accts = new LinkedHashMap<>();
        for (LoanAccount.Status s : LoanAccount.Status.values()) {
            accts.put(s.name(), accountRepository.countByStatus(s.name()));
        }
        Map<String, Long> dec = new LinkedHashMap<>();
        for (String d : List.of("APPROVE", "REJECT", "RETURN_FOR_CORRECTION")) {
            dec.put(d, decisions.countByDecision(d));
        }
        Map<String, Long> disb = new LinkedHashMap<>();
        for (String s : List.of("PROCESSING", "SUCCEEDED", "FAILED", "UNKNOWN")) {
            disb.put(s, disbursements.countByStatus(s));
        }
        Map<String, Long> pays = new LinkedHashMap<>();
        for (String s : List.of("PENDING", "SUCCESS", "FAILED")) {
            pays.put(s, repayments.countByStatus(s));
        }
        audit.record(Actor.from(auth).userId(), "REPORT_RUN", "REPORT", "portfolio", "SUCCESS", Map.of());
        return new PortfolioReport(Instant.now(clock), apps, accts, dec, disb, pays);
    }

    /** Role-specific KPIs for the staff dashboard. */
    @GetMapping("/api/v1/staff/dashboard")
    @PreAuthorize("hasAnyRole('LOAN_OFFICER','CREDIT_ANALYST','DOCUMENT_VERIFIER','LOAN_APPROVER','FINANCE_OFFICER','ADMIN')")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(Authentication auth) {
        Actor actor = Actor.from(auth);
        Map<String, Object> kpis = new LinkedHashMap<>();
        if (actor.hasAny(Role.LOAN_OFFICER, Role.CREDIT_ANALYST, Role.LOAN_APPROVER)) {
            kpis.put("newApplications", applications.countByStatus(ApplicationStatus.SUBMITTED));
            kpis.put("inReview", applications.countByStatus(ApplicationStatus.UNDER_REVIEW));
            kpis.put("returned", applications.countByStatus(ApplicationStatus.RETURNED_FOR_CORRECTION));
        }
        if (actor.has(Role.DOCUMENT_VERIFIER)) {
            kpis.put("documentsPendingReview",
                    documents.findByStatus("PENDING_REVIEW", PageRequest.of(0, 1)).getTotalElements());
        }
        if (actor.hasAny(Role.FINANCE_OFFICER, Role.ADMIN)) {
            kpis.put("awaitingDisbursement", accountRepository.countByStatus("PENDING_DISBURSEMENT"));
            kpis.put("unknownDisbursements", disbursements.countByStatus("UNKNOWN"));
            kpis.put("pendingRepayments", repayments.countByStatus("PENDING"));
            kpis.put("overdueInstallments", accounts.overdue(LocalDate.now(clock), 0, 1).totalElements());
        }
        kpis.put("roles", actor.roles());
        return kpis;
    }

    @GetMapping("/api/v1/loan-accounts/{id}/closure-check")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public ClosureCheck closureCheck(@PathVariable String id) {
        LoanAccount account = accounts.get(id);
        List<String> blockers = closure.blockers(account);
        return new ClosureCheck(id, account.getStatus(), blockers.isEmpty(), blockers);
    }

    @PostMapping("/api/v1/loan-accounts/{id}/closure")
    @PreAuthorize("hasRole('FINANCE_OFFICER')")
    public AccountDtos.AccountResponse close(@PathVariable String id, Authentication auth) {
        return AccountDtos.AccountResponse.from(closure.close(Actor.from(auth).userId(), id));
    }
}
