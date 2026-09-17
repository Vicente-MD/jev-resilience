package ai.jev.resilience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the TypeSafe Jev client, bound from the {@code typesafe.jev.*}
 * namespace (see {@code application.yml}).
 */
@ConfigurationProperties(prefix = "typesafe.jev")
public class JevResilienceProperties {

    /** API key issued by the TypeSafe console (https://console.typesafe.ai/settings/keys). */
    private String apiKey;

    /** Base URL of the TypeSafe Jev API. Defaults to the public production endpoint. */
    private String baseUrl = "https://api.typesafe.ai";

    /** Jev model identifier to evaluate against. */
    private String model = "jev-latest";

    /** Non-blocking HTTP call timeout applied to the WebClient request. */
    private Duration timeout = Duration.ofMillis(750);

    /** The Noul question key used in the request/response payload. */
    private String questionKey = "is_silent_failure";

    /** The Noul question instructions sent to Jev. */
    private String instructions =
            "Does this payload represent a silent failure, a maintenance window, or an error state disguised as a success?";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getQuestionKey() {
        return questionKey;
    }

    public void setQuestionKey(String questionKey) {
        this.questionKey = questionKey;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
}
