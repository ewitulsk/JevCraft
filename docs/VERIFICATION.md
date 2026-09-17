# Verification

## Automated

- `gradlew test`: provider schema fixtures, permissions, exactly-once grants, chat isolation, memory privacy, inventory revisions, moving chunk bounds, parser behavior, stale-decision cancellation, and scheduling.
- `gradlew runGameTestServer`: boots NeoForge 21.1.250 for Minecraft 1.21.1 and exercises registered companion creation, body attributes, persistence, inventory, goals, and immediate stop.
- `gradlew build`: creates full and client-only artifacts.

## Opt-in live route

Set `AI_GATEWAY_API_KEY` only in the process environment and run `gradlew liveGatewaySmoke`. It sends one compact mixed-question request to `typesafe-ai/jev`. The key is not logged or stored.

Verified on September 17, 2026: Vercel gateway returned all three answer types from `typesafe-ai/jev` (429 input tokens, 998 ms observed latency). This establishes transport and schema compatibility, not gameplay competence.

The direct TypeSafe route has deterministic fake-endpoint coverage but no live result because no TypeSafe credential was supplied.

## Not yet established

Hidden-client input replay, unmodified-server connection behavior, real mining/container/crafting, full vanilla action coverage, persistent restart and portal tickets, offline-owner operation, and one/five/ten-agent TPS/cost benchmarks remain open. See `CAPABILITY_MATRIX.md`.
