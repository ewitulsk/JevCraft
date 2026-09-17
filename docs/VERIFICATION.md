# Verification

## Automated

- `gradlew test`: provider schema fixtures, permissions, exactly-once grants, chat isolation, memory privacy, inventory revisions, moving chunk bounds, parser behavior, stale-decision cancellation, and scheduling.
- `gradlew runGameTestServer`: boots NeoForge 21.1.250 for Minecraft 1.21.1 and runs 12 in-world tests covering body attributes, role/chat persistence, native `/msg` coexistence and authority, world roster/cap/grants, progressive mining, placement, combat, food, chest-menu transfer, crafting-menu callbacks, and immediate stop.
- `gradlew build`: creates full and client-only artifacts.
- `scripts/Test-HiddenTakeover.ps1`: launches only project-owned processes and an unfocused hidden GLFW client against a loopback dedicated server. Its default deterministic typed decision fixture isolates client mechanics; `-LiveGateway` requires a live Vercel/Jev decision. Paired evidence requires normal multiplayer block removal, item pickup, goal completion, and released synthetic keys.
- `scripts/Test-Persistence.ps1`: creates state in a dedicated-server world, stops through the normal save path, boots the same world again, and mechanically verifies entity and world SavedData fields before another orderly stop.
- `scripts/Test-RemoteTicking.ps1`: boots a dedicated server with no players, places a Jev and furnace 40 chunks from spawn, and requires both the entity and furnace to keep ticking through a registered 5×5 ticking region.

## Opt-in live route

Set `AI_GATEWAY_API_KEY` only in the process environment and run `gradlew liveGatewaySmoke`. It sends one compact mixed-question request to `typesafe-ai/jev`. The key is not logged or stored.

Verified on September 17, 2026 after account credit was added: Vercel gateway returned all three answer types from `typesafe-ai/jev` (429 input tokens, 852 ms). A live in-world GameTest logged `JEV_LIVE_DECISION` at 705 ms, selected a visible log, and completed normal progressive mining. These establish the gateway route and one narrow gameplay decision, not general gameplay competence.

The hidden-client takeover fixture passed on September 17, 2026 with paired `HIDDEN_TAKEOVER_CLIENT_PASS` and `HIDDEN_TAKEOVER_SERVER_PASS` evidence. A fresh `-LiveGateway` run also passed after account credit was added; the client recorded Vercel/Jev decision latencies and the server independently observed authoritative block removal.

The direct TypeSafe route has deterministic fake-endpoint coverage but no live result because no TypeSafe credential was supplied.

## Not yet established

Unmodified-server connection acceptance, general recipe/menu operation, full vanilla action coverage, third-party chat-mod coexistence, portal ticket transfer, broader offline-owner task completion, the live direct-TypeSafe route, and one/five/ten-agent TPS/cost benchmarks remain open. See `CAPABILITY_MATRIX.md`.
