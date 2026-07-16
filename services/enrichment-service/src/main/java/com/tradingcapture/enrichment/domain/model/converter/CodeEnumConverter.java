package com.tradingcapture.enrichment.domain.model.converter;

import com.tradingcapture.enrichment.domain.model.CodeEnum;
import jakarta.persistence.AttributeConverter;

/**
 * Base for enum-to-string JPA converters (ADR-0014: explicit string codes, never ordinals).
 * Unknown database codes fail loudly. Converters are applied explicitly per field with
 * {@code @Convert}, never auto-applied.
 */
abstract class CodeEnumConverter<E extends Enum<E> & CodeEnum> implements AttributeConverter<E, String> {

    private final E[] values;

    protected CodeEnumConverter(Class<E> type) {
        this.values = type.getEnumConstants();
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (E value : values) {
            if (value.code().equals(dbData)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown code '" + dbData + "' for " + values[0].getDeclaringClass().getSimpleName());
    }
}
