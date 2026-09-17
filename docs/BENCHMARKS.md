# Benchmarks

## No-player ticking-region scaling

Measured September 17, 2026 with `scripts/Test-AgentScaling.ps1` on Windows 11, an AMD Ryzen 7 9800X3D (8 cores / 16 logical processors), 31.2 GiB RAM, and the Java 21 Minecraft runtime. Each point uses a fresh flat dedicated-server world, no connected players, 400 steady-state ticks, and disjoint companion regions 40+ chunks from spawn. Region setup/world generation occurs before the measurement clock.

| Jevs | Exact ticking tickets | Minimum entity ticks | Paced wall ms/tick | Minecraft processing ms/tick |
|---:|---:|---:|---:|---:|
| 0 | 0 | 0 | 49.875 | 0.110 |
| 1 | 25 | 399 | 49.880 | 0.316 |
| 5 | 125 | 399 | 49.878 | 0.665 |
| 10 | 250 | 399 | 49.880 | 0.749 |

All four runs sustained the 20 TPS pacing target. The processing figure is Minecraft's internal rolling average, while wall ms/tick demonstrates pacing; they are intentionally reported separately. This benchmark exercises persistent entity and chunk/block-entity simulation overhead with idle companions. It does not include terrain generation, active navigation, combat, or inference traffic and must not be presented as the final ten-agent gameplay/cost gate.

The script writes a per-run JSON manifest with source commit/dirty state, CPU, RAM, OS, timing, ticket count, and entity-tick assertions under ignored `artifacts/agent-scaling-*` directories.
