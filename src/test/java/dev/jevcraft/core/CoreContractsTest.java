package dev.jevcraft.core;

import dev.jevcraft.inference.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CoreContractsTest {
    @Test void rosterEnforcesNamesCapAndExactlyOnceGrant() {
        CompanionRoster roster = new CompanionRoster(2);
        UUID owner = UUID.randomUUID();
        roster.register(new CompanionRoster.Companion(UUID.randomUUID(), "JevOne", owner, true));
        roster.register(new CompanionRoster.Companion(UUID.randomUUID(), "JevTwo", owner, true));
        assertThrows(IllegalStateException.class, () -> roster.register(new CompanionRoster.Companion(UUID.randomUUID(), "Third", owner, true)));
        assertEquals(CompanionRoster.GrantState.PENDING, roster.ensureGrant(owner));
        assertFalse(roster.deliverGrant(owner, false)); assertTrue(roster.deliverGrant(owner, true));
        assertFalse(roster.deliverGrant(owner, true)); assertTrue(roster.consumeGrant(owner)); assertFalse(roster.consumeGrant(owner));
        assertTrue(roster.findByName("jEvOnE").isPresent());
    }

    @Test void permissionsNeverConflateOwnershipAndOperatorTeleport() {
        UUID owner = UUID.randomUUID(), admin = UUID.randomUUID(), stranger = UUID.randomUUID();
        PermissionPolicy policy = new PermissionPolicy(owner, Set.of(admin), true);
        assertTrue(policy.canIssueGoal(owner, false)); assertTrue(policy.canIssueGoal(admin, false));
        assertFalse(policy.canIssueGoal(stranger, false)); assertFalse(policy.canTeleport(owner, false));
        assertTrue(policy.canTeleport(stranger, true));
    }

    @Test void chatFansOutIndependentlyAndNeverLoopsJevReplies() {
        ChatRouter router = new ChatRouter(10, Duration.ofSeconds(5));
        UUID sender = UUID.randomUUID(), one = UUID.randomUUID(), two = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ChatRouter.Message order = router.publish(sender, "mine stone", true, false, now);
        router.publish(one, "Working.", true, true, now);
        assertEquals(List.of(order), router.drain(one, now)); assertEquals(List.of(order), router.drain(two, now));
        assertTrue(router.executable(order, now.plusSeconds(4))); assertFalse(router.executable(order, now.plusSeconds(6)));
        assertTrue(router.drain(one, now).isEmpty());
    }

    @Test void inventoryRequiresNewRevisionAndVerifiedChange() {
        var before = new InventoryTransaction.Snapshot(7, 10, Map.of(0, new InventoryTransaction.Slot(0, "oak_log", 4)), null);
        InventoryTransaction tx = new InventoryTransaction(before, new InventoryTransaction.Operation(InventoryTransaction.Click.QUICK_MOVE, 0, 1, 4));
        tx.applied(); tx.acknowledged(7, 11);
        var after = new InventoryTransaction.Snapshot(7, 11, Map.of(1, new InventoryTransaction.Slot(1, "oak_log", 4)), null);
        assertTrue(tx.verify(after)); assertEquals(InventoryTransaction.State.VERIFIED, tx.state());
    }

    @Test void movingRegionAddsBeforeReleasingAndStaysBounded() {
        MovingChunkRegion region = new MovingChunkRegion(1);
        var first = region.moveTo(new MovingChunkRegion.Chunk(0, 0)); assertEquals(9, first.addFirst().size());
        region.commit(new MovingChunkRegion.Chunk(0, 0));
        var next = region.moveTo(new MovingChunkRegion.Chunk(1, 0));
        assertEquals(3, next.addFirst().size()); assertEquals(3, next.releaseAfterReady().size());
        region.commit(new MovingChunkRegion.Chunk(1, 0)); assertEquals(9, region.footprint());
    }

    @Test void parserExtractsQuantityClausesAndQuotedText() {
        GoalParser.Goal goal = new GoalParser().parse("collect 32 oak logs and then put them in the chest by my bed");
        assertEquals(GoalParser.Kind.DEPOSIT, goal.kind()); assertEquals(32, goal.quantity()); assertEquals(2, goal.clauses().size());
        assertEquals("the chest by my bed", goal.destination());
        assertEquals("hello", new GoalParser().parse("write \"hello\" on the sign").subject());
        assertEquals(4, new GoalParser().parse("collect four logs").quantity());
    }

    @Test void memoriesCannotLeakPrivateMessages() {
        MemoryStore store = new MemoryStore(); UUID jev = UUID.randomUUID(), person = UUID.randomUUID();
        store.remember(new MemoryStore.Memory(jev, "chat", "private base", Instant.now(), 1, person, true));
        store.remember(new MemoryStore.Memory(jev, "landmark", "public base", Instant.now(), .8, person, false));
        assertEquals(1, store.exportPublic(jev).size()); assertEquals("public base", store.exportPublic(jev).getFirst().value());
    }

    @Test void delayedDecisionCannotSurviveGoalReplacement() {
        CompletableFuture<InferenceResponse> deferred = new CompletableFuture<>();
        InferenceProvider provider = new InferenceProvider() {
            public String id() { return "fake"; }
            public CompletableFuture<InferenceResponse> evaluate(InferenceRequest request, CancellationToken token) { token.onCancel(() -> deferred.cancel(true)); return deferred; }
        };
        UUID actor = UUID.randomUUID(); Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        DecisionCoordinator coordinator = new DecisionCoordinator(provider, clock);
        coordinator.update(actor, new DecisionCoordinator.ActorVersion(1, 1, 1, true, true));
        InferenceRequest request = new InferenceRequest(UUID.randomUUID(), actor, 1, 1, 1, "state",
                Map.of("go", new Question.BooleanProbability("go?", Map.of())), clock.instant().plusSeconds(5));
        CompletableFuture<InferenceResponse> result = coordinator.submit(request);
        coordinator.update(actor, new DecisionCoordinator.ActorVersion(1, 1, 2, true, true));
        deferred.complete(new InferenceResponse(request.requestId(), "fake", "jev", Map.of("go", new Answer.BooleanProbability(1)), null, null, null, Duration.ZERO));
        assertThrows(CompletionException.class, result::join);
    }

    @Test void schedulerKeepsOnlyLatestAndCapsControllers() {
        FairScheduler scheduler = new FairScheduler(10, 2); Instant deadline = Instant.now().plusSeconds(2); UUID actor = UUID.randomUUID();
        scheduler.offer(new FairScheduler.Work(actor, 1, deadline, 1)); scheduler.offer(new FairScheduler.Work(actor, 2, deadline, 2));
        assertEquals(1, scheduler.queued()); assertEquals(2, scheduler.poll(Instant.now()).orElseThrow().urgency());
    }

    @Test void schedulerIsFairHonorsConcurrencyExpiryAndCancellation() {
        FairScheduler scheduler = new FairScheduler(3, 1); Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), expired = UUID.randomUUID();
        scheduler.offer(new FairScheduler.Work(first, 1, now.plusSeconds(5), 1));
        scheduler.offer(new FairScheduler.Work(second, 1, now.plusSeconds(5), 2));
        scheduler.offer(new FairScheduler.Work(expired, 9, now.minusSeconds(1), 3));
        assertEquals(first, scheduler.poll(now).orElseThrow().actor()); assertEquals(1, scheduler.running()); assertTrue(scheduler.poll(now).isEmpty());
        scheduler.complete(first); assertEquals(second, scheduler.poll(now).orElseThrow().actor());
        scheduler.cancel(second); assertEquals(0, scheduler.running()); assertEquals(0, scheduler.queued());
    }

    @Test void inferenceMetricsBoundLatencyAndEstimateInputCost() {
        InferenceMetrics metrics = new InferenceMetrics(); UUID request = UUID.randomUUID();
        metrics.requestStarted(); metrics.succeeded(new InferenceResponse(request, "gateway", "jev", Map.of(), new InferenceResponse.Usage(100, 0), Map.of(), List.of(), Duration.ofMillis(30)));
        metrics.requestStarted(); metrics.succeeded(new InferenceResponse(request, "gateway", "jev", Map.of(), new InferenceResponse.Usage(300, 0), Map.of(), List.of(), Duration.ofMillis(10)));
        metrics.requestStarted(); metrics.failed(new ProviderException(429, "limited", Duration.ofSeconds(1)));
        var snapshot = metrics.snapshot(.04);
        assertEquals(3, snapshot.requests()); assertEquals(2, snapshot.successes()); assertEquals(1, snapshot.failures()); assertEquals(1, snapshot.throttles());
        assertEquals(400, snapshot.inputTokens()); assertEquals(10, snapshot.p50LatencyMs()); assertEquals(30, snapshot.p95LatencyMs());
        assertEquals(.000016, snapshot.estimatedInputCostUsd(), .0000001);
    }
}
