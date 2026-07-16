package com.tradingcapture.enrichment.domain.repository;

import com.tradingcapture.enrichment.domain.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {
}
