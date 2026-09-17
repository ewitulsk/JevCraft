package dev.jevcraft.core;

import dev.jevcraft.inference.InferenceResponse;
import dev.jevcraft.inference.ProviderException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

/** Bounded, credential-free host metrics suitable for status display and evidence manifests. */
public final class InferenceMetrics {
    public record Snapshot(long requests, long successes, long failures, long throttles, long inputTokens, long outputTokens,
                           long p50LatencyMs, long p95LatencyMs, double estimatedInputCostUsd) {}
    private static final int LATENCY_WINDOW = 512;
    private final ArrayDeque<Long> latencies = new ArrayDeque<>();
    private long requests, successes, failures, throttles, inputTokens, outputTokens;

    public synchronized void requestStarted() { requests++; }
    public synchronized void succeeded(InferenceResponse response) {
        successes++; inputTokens += response.usage().inputTokens(); outputTokens += response.usage().outputTokens();
        latencies.addLast(response.latency().toMillis()); while (latencies.size() > LATENCY_WINDOW) latencies.removeFirst();
    }
    public synchronized void failed(Throwable failure) {
        failures++; Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ProviderException provider && provider.status() == 429) throttles++;
    }
    public synchronized Snapshot snapshot(double inputUsdPerMillion) {
        ArrayList<Long> sorted = new ArrayList<>(latencies); Collections.sort(sorted);
        return new Snapshot(requests, successes, failures, throttles, inputTokens, outputTokens, percentile(sorted, .50), percentile(sorted, .95), inputTokens * inputUsdPerMillion / 1_000_000d);
    }
    private static long percentile(ArrayList<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1)); return sorted.get(index);
    }
}
