package com.example.plms.account;

import com.example.plms.audit.AuditService;
import com.example.plms.auth.Role;
import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
import com.example.plms.common.PageResponse;
import com.example.plms.core.money.EmiCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LoanAccountService {

    private static final Logger log = LoggerFactory.getLogger(LoanAccountService.class);
    private static final Role[] STAFF_READERS = {Role.FINANCE_OFFICER, Role.LOAN_OFFICER, Role.LOAN_APPROVER,
            Role.ADMIN};

    private final LoanAccountRepository accounts;
    private final EmiInstallmentRepository installments;
    private final AuditService audit;
    private final Clock clock;
    private final EmiCalculator calculator = new EmiCalculator();

    public LoanAccountService(LoanAccountRepository accounts, EmiInstallmentRepository installments,
                              AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.installments = installments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LoanAccount openPending(String applicationId, String customerId, String productName, BigDecimal principal,
                                   BigDecimal annualRate, int months, BigDecimal feePercent, String actorId) {
        if (accounts.findByApplicationId(applicationId).isPresent()) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "A loan account already exists for this application.");
        }
        LoanAccount account = accounts.save(new LoanAccount(applicationId, customerId, productName, principal,
                annualRate, months, feePercent, Instant.now(clock)));
        audit.record(actorId, "LOAN_ACCOUNT_OPENED", "LOAN_ACCOUNT", account.getId(), "SUCCESS",
                Map.of("applicationId", applicationId, "principal", principal));
        return account;
    }

    @Transactional
    public LoanAccount lockForUpdate(String accountId) {
        return accounts.findForUpdate(accountId).orElseThrow(() -> ApiException.notFound("Loan account"));
    }

    @Transactional(readOnly = true)
    public LoanAccount get(String accountId) {
        return accounts.findById(accountId).orElseThrow(() -> ApiException.notFound("Loan account"));
    }

    /** Owner or permitted staff; other customers get 404. */
    @Transactional
    public LoanAccount getAccessible(Actor actor, String accountId) {
        LoanAccount account = accounts.findById(accountId).orElse(null);
        if (account != null && (actor.hasAny(STAFF_READERS)
                || (actor.isCustomer() && account.getCustomerId().equals(actor.userId())))) {
            return account;
        }
        if (account != null) {
            audit.recordIndependently(actor.userId(), "ACCESS_DENIED", "LOAN_ACCOUNT", accountId, "DENIED", Map.of());
        }
        throw ApiException.notFound("Loan account");
    }

    @Transactional(readOnly = true)
    public List<LoanAccount> mine(String customerId) {
        return accounts.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<LoanAccount> awaitingDisbursement() {
        return accounts.findByStatusOrderByCreatedAtAsc(LoanAccount.Status.PENDING_DISBURSEMENT.name());
    }

    /** Called only after a confirmed successful disbursement. Generates the amortisation schedule. */
    @Transactional
    public List<EmiInstallment> activateAndSchedule(LoanAccount account, String actorId) {
        Instant now = Instant.now(clock);
        LocalDate firstDue = LocalDate.now(clock).plusMonths(1);
        List<EmiCalculator.ScheduleLine> lines = calculator.schedule(account.getPrincipal(), account.getAnnualRate(),
                account.getTenureMonths(), firstDue);
        account.activate(calculator.calculate(account.getPrincipal(), account.getAnnualRate(),
                account.getTenureMonths()), now);
        List<EmiInstallment> saved = lines.stream().map(l -> installments.save(new EmiInstallment(account.getId(),
                l.installmentNo(), l.dueDate(), l.openingBalance(), l.principal(), l.interest(), l.total(),
                l.closingBalance()))).toList();
        audit.record(actorId, "EMI_SCHEDULE_GENERATED", "LOAN_ACCOUNT", account.getId(), "SUCCESS",
                Map.of("installments", saved.size(), "emi", account.getMonthlyEmi()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<EmiInstallment> schedule(String accountId) {
        return installments.findByLoanAccountIdOrderByInstallmentNoAsc(accountId);
    }

    @Transactional(readOnly = true)
    public long unpaidInstallments(String accountId) {
        return installments.countByLoanAccountIdAndStatusNot(accountId, EmiInstallment.Status.PAID.name());
    }

    @Transactional
    public EmiInstallment installment(String accountId, int installmentNo) {
        return installments.findByLoanAccountIdAndInstallmentNo(accountId, installmentNo)
                .orElseThrow(() -> ApiException.notFound("Installment"));
    }

    @Transactional
    public EmiInstallment installmentById(String installmentId) {
        return installments.findById(installmentId).orElseThrow(() -> ApiException.notFound("Installment"));
    }

    @Transactional(readOnly = true)
    public Integer earliestUnpaidNo(String accountId) {
        return installments.findFirstByLoanAccountIdAndStatusNotOrderByInstallmentNoAsc(accountId,
                EmiInstallment.Status.PAID.name()).map(EmiInstallment::getInstallmentNo).orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountDtos.OverdueRow> overdue(LocalDate asOf, Integer page, Integer size) {
        LocalDate day = asOf == null ? LocalDate.now(clock) : asOf;
        var pageable = PageResponse.pageable(page, size, null, Set.of("dueDate"), "dueDate");
        Page<EmiInstallment> result = installments.findOverdue(day, pageable);
        return PageResponse.of(result, i -> AccountDtos.OverdueRow.from(i, day));
    }

    /** Daily overdue marking (01:15 in the configured zone). */
    @Scheduled(cron = "${plms.overdue-cron:0 15 1 * * *}", zone = "${plms.time-zone}")
    @Transactional
    public void markOverdue() {
        List<EmiInstallment> due = installments.findDueBefore(LocalDate.now(clock));
        due.forEach(EmiInstallment::markOverdue);
        if (!due.isEmpty()) {
            log.info("Marked {} installments overdue", due.size());
        }
    }
}
