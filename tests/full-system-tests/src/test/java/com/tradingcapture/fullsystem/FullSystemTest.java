package com.tradingcapture.fullsystem;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Base for the cross-service scenarios. They run ONLY when the full stack is up
 * (FULL_STACK=1, set by scripts/run-scenarios.ps1) — exactly like the Playwright *.full.spec.ts
 * tier. Without it they self-skip (never disabled to hide a defect). The module ships no
 * production code, so it is excluded from the JaCoCo gate (see its POM).
 */
@EnabledIfEnvironmentVariable(named = "FULL_STACK", matches = "1")
abstract class FullSystemTest {
    protected final FullSystemHarness stack = new FullSystemHarness();
}
