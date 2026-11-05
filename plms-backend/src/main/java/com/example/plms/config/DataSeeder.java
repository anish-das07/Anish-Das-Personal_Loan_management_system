package com.example.plms.config;

import com.example.plms.auth.Role;
import com.example.plms.auth.UserAccount;
import com.example.plms.auth.UserAccountRepository;
import com.example.plms.common.PlmsProperties;
import com.example.plms.eligibility.EligibilityDtos;
import com.example.plms.eligibility.EligibilityPolicyEntity;
import com.example.plms.eligibility.EligibilityPolicyRepository;
import com.example.plms.product.LoanProduct;
import com.example.plms.product.LoanProductRepository;
import com.example.plms.product.ProductDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Synthetic demo data: staff users, one eligibility policy version and three products.
 * Enabled with plms.seed.enabled=true (local H2, local MySQL and tests). Never enable in production;
 * the staff password comes from PLMS_SEED_STAFF_PASSWORD.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String POLICY_VERSION = "PERSONAL-2026.1";

    public static final Map<String, Role> STAFF = Map.of(
            "admin@plms.local", Role.ADMIN,
            "officer@plms.local", Role.LOAN_OFFICER,
            "analyst@plms.local", Role.CREDIT_ANALYST,
            "verifier@plms.local", Role.DOCUMENT_VERIFIER,
            "approver@plms.local", Role.LOAN_APPROVER,
            "approver2@plms.local", Role.LOAN_APPROVER,
            "finance@plms.local", Role.FINANCE_OFFICER);

    private final PlmsProperties properties;
    private final UserAccountRepository users;
    private final EligibilityPolicyRepository policies;
    private final LoanProductRepository products;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DataSeeder(PlmsProperties properties, UserAccountRepository users, EligibilityPolicyRepository policies,
                      LoanProductRepository products, PasswordEncoder encoder, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.policies = policies;
        this.products = products;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.seed() == null || !properties.seed().enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        String password = properties.seed().staffPassword();
        if (password == null || password.length() < 8) {
            throw new IllegalStateException("plms.seed.staff-password must be set (8+ chars) when seeding is enabled");
        }
        String hash = encoder.encode(password);
        STAFF.forEach((email, role) -> {
            if (!users.existsByEmail(email)) {
                String name = role.name().charAt(0) + role.name().substring(1).toLowerCase().replace('_', ' ');
                users.save(new UserAccount(email, hash, name + (email.startsWith("approver2") ? " 2" : ""),
                        EnumSet.of(role), now));
            }
        });

        if (!policies.existsById(POLICY_VERSION)) {
            policies.save(new EligibilityPolicyEntity(new EligibilityDtos.PolicyRequest(POLICY_VERSION,
                    new BigDecimal("25000.00"), new BigDecimal("0.4000"), new BigDecimal("0.5500"), 6,
                    Set.of("SALARIED", "SELF_EMPLOYED"), 21, 60, true, 600, 650, true), now));
        }

        LocalDate from = LocalDate.of(2026, 1, 1);
        seedProduct("PERSONAL-FLEX", "Personal Flex", "Flexible personal loan for planned expenses.",
                "Home improvement", "50000.00", "1500000.00", 6, 60, "12.00", "1.00", from);
        seedProduct("PERSONAL-PLUS", "Personal Plus", "Larger personal loan with longer tenure.",
                "Major purchase", "100000.00", "2500000.00", 12, 84, "11.50", "1.50", from);
        seedProduct("DEBT-CONSOLIDATION", "Debt Consolidation", "Combine existing debts into one EMI.",
                "Debt consolidation", "50000.00", "1000000.00", 12, 48, "13.00", "1.00", from);
        log.info("Synthetic seed data ready (staff users, policy {}, products)", POLICY_VERSION);
    }

    private void seedProduct(String code, String name, String description, String purpose, String min, String max,
                             int minMonths, int maxMonths, String rate, String fee, LocalDate from) {
        if (products.findByCode(code).isPresent()) {
            return;
        }
        LoanProduct p = new LoanProduct(code);
        p.apply(new ProductDtos.ProductRequest(code, name, description, purpose, new BigDecimal(min),
                new BigDecimal(max), minMonths, maxMonths, new BigDecimal(rate), new BigDecimal(fee), from, null, true,
                POLICY_VERSION));
        products.save(p);
    }
}
