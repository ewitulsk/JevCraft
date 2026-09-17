# Verification

## Automated

- `gradlew test`: provider schema fixtures, permissions, exactly-once grants, chat isolation, memory privacy, inventory revisions, moving chunk bounds, parser behavior, stale-decision cancellation, and scheduling.
- `gradlew runGameTestServer`: boots NeoForge 21.1.250 for Minecraft 1.21.1 and runs 11 in-world tests covering body attributes, persistence, bounded chat provenance, world roster/cap/grants, progressive mining, placement, combat, food, chest-menu transfer, crafting-menu callbacks, and immediate stop.
- `gradlew build`: creates full and client-only artifacts.
- `scripts/Test-HiddenTakeover.ps1`: launches only project-owned processes and an unfocused hidden GLFW client against a loopback dedicated server. A deterministic typed decision fixture isolates client mechanics; paired evidence requires normal multiplayer block removal, item pickup, goal completion, and released synthetic keys.

## Opt-in live route

Set `AI_GATEWAY_API_KEY` only in the process environment and run `gradlew liveGatewaySmoke`. It sends one compact mixed-question request to `typesafe-ai/jev`. The key is not logged or stored.

Verified on September 17, 2026: Vercel gateway returned all three answer types from `typesafe-ai/jev` (429 input tokens; 998 ms and 755 ms observed runs). A live in-world GameTest also had Jev select a visible log and complete normal progressive mining. These establish the gateway route and one narrow gameplay decision, not general gameplay competence.

The hidden-client takeover fixture passed on September 17, 2026 with paired `HIDDEN_TAKEOVER_CLIENT_PASS` and `HIDDEN_TAKEOVER_SERVER_PASS` evidence. A subsequent live-gateway takeover attempt reached the gateway but received HTTP 429 free-tier rate limiting; that failed evidence is retained locally and is not called a live takeover pass.

The direct TypeSafe route has deterministic fake-endpoint coverage but no live result because no TypeSafe credential was supplied.

## Not yet established

Unmodified-server connection acceptance, general recipe/menu operation, full vanilla action coverage, native `/msg` coexistence, persistent restart and portal tickets, offline-owner operation, a successful live-gateway takeover run, the live direct-TypeSafe route, and one/five/ten-agent TPS/cost benchmarks remain open. See `CAPABILITY_MATRIX.md`.
