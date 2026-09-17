package dev.jevcraft.inference;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InferenceRequest(
        UUID requestId,
        UUID actorId,
        long sessionVersion,
        long observationVersion,
        long goalVersion,
        Object state,
        Map<String, Question> questions,
        Instant deadline) {
    public InferenceRequest {
        requestId = requestId == null ? UUID.randomUUID() : requestId;
        if (actorId == null || state == null || deadline == null) throw new IllegalArgumentException("actor, state and deadline required");
        questions = Map.copyOf(questions);
        if (questions.isEmpty()) throw new IllegalArgumentException("questions required");
    }
}
