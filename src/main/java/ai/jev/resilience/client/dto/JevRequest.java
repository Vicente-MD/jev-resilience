package ai.jev.resilience.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Request body for {@code POST /v1/systemone}.
 *
 * @param state      the stringified state to evaluate (here: the API response payload).
 * @param model      the Jev model identifier (e.g. {@code jev-latest}).
 * @param questions  a map of question-key to {@link NoulQuestion}; all questions in the map
 *                   are evaluated in parallel by Jev in a single call.
 */
public record JevRequest(
        String state,
        String model,
        @JsonProperty("questions") Map<String, NoulQuestion> questions) {

    public record NoulQuestion(String type, String instructions) {
        public static NoulQuestion of(String instructions) {
            return new NoulQuestion("noul", instructions);
        }
    }
}
