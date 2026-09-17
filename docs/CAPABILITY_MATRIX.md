# Capability matrix

Status is deliberately split so an API surface is never presented as autonomous gameplay evidence.

| Family | Exposed | Mechanically verified | Live Jev measured | Mod compatibility |
|---|---|---|---|---|
| Typed Choice / Score / Boolean inference | Yes, both provider dialects | Fake endpoints; live Vercel mixed request | Vercel transport only | N/A |
| Cancellation, freshness, ten-controller scheduling, cost controls | Shared fair queue; configurable concurrency and hard input-token ceiling; host-wide retry backoff; `/jev metrics` | JUnit contracts plus live in-world dispatch through scheduler | Repeated 1/5/10 typed-transport benchmark; no ten-task gameplay run yet | N/A |
| Goal parsing and typed goal families | Partial | JUnit contracts | Not measured | Vanilla labels only |
| Local takeover instruction/stop UI | Shared by full/client artifacts; visible status; physical-input reclaim; F9 cancellation; normal multiplayer movement/mining/pickup/placement | Hidden unfocused real-client loopback passes with deterministic and live decisions and paired server evidence; isolated client-only runtime joins official Mojang 1.21.1 server | Live Vercel/Jev mining plus placement passed September 17, 2026 | Official unmodified server connection passed; anti-cheat/plugin behavior remains server-specific |
| Ground movement | Follow/navigation and mining approach | Server entity boot; mining approach exercised | Not measured end-to-end | Vanilla only |
| Mining / placement / pickup | Dedicated per-Jev player context; progressive mining; normal use placement; authoritative pickup; takeover rejects collision-occupied support faces | GameTests plus hidden-client mining, real item-entity pickup, and placement with authoritative server assertions | Vercel-selected visible-log mining and support-face placement passed | Vanilla only |
| Buckets / tools / crops / controls | Separate aimed item-use and block-use pipelines with authoritative inventory synchronization | Water placement and pickup, hoe transformation/durability, seed planting/consumption, and lever toggle GameTests | No | Vanilla callbacks only |
| Combat / food | Dedicated player attack context; damageable body; food callbacks | GameTests for damage and food consumption | No | Vanilla only |
| Personal inventory, equipment, experience, death, respawn | 36-slot inventory; armor/hand slots; XP state; personal spawn; exactly-once drops; persisted same-UUID respawn queue | GameTests plus queued-respawn two-boot round trip | No | Respawns at safe personal/world spawn with death-position fallback |
| Containers and workstations | Revision transaction core plus real chest and crafting menu sessions | Chest deposit and single-ingredient crafting GameTests | No | Vanilla chest/table proof only |
| Vehicles | Reach-checked native entity mount/dismount for companion bodies | Vanilla minecart riding relationship GameTest | No | Vanilla entity riding API |
| Spawn egg and starter grant | World SavedData roster, unique names, ten-living cap, pending/delivered/consumed grants | JUnit roster contracts and world-roster GameTest | N/A | Vanilla inventory |
| Owner/admin permissions | UUID owner plus persisted administrator set; `/jev admin` owner/operator management | JUnit contracts; serialization and command GameTests | N/A | Command-permission mods untested |
| Public/private chat routing | Server public-chat fan-out; native `/msg`/`tell`/`w` exact-name routing; `/jev msg` fallback; bounded persisted provenance | Permission, coexistence, and persistence GameTests | No | Third-party chat mods untested |
| Moving ticking chunks | 5×5 region per living companion; join-time bootstrap; destination-first cross-dimension handoff; synchronous non-saving removal cleanup; saved tickets; orphan validation | No-player furnace test; exact Overworld→Nether +25/release lifecycle; baseline/1/5/10 distant-agent benchmark with exact 0/25/125/250 tickets and 20 TPS | No live ten-agent task benchmark | Vanilla server only |
| Persistence / restart | Entity owner/admins, goal, health, food, inventory, recent chat, roster, grant state, and ticking tickets | Serialization GameTests plus two-boot dedicated-server round trip | N/A | Vanilla saves only |
| Operator teleport | Separately persisted server-operator capability; destination tickets prepared before movement | Permission, persistence, and real-entity teleport GameTests | No | Same-dimension vanilla path only |
| Building / portals / creative / text | Single-block building and portal lifecycle only; creative and text absent | Placement and cross-dimension ticket handoff GameTests | Live takeover placement only | Unknown beyond vanilla placement/portal lifecycle |

This table is the source of truth for release claims. The repository is currently an executable feasibility slice, not completion of every Stage 2–7 gameplay gate in `JEVCRAFT_PLAN.md`.
