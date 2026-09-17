package ai.jev.resilience.aop;

import ai.jev.resilience.annotation.SemanticCircuitBreaker;
import ai.jev.resilience.client.JevEvaluationService;
import ai.jev.resilience.exception.SemanticFailureException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Intercepts methods annotated with {@link SemanticCircuitBreaker} and screens their emitted
 * items for silent failures via {@link JevEvaluationService}, entirely on the reactive pipeline
 * (no blocking calls are ever made).
 *
 * <p>For {@code Mono<T>}-returning methods, the original Mono is transformed with
 * {@code flatMap}: each emitted item is stringified, sent to Jev, and either passed through
 * unchanged or replaced with a {@link Mono#error(Throwable)} carrying a
 * {@link SemanticFailureException} when the Jev confidence score exceeds the configured
 * threshold. {@code Flux<T>}-returning methods are supported the same way via
 * {@code flatMap} per-element.
 */
@Aspect
public class ReactiveSemanticCircuitBreakerAspect {

    private static final Logger log = LoggerFactory.getLogger(ReactiveSemanticCircuitBreakerAspect.class);

    private final JevEvaluationService jevEvaluationService;
    private final ObjectMapper objectMapper;

    public ReactiveSemanticCircuitBreakerAspect(JevEvaluationService jevEvaluationService, ObjectMapper objectMapper) {
        this.jevEvaluationService = jevEvaluationService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(semanticCircuitBreaker)")
    public Object around(ProceedingJoinPoint joinPoint, SemanticCircuitBreaker semanticCircuitBreaker) throws Throwable {
        Object result = joinPoint.proceed();
        double threshold = semanticCircuitBreaker.confidenceThreshold();

        if (result instanceof Mono<?> mono) {
            return mono.flatMap(item -> screen(item, threshold));
        }
        if (result instanceof Flux<?> flux) {
            return flux.flatMap(item -> screen(item, threshold));
        }

        log.warn(
                "@SemanticCircuitBreaker applied to a method not returning Mono/Flux ({}). "
                        + "Skipping semantic evaluation for: {}",
                result == null ? "null" : result.getClass(), joinPoint.getSignature());
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> screen(T item, double threshold) {
        String payload = stringify(item);
        return jevEvaluationService.evaluateSilentFailure(payload)
                .flatMap(score -> {
                    if (score > threshold) {
                        return Mono.<T>error(new SemanticFailureException(score, payload));
                    }
                    return Mono.just(item);
                });
    }

    private String stringify(Object item) {
        if (item instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload for Jev evaluation, using toString() fallback: {}", e.toString());
            return String.valueOf(item);
        }
    }
}
