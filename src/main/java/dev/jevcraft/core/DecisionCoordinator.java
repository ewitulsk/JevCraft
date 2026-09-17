package dev.jevcraft.core;

import dev.jevcraft.inference.*;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public final class DecisionCoordinator implements AutoCloseable {
    public record ActorVersion(long session, long observation, long goal, boolean alive, boolean controlOwned) {}
    private record Pending(CancellationToken token, InferenceRequest request) {}
    private final InferenceProvider provider;
    private final Clock clock;
    private final ConcurrentMap<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ActorVersion> versions = new ConcurrentHashMap<>();

    public DecisionCoordinator(InferenceProvider provider, Clock clock) { this.provider = provider; this.clock = clock; }
    public void update(UUID actor, ActorVersion version) { versions.put(actor, version); }
    public CompletableFuture<InferenceResponse> submit(InferenceRequest request) {
        CancellationToken token = new CancellationToken();
        Pending next = new Pending(token, request);
        Pending previous = pending.put(request.actorId(), next);
        if (previous != null) previous.token.cancel();
        return provider.evaluate(request, token).thenApply(response -> {
            ActorVersion current = versions.get(request.actorId());
            if (token.isCancelled() || clock.instant().isAfter(request.deadline()) || current == null || !current.alive() || !current.controlOwned()
                    || current.session() != request.sessionVersion() || current.observation() != request.observationVersion()
                    || current.goal() != request.goalVersion()) throw new CancellationException("stale decision");
            return response;
        }).whenComplete((ignored, failure) -> pending.remove(request.actorId(), next));
    }
    public void stop(UUID actor) { Pending p = pending.remove(actor); if (p != null) p.token.cancel(); }
    public void switchProviderSession() { pending.values().forEach(p -> p.token.cancel()); pending.clear(); }
    public int inFlight() { return pending.size(); }
    @Override public void close() { pending.values().forEach(p -> p.token.cancel()); provider.close(); }
}
