package ai.jev.resilience.exception;

/**
 * Raised when TypeSafe Jev determines, with a confidence above the configured
 * {@code confidenceThreshold}, that an otherwise-successful reactive response actually
 * represents a silent failure (e.g. an error, stack trace, or maintenance notice disguised
 * as an HTTP 200 payload).
 */
public class SemanticFailureException extends RuntimeException {

    private final double confidenceScore;
    private final String payloadSnippet;

    public SemanticFailureException(double confidenceScore, String payloadSnippet) {
        super(String.format(
                "Semantic Circuit Breaker tripped: Jev noul confidence=%.3f indicates a silent failure. Payload (truncated): %s",
                confidenceScore, truncate(payloadSnippet)));
        this.confidenceScore = confidenceScore;
        this.payloadSnippet = payloadSnippet;
    }

    private static String truncate(String payload) {
        if (payload == null) {
            return "<null>";
        }
        return payload.length() > 300 ? payload.substring(0, 300) + "..." : payload;
    }

    /** The Jev {@code noul} confidence score (0.0-1.0) that triggered this exception. */
    public double getConfidenceScore() {
        return confidenceScore;
    }

    /** The raw stringified payload that was evaluated by Jev. */
    public String getPayloadSnippet() {
        return payloadSnippet;
    }
}
