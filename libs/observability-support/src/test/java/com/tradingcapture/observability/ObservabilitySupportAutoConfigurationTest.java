package com.tradingcapture.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the auto-configuration contributes the expected beans and that the common-tag
 * customizer stamps every meter with a stable {@code application} label.
 */
class ObservabilitySupportAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilitySupportAutoConfiguration.class));

    @Test
    void registersCorrelationFilterAndTaskDecoratorInAServletApp() {
        webRunner.run(context -> {
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context).hasSingleBean(MdcTaskDecorator.class);
            assertThat(context).hasSingleBean(MeterRegistryCustomizer.class);
        });
    }

    @Test
    void correlationFilterBeanIsNotRegisteredOutsideAServletApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObservabilitySupportAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void applicationCommonTagStampsMetersWithTheConfiguredApplicationName() {
        webRunner.withPropertyValues("spring.application.name=trading-service").run(context -> {
            @SuppressWarnings("unchecked")
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    context.getBean(MeterRegistryCustomizer.class);

            MeterRegistry registry = new SimpleMeterRegistry();
            customizer.customize(registry);
            Counter counter = registry.counter("tcs.test.counter");

            assertThat(counter.getId().getTag("application")).isEqualTo("trading-service");
        });
    }

    @Test
    void applicationTagFallsBackToUnknownWhenNameIsUnset() {
        webRunner.run(context -> {
            @SuppressWarnings("unchecked")
            MeterRegistryCustomizer<MeterRegistry> customizer =
                    context.getBean(MeterRegistryCustomizer.class);

            MeterRegistry registry = new SimpleMeterRegistry();
            customizer.customize(registry);

            assertThat(registry.counter("x").getId().getTag("application")).isEqualTo("unknown");
        });
    }
}
