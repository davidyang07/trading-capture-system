package com.tradingcapture.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
public class ObservabilitySupportAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Must run before security and MVC so every log line of the request carries the ID.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * Phase 11: tag every meter with a stable {@code application} label so Prometheus/Grafana
     * can slice metrics per service. Derived from {@code spring.application.name}.
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    public MeterRegistryCustomizer<MeterRegistry> applicationCommonTag(
            @Value("${spring.application.name:unknown}") String applicationName) {
        return registry -> registry.config().meterFilter(MeterFilter.commonTags(
                io.micrometer.core.instrument.Tags.of("application", applicationName)));
    }
}
