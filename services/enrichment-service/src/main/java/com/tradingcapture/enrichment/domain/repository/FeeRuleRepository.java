package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.ActivationStatus;
import com.tradingcapture.enrichment.domain.model.FeeRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeeRuleRepository extends JpaRepository<FeeRule, String> {

    /** The active rule set, ordered deterministically; filtered by dimension in the service. */
    List<FeeRule> findByStatusOrderByFeeRuleId(ActivationStatus status);
}
