package com.tradingcapture.trading.domain.model.converter;

import jakarta.persistence.AttributeConverter;

import java.util.function.Function;

/**
 * Base for enum-to-FIX-code JPA converters (ADR-0014: explicit string codes, never ordinals).
 * Unknown database codes fail loudly — a silent default would corrupt financial history.
 * Converters are applied explicitly per field with {@code @Convert}, never auto-applied.
 */
abstract class CodeEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Function<E, String> toCode;
    private final Function<String, E> fromCode;

    protected CodeEnumConverter(Function<E, String> toCode, Function<String, E> fromCode) {
        this.toCode = toCode;
        this.fromCode = fromCode;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : toCode.apply(attribute);
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : fromCode.apply(dbData);
    }
}
