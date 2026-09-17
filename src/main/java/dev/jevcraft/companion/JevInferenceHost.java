package dev.jevcraft.companion;

import dev.jevcraft.JevCraft;
import dev.jevcraft.core.FairScheduler;
import dev.jevcraft.core.InferenceMetrics;
import dev.jevcraft.inference.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Shared server inference host. HTTP callbacks only enqueue work back onto the server thread. */
public final class JevInferenceHost {
    private record QueuedDecision(JevCompanion companion, ServerLevel level, Map<String, BlockPos> candidates, long goalVersion, InferenceRequest request) {}
    private static final JevInferenceHost INSTANCE = new JevInferenceHost();
    private final InferenceProvider provider;
    private final Map<UUID, CancellationToken> inFlight = new ConcurrentHashMap<>();
    private final Map<UUID, QueuedDecision> queued = new LinkedHashMap<>();
    private final FairScheduler scheduler;
    private final InferenceMetrics metrics = new InferenceMetrics();
    private final long maxInputTokens;
    private final double inputUsdPerMillion;
    private long sequence;
    private Instant backoffUntil = Instant.MIN;

    private JevInferenceHost() {
        String selected = value("JEVCRAFT_PROVIDER", "vercel_gateway");
        provider = switch (selected) {
            case "vercel_gateway" -> provider(value("AI_GATEWAY_API_KEY", ""), true);
            case "typesafe_direct" -> provider(value("TYPESAFE_API_KEY", ""), false);
            case "disabled" -> null;
            default -> throw new IllegalArgumentException("Unknown JEVCRAFT_PROVIDER: " + selected);
        };
        scheduler = new FairScheduler(10, integer("JEVCRAFT_MAX_CONCURRENT", 2, 1, 10));
        maxInputTokens = longValue("JEVCRAFT_MAX_INPUT_TOKENS", Long.MAX_VALUE, 1, Long.MAX_VALUE);
        inputUsdPerMillion = doubleValue("JEVCRAFT_INPUT_USD_PER_MILLION", .04, 0, 1_000);
    }
    private static InferenceProvider provider(String key, boolean gateway) {
        return key.isBlank() ? null : gateway ? JsonInferenceProvider.vercel(key) : JsonInferenceProvider.typesafe(key);
    }
    private static String value(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) return property;
        String environment = System.getenv(name);
        return environment == null || environment.isBlank() ? fallback : environment;
    }
    public static JevInferenceHost instance() { return INSTANCE; }
    public boolean enabled() { return provider != null; }
    public synchronized void cancel(UUID actor) {
        scheduler.cancel(actor); queued.remove(actor); CancellationToken previous = inFlight.remove(actor); if (previous != null) previous.cancel();
    }
    public InferenceMetrics.Snapshot metrics() { return metrics.snapshot(inputUsdPerMillion); }
    public synchronized int queuedCount() { return scheduler.queued(); }
    public synchronized int runningCount() { return scheduler.running(); }

    public synchronized void chooseMiningTarget(JevCompanion companion, ServerLevel level, Map<String, BlockPos> candidates, String goal, long goalVersion) {
        if (provider == null || candidates.isEmpty() || inFlight.containsKey(companion.getUUID())) return;
        Map<String, Object> criteria = new LinkedHashMap<>();
        candidates.forEach((id, pos) -> criteria.put(id, "Mine the visible block at relative position " + relative(companion, pos)));
        criteria.put("wait", "Do not mine any candidate yet");
        InferenceRequest request = new InferenceRequest(UUID.randomUUID(), companion.getUUID(), 1, companion.tickCount, goalVersion,
                Map.of("goal", goal, "health", companion.getHealth(), "food", companion.foodLevel(), "inventory", companion.inventorySummary(),
                        "recentChat", companion.recentChat()),
                Map.of("action", new Question.Choice("Choose the best feasible next action for the goal. Select only one supplied candidate.", criteria)),
                Instant.now().plusSeconds(3));
        queued.put(companion.getUUID(), new QueuedDecision(companion, level, Map.copyOf(candidates), goalVersion, request));
        scheduler.offer(new FairScheduler.Work(companion.getUUID(), 1, request.deadline(), ++sequence));
    }
    public synchronized void tick(MinecraftServer server) {
        if (provider == null) return;
        Instant now = Instant.now();
        queued.entrySet().removeIf(entry -> { if (entry.getValue().request().deadline().isBefore(now)) { scheduler.cancel(entry.getKey()); return true; } return false; });
        if (now.isBefore(backoffUntil) || metrics().inputTokens() >= maxInputTokens) return;
        Optional<FairScheduler.Work> work;
        while ((work = scheduler.poll(now)).isPresent()) {
            QueuedDecision decision = queued.remove(work.get().actor());
            if (decision == null) { scheduler.complete(work.get().actor()); continue; }
            dispatch(decision);
            if (metrics().inputTokens() >= maxInputTokens) break;
        }
    }
    private void dispatch(QueuedDecision decision) {
        JevCompanion companion = decision.companion(); ServerLevel level = decision.level(); InferenceRequest request = decision.request(); long goalVersion = decision.goalVersion();
        Map<String, BlockPos> candidates = decision.candidates(); CancellationToken token = new CancellationToken(); inFlight.put(companion.getUUID(), token); metrics.requestStarted();
        provider.evaluate(request, token).whenComplete((response, failure) -> level.getServer().execute(() -> {
            synchronized (this) { inFlight.remove(companion.getUUID(), token); scheduler.complete(companion.getUUID()); }
            if (failure != null) {
                metrics.failed(failure); Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                if (cause instanceof ProviderException providerFailure && providerFailure.retryable()) {
                    Duration delay = providerFailure.retryAfter() == null ? Duration.ofSeconds(2) : providerFailure.retryAfter();
                    synchronized (this) { Instant candidate = Instant.now().plus(delay); if (candidate.isAfter(backoffUntil)) backoffUntil = candidate; }
                }
                JevCraft.LOGGER.debug("Jev decision failed for {}: {}", companion.getUUID(), cause.toString()); return;
            }
            metrics.succeeded(response);
            if (!companion.isAlive() || companion.isRemoved() || companion.goalVersion() != goalVersion || companion.level() != level) return;
            JevCraft.LOGGER.info("JEV_LIVE_DECISION provider={} model={} latencyMs={}", response.provider(), response.model(), response.latency().toMillis());
            Answer answer = response.answers().get("action");
            if (answer instanceof Answer.Choice choice) {
                BlockPos selected = candidates.get(choice.choice());
                if (selected != null && companion.canSeeBlock(level, selected)) companion.actions().beginMine(selected, goalVersion);
            }
        }));
    }
    private static int integer(String name, int fallback, int min, int max) { try { return Math.max(min, Math.min(max, Integer.parseInt(value(name, Integer.toString(fallback))))); } catch (NumberFormatException ignored) { return fallback; } }
    private static long longValue(String name, long fallback, long min, long max) { try { return Math.max(min, Math.min(max, Long.parseLong(value(name, Long.toString(fallback))))); } catch (NumberFormatException ignored) { return fallback; } }
    private static double doubleValue(String name, double fallback, double min, double max) { try { return Math.max(min, Math.min(max, Double.parseDouble(value(name, Double.toString(fallback))))); } catch (NumberFormatException ignored) { return fallback; } }
    private static String relative(JevCompanion companion, BlockPos pos) {
        return (pos.getX() - companion.getBlockX()) + "," + (pos.getY() - companion.getBlockY()) + "," + (pos.getZ() - companion.getBlockZ());
    }
}
