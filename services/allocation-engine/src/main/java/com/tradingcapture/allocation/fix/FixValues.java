package com.tradingcapture.allocation.fix;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * FIX value formats and storage scales, shared by the validator and the persistence mapper
 * so "the validator accepted it" always implies "the mapper can convert it" — a single
 * definition, not two copies that could drift.
 */
public final class FixValues {

    /** FIX LocalMktDate: yyyyMMdd. */
    public static final DateTimeFormatter LOCAL_MKT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * FIX UTCTimestamp: yyyyMMdd-HH:mm:ss with an optional fractional part of 1–6 digits
     * (millisecond and microsecond precisions both occur in the wild). Built with
     * {@code appendFraction} because chained optional patterns ({@code [.SSS][.SSSSSS]})
     * do not backtrack and reject microsecond values.
     */
    public static final DateTimeFormatter UTC_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyyMMdd-HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, true)
            .optionalEnd()
            .toFormatter();

    /** DECIMAL(19,4) money/quantity storage scale (ADR-0014). */
    public static final int QTY_SCALE = 4;

    /** DECIMAL(19,8) price/rate storage scale (ADR-0014). */
    public static final int PX_SCALE = 8;

    /** DECIMAL precision (total digits) for both financial column families. */
    public static final int DECIMAL_PRECISION = 19;

    private FixValues() {
    }
}
