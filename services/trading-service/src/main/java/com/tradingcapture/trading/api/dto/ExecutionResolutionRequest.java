package com.tradingcapture.trading.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Machine batch resolution request (allocation-engine, scope {@code allocation:process}): the
 * external FIX ExecIDs (tag 17) from an allocation instruction's {@code NoExecs} group.
 */
public record ExecutionResolutionRequest(
        @Schema(example = "[\"EXEC-2026-0001\", \"EXEC-2026-0002\"]")
        @NotEmpty @Size(max = 500)
        List<@NotBlank @Size(max = 64) String> execIds) {
}
