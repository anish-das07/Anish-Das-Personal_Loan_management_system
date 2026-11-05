package com.example.plms.disbursement;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulated bank transfer adapter. No money moves. A "timeout" means the outcome is unknown to the
 * caller, while the simulated bank actually accepted it, so reconciliation (query by reference)
 * finds the transfer instead of a retry duplicating it.
 */
@Component
public class SimulatedBankAdapter {

    public enum Outcome { SUCCESS, FAILED, UNKNOWN }

    public enum Mode { SUCCESS, FAIL, TIMEOUT }

    public record Result(Outcome outcome, String transactionRef, String reason) {
    }

    private final Map<String, Result> ledger = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Result transfer(String reference, BigDecimal amount, Mode mode) {
        Result existing = ledger.get(reference);
        if (existing != null) {
            return existing; // the bank is idempotent on our reference too
        }
        Mode m = mode == null ? Mode.SUCCESS : mode;
        return switch (m) {
            case SUCCESS -> record(reference, new Result(Outcome.SUCCESS, nextRef(), null));
            case FAIL -> record(reference, new Result(Outcome.FAILED, null, "BENEFICIARY_ACCOUNT_INVALID"));
            case TIMEOUT -> {
                record(reference, new Result(Outcome.SUCCESS, nextRef(), null));
                yield new Result(Outcome.UNKNOWN, null, "TIMEOUT");
            }
        };
    }

    public Result query(String reference) {
        Result r = ledger.get(reference);
        return r == null ? new Result(Outcome.UNKNOWN, null, "NOT_FOUND_YET") : r;
    }

    private Result record(String reference, Result result) {
        ledger.put(reference, result);
        return result;
    }

    private String nextRef() {
        // Sequence for readability plus a random suffix so references stay unique across restarts.
        return "SIM-DISB-" + Year.now() + "-" + String.format("%04d", sequence.incrementAndGet()) + "-"
                + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
