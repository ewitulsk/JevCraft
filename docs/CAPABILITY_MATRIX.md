# Capability matrix

Status is deliberately split so an API surface is never presented as autonomous gameplay evidence.

| Family | Exposed | Mechanically verified | Live Jev measured | Mod compatibility |
|---|---|---|---|---|
| Typed Choice / Score / Boolean inference | Yes, both provider dialects | Fake endpoints; live Vercel mixed request | Vercel transport only | N/A |
| Cancellation, freshness, ten-controller scheduling | Yes | JUnit contracts | Not measured at ten actors | N/A |
| Goal parsing and typed goal families | Partial | JUnit contracts | Not measured | Vanilla labels only |
| Local takeover instruction/stop UI | Shared by full/client artifacts; visible status; physical-input reclaim; F9 cancellation; normal multiplayer movement/mining/pickup | Hidden unfocused real-client loopback pass with deterministic typed decision fixture | Live gateway decision transport measured separately; live takeover currently rate-limited | Vanilla NeoForge client/server path |
| Ground movement | Follow/navigation and mining approach | Server entity boot; mining approach exercised | Not measured end-to-end | Vanilla only |
| Mining / placement / pickup | Dedicated per-Jev player context; progressive mining; normal use placement; authoritative pickup | GameTests for mining, placement and stack consumption | Vercel-selected visible-log mining passed | Vanilla only |
| Combat / food | Dedicated player attack context; damageable body; food callbacks | GameTests for damage and food consumption | No | Vanilla only |
| Personal inventory and death drops | 36-slot companion inventory | Save/load GameTest; transaction contracts | No | Vanilla stacks only |
| Containers and workstations | Revision transaction core plus real chest and crafting menu sessions | Chest deposit and single-ingredient crafting GameTests | No | Vanilla chest/table proof only |
| Spawn egg and starter grant | World SavedData roster, unique names, ten-living cap, pending/delivered/consumed grants | JUnit roster contracts and world-roster GameTest | N/A | Vanilla inventory |
| Owner/admin permissions | UUID owner plus persisted administrator set; command checks consume both roles | JUnit contracts and serialization GameTest | N/A | Access-management UI/command still open |
| Public/private chat routing | Server public-chat fan-out and permission-checked `/jev msg`; bounded persisted provenance | JUnit contracts and persistence GameTest | No | Native `/msg` name routing remains open |
| Moving ticking chunks | 5×5 region per living companion | Compiles and ticket controller registers | No long-run benchmark | Vanilla server only |
| Persistence / restart | Entity owner, goal, food, inventory and recent chat | Serialization GameTests | No restart fixture | Vanilla saves only |
| Building / portals / mounts / creative / text | Not implemented | No | No | Unknown |

This table is the source of truth for release claims. The repository is currently an executable feasibility slice, not completion of every Stage 2–7 gameplay gate in `JEVCRAFT_PLAN.md`.
