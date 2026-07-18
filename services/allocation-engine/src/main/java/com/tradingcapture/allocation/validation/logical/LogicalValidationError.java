package com.tradingcapture.allocation.validation.logical;

import java.util.Comparator;

/**
 * One logical business-validation failure: a stable {@link LogicalValidationCode}, the normalized
 * identifier it concerns (account id, exec id, or empty for allocation-level), and a safe message.
 * The natural order — stage, then code, then identifier — is the deterministic error ordering.
 */
public record LogicalValidationError(LogicalValidationCode code, String identifier, String message) {

    public static final Comparator<LogicalValidationError> DETERMINISTIC_ORDER =
            Comparator.comparingInt((LogicalValidationError e) -> e.code().stage().ordinal())
                    .thenComparing(e -> e.code().name())
                    .thenComparing(e -> e.identifier() == null ? "" : e.identifier());

    public LogicalValidationError {
        identifier = identifier == null ? "" : identifier;
    }

    public ValidationStage stage() {
        return code.stage();
    }
}
