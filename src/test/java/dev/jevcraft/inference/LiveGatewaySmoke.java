package dev.jevcraft.inference;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class LiveGatewaySmoke {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("AI_GATEWAY_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("AI_GATEWAY_API_KEY is required");
        InferenceProvider provider = JsonInferenceProvider.vercel(key);
        InferenceRequest request = new InferenceRequest(UUID.randomUUID(), UUID.randomUUID(), 1, 1, 1,
                Map.of("actor", Map.of("health", 20, "food", 18), "visible", List.of("oak_log", "stone"), "goal", "collect four logs"),
                Map.of(
                        "action", new Question.Choice("Choose the best feasible next action.", Map.of("mine_log", "Mine the visible oak log", "mine_stone", "Mine visible stone", "wait", "Do nothing")),
                        "risk", new Question.Score("Rate immediate danger.", List.of("safe", "caution", "danger")),
                        "goal_relevant", new Question.BooleanProbability("Is mining the visible oak log relevant to the stated goal?", Map.of())),
                Instant.now().plusSeconds(20));
        InferenceResponse response = provider.evaluate(request, new CancellationToken()).get(25, TimeUnit.SECONDS);
        if (!response.answers().keySet().equals(request.questions().keySet())) throw new IllegalStateException("mixed answer IDs mismatch");
        System.out.println("LIVE_GATEWAY_PASS provider=" + response.provider() + " model=" + response.model()
                + " questions=" + response.answers().size() + " inputTokens=" + response.usage().inputTokens()
                + " latencyMs=" + response.latency().toMillis());
    }
}
