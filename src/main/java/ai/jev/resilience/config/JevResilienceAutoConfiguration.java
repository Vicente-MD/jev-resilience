package ai.jev.resilience.config;

import ai.jev.resilience.aop.ReactiveSemanticCircuitBreakerAspect;
import ai.jev.resilience.client.JevEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for {@code jev-resilience}. Registers the
 * reactive {@link WebClient.Builder}, {@link JevEvaluationService}, and
 * {@link ReactiveSemanticCircuitBreakerAspect} beans required to power
 * {@code @SemanticCircuitBreaker}.
 *
 * <p>Activated only when {@code typesafe.jev.api-key} is configured, so the starter is a
 * no-op when not set up.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(JevResilienceProperties.class)
@ConditionalOnProperty(prefix = "typesafe.jev", name = "api-key")
public class JevResilienceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder jevWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    public JevEvaluationService jevEvaluationService(WebClient.Builder jevWebClientBuilder,
                                                      JevResilienceProperties properties) {
        return new JevEvaluationService(jevWebClientBuilder, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveSemanticCircuitBreakerAspect reactiveSemanticCircuitBreakerAspect(
            JevEvaluationService jevEvaluationService, ObjectMapper objectMapper) {
        return new ReactiveSemanticCircuitBreakerAspect(jevEvaluationService, objectMapper);
    }
}
