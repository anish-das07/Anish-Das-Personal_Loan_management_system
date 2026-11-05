package com.example.plms.repayment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated payment gateway. Payment method selects the simulated result:
 * SIMULATED (success), SIMULATED_DECLINE (failure), SIMULATED_TIMEOUT (unknown to caller, captured by
 * the gateway, so reconciliation confirms it rather than charging twice).
 */
@Component
public class SimulatedPaymentGateway {

    public enum Outcome { SUCCESS, FAILED, UNKNOWN }

    public record Result(Outcome outcome, String reference, String reason) {
    }

    private final Map<String, Result> captured = new ConcurrentHashMap<>();

    public Result charge(String paymentId, BigDecimal amount, String method) {
        Result existing = captured.get(paymentId);
        if (existing != null) {
            return existing;
        }
        String ref = "SIM-PAY-" + Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return switch (method) {
            case "SIMULATED_DECLINE" -> store(paymentId, new Result(Outcome.FAILED, ref, "DECLINED_BY_SIMULATOR"));
            case "SIMULATED_TIMEOUT" -> {
                store(paymentId, new Result(Outcome.SUCCESS, ref, null));
                yield new Result(Outcome.UNKNOWN, null, "TIMEOUT");
            }
            default -> store(paymentId, new Result(Outcome.SUCCESS, ref, null));
        };
    }

    public Result query(String paymentId) {
        Result r = captured.get(paymentId);
        return r == null ? new Result(Outcome.UNKNOWN, null, "NOT_FOUND_YET") : r;
    }

    private Result store(String paymentId, Result r) {
        captured.put(paymentId, r);
        return r;
    }
}
