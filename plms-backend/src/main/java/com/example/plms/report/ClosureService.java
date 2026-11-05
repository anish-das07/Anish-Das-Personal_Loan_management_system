package com.example.plms.report;

import com.example.plms.account.LoanAccount;
import com.example.plms.account.LoanAccountService;
import com.example.plms.application.ApplicationService;
import com.example.plms.audit.AuditService;
import com.example.plms.common.ApiError;
import com.example.plms.common.ApiException;
import com.example.plms.core.workflow.ApplicationStatus;
import com.example.plms.disbursement.DisbursementService;
import com.example.plms.notification.NotificationService;
import com.example.plms.repayment.RepaymentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Closure requires no outstanding dues and no unresolved or unknown transactions. */
@Service
public class ClosureService {

    private final LoanAccountService accounts;
    private final RepaymentService repayments;
    private final DisbursementService disbursements;
    private final ApplicationService applications;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ClosureService(LoanAccountService accounts, RepaymentService repayments,
                          DisbursementService disbursements, ApplicationService applications,
                          NotificationService notifications, AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.repayments = repayments;
        this.disbursements = disbursements;
        this.applications = applications;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<String> blockers(LoanAccount account) {
        List<String> blockers = new ArrayList<>();
        if (!account.is(LoanAccount.Status.ACTIVE)) {
            blockers.add("Account is " + account.getStatus() + ", not ACTIVE");
            return blockers;
        }
        long unpaid = accounts.unpaidInstallments(account.getId());
        if (unpaid > 0) {
            blockers.add(unpaid + " installment(s) are unpaid");
        }
        if (account.getOutstandingPrincipal().signum() != 0) {
            blockers.add("Outstanding principal is " + account.getOutstandingPrincipal());
        }
        if (repayments.hasPending(account.getId())) {
            blockers.add("A payment with an unknown outcome needs reconciliation");
        }
        if (disbursements.hasUnresolved(account.getId())) {
            blockers.add("A disbursement with an unknown outcome needs reconciliation");
        }
        return blockers;
    }

    @Transactional
    public LoanAccount close(String financeId, String accountId) {
        LoanAccount account = accounts.lockForUpdate(accountId);
        List<String> blockers = blockers(account);
        if (!blockers.isEmpty()) {
            audit.recordIndependently(financeId, "LOAN_CLOSURE_BLOCKED", "LOAN_ACCOUNT", accountId, "DENIED",
                    Map.of("blockers", String.join("; ", blockers)));
            throw new ApiException(409, "CLOSURE_BLOCKED", "The loan cannot be closed yet.",
                    blockers.stream().map(b -> new ApiError.FieldError("closure", b)).toList());
        }
        account.close(Instant.now(clock));
        applications.transition(account.getApplicationId(), null, ApplicationStatus.CLOSED, financeId,
                "LOAN_CLOSED", "Your loan is fully repaid and closed");
        notifications.notify(account.getCustomerId(), "LOAN_CLOSED", "Loan closed",
                "Your loan is fully repaid and has been closed. Thank you.");
        audit.record(financeId, "LOAN_CLOSED", "LOAN_ACCOUNT", accountId, "SUCCESS", Map.of());
        return account;
    }
}
