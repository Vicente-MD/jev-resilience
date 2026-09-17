package ai.jev.resilience.client;

import ai.jev.resilience.config.JevResilienceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast, offline check that {@link JevEvaluationService} calls TypeSafe Jev "rightly": correct
 * path, correct Authorization header, and a request body matching the documented
 * {@code POST /v1/systemone} Noul-question schema. Uses a local {@link MockWebServer} instead
 * of the real Jev API, so it runs in milliseconds with no API key required.
 */
class JevEvaluationServiceMockWebServerTest {

    private MockWebServer server;
    private JevEvaluationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();

        JevResilienceProperties properties = new JevResilienceProperties();
        properties.setApiKey("test-api-key");
        properties.setBaseUrl(server.url("/").toString());
        properties.setModel("jev-latest");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setQuestionKey("is_silent_failure");
        properties.setInstructions(
                "Does this payload represent a silent failure, a maintenance window, or an error state disguised as a success?");

        service = new JevEvaluationService(WebClient.builder(), properties);
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsWellFormedNoulRequestAndParsesScore() throws InterruptedException, IOException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "model": "jev-latest",
                          "answers": {
                            "is_silent_failure": { "type": "noul", "noul": 0.93 }
                          },
                          "usage": { "input_tokens": 42, "output_tokens": 7 }
                        }
                        """));

        Double score = service.evaluateSilentFailure("{\"status\":\"ok\",\"note\":\"system under maintenance\"}").block();
        assertThat(score).isEqualTo(0.93);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/systemone");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
        assertThat(request.getHeader("Content-Type")).contains("application/json");

        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(body.get("model").asText()).isEqualTo("jev-latest");
        assertThat(body.get("state").asText()).contains("system under maintenance");
        JsonNode question = body.get("questions").get("is_silent_failure");
        assertThat(question.get("type").asText()).isEqualTo("noul");
        assertThat(question.get("instructions").asText()).contains("silent failure");
    }

    @Test
    void failsOpenWhenServerErrors() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        Double score = service.evaluateSilentFailure("irrelevant payload").block();

        assertThat(score).isEqualTo(0.0);
    }
}
