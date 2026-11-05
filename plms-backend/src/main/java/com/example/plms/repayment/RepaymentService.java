package com.example.plms.repayment;

import com.example.plms.account.EmiInstallment;
import com.example.plms.account.LoanAccount;
import com.example.plms.account.LoanAccountService;
import com.example.plms.audit.AuditService;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
import com.example.plms.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Simulated repayments with idempotency keys. Policy (configurable per product in a fuller build):
 * one full installment per payment, earliest unpaid first; partial payments are not supported.
 * A failed payment never reduces principal; an unknown outcome is reconciled, not retried.
 */
@Service
public class RepaymentService {

    private final RepaymentRepository repayments;
    private final RepaymentAllocationRepository allocations;
    private final LoanAccountService accounts;
    private final SimulatedPaymentGateway gateway;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public RepaymentService(RepaymentRepository repayments, RepaymentAllocationRepository allocations,
                            LoanAccountService accounts, SimulatedPaymentGateway gateway,
                            NotificationService notifications, AuditService audit, Clock clock) {
        this.repayments = repayments;
        this.allocations = allocations;
        this.accounts = accounts;
        this.gateway = gateway;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    public record Outcome(Repayment repayment, boolean replay) {
    }

    @Transactional
    public Outcome initiate(String customerId, String idempotencyKey, RepaymentDtos.CreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "An Idempotency-Key header (max 100 chars) is required.");
        }
        var existing = repayments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Repayment r = existing.get();
            if (r.getCustomerId().equals(customerId)
                    && r.sameRequest(request.loanAccountId(), request.installmentNo(), request.amount())) {
                return new Outcome(r, true);
            }
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different request.");
        }

        LoanAccount account = accounts.lockForUpdate(request.loanAccountId());
        if (!account.getCustomerId().equals(customerId)) {
            audit.recordIndependently(customerId, "ACCESS_DENIED", "LOAN_ACCOUNT", account.getId(), "DENIED", Map.of());
            throw ApiException.notFound("Loan account");
        }
        if (!account.is(LoanAccount.Status.ACTIVE)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION", "Payments can only be made on an active loan.");
        }
        EmiInstallment inst = accounts.installment(account.getId(), request.installmentNo());
        if (inst.is(EmiInstallment.Status.PAID)) {
            throw ApiException.conflict("INSTALLMENT_ALREADY_PAID", "This installment is already paid.");
        }
        Integer earliest = accounts.earliestUnpaidNo(account.getId());
        if (earliest != null && earliest != inst.getInstallmentNo()) {
            throw ApiException.conflict("OUT_OF_ORDER", "Pay installment " + earliest + " first.");
        }
        if (repayments.existsByInstallmentIdAndStatus(inst.getId(), Repayment.Status.PENDING.name())) {
            throw ApiException.conflict("PAYMENT_IN_PROGRESS",
                    "A payment for this installment is awaiting confirmation. Reconcile it before paying again.");
        }
        if (request.amount().compareTo(inst.getTotalDue()) != 0) {
            throw ApiException.badRequest("AMOUNT_MISMATCH",
                    "The amount must equal the installment due (" + inst.getTotalDue() + "). Partial payments are not supported by this product policy.");
        }

        Instant now = Instant.now(clock);
        Repayment repayment = repayments.saveAndFlush(new Repayment(account.getId(), inst.getId(),
                inst.getInstallmentNo(), customerId, idempotencyKey, request.amount(), request.paymentMethod(), now));
        SimulatedPaymentGateway.Result result = gateway.charge(repayment.getId(), repayment.getAmount(),
                request.paymentMethod());
        apply(repayment, account, inst, result, customerId);
        return new Outcome(repayment, false);
    }

    @Transactional
    public Repayment reconcile(Actor actor, String repaymentId) {
        Repayment repayment = repayments.findById(repaymentId).orElseThrow(() -> ApiException.notFound("Repayment"));
        boolean owner = actor.isCustomer() && repayment.getCustomerId().equals(actor.userId());
        if (!owner && !actor.has(Role.FINANCE_OFFICER)) {
            throw ApiException.notFound("Repayment");
        }
        LoanAccount account = accounts.lockForUpdate(repayment.getLoanAccountId());
        if (!repayment.is(Repayment.Status.PENDING)) {
            throw ApiException.conflict("NOTHING_TO_RECONCILE", "Only payments with an unknown outcome need reconciliation.");
        }
        EmiInstallment inst = accounts.installmentById(repayment.getInstallmentId());
        apply(repayment, account, inst, gateway.query(repayment.getId()), actor.userId());
        audit.record(actor.userId(), "REPAYMENT_RECONCILED", "REPAYMENT", repaymentId, "SUCCESS",
                Map.of("status", repayment.getStatus()));
        return repayment;
    }

    @Transactional(readOnly = true)
    public Repayment getAccessible(Actor actor, String repaymentId) {
        Repayment r = repayments.findById(repaymentId).orElseThrow(() -> ApiException.notFound("Repayment"));
        boolean owner = actor.isCustomer() && r.getCustomerId().equals(actor.userId());
        if (!owner && !actor.hasAny(Role.FINANCE_OFFICER, Role.ADMIN)) {
            throw ApiException.notFound("Repayment");
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Optional<RepaymentAllocation> allocation(String repaymentId) {
        return allocations.findByRepaymentId(repaymentId);
    }

    @Transactional(readOnly = true)
    public List<Repayment> forAccount(String accountId) {
        return repayments.findByLoanAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public boolean hasPending(String accountId) {
        return repayments.existsByLoanAccountIdAndStatus(accountId, Repayment.Status.PENDING.name());
    }

    private void apply(Repayment repayment, LoanAccount account, EmiInstallment inst,
                       SimulatedPaymentGateway.Result result, String actorId) {
        Instant now = Instant.now(clock);
        switch (result.outcome()) {
            case SUCCESS -> {
                // Allocation order: fees, then interest, then principal. Full-installment policy.
                allocations.save(new RepaymentAllocation(repayment.getId(), inst.getId(), inst.getFeeDue(),
                        inst.getInterestDue(), inst.getPrincipalDue()));
                inst.markPaid(now);
                account.reducePrincipal(inst.getPrincipalDue());
                repayment.succeed(result.reference(), now);
                audit.record(actorId, "REPAYMENT_SUCCEEDED", "REPAYMENT", repayment.getId(), "SUCCESS",
                        Map.of("installmentNo", inst.getInstallmentNo(), "amount", repayment.getAmount()));
                notifications.notify(account.getCustomerId(), "REPAYMENT_RECEIVED", "Payment received",
                        "Installment " + inst.getInstallmentNo() + " was paid (simulated). Reference "
                                + result.reference() + ".");
            }
            case FAILED -> {
                repayment.fail(result.reference(), result.reason(), now);
                audit.record(actorId, "REPAYMENT_FAILED", "REPAYMENT", repayment.getId(), "FAILED",
                        Map.of("reason", result.reason()));
            }
            case UNKNOWN -> audit.record(actorId, "REPAYMENT_OUTCOME_UNKNOWN", "REPAYMENT", repayment.getId(),
                    "PENDING", Map.of());
        }
    }
}
