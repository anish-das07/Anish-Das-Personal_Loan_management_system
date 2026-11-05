package com.example.plms.auth;

import java.util.Optional;

/** Application authorities (case study section 2 permission matrix). */
public enum Role {
    CUSTOMER(false),
    LOAN_OFFICER(true),
    CREDIT_ANALYST(true),
    DOCUMENT_VERIFIER(true),
    LOAN_APPROVER(true),
    FINANCE_OFFICER(true),
    ADMIN(true);

    private final boolean staff;

    Role(boolean staff) {
        this.staff = staff;
    }

    public boolean isStaff() {
        return staff;
    }

    public static Optional<Role> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (Role r : values()) {
            if (r.name().equals(value)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
