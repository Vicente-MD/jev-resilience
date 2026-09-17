package ai.jev.resilience.client;

import ai.jev.resilience.client.dto.JevRequest;
import ai.jev.resilience.client.dto.JevResponse;
import ai.jev.resilience.config.JevResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Non-blocking client for TypeSafe Jev's {@code /v1/systemone} endpoint.
 *
 * <p>Submits a single {@code Noul} question against the stringified response payload and
 * returns the resulting confidence score (0.0-1.0) that the payload represents a silent
 * failure. The entire call chain is reactive: no thread is ever blocked waiting on the
 * TypeSafe API.
 */
public class JevEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(JevEvaluationService.class);
    private static final String SYSTEM_ONE_PATH = "/v1/systemone";

    private final WebClient webClient;
    private final JevResilienceProperties properties;

    public JevEvaluationService(WebClient.Builder webClientBuilder, JevResilienceProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", properties.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Evaluates the given stringified payload with Jev's {@code Noul} primitive, asking whether
     * it represents a silent failure, maintenance window, or disguised error state.
     *
     * @param payload the stringified response body to evaluate.
     * @return a {@link Mono} emitting the Jev {@code noul} confidence score (0.0-1.0). On any
     *         transport error, timeout, or malformed response, the Mono emits {@code 0.0}
     *         (fail-open: never trips the circuit breaker due to Jev unavailability) after
     *         logging a warning.
     */
    public Mono<Double> evaluateSilentFailure(String payload) {
        JevRequest request = new JevRequest(
                payload,
                properties.getModel(),
                Map.of(properties.getQuestionKey(),
                        JevRequest.NoulQuestion.of(properties.getInstructions())));

        return webClient.post()
                .uri(SYSTEM_ONE_PATH)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JevResponse.class)
                .timeout(properties.getTimeout())
                .map(response -> extractScore(response, properties.getQuestionKey()))
                .doOnError(error -> log.warn(
                        "Jev evaluation failed, failing open (treating payload as valid): {}", error.toString()))
                .onErrorReturn(0.0);
    }

    private double extractScore(JevResponse response, String questionKey) {
        if (response == null || response.answers() == null) {
            return 0.0;
        }
        JevResponse.NoulAnswer answer = response.answers().get(questionKey);
        if (answer == null || answer.noul() == null) {
            return 0.0;
        }
        return answer.noul();
    }
}
