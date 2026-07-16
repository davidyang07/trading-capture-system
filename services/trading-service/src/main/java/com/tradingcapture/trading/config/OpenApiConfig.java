package com.tradingcapture.trading.config;

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
    public OpenAPI tradingServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trading Service API")
                        .version("1.0.0")
                        .description("Orders, executions, append-only correction/cancellation chains, "
                                + "and the machine-facing execution allocation-state API."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
