# JevCraft

JevCraft is a NeoForge 1.21.1 mod implementing a shared typed-decision runtime for local-player takeover and persistent independent companions.

## Safety and credentials

Copy `.env.example` to `.env` for local tooling or provide the variables in the launch environment. Credentials are never stored in worlds, logs, or committed configuration.

## Build and test

Requires Java 21. On Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

`build/libs/jevcraft-full-*.jar` is the full client/server mod. `jevcraft-client-*.jar` is the client-only takeover edition; its isolated runtime has been verified joining Mojang's official unmodified 1.21.1 server.

Both editions include the same takeover controller: press F8 for the non-pausing instruction overlay and F9 for immediate stop. The verified takeover goals include collecting oak logs and placing a requested hotbar block; they use Jev to select visible targets or support faces and ordinary client movement, mining, pickup, and item-use paths.

See `docs/CAPABILITY_MATRIX.md`, `docs/VERIFICATION.md`, and `docs/BENCHMARKS.md` for the difference between implemented mechanics, deterministic verification, live Jev gameplay evidence, and performance measurements.

Companion commands are `/jev goal <name> <instruction>`, `/jev stop <name>`, `/jev admin <name> add|remove <player>`, `/jev setspawn <name>`, `/jev operator <name> enable|disable`, `/jev metrics`, and the diagnostic fallback `/jev msg <name> <message>`. Native `/msg`, `/tell`, and `/w` route to an exact loaded Jev name while preserving ordinary player targets. Owners and persisted administrators may assign or stop goals; only owners or server operators manage administrators, and only a server operator can grant the separate teleport capability. Public chat is retained as bounded, attributed observation context and never becomes a goal by itself.

Server inference uses a fair shared queue with two concurrent requests by default, one in-flight request per Jev, expired-work removal, host-wide `Retry-After` backoff, rolling token/latency/throttle counters, configurable input-cost estimation, and an optional hard input-token ceiling. The non-secret controls are documented in `.env.example`.

## Current maturity

This repository is an executable feasibility implementation. It proves the dual provider contract, live Vercel evaluation transport, live companion and hidden-client visible-log choices, stale-response safety, two artifact shapes, official-server client-only compatibility, restart and queued-respawn persistence, exactly-once death drops, no-player remote entity/block-entity ticking, leak-free cross-dimension ticket handoff, isolated simultaneous companion inventories, menu-driven stack management and equipment swaps, supplied sign/book text handling, and real companion point-to-point movement across slabs, ladders, scaffolding, water surface/dive routes, closed wooden doors, and closed fence gates, mining, placement, buckets, tools, crops, controls, melee/bow/shield/splash-potion combat, eating, feeding/breeding/taming, leads, villager trading, vehicles, sleep/night-skip participation, chest deposit, shaped crafting, furnace/smoker/blast-furnace paths, stonecutting, grindstone repair, loom patterning, XP-paid anvil renaming, lapis/XP-paid enchanting, netherite smithing, fueled potion brewing, filled-map scaling, and paid beacon activation with companion effect delivery. It does **not** yet satisfy the plan's complete vanilla action surface or release gates; those gaps are kept explicit in the capability matrix.
