package com.tradingcapture.allocation.fix;

/**
 * Typed outcome of FIX parsing — explicit results instead of control-flow exceptions.
 * {@code Malformed} and {@code UnsupportedType} are permanent failures (never retried,
 * docs/architecture/event-flows.md).
 */
public sealed interface FixParseResult {

    /** FIX syntax could not be parsed; {@code summary} is safe (no payload dump). */
    record Malformed(String summary) implements FixParseResult {
    }

    /** Parsed, but MsgType (35) is not AllocationInstruction (J). */
    record UnsupportedType(String msgType) implements FixParseResult {
    }

    /** Structurally parsed; field-level validation has not run yet. */
    record Parsed(ExtractedAllocation extracted) implements FixParseResult {
    }
}
