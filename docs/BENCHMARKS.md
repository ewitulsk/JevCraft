# Benchmarks

## No-player ticking-region scaling

Measured September 17, 2026 with `scripts/Test-AgentScaling.ps1` on Windows 11, an AMD Ryzen 7 9800X3D (8 cores / 16 logical processors), 31.2 GiB RAM, and the Java 21 Minecraft runtime. Each point uses a fresh flat dedicated-server world, no connected players, 400 steady-state ticks, and disjoint companion regions 40+ chunks from spawn. Region setup/world generation occurs before the measurement clock.

| Jevs | Exact ticking tickets | Minimum entity ticks | Paced wall ms/tick | Minecraft processing ms/tick |
|---:|---:|---:|---:|---:|
| 0 | 0 | 0 | 49.878 | 0.124 |
| 1 | 25 | 399 | 49.878 | 0.406 |
| 5 | 125 | 399 | 49.878 | 0.778 |
| 10 | 250 | 399 | 49.880 | 0.933 |

All four runs sustained the 20 TPS pacing target. The processing figure is Minecraft's internal rolling average, while wall ms/tick demonstrates pacing; they are intentionally reported separately. This benchmark exercises persistent entity and chunk/block-entity simulation overhead with idle companions. It does not include terrain generation, active navigation, combat, or inference traffic and must not be presented as the final ten-agent gameplay/cost gate.

The script writes a per-run JSON manifest with source commit/dirty state, CPU, RAM, OS, timing, ticket count, and entity-tick assertions under ignored `artifacts/agent-scaling-*` directories.

## Live Vercel/Jev typed-transport scaling

Measured September 17, 2026 with `gradlew liveGatewayScale`, Vercel AI Gateway, model `typesafe-ai/jev`, three repeats per scale point, and concurrency capped at two to match the default server scheduler. Every response contained the requested Choice, Score, and Boolean answer types. This is a paid opt-in task and reads the key only from the process environment.

| Actors | Repeats | Requests | Input tokens | Output tokens | Wall time | p50 latency | p95 latency | Estimated input cost |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 3 | 3 | 1,291 | 222 | 1,262 ms | 276 ms | 732 ms | $0.00005164 |
| 5 | 3 | 15 | 6,455 | 1,110 | 2,804 ms | 243 ms | 461 ms | $0.00025820 |
| 10 | 3 | 30 | 12,910 | 2,220 | 3,982 ms | 235 ms | 323 ms | $0.00051640 |

All 48 calls completed without a rate-limit response. Cost uses the configured $0.04 per million input tokens and is explicitly an input-only estimate; output tokens are reported but not silently assigned a price. This benchmark proves typed transport capacity and account behavior, not ten simultaneous Minecraft task completions.

## Live hidden-client gameplay

Measured September 17, 2026 with `scripts/Test-HiddenTakeover.ps1 -LiveGateway` after paid account credit was enabled. A hidden real client connected to a dedicated loopback server and completed a two-step task using ordinary multiplayer mechanics. Jev selected a visible oak log in 677 ms and a valid placement support in 337 ms. The client mined and collected the resulting item entity, placed one cobblestone through the normal item-use path, released all synthetic keys, and waited for the dedicated server to independently confirm both block changes. The successful run used exactly two live decisions and had no 429 response. This is a narrow gameplay proof, not a general-task or multi-agent success benchmark.
