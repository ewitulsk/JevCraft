# Verification

## Automated

- `gradlew test`: provider schema fixtures, permissions, exactly-once grants, chat isolation, memory privacy, inventory revisions, moving chunk bounds, parser behavior, stale-decision cancellation, fair concurrency/expiry/cancellation scheduling, and bounded usage/cost metrics.
- `gradlew runGameTestServer`: boots NeoForge 21.1.250 for Minecraft 1.21.1 and runs 27 in-world tests covering body/equipment/XP/spawn persistence, role/chat persistence, exactly-once death drops and same-UUID respawn, separately authorized teleport, cross-dimension portal ticket handoff and cleanup, minecart mount/dismount, animal feeding/breeding and lead ownership, two-companion inventory isolation, bucket place/pickup, hoe durability, crop planting, lever state, owned-arrow ranged combat, mid-draw cancellation, native `/msg` coexistence and authority, world roster/cap/grants, progressive mining, placement, melee combat, food, chest-menu transfer, single-ingredient and shaped 3×3 crafting-menu callbacks, furnace/smoker/blast-furnace loading, normal processing, result collection, and immediate stop.
- `gradlew build`: creates full and client-only artifacts.
- `scripts/Test-HiddenTakeover.ps1`: launches only project-owned processes and an unfocused hidden GLFW client against a loopback dedicated server. Its default deterministic typed decision fixture isolates client mechanics; `-LiveGateway` requires live Vercel/Jev decisions. Paired evidence requires normal multiplayer block removal, item pickup, normal item-use placement, server-observed world changes, goal completion, and released synthetic keys.
- `scripts/Test-VanillaServerCompatibility.ps1`: verifies Mojang's published SHA-1 for the official 1.21.1 server, asserts the client jar contains no full-mod, companion, mixin, or test classes, loads that exact jar plus a non-distributed hidden-window helper, and requires independent client and vanilla-server login evidence.
- `scripts/Test-Persistence.ps1`: creates state and a pending respawn in a dedicated-server world, stops through the normal save path, boots the same world again, and mechanically verifies entity state, world SavedData, ticking tickets, and completion of the queued same-UUID respawn before another orderly stop.
- `scripts/Test-RemoteTicking.ps1`: boots a dedicated server with no players, places a Jev and furnace 40 chunks from spawn, and requires both the entity and furnace to keep ticking through a registered 5×5 ticking region.
- `scripts/Test-AgentScaling.ps1`: runs fresh no-player dedicated-server worlds at 0/1/5/10 distant Jevs, asserts exact bounded ticket counts and per-entity ticks, and records both 20 TPS wall pacing and Minecraft's internal processing time. See `BENCHMARKS.md`.

## Opt-in live route

Set `AI_GATEWAY_API_KEY` only in the process environment and run `gradlew liveGatewaySmoke`. It sends one compact mixed-question request to `typesafe-ai/jev`. `gradlew liveGatewayScale` runs three paid repeats at 1/5/10 actors with concurrency two and reports tokens, latency, wall time, and input-cost estimates. The key is not logged or stored.

Verified on September 17, 2026 after account credit was added: Vercel gateway returned all three answer types from `typesafe-ai/jev` (429 input tokens, 852 ms). A live in-world GameTest passed again through the shared fair scheduler (`JEV_LIVE_DECISION`, 827 ms), selected a visible log, and completed normal progressive mining. These establish the gateway route and one narrow gameplay decision, not general gameplay competence.

The hidden-client takeover fixture passed on September 17, 2026 with paired `HIDDEN_TAKEOVER_CLIENT_PASS` and `HIDDEN_TAKEOVER_SERVER_PASS` evidence. A fresh `-LiveGateway` run also passed after account credit was added: Jev selected the visible log in 677 ms and a collision-safe placement face in 337 ms, while the server independently observed authoritative block removal and cobblestone placement. Candidate sets now exclude collision-occupied surfaces and do not offer a wait action when executable candidates exist.

The client-only compatibility harness passed on September 17, 2026 against Mojang's official unmodified 1.21.1 server jar (SHA-1 `59353fb40c36d304f2035d51e7d6e6baa98dc05c`). The client log identified `jevcraft_client` and not the full `jevcraft` mod, emitted `VANILLA_COMPAT_CLIENT_PASS`, and the vanilla server independently recorded the player's login, join, and clean disconnect.

The direct TypeSafe route has deterministic fake-endpoint coverage but no live result because no TypeSafe credential was supplied.

## Not yet established

General automatic recipe planning and the remaining specialized menus, full vanilla action coverage, third-party chat-mod coexistence, broader offline-owner task completion, the live direct-TypeSafe route, and repeated one/five/ten-agent live Minecraft task-success benchmarks remain open. Mechanical 0/1/5/10 ticking-region scaling, repeated 1/5/10 paid typed-transport scaling, unmodified-server client-only connection acceptance, and cross-dimension ticket transfer are established separately. See `CAPABILITY_MATRIX.md`.
