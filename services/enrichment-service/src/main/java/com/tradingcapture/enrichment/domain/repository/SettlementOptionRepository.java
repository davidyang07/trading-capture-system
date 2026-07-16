package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.SettlementOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementOptionRepository extends JpaRepository<SettlementOption, String> {
}
