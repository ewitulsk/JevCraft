package dev.jevcraft.inference;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InferenceResponse(
        UUID requestId,
        String provider,
        String model,
        Map<String, Answer> answers,
        Usage usage,
        Map<String, Object> metadata,
        List<String> warnings,
        Duration latency) {
    public InferenceResponse {
        answers = Map.copyOf(answers);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        usage = usage == null ? new Usage(0, 0) : usage;
    }
    public record Usage(long inputTokens, long outputTokens) {}
}
