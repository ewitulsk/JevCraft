package dev.jevcraft.companion;

import dev.jevcraft.JevCraft;
import dev.jevcraft.inference.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Shared server inference host. HTTP callbacks only enqueue work back onto the server thread. */
public final class JevInferenceHost {
    private static final JevInferenceHost INSTANCE = new JevInferenceHost();
    private final InferenceProvider provider;
    private final Map<UUID, CancellationToken> inFlight = new ConcurrentHashMap<>();

    private JevInferenceHost() {
        String selected = value("JEVCRAFT_PROVIDER", "vercel_gateway");
        provider = switch (selected) {
            case "vercel_gateway" -> provider(value("AI_GATEWAY_API_KEY", ""), true);
            case "typesafe_direct" -> provider(value("TYPESAFE_API_KEY", ""), false);
            case "disabled" -> null;
            default -> throw new IllegalArgumentException("Unknown JEVCRAFT_PROVIDER: " + selected);
        };
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
    public void cancel(UUID actor) { CancellationToken previous = inFlight.remove(actor); if (previous != null) previous.cancel(); }

    public void chooseMiningTarget(JevCompanion companion, ServerLevel level, Map<String, BlockPos> candidates, String goal, long goalVersion) {
        if (provider == null || candidates.isEmpty() || inFlight.containsKey(companion.getUUID())) return;
        Map<String, Object> criteria = new LinkedHashMap<>();
        candidates.forEach((id, pos) -> criteria.put(id, "Mine the visible block at relative position " + relative(companion, pos)));
        criteria.put("wait", "Do not mine any candidate yet");
        InferenceRequest request = new InferenceRequest(UUID.randomUUID(), companion.getUUID(), 1, companion.tickCount, goalVersion,
                Map.of("goal", goal, "health", companion.getHealth(), "food", companion.foodLevel(), "inventory", companion.inventorySummary(),
                        "recentChat", companion.recentChat()),
                Map.of("action", new Question.Choice("Choose the best feasible next action for the goal. Select only one supplied candidate.", criteria)),
                Instant.now().plusSeconds(3));
        CancellationToken token = new CancellationToken(); inFlight.put(companion.getUUID(), token);
        provider.evaluate(request, token).whenComplete((response, failure) -> level.getServer().execute(() -> {
            inFlight.remove(companion.getUUID(), token);
            if (failure != null) { JevCraft.LOGGER.debug("Jev decision failed for {}: {}", companion.getUUID(), failure.toString()); return; }
            if (!companion.isAlive() || companion.isRemoved() || companion.goalVersion() != goalVersion || companion.level() != level) return;
            Answer answer = response.answers().get("action");
            if (answer instanceof Answer.Choice choice) {
                BlockPos selected = candidates.get(choice.choice());
                if (selected != null && companion.canSeeBlock(level, selected)) companion.actions().beginMine(selected, goalVersion);
            }
        }));
    }
    private static String relative(JevCompanion companion, BlockPos pos) {
        return (pos.getX() - companion.getBlockX()) + "," + (pos.getY() - companion.getBlockY()) + "," + (pos.getZ() - companion.getBlockZ());
    }
}
