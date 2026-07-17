package com.tradingcapture.allocation.domain.repository;

import com.tradingcapture.allocation.domain.model.ValidationError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidationErrorRepository extends JpaRepository<ValidationError, Long> {

    List<ValidationError> findByRawMessageIdOrderByIdAsc(Long rawMessageId);
}
