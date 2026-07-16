package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.exception.InvalidRequestException;
import com.tradingcapture.trading.domain.service.command.AllocationStateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Machine payload (allocation-engine, scope {@code allocation:process}).
 * ALLOCATE requires explicit execution IDs; RELEASE may omit them to release every
 * execution bound to the allocation.
 */
public record AllocationStateUpdateRequest(
        @Schema(example = "ALLOC-2026-0001")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "allocationId must match ^[A-Za-z0-9._-]{1,64}$")
        String allocationId,

        @NotNull
        AllocationStateCommand.Action action,

        @Size(min = 1, max = 500)
        List<@NotBlank String> executionInternalIds) {

    public AllocationStateCommand toCommand() {
        if (action == AllocationStateCommand.Action.ALLOCATE
                && (executionInternalIds == null || executionInternalIds.isEmpty())) {
            throw new InvalidRequestException("executionInternalIds is required for ALLOCATE");
        }
        return new AllocationStateCommand(allocationId, action, executionInternalIds);
    }
}
