package dev.jevcraft.inference;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Opt-in paid transport benchmark. This measures typed Jev calls, not Minecraft task success. */
public final class LiveGatewayScaleBenchmark {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("AI_GATEWAY_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("AI_GATEWAY_API_KEY is required");
        double price = doubleValue(System.getenv("JEVCRAFT_INPUT_USD_PER_MILLION"), .04);
        int repeats = Math.max(1, Math.min(20, intValue(System.getenv("JEVCRAFT_BENCHMARK_REPEATS"), 3)));
        try (InferenceProvider provider = JsonInferenceProvider.vercel(key)) {
            for (int actors : new int[]{1, 5, 10}) run(provider, actors, repeats, price);
        }
    }
    private static void run(InferenceProvider provider, int actors, int repeats, double price) throws Exception {
        long wallStart = System.nanoTime(), inputTokens = 0, outputTokens = 0; List<Long> latencies = new ArrayList<>();
        String model = "";
        for (int repeat = 0; repeat < repeats; repeat++) {
            for (int offset = 0; offset < actors; offset += 2) {
                List<java.util.concurrent.CompletableFuture<InferenceResponse>> batch = new ArrayList<>();
                for (int index = offset; index < Math.min(actors, offset + 2); index++) batch.add(provider.evaluate(request(repeat * 100 + index), new CancellationToken()));
                for (var future : batch) {
                    InferenceResponse response = future.get(25, TimeUnit.SECONDS);
                    if (!response.answers().keySet().equals(Set.of("action", "risk", "goal_relevant"))) throw new IllegalStateException("typed answer IDs mismatch");
                    if (!(response.answers().get("action") instanceof Answer.Choice) || !(response.answers().get("risk") instanceof Answer.Score)
                            || !(response.answers().get("goal_relevant") instanceof Answer.BooleanProbability)) throw new IllegalStateException("typed answer classes mismatch");
                    model = response.model(); inputTokens += response.usage().inputTokens(); outputTokens += response.usage().outputTokens(); latencies.add(response.latency().toMillis());
                }
            }
        }
        Collections.sort(latencies); long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        long p50 = percentile(latencies, .5), p95 = percentile(latencies, .95); double estimated = inputTokens * price / 1_000_000d;
        System.out.printf(Locale.ROOT, "LIVE_GATEWAY_SCALE_PASS actors=%d repeats=%d concurrency=2 requests=%d model=%s inputTokens=%d outputTokens=%d wallMs=%d p50Ms=%d p95Ms=%d estimatedInputCostUsd=%.8f%n",
                actors, repeats, actors * repeats, model, inputTokens, outputTokens, wallMs, p50, p95, estimated);
    }
    private static InferenceRequest request(int index) {
        return new InferenceRequest(UUID.randomUUID(), UUID.nameUUIDFromBytes(("jevcraft:scale:" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)), 1, index, 1,
                Map.of("actor", index, "health", 20, "food", 18, "visible", List.of("oak_log", "stone"), "goal", "collect four logs"),
                Map.of("action", new Question.Choice("Choose the best feasible next action.", Map.of("mine_log", "Mine the visible oak log", "mine_stone", "Mine visible stone", "wait", "Do nothing")),
                        "risk", new Question.Score("Rate immediate danger.", List.of("safe", "caution", "danger")),
                        "goal_relevant", new Question.BooleanProbability("Is mining the visible oak log relevant to the stated goal?", Map.of())), Instant.now().plusSeconds(20));
    }
    private static long percentile(List<Long> sorted, double p) { return sorted.get(Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * p) - 1))); }
    private static double doubleValue(String value, double fallback) { try { return value == null || value.isBlank() ? fallback : Double.parseDouble(value); } catch (NumberFormatException ignored) { return fallback; } }
    private static int intValue(String value, int fallback) { try { return value == null || value.isBlank() ? fallback : Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; } }
}
