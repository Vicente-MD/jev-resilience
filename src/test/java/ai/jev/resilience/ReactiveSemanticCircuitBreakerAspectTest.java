package ai.jev.resilience;

import ai.jev.resilience.annotation.SemanticCircuitBreaker;
import ai.jev.resilience.aop.ReactiveSemanticCircuitBreakerAspect;
import ai.jev.resilience.client.JevEvaluationService;
import ai.jev.resilience.exception.SemanticFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ReactiveSemanticCircuitBreakerAspectTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passesThroughWhenBelowThreshold() throws Throwable {
        JevEvaluationService jev = Mockito.mock(JevEvaluationService.class);
        when(jev.evaluateSilentFailure(anyString())).thenReturn(Mono.just(0.10));

        ReactiveSemanticCircuitBreakerAspect aspect =
                new ReactiveSemanticCircuitBreakerAspect(jev, objectMapper);

        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(Mono.just("all good"));
        Signature signature = Mockito.mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);

        SemanticCircuitBreaker annotation = annotation(0.85);

        Object result = aspect.around(joinPoint, annotation);

        @SuppressWarnings("unchecked")
        Mono<Object> resultMono = (Mono<Object>) result;
        StepVerifier.create(resultMono)
                .expectNext("all good")
                .verifyComplete();
    }

    @Test
    void tripsCircuitWhenAboveThreshold() throws Throwable {
        JevEvaluationService jev = Mockito.mock(JevEvaluationService.class);
        when(jev.evaluateSilentFailure(anyString())).thenReturn(Mono.just(0.97));

        ReactiveSemanticCircuitBreakerAspect aspect =
                new ReactiveSemanticCircuitBreakerAspect(jev, objectMapper);

        ProceedingJoinPoint joinPoint = Mockito.mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(Mono.just("{\"status\":\"ok but actually down for maintenance\"}"));
        Signature signature = Mockito.mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);

        SemanticCircuitBreaker annotation = annotation(0.85);

        Object result = aspect.around(joinPoint, annotation);

        StepVerifier.create((Mono<?>) result)
                .expectError(SemanticFailureException.class)
                .verify();
    }

    private SemanticCircuitBreaker annotation(double threshold) {
        return new SemanticCircuitBreaker() {
            @Override
            public double confidenceThreshold() {
                return threshold;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return SemanticCircuitBreaker.class;
            }
        };
    }
}
