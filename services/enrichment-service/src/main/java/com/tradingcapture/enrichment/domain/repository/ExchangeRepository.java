package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRepository extends JpaRepository<Exchange, String> {
}
