package com.tradingcapture.allocation.fix;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import quickfix.ConfigError;
import quickfix.DataDictionary;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the FIX 4.4 data dictionary once. The dictionary drives group parsing, required-field
 * knowledge, and field-name resolution for the validation taxonomy.
 */
@Configuration
public class FixDictionaryConfig {

    @Bean
    public DataDictionary fixDataDictionary() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fix44.xml")) {
            if (in == null) {
                throw new IllegalStateException("fix44.xml missing from the allocation-engine classpath");
            }
            return new DataDictionary(in);
        } catch (IOException | ConfigError e) {
            throw new IllegalStateException("Failed to load FIX 4.4 data dictionary", e);
        }
    }
}
