# Architecture

The runtime keeps inference separate from game authority:

1. Capture an immutable observation and candidate map on the owning game thread.
2. Retrieve only the companion's relevant memories.
3. Submit typed questions asynchronously through the selected provider.
4. Reject results whose session, observation, goal, deadline, life state, or control ownership changed.
5. Revalidate the selected candidate against current world state before an ordinary game action.
6. Verify the result from authoritative game state and update memory.

`JsonInferenceProvider` implements the Vercel v4 evaluation protocol and TypeSafe direct `systemone` dialect. `DecisionCoordinator` owns cancellation and freshness. `FairScheduler` keeps only the newest queued observation per actor. Domain classes keep permissions, mailbox cursors, memories, inventory revisions, grants, and moving chunk calculations independently testable.

The full artifact contains the server-authoritative companion type. The client-only artifact contains only shared runtime classes and the takeover entry point, avoiding custom registries on an unmodified remote server.
