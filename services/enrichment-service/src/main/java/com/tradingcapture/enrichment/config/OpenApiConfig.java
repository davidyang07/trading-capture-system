package com.tradingcapture.enrichment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc metadata. The hand-written contract in {@code contracts/openapi/} remains the
 * contract of record; the live document served here is reconciled against it at phase gates.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI enrichmentServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enrichment Service API")
                        .version("1.0.0")
                        .description("Instruments and alternate identifiers, exchanges, currencies, settlement "
                                + "options, and fee rules, with Redis-cached lookups and a machine-facing batch "
                                + "instrument-resolution API."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
