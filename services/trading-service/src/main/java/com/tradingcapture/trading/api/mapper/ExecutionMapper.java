package com.tradingcapture.trading.api.mapper;

import com.tradingcapture.trading.api.dto.ExecutionDetailResponse;
import com.tradingcapture.trading.api.dto.ExecutionResponse;
import com.tradingcapture.trading.api.dto.MiscFeeResponse;
import com.tradingcapture.trading.domain.model.Execution;
import com.tradingcapture.trading.domain.model.ExecutionMiscFee;
import com.tradingcapture.trading.domain.service.ExecutionQueryService.ExecutionWithChain;
import org.springframework.stereotype.Component;

import java.util.List;

/** Hand-written entity-to-DTO mapping for executions and their chains. */
@Component
public class ExecutionMapper {

    public ExecutionResponse toResponse(Execution execution) {
        return new ExecutionResponse(execution.getInternalId(),
                execution.getExecId(),
                execution.getOrder().getOrderId(),
                execution.getExecType(),
                execution.getExecRefId(),
                execution.getRootExecutionId(),
                execution.getOrdStatus(),
                execution.getSide(),
                execution.getLastQty(),
                execution.getLastPx(),
                execution.getCumQty(),
                execution.getLeavesQty(),
                execution.getAvgPx(),
                execution.getCurrency(),
                execution.getTradeDate(),
                execution.getTransactTime(),
                execution.getSettleType(),
                execution.getSettleDate(),
                execution.getText(),
                execution.getAllocationState(),
                execution.getAllocationId(),
                execution.getCreatedAt(),
                execution.getVersion());
    }

    public ExecutionDetailResponse toDetailResponse(ExecutionWithChain detail) {
        Execution execution = detail.execution();
        Execution tip = detail.activeTip();
        List<ExecutionDetailResponse.ChainLink> chain = detail.chain().stream()
                .map(row -> new ExecutionDetailResponse.ChainLink(row.getInternalId(),
                        row.getExecId(),
                        row.getExecType(),
                        row.getLastQty(),
                        row.getLastPx(),
                        row.getTransactTime(),
                        row.getInternalId().equals(tip.getInternalId())))
                .toList();
        List<MiscFeeResponse> fees = execution.getMiscFees().stream()
                .map(this::toFeeResponse)
                .toList();
        return new ExecutionDetailResponse(toResponse(execution),
                fees,
                execution.getRootExecutionId(),
                !tip.isCancellation(),
                chain);
    }

    private MiscFeeResponse toFeeResponse(ExecutionMiscFee fee) {
        return new MiscFeeResponse(fee.getFeeType(), fee.getAmount(), fee.getCurrency(), fee.getBasis());
    }
}
