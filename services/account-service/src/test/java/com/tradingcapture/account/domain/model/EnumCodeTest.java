package com.tradingcapture.account.domain.model;

import com.tradingcapture.account.domain.model.converter.AccountStatusConverter;
import com.tradingcapture.account.domain.model.converter.CashDirectionConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Enum converters round-trip their string codes and fail loudly on unknown codes (ADR-0014). */
class EnumCodeTest {

    @Test
    void statusConverterRoundTrips() {
        AccountStatusConverter converter = new AccountStatusConverter();
        for (AccountStatus status : AccountStatus.values()) {
            assertThat(converter.convertToDatabaseColumn(status)).isEqualTo(status.name());
            assertThat(converter.convertToEntityAttribute(status.name())).isEqualTo(status);
        }
    }

    @Test
    void directionConverterRoundTrips() {
        CashDirectionConverter converter = new CashDirectionConverter();
        assertThat(converter.convertToDatabaseColumn(CashDirection.CREDIT)).isEqualTo("CREDIT");
        assertThat(converter.convertToEntityAttribute("DEBIT")).isEqualTo(CashDirection.DEBIT);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatThrownBy(() -> new AccountStatusConverter().convertToEntityAttribute("BOGUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void nullsPassThrough() {
        AccountStatusConverter converter = new AccountStatusConverter();
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
