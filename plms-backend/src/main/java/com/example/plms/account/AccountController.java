package com.example.plms.account;

import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
import com.example.plms.core.money.EmiCalculator;
import com.example.plms.product.LoanProduct;
import com.example.plms.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
public class AccountController {

    private final LoanAccountService accounts;
    private final ProductService products;
    private final Clock clock;
    private final EmiCalculator calculator = new EmiCalculator();

    public AccountController(LoanAccountService accounts, ProductService products, Clock clock) {
        this.accounts = accounts;
        this.products = products;
        this.clock = clock;
    }

    @GetMapping("/api/v1/loan-accounts/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AccountDtos.AccountResponse> mine(Authentication auth) {
        return accounts.mine(Actor.from(auth).userId()).stream().map(AccountDtos.AccountResponse::from).toList();
    }

    @GetMapping("/api/v1/loan-accounts/{id}")
    public AccountDtos.AccountResponse get(@PathVariable String id, Authentication auth) {
        return AccountDtos.AccountResponse.from(accounts.getAccessible(Actor.from(auth), id));
    }

    @GetMapping("/api/v1/loan-accounts/{id}/schedule")
    public AccountDtos.ScheduleResponse schedule(@PathVariable String id, Authentication auth) {
        LoanAccount account = accounts.getAccessible(Actor.from(auth), id);
        List<EmiInstallment> rows = accounts.schedule(id);
        BigDecimal totalInterest = rows.stream().map(EmiInstallment::getInterestDue)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal totalPayment = rows.stream().map(EmiInstallment::getTotalDue)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new AccountDtos.ScheduleResponse(account.getId(), account.getAnnualRate(), account.getTenureMonths(),
                account.getMonthlyEmi(), totalInterest, totalPayment, rows.isEmpty() ? "NOT_GENERATED" : "GENERATED",
                AccountDtos.ROUNDING_POLICY, rows.stream().map(AccountDtos.InstallmentView::from).toList());
    }

    /** Server-authoritative EMI estimate. With a productId, the product's rate and limits apply. */
    @PostMapping("/api/v1/emi/quote")
    public AccountDtos.QuoteResponse quote(@Valid @RequestBody AccountDtos.QuoteRequest request) {
        BigDecimal rate = request.annualRate();
        if (request.productId() != null && !request.productId().isBlank()) {
            LoanProduct product = products.getAvailable(request.productId());
            if (request.principal().compareTo(product.getMinAmount()) < 0
                    || request.principal().compareTo(product.getMaxAmount()) > 0
                    || request.months() < product.getMinMonths() || request.months() > product.getMaxMonths()) {
                throw ApiException.badRequest("OUTSIDE_PRODUCT_LIMITS",
                        "Amount or tenure is outside the product limits.");
            }
            rate = product.getAnnualRate();
        }
        EmiCalculator.Quote q = calculator.quote(request.principal(), rate, request.months(),
                LocalDate.now(clock).plusMonths(1));
        return AccountDtos.QuoteResponse.from(request.principal(), rate, request.months(), q);
    }
}
