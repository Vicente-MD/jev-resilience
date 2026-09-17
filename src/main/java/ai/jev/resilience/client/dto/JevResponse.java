package ai.jev.resilience.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Response body from {@code POST /v1/systemone}.
 *
 * <pre>{@code
 * {
 *   "model": "jev-latest",
 *   "answers": {
 *     "is_silent_failure": { "type": "noul", "noul": 0.97 }
 *   },
 *   "usage": { "input_tokens": 312, "output_tokens": 48 }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JevResponse(String model, Map<String, NoulAnswer> answers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoulAnswer(String type, Double noul) {
    }
}
