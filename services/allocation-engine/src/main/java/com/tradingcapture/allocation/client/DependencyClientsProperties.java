package com.tradingcapture.allocation.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Externalized configuration for the account/enrichment/trading M2M clients ({@code tcs.clients.*}):
 * base URL, connect/read timeouts, the OAuth2 client registration id, and the Resilience4j instance
 * name — no hostnames, tokens, or secrets hardcoded. Timeouts default to conservative values so a
 * slow dependency cannot stall orchestration indefinitely.
 */
@ConfigurationProperties("tcs.clients")
public class DependencyClientsProperties {

    private Client account = new Client();
    private Client enrichment = new Client();
    private Client trading = new Client();

    public Client getAccount() {
        return account;
    }

    public void setAccount(Client account) {
        this.account = account;
    }

    public Client getEnrichment() {
        return enrichment;
    }

    public void setEnrichment(Client enrichment) {
        this.enrichment = enrichment;
    }

    public Client getTrading() {
        return trading;
    }

    public void setTrading(Client trading) {
        this.trading = trading;
    }

    public static class Client {
        /** Base URL of the dependency (e.g. http://account-service:8082). */
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        /** Spring Security OAuth2 client registration id used to obtain the machine token. */
        private String registrationId = "allocation-engine";
        /** Resilience4j retry/circuit-breaker instance name (defaults to the dependency name). */
        private String resilienceInstance;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public String getRegistrationId() {
            return registrationId;
        }

        public void setRegistrationId(String registrationId) {
            this.registrationId = registrationId;
        }

        public String getResilienceInstance() {
            return resilienceInstance;
        }

        public void setResilienceInstance(String resilienceInstance) {
            this.resilienceInstance = resilienceInstance;
        }
    }
}
