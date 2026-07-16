package com.tradingcapture.trading.api.dto;

import com.tradingcapture.trading.domain.service.command.ExecutionCommands;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cancellation payload; the whole body is optional. */
public record CancelExecutionRequest(
        @Schema(description = "External execution ID for the cancellation record; generated when omitted")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "execId must match ^[A-Za-z0-9._-]{1,64}$")
        String execId,

        @Size(max = 500)
        String text) {

    public static CancelExecutionRequest empty() {
        return new CancelExecutionRequest(null, null);
    }

    public ExecutionCommands.Cancel toCommand() {
        return new ExecutionCommands.Cancel(execId, text);
    }
}
