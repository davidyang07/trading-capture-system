package com.tradingcapture.account;

import com.tradingcapture.testsupport.MySqlContainerSupport;
import com.tradingcapture.testsupport.TestTokenIssuer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot smoke test against a real MySQL (singleton container) with Flyway applied: proves the
 * context wires (JPA, security, observability, springdoc) and that the public actuator health
 * endpoint answers with a correlation header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.locations=classpath:db/migration",
        "tcs.security.jwt-mode=local-keys",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + TestTokenIssuer.ISSUER
})
@Tag("integration")
class AccountServiceApplicationTests extends MySqlContainerSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void responsesCarryCorrelationIdHeader() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
    }
}
