package com.example.plms.disbursement;

import com.example.plms.account.LoanAccount;
import com.example.plms.account.LoanAccountService;
import com.example.plms.application.ApplicationService;
import com.example.plms.application.LoanApplication;
import com.example.plms.audit.AuditService;
import com.example.plms.common.ApiException;
import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulated disbursement with idempotency and reconciliation. A timeout is "unknown", never assumed to
 * have failed: the outcome is reconciled by reference before anything is retried.
 */
@Service
public class DisbursementService {

    private static final Set<String> BLOCKING = Set.of(Disbursement.Status.PROCESSING.name(),
            Disbursement.Status.UNKNOWN.name(), Disbursement.Status.SUCCEEDED.name());

    private final DisbursementRepository disbursements;
    private final LoanAccountService accounts;
    private final ApplicationService applications;
    private final SimulatedBankAdapter bank;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public DisbursementService(DisbursementRepository disbursements, LoanAccountService accounts,
                               ApplicationService applications, SimulatedBankAdapter bank,
                               NotificationService notifications, AuditService audit, Clock clock) {
        this.disbursements = disbursements;
        this.accounts = accounts;
        this.applications = applications;
        this.bank = bank;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    public record Outcome(Disbursement disbursement, boolean replay) {
    }

    @Transactional
    public Outcome create(String financeId, String accountId, String idempotencyKey,
                          DisbursementDtos.CreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "An Idempotency-Key header (max 100 chars) is required.");
        }
        LoanAccount account = accounts.lockForUpdate(accountId);
        BigDecimal fee = request.feeAmount() != null ? request.feeAmount()
                : account.getPrincipal().multiply(account.getProcessingFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        var existing = disbursements.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().sameRequest(accountId, request.grossAmount(), fee)) {
                return new Outcome(existing.get(), true);
            }
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different request.");
        }
        if (!account.is(LoanAccount.Status.PENDING_DISBURSEMENT)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION", "The loan account is not awaiting disbursement.");
        }
        if (disbursements.existsByLoanAccountIdAndStatusIn(accountId, BLOCKING)) {
            throw ApiException.conflict("DISBURSEMENT_IN_PROGRESS",
                    "A disbursement for this account is in progress or needs reconciliation.");
        }
        if (request.grossAmount().compareTo(account.getPrincipal()) > 0) {
            throw ApiException.badRequest("AMOUNT_EXCEEDS_APPROVED", "The amount cannot exceed the approved principal.");
        }
        if (fee.signum() < 0 || fee.compareTo(request.grossAmount()) > 0) {
            throw ApiException.badRequest("INVALID_FEE", "The fee must be between zero and the gross amount.");
        }

        LoanApplication app = applications.get(account.getApplicationId());
        if (app.getStatus() == ApplicationStatus.APPROVED) {
            applications.transition(app.getId(), null, ApplicationStatus.DISBURSEMENT_PENDING, financeId,
                    "DISBURSEMENT_REQUESTED", "Disbursement is in progress");
        }

        Instant now = Instant.now(clock);
        Disbursement d = disbursements.saveAndFlush(new Disbursement(accountId, idempotencyKey,
                request.grossAmount(), fee, financeId, now));
        audit.record(financeId, "DISBURSEMENT_REQUESTED", "DISBURSEMENT", d.getId(), "SUCCESS",
                Map.of("loanAccountId", accountId, "gross", d.getGrossAmount(), "net", d.getNetAmount()));

        SimulatedBankAdapter.Result result = bank.transfer(d.getId(), d.getNetAmount(), request.simulatedOutcome());
        apply(d, account, result, financeId);
        return new Outcome(d, false);
    }

    @Transactional
    public Disbursement reconcile(String financeId, String disbursementId) {
        Disbursement d = disbursements.findById(disbursementId)
                .orElseThrow(() -> ApiException.notFound("Disbursement"));
        LoanAccount account = accounts.lockForUpdate(d.getLoanAccountId());
        if (!d.is(Disbursement.Status.UNKNOWN)) {
            throw ApiException.conflict("NOTHING_TO_RECONCILE", "Only disbursements with an unknown outcome need reconciliation.");
        }
        apply(d, account, bank.query(d.getId()), financeId);
        audit.record(financeId, "DISBURSEMENT_RECONCILED", "DISBURSEMENT", d.getId(), "SUCCESS",
                Map.of("status", d.getStatus()));
        return d;
    }

    @Transactional(readOnly = true)
    public List<Disbursement> forAccount(String accountId) {
        return disbursements.findByLoanAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public boolean hasUnresolved(String accountId) {
        return disbursements.existsByLoanAccountIdAndStatusIn(accountId,
                Set.of(Disbursement.Status.PROCESSING.name(), Disbursement.Status.UNKNOWN.name()));
    }

    private void apply(Disbursement d, LoanAccount account, SimulatedBankAdapter.Result result, String actorId) {
        Instant now = Instant.now(clock);
        switch (result.outcome()) {
            case SUCCESS -> {
                d.succeed(result.transactionRef(), now);
                accounts.activateAndSchedule(account, actorId);
                applications.transition(account.getApplicationId(), null, ApplicationStatus.ACTIVE, actorId,
                        "DISBURSED", "Your loan has been disbursed");
                notifications.notify(account.getCustomerId(), "LOAN_DISBURSED", "Loan disbursed",
                        "Your loan was disbursed (simulated). Net amount: " + d.getNetAmount() + ".");
                audit.record(actorId, "DISBURSEMENT_SUCCEEDED", "DISBURSEMENT", d.getId(), "SUCCESS",
                        Map.of("transactionRef", result.transactionRef()));
            }
            case FAILED -> {
                d.fail(result.reason(), now);
                audit.record(actorId, "DISBURSEMENT_FAILED", "DISBURSEMENT", d.getId(), "FAILED",
                        Map.of("reason", result.reason()));
            }
            case UNKNOWN -> {
                d.unknown(now);
                audit.record(actorId, "DISBURSEMENT_OUTCOME_UNKNOWN", "DISBURSEMENT", d.getId(), "PENDING", Map.of());
            }
        }
    }
}
