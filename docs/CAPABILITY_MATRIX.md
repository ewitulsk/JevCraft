# Capability matrix

Status is deliberately split so an API surface is never presented as autonomous gameplay evidence.

| Family | Exposed | Mechanically verified | Live Jev measured | Mod compatibility |
|---|---|---|---|---|
| Typed Choice / Score / Boolean inference | Yes, both provider dialects | Fake endpoints; live Vercel mixed request | Vercel transport only | N/A |
| Cancellation, freshness, ten-controller scheduling | Yes | JUnit contracts | Not measured at ten actors | N/A |
| Goal parsing and typed goal families | Partial | JUnit contracts | Not measured | Vanilla labels only |
| Local takeover instruction/stop UI | Yes in client artifact | Build-time only | Not measured in a real client | Unknown |
| Ground movement | Follow goal for companion | Server entity boot only | Not measured | Vanilla only |
| Mining / placement / pickup | Not yet wired to body adapter | No | No | Unknown |
| Combat | Vanilla damageable body only | Spawn/health GameTest | No | Unknown |
| Personal inventory and death drops | 36-slot companion inventory | Save/load GameTest; transaction contracts | No | Vanilla stacks only |
| Containers and workstations | Transaction state machine only | JUnit contracts | No | Unknown |
| Spawn egg and starter grant | Yes | Server boot; deterministic roster contracts | N/A | Vanilla inventory |
| Owner/admin permissions | Owner and operator command checks | JUnit contracts | N/A | Command/chat mods untested |
| Public/private chat routing | Core bounded fan-out only | JUnit contracts | No | Not integrated with `/msg` yet |
| Moving ticking chunks | 5×5 region per living companion | Compiles and ticket controller registers | No long-run benchmark | Vanilla server only |
| Persistence / restart | Entity owner, goal, food, inventory | Serialization GameTest | No restart fixture | Vanilla saves only |
| Building / portals / mounts / creative / text | Not implemented | No | No | Unknown |

This table is the source of truth for release claims. The repository is currently an executable feasibility slice, not completion of every Stage 2–7 gameplay gate in `JEVCRAFT_PLAN.md`.
