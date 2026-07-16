package com.tradingcapture.enrichment.domain.exception;

/** A requested reference-data record does not exist (HTTP 404). */
public class ResourceNotFoundException extends EnrichmentDomainException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException instrument(String id) {
        return new ResourceNotFoundException(ErrorCode.INSTRUMENT_NOT_FOUND, "Instrument not found: " + id);
    }

    public static ResourceNotFoundException exchange(String mic) {
        return new ResourceNotFoundException(ErrorCode.EXCHANGE_NOT_FOUND, "Exchange not found: " + mic);
    }

    public static ResourceNotFoundException currency(String code) {
        return new ResourceNotFoundException(ErrorCode.CURRENCY_NOT_FOUND, "Currency not found: " + code);
    }

    public static ResourceNotFoundException settlementOption(String code) {
        return new ResourceNotFoundException(ErrorCode.SETTLEMENT_OPTION_NOT_FOUND, "Settlement option not found: " + code);
    }

    public static ResourceNotFoundException feeRule(String id) {
        return new ResourceNotFoundException(ErrorCode.FEE_RULE_NOT_FOUND, "Fee rule not found: " + id);
    }

    public static ResourceNotFoundException identifier(String source, String value) {
        return new ResourceNotFoundException(ErrorCode.IDENTIFIER_NOT_FOUND,
                "No instrument for identifier " + source + ":" + value);
    }
}
