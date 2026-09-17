package ai.jev.resilience.client;

import ai.jev.resilience.config.JevResilienceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in end-to-end check against the **real** TypeSafe Jev API. Only runs when
 * {@code TYPESAFE_API_KEY} is set in the environment, so it never runs in a normal build
 * without credentials.
 *
 * <pre>{@code
 * TYPESAFE_API_KEY=... mvn test -Dtest=JevEvaluationServiceRealApiIT
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class JevEvaluationServiceRealApiIT {

    private JevEvaluationService service() {
        JevResilienceProperties properties = new JevResilienceProperties();
        properties.setApiKey(System.getenv("TYPESAFE_API_KEY"));
        properties.setBaseUrl("https://api.typesafe.ai");
        properties.setModel("jev-latest");
        properties.setTimeout(Duration.ofSeconds(20));
        properties.setQuestionKey("is_silent_failure");
        properties.setInstructions(
                "Does this payload represent a silent failure, a maintenance window, or an error state disguised as a success?");
        return new JevEvaluationService(WebClient.builder(), properties);
    }

    @Test
    void detectsADisguisedMaintenanceNotice() {
        Double score = service()
                .evaluateSilentFailure("{\"status\":\"ok\",\"note\":\"system is currently under maintenance, please retry later\"}")
                .block();

        assertThat(score).isNotNull();
        System.out.println("[Jev real API] silent-failure payload -> noul=" + score);
        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void passesThroughAGenuinelySuccessfulPayload() {
        Double score = service()
                .evaluateSilentFailure("{\"status\":\"ok\",\"paymentId\":\"pay_123\",\"amount\":4200,\"currency\":\"USD\"}")
                .block();

        assertThat(score).isNotNull();
        System.out.println("[Jev real API] healthy payload -> noul=" + score);
        assertThat(score).isLessThan(0.5);
    }
}
