package ai.jev.resilience.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a WebFlux controller/client method (returning {@code Mono<T>} or {@code Flux<T>})
 * whose successful ({@code HTTP 200}) payload should be screened for "silent failures" by
 * TypeSafe Jev's {@code Noul} primitive before being emitted downstream.
 *
 * <p>The screening is performed non-blockingly by {@link ai.jev.resilience.aop.ReactiveSemanticCircuitBreakerAspect},
 * which calls {@link ai.jev.resilience.client.JevEvaluationService} inside a reactive
 * {@code flatMap} stage. If Jev's confidence that the payload is a disguised failure exceeds
 * {@link #confidenceThreshold()}, the stream terminates with a
 * {@link ai.jev.resilience.exception.SemanticFailureException} instead of emitting the item.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SemanticCircuitBreaker {

    /**
     * Minimum Jev {@code noul} confidence score (0.0-1.0) that a payload represents a silent
     * failure, maintenance window, or disguised error state, above which the reactive stream
     * is short-circuited with a {@link ai.jev.resilience.exception.SemanticFailureException}.
     */
    double confidenceThreshold() default 0.85;
}
