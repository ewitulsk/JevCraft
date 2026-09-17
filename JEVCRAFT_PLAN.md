# JevCraft: research and implementation plan

Prepared September 17, 2026. Target: Minecraft Java Edition 1.21.1, NeoForge.

This is a proposed implementation plan, not a claim of working gameplay. Research included TypeSafe's public documentation and source material, Vercel AI Gateway's Jev model listing, NeoForge 1.21.1 documentation and FakePlayer source, existing Minecraft automation projects, and the local Planetary Sable testing setup. No authenticated Jev calls, game launches, or gameplay benchmarks were performed. Account-specific model availability, quotas, latency, and cost remain to be measured.

## 1. Product contract

JevCraft supports two selectable inference providers: **Vercel AI Gateway (the default for this project)** and **the official TypeSafe/Jev API**. Both must support both gameplay modes through the same typed decision contract.

JevCraft gives Jev two bodies with one shared decision architecture:

1. **Player takeover.** Jev controls the real local player. A new instruction box accepts text without sending it to public chat. The player can immediately stop takeover.
2. **Independent companions.** Named, custom Jev entities live in the world, spawned through a Jev Spawn Egg. They have player-like health, hunger, inventory, equipment, death, item drops, and persistent memories.

Creative players can obtain the egg from the creative menu. Survival players receive one starter egg. The recommended interpretation is one grant per player UUID per world, not on every login or death. If the inventory is full, retain a pending grant and deliver it when space becomes available. Egg delivery and consumption must be reconciled across saves so retries cannot duplicate them.

Every Jev receives public chat, including messages from people who cannot command it. Only its configurable owners/admins can issue instructions. Private messages use `/msg <JevName> <message>`. Replies use authored templates with verified names, item labels, and results. Operator permission enables teleportation. Ordinary commands from owners do not confer operator authority.

**Player-like chunk loading is required.** Each living companion keeps a moving area of surrounding chunks loaded and simulated, even with no human nearby. It can keep traveling and working while owners are elsewhere or offline, as long as the server is running. Closing a single-player world stops its server and therefore stops the Jevs too.

The scope includes every ordinary player action: movement, looking, mining, placement, combat, item use, personal inventory, containers, crafting, and the less common interactions described below. Programmatic inventory operation is a requirement.

**Ten-Jev cap:** proposed policy is a maximum of ten registered living companion entities per world/server across all dimensions, including unloaded companions, and no more than ten concurrently running Jev controllers across both modes on a modded server. At ten active companions, takeover requires suspending one controller; it does not delete a companion. This is a proposed interpretation of “cap the total at ten.” On an unmodified remote server, a client can enforce only its own limits; it cannot coordinate other installations.

Defaults to make implementation concrete: unique case-insensitive command names; immutable UUID identities; egg user becomes initial owner; operators can repair ownership; companions continue authorized tasks while loaded even if an owner logs out; no new goal starts solely because an unauthorized player asked for it. Owners can explicitly stop, replace, or queue goals.

## 2. Multiplayer deployment boundary

| Environment | Player takeover | Independent custom Jevs |
|---|---|---|
| Single-player with JevCraft | Yes, local controller | Yes, integrated-server controller |
| NeoForge server with JevCraft and matching clients | Yes | Yes, server-owned entities |
| Compatible server without JevCraft | Client-only takeover target | Custom entities and eggs unavailable |
| Server/client versions or modpacks incompatible with 1.21.1 | Separate compatibility work | Separate compatibility work |

“Any multiplayer server” cannot mean injecting a new persistent custom entity into a server that does not run its implementation. Client takeover can use ordinary player networking, but server rules, anti-cheat, inventory plugins, and mod compatibility still determine whether it works. No bypass of server validation is part of this design.

Use two distributable artifacts from one repository: a client-only takeover edition and a full companion edition. This avoids forcing custom registry entries or required mod handshakes onto unmodified servers. The full edition must also support takeover, so users never need both installed. Verify actual connection behavior before settling packaging.

NeoForge distinguishes physical and logical sides; entity simulation and authoritative state belong on the logical server, including single-player. Keep rendering and client input code out of dedicated-server initialization. [NeoForge sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/)

## 3. What Jev actually provides

TypeSafe presents Jev as a System One model with typed, probabilistic decisions rather than generated prose. The launch article reports 70–500 ms end-to-end calls, and its Doom demonstration uses structured state rather than images. These are vendor-reported results, not Minecraft measurements. Its type guarantee prevents out-of-schema answers; it does not prove that an in-schema decision is correct or sensible. [TypeSafe launch article](https://typesafe.ai/blog/introducing-system-one-models-and-jev)

The primitives are Choice for options, Score for rubric levels, and Noul for a yes/no probability. Questions in one request evaluate independently against the same state. Batch independent judgments, but do not assume that an answer about a target can condition another question in that same call. Question IDs are bookkeeping, not instructions; each question must describe its judgment fully. The documented shared request budget is approximately 32,000 tokens. [Primitives](https://docs.typesafe.ai/primitives)

Choice supports up to 255 options. Large item registries, terrain candidate lists, and build proposals therefore need grouping, retrieval, or successive selection. Always offer an unavailable/unknown/none option where appropriate. [Choice](https://docs.typesafe.ai/primitives/choice)

Choice and Score confidence is derived from the returned probability distribution. It is not a measured probability that a whole Minecraft task will succeed. Establish thresholds on our own scenarios, with separate policies for movement, targeting, and spending scarce items. Noul has no separate confidence field. [Confidence](https://docs.typesafe.ai/confidence)

State can be structured JSON, making compact actor observations a natural input. No implicit conversation or memory service should be assumed; send the relevant current state and retrieved memories in each request. [State](https://docs.typesafe.ai/concepts/state)

The documented transport is `POST https://api.typesafe.ai/v1/systemone` with bearer authentication and state, model, and questions. The API returns answers and usage; its documented errors include authentication failures, validation errors, rate limiting, and overload. Record the actual returned model identifier. [API reference](https://docs.typesafe.ai/api)

Public SDK documentation lists Python and JavaScript and allows direct HTTP from other languages. **Recommendation: an asynchronous provider interface with separate Vercel Gateway and official TypeSafe adapters.** Prefer Java HTTP implementations inside the mod where the provider's evaluation transport is verified. This remains ordinary JSON transport even though Jev is not generating JSON as prose. [Client SDKs](https://docs.typesafe.ai/sdk)

Do not silently add a second model for planning or conversation. TypeSafe's smart-home demo does use an LLM for splitting complex instructions and free-form replies, so that demo is not proof that arbitrary instruction decomposition works through Jev alone. Our decomposition approach needs explicit tests. [Smart-home demo](https://docs.typesafe.ai/demos/smart-home)

### Selectable inference providers

| Setting | Vercel AI Gateway — default | Official TypeSafe API |
|---|---|---|
| Provider identifier | `vercel_gateway` | `typesafe_direct` |
| Model configuration | `typesafe-ai/jev` as currently documented | `jev-latest` or an account-supported pinned Jev version |
| Credentials | Vercel AI Gateway API key | TypeSafe API key |
| Request integration | Jev evaluation interface; verify exact gateway wire protocol and authentication during Stage 1 | Documented `/v1/systemone` HTTP endpoint |
| Usage accounting | Gateway usage, billing and rate-limit metadata | TypeSafe usage and rate-limit metadata |

Vercel's Jev listing demonstrates `experimental_evaluate` from the AI SDK with `typesafe-ai/jev` and a question of type `boolean`. This is an evaluation interface, not evidence that Jev works through ordinary chat completions. Its example also shows that gateway terminology differs from TypeSafe's `noul`. [Vercel Jev model and example](https://vercel.com/ai-gateway/models/jev)

Define a provider-independent request containing state, typed questions, candidate IDs, deadline, and cancellation identity. Normalize responses to choices, score/rubric data, boolean probability, confidence where supplied, usage, and provider/model/request metadata. Preserve raw distributions. Translate the documented boolean/Noul semantics explicitly; never collapse a probability to true/false or fabricate a missing confidence score. Verify score and choice schemas, candidate limits, supported state formats, and response metadata on both routes rather than assuming they match.

Stage 1 must inspect and pin the applicable Vercel AI SDK/gateway evaluation implementation and run an authenticated mixed-question smoke test. The model listing verifies product support, but this research has not established the exact Java-callable evaluation endpoint or complete response schema. Prefer a native Java adapter once that transport is verified. If a supported integration requires the experimental JavaScript SDK, evaluate a small locally bound, authenticated bridge with a pinned SDK version; document its packaging and startup cost before adopting it. The gateway route is a first-class deliverable and must not be deferred simply because the official endpoint is easier to call.

Expose a provider selector, model, provider-specific credential reference, connection test, timeout/concurrency settings, and cost settings in configuration. Selecting one provider does not require credentials for the other. Selection applies at the inference host: client-local for standalone takeover, server-side for companions, and the integrated server in single-player. No other player can redirect a server's inference destination or obtain its credentials.

Provider changes invalidate in-flight decisions and start a new inference session without discarding goals or memories. Automatic cross-provider fallback is off by default. If later enabled explicitly, it must use separately configured credentials, respect the original deadline and combined spend limit, remain within Jev models, and accept at most one decision for each observation. Gateway routing must not silently substitute an unrelated text model. Record the selected route and resolved model when available; a gateway alias is not proof it matches a directly pinned version.

Both providers share cancellation, freshness checks, ten-agent scheduling, and action validation. Validate each adapter with recorded schema fixtures and fake endpoints, then compare live route behavior on the same observations. Measure latency and costs separately; gateway overhead, underlying routing, model versions, and account quotas must not be assumed identical. Exact answer equality is not a required parity test; preserved semantics, valid actions, and measured task quality are.

## 4. Original harness architecture

The runtime cycle is:

**Observe → retrieve relevant memories → construct feasible candidates → ask Jev focused questions → reconcile decisions → execute ordinary actions → verify outcomes → update memory.**

Suggested modules:

| Module | Responsibility |
|---|---|
| `jev-core` | Actor-independent observations, goals, candidate descriptions, decision arbitration, memory interfaces, cancellation |
| `jev-inference` | Shared provider contract, configuration, cancellation/deadlines, normalized decisions and usage |
| `jev-vercel` | Default Vercel AI Gateway evaluation adapter, schema mapping, authentication and gateway metadata |
| `jev-typesafe` | Official direct API adapter, authentication, typed schemas and TypeSafe metadata |
| `jev-minecraft-common` | Minecraft action definitions, recipe knowledge, item/entity descriptors, bounded path planning, menu semantics |
| `jev-client` | Local-player observation/control, instruction overlay, takeover status and stop handling |
| `jev-companions` | Custom entity, spawn egg, server controller, permissions, messaging, persistence, lifecycle |
| `jev-tests` | Scripted controllers, fake API server, GameTests, hidden clients, evidence collection |

These can be Gradle subprojects or carefully separated source sets. Avoid building a plugin framework before the first end-to-end gameplay proof.

### Continuous control

Minecraft normally targets 20 simulation ticks per second. Movement, collision, mining progress, cooldowns, and menu handling continue on game ticks. Jev chooses priorities and short action intentions asynchronously; it is not asked to generate each tick.

Proposed initial scheduling targets, subject to measurement:

- Tick-level deterministic movement and cancellation.
- Around 2–5 Jev decisions/second for actively changing situations.
- Up to 10/second as a benchmark target for urgent reactive cases, only if latency and account capacity permit.
- Event-driven or much slower requests while idle, waiting for a furnace, or following a stable route.

With one outstanding request per actor, 500 ms latency allows only about two sequential decisions per second for that actor. Do not advertise ten merely because a timer fires ten times. Track completed, fresh decisions, not attempted calls.

Every request captures actor/session identity, world and dimension, observation version, goal version, candidate map, and expiry. The response can select only candidates from that snapshot. Before execution, recheck reach, target existence, inventory state, permissions, and control ownership. Discard responses after stop, death, disconnect, dimension transfer, or goal replacement.

Independent answers can conflict. A deterministic arbiter enforces rules such as no simultaneous inventory transaction and block use, no attack against a different actor than the selected target, and no action toward an expired target. Prefer selecting complete feasible action candidates where combinations would otherwise be ambiguous.

Low-level safeguards can release an attack button, stop at an edge, avoid walking into a newly occupied space, or terminate an invalid transaction without waiting for Jev. They do not invent long-term goals. Finite action durations prevent a lost connection from leaving a key held forever.

Never block a game tick on HTTP or allow an HTTP callback to mutate the world. Capture immutable state on the owning thread; do bounded work elsewhere; apply validated results on that thread. NeoForge documents main-thread payload handling and explicit scheduling for off-thread work. [Networking](https://docs.neoforged.net/docs/1.21.1/networking/payload/)

### Natural-language goals without generated strings

Parse candidate numbers, coordinates, quoted text, names, and registry labels in code. Ask Jev which spans or candidates the user intended. This supports exact quantities and text without inventing a numeric output through Score. TypeSafe's extraction cookbook demonstrates finding candidates in code and letting the model select them. [Candidate extraction](https://docs.typesafe.ai/cookbooks/pre_parsed_value_extraction_cookbook)

Represent goals as typed steps with constraints and completion predicates: acquire items, move, craft, deposit, follow, defend, build, explore, wait, or interact. Use recipe and action preconditions to create dependency graphs. Jev selects relevant resources, alternatives, priorities, and recovery choices.

For “get 32 oak logs and put them in the chest by my bed,” extract 32, identify oak logs, resolve observed or remembered bed/chest candidates, and verify the destination inventory gains the required items. Unknown destinations lead to exploration or a templated clarification. Completion comes from game evidence, not the model saying “done.”

Compound instructions need clause segmentation, ordering, negation, quantities, and pronoun tests. A conjunction alone is not a reliable parser. Unresolved instructions produce a precise question rather than a guessed goal.

### Building and open-ended ability

Give Jev block placement as a primitive plus authored geometric generators for walls, floors, rooms, roofs, bridges, and repeated structures. A blueprint becomes a dependency sequence for clearing, scaffolding, resource acquisition, orientation, and placement. Each placement obeys normal interaction rules.

Jev can choose and revise structured build proposals. It can copy observed structures or execute supplied blueprints. Arbitrarily creative architecture from unrestricted prose remains an intelligence challenge; a typed API does not automatically supply a design generator. Keep the action surface open and expand compositional building through measured scenarios rather than presenting a template catalog as universal creative competence.

Signs, books, and item renaming can use text supplied by the user or authored templates. Freely inventing a novel book is outside a Jev-only text capability. This preserves those game actions while accurately describing the model's limitation.

## 5. Human-equivalent observations

Use structured representations of what a human could perceive, with explicit approximation limits. This is semantic perception, not literal pixel vision.

| Channel | Information to expose | Boundary |
|---|---|---|
| Body and HUD | Health, food bar, air, armor, effects, experience, hotbar, hand contents | Avoid hidden internals such as exact saturation unless the selected human-visible interface exposes them |
| Vision | Visible block faces, entities, positions, facing, movement, exposed text and item appearances | Field of view, range, occlusion, and conservative visibility/light rules; no hidden ore or private entity internals |
| Hearing | Audible sound category, estimated direction/distance, temporal event | No precise unseen source coordinates or identity unless legitimately distinguishable |
| Personal inventory | Slots, stacks, visible durability/tooltips, equipment, cursor-held stack | Respect available UI information; no arbitrary hidden NBT |
| Containers | Open menu contents, slots, buttons, labels, progress and recipes available there | Opening and access checks first; never scan unopened inventories |
| Environment | Visible weather, terrain, fluids, fire, darkness, signs, maps, books | Text/maps need explicit observation adapters; no world seed or omniscient structure lookup |
| Memory | Previously observed landmarks, routes, container contents and events | Mark observation time and uncertainty; revisiting may invalidate old facts |
| Chat | Public chat for every Jev, authorized instructions, its own private messages | Private messages to other people or Jevs are excluded |

Public chat reaching all companions across dimensions is an explicit product feature, independent of physical hearing.

Server companions need a virtual eye and ear position; reading the server world is not permission to reveal all of it. Pathfinding consumes only the same visible/remembered terrain model. Unknown cells require exploration. Target IDs may be opaque internal handles, but must not leak hidden traits in their descriptions.

Occlusion alone is insufficient for literal visual equivalence: lighting, transparent blocks, particles, textures, and resource packs affect human perception. Start with conservative documented approximations, add coverage for vanilla visual/text channels, and mark unsupported modded visual channels explicitly. A future image-capable model could plug into the observation interface; current design does not depend on that becoming available.

## 6. Two action adapters

### Local-player takeover

Use the actual local player's input/movement and interaction paths. Mouse aiming is an in-game rotation operation; no desktop cursor automation is required. Inventory actions go through the normal client menu interaction route with slot IDs and click types, not screen coordinates.

The instruction box should be a non-pausing overlay. While it has keyboard focus, typed letters cannot become movement commands; Jev can keep operating under its existing goal. Submitting replaces or queues a goal according to the selected UI mode. Show current task, active action, connection state, and a visible stop control.

The stop key takes priority over inference and overlay focus and releases every synthetic input immediately on the next client tick. Physical gameplay input outside text entry should reclaim control by default. Stop on disconnect, death, and unexpected screens; handle expected inventory interfaces through the menu controller. Resuming is explicit, not triggered by a late response. Test cancellation while holding use, drawing a bow, dragging a stack, or mining.

### Independent entity and player interactions

Recommendation: a registered custom humanoid entity owns identity, body, equipment, inventory, health, hunger, and persistent state. A dedicated per-Jev player-interaction context supplies player-dependent mechanics where required. There must be one authoritative inventory and one authoritative body, even if an interaction method requires a player-shaped adapter.

This is the highest-risk architecture and must be proven before broad implementation. NeoForge's stock FakePlayer deliberately disables tick, death, menu opening and riding, and treats damage differently. Simply spawning or reusing it does not provide a survival companion. [NeoForge 1.21.1 FakePlayer source](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/common/util/FakePlayer.java)

The proof must cover normal mining speed and drops, tool damage, item use, crafting/menu callbacks, combat attribution, eating, death, vehicle access, and two Jevs using different inventories concurrently. Avoid a shared fake-player singleton. Temporary stack copying must never create a second source of truth or duplicate cursor-held items.

If the custom-entity-plus-context architecture fails player-dependent mechanics, investigate a purpose-built server-player simulation before locking persistence and networking. Keep the user-facing egg and named-companion behavior, but report any necessary change from a registered custom entity. Do not quietly substitute a separate authenticated bot account or claim a stock FakePlayer is equivalent.

### Programmatic inventory transactions

Implement menu sessions rather than directly inserting/removing items from arbitrary containers. NeoForge menus expose slots, validity, synchronization, and quick-move behavior; crafting output slots can invoke additional callbacks. These semantics are central to correct operation. [Menus](https://docs.neoforged.net/docs/1.21.1/gui/menus/)

Track menu identity, state revision, cursor stack, source/destination slots, expected preconditions, and expected changes. Apply one valid operation or controlled short sequence, wait for authoritative state, and verify the result. If another player changes a chest, recompute the transfer. Do not blindly resend an uncertain click. Support pick up/place, split, shift transfer, hotbar swap, drop, crafting output, and menu-specific buttons.

For takeover, use the normal client controller and server acknowledgment path. For companions, execute equivalent validated server menu behavior through their dedicated context. Access to an item-handler capability alone is not permission to bypass player reach, menu validity, protection events, recipe costs, or item callbacks.

## 7. Complete action coverage

Maintain a versioned capability matrix with four separate columns: action exposed, action mechanically verified, Jev decision quality measured, and mod compatibility verified. A working API method alone does not establish successful autonomous play.

| Family | Required coverage |
|---|---|
| Ground movement | Walk, strafe, look, sprint, crouch, jump, step/slab traversal, crawling conditions, ladders, scaffolding, doors and gates |
| Water and air | Swim, surface, dive, breathing, creative flight when entitled, elytra and rockets, fall recovery |
| World interaction | Mine/stop mining, pick up drops, place with face/orientation, buckets, ignition, tools, crops, switches, redstone controls |
| Combat | Targeting, melee cooldowns, shields, bows/crossbows, projectiles, potions, equipment choice, retreat, PvP rules |
| Inventory | Hotbar, offhand, armor, stack split/merge/swap/drop, carried cursor stack, full inventory and death drops |
| Workstations | Inventory/table crafting, furnaces/smokers/blast furnaces, brewing, anvils, enchanting, smithing, grindstones, stonecutters, looms, cartography, beacon selection |
| Entities and travel | Trading, feeding, breeding, taming, leads, mounts, boats/minecarts, sleep and spawn points, portals and dimension travel |
| Information and text | Read signs/books/maps and menu labels; write supplied sign/book text; rename supplied item text; chat templates |
| Creative/operator | Creative inventory actions only in creative; teleport only with explicit authority; no automatic grant of unrelated administrative powers |
| Modded interactions | Generic conventional menus and items first; explicit adapters for unusual screens, packets, machines, and mechanics |

Generic modded support cannot establish compatibility with every possible custom screen. The extension interface should describe perception, legal actions, preconditions, and outcome checks for unsupported mechanics. Unknown interfaces should be reported, not manipulated by guessing slot meanings.

## 8. Identity, chat, permissions, lifecycle

Store owners/admins as player UUIDs, not display names. Proposed role distinction: owners can edit administrators and ownership; admins can issue goals and manage permitted companion settings; server operators can recover any companion. Check permissions in code before converting text into an executable goal, and again if privileges change while work is queued.

Preserve public messages as observations tagged with authenticated sender identity and authorization status. An unauthorized player's instruction must never become authorized because it is quoted, placed on a sign, or repeated by another Jev. Owners can explicitly delegate a bounded task; ambient text cannot mutate the access list.

Use the server chat event where applicable and derive identity from the actual player object. Do not authenticate by parsing a chat prefix. NeoForge exposes a server chat event with the sender and message; cancellation and other chat mods still require integration tests. [ServerChatEvent source](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/event/ServerChatEvent.java)

`/msg` requires deliberate command integration because Jevs are not ordinary connected players. Add narrowly scoped Jev-name resolution while preserving normal player messaging, aliases, command suggestions, and signed-chat behavior. Reserve an unambiguous Jev name or qualified alias on player-name collision. Provide `/jev msg` as a diagnostic fallback, not a replacement for the requested `/msg` experience. Prototype coexistence with vanilla commands early.

Public chat fans out through a server event log with independent per-Jev cursors. Unloaded Jevs retain a bounded mailbox and read it on activation; stale commands must not execute unexpectedly. Retention and expiry must be visible settings. Cap message size and processing rate. Jev-originated status replies are marked so ten agents cannot create an automatic reply loop. A general authorized order may reach several Jevs, but a shared resource reservation board should avoid needless duplicate work.

Persist UUID, name, role lists, operator/teleport flag, inventory, equipment, food/health/experience, location, dimension, spawn point, memory, and goal status. A world registry tracks the global cap and starter-egg grants; entity state tracks the body. NeoForge SavedData and attachments provide persistence building blocks, but explicit serialization and lifecycle handling are still required. [SavedData](https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/), [Attachments](https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/)

Recommended death behavior is player-like: normal drops respecting applicable game rules, persistent identity/memory, and respawn at a valid personal/world spawn after a configurable delay. Respawn uses the same roster slot and grants no new starter egg. Exact timing is a configurable product default. Ensure dropped items cannot also be restored into the respawned inventory.

### Player-like chunk loading and simulation

Ordinary entities do not automatically get all player chunk-loading behavior. Each living Jev therefore owns a persistent, moving chunk-loading region. Default loading and simulation distances should follow the server's corresponding player settings, with explicit administrator overrides. Owners leaving or the Jev standing idle must not unload that region. Pausing inference also leaves its body and surrounding simulation active. Only explicit hibernation/removal, death/respawn handoff, world shutdown, or a configured administrative policy changes that obligation.

NeoForge 1.21.1 exposes registered ticket controllers with entity UUID ownership, persistent restoration, validation callbacks, and a ticking option. Its forced-chunk implementation distinguishes ticking tickets from ordinary loaded chunks. Use that supported mechanism as the starting point. [TicketController](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/common/world/chunk/TicketController.java), [ForcedChunkManager](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/common/world/chunk/ForcedChunkManager.java)

Loading a chunk is not proof of player-equivalent simulation. Independently test entity ticks, block-entity work such as furnaces, scheduled redstone/fluid ticks, random ticks such as crop growth, mob spawning/despawning, and mechanics that explicitly search for players. Where vanilla proximity predicates exclude a custom Jev, add targeted, tested participation in the relevant simulation logic. Do not make a fake global player entry merely to influence one predicate. Sleeping/night-skip participation and hostile targeting also need explicit comparison with normal players.

Track ticket ownership by Jev UUID and dimension. Add leading-edge chunks before releasing trailing-edge chunks, and wait for terrain readiness before moving into it. Share overlap accounting so a chunk remains loaded while any Jev or other ticket holder still needs it. Prepare destination tickets for portals/teleports and release the old region only after successful arrival. On restart, validate persisted tickets against the companion registry, restore living Jevs without needing a human visit, and remove orphan tickets. Registry and entity load ordering must be handled without falsely deleting a valid sleeping entity's tickets.

Keep the footprint finite and moving rather than accumulating every visited chunk. Ten widely separated Jevs are the worst case. For a square radius of r chunks, the rough footprint is (2r+1)^2 per Jev before overlap and ticket propagation; radius 10 is roughly 4,410 chunks for ten separated regions. This is a sizing illustration, not an exact vanilla ticket calculation. Benchmark the actual footprint at the agreed player distances. Queue new terrain generation within a per-tick budget; when it cannot keep up, Jev waits at the boundary rather than silently losing its loading radius or freezing the server with bulk synchronous loads.

Required tests: a lone Jev mines, farms, smelts, and travels with all humans far away; a server continues the scenario after the last human disconnects; overlapping Jevs separate without unloading each other's chunks; a portal transfer releases old tickets; server restart restores companions and their regions; ten distant Jevs show bounded ticket count and measured tick cost. Normal takeover already benefits from the real player's chunk loading and must not add duplicate tickets.

Teleport permission is separate from command ownership. On a modded server, an explicit server-authorized Jev operator flag permits teleport actions; owners cannot grant it unless they also have server permission. Takeover uses the real player's command privileges. On unmodified servers, denied teleport commands remain denied. Granting teleport is not a reason to reveal hidden terrain or grant creative inventory access.

## 9. Ten-agent performance and operating cost

Implement a shared scheduler per host with per-agent fairness, capped concurrency, deadline-aware priorities, and rate-limit backoff. One slow agent must not block others; ten calls completing together must not stall the game thread. Public chat parsing may reuse candidate extraction, but memories, private instructions, and permissions remain isolated.

Start with one in-flight request per actor, replacing queued stale observations with the newest. Prioritize urgent changed situations, but retain minimum service for the other agents. Do not retry expired combat decisions. TypeSafe's retry documentation supports bounded budgets and Retry-After handling; the game adapter needs substantially tighter action validity than a generic background request. [Retry documentation](https://docs.typesafe.ai/sdk/python/api/retries)

Do not send an entire chunk or lifetime transcript each time. Send nearby relevant observations, compressed geometry/features, current goal, candidate descriptions, and selected memories. Log enough to reconstruct why a decision was accepted, with sensitive chat redacted or excluded by default.

Illustrative cost arithmetic below uses the official launch article's stated $0.042 per million input tokens and no output charge. It is the **direct-API example**, not the default gateway's billing rate:

| Input tokens per complete request | Requests/sec/agent | Ten agents: requests/sec | Approximate input cost/hour |
|---|---:|---:|---:|
| 2,000 | 2 | 20 | $6.05 |
| 2,000 | 5 | 50 | $15.12 |
| 2,000 | 10 | 100 | $30.24 |
| 5,000 | 5 | 50 | $37.80 |

Formula: agents × requests/sec × tokens/request × 3,600 × price/1,000,000. State AND questions count. These are estimates, not quotes or measured bills, and exclude retries. Confirm current account pricing and quotas before implementation benchmarks. [Pricing in launch article](https://typesafe.ai/blog/introducing-system-one-models-and-jev)

Vercel's Jev listing currently displays $0.04 per million input tokens. At that displayed rate, the same four input-only scenarios are approximately $5.76, $14.40, $28.80, and $36.00 per hour. Confirm applicable billing details and any other charges before treating these as total costs; do not assume the displayed gateway price and direct price are interchangeable. Store price configuration and observed usage per provider and show the selected provider in the cost display. [Vercel Jev pricing](https://vercel.com/ai-gateway/models/jev)

Expose rolling token use, requests, approximate spend, latency percentiles, throttling, and an optional hard spend limit. An estimate is labeled as an estimate. Keep Vercel and TypeSafe credentials separate. API keys stay in client-local configuration for client-only takeover and server-local configuration for companions; never send the server key to clients or save keys into worlds, logs, or source control. Server-provided credentials are not required for takeover on unmodified servers.

## 10. Existing harnesses: useful ideas and limits

| Project | Relevant evidence and inspiration | Decision for JevCraft |
|---|---|---|
| [Mineflayer](https://github.com/PrismarineJS/mineflayer) and its [API](https://github.com/PrismarineJS/mineflayer/blob/master/docs/api.md) | Structured bot operations for movement, digging, entities, inventories and containers; examples organize skills by action family | Use as an action-coverage and transaction-design reference. Our implementation uses native Minecraft/NeoForge objects rather than an external JavaScript bot connection |
| [Mineflayer-pathfinder](https://github.com/PrismarineJS/mineflayer-pathfinder) | Goal-based navigation with movement rules and configurable costs | Build bounded planning with explicit resource/hazard costs, cancellation, and observed-terrain limits |
| [Baritone](https://github.com/cabaletta/baritone) | Mature navigation/mining/building automation and a public API | Study path execution and replanning. Keep our navigation replaceable; do not assume the inspected branch is a drop-in 1.21.1 companion engine |
| [Mindcraft](https://github.com/mindcraft-bots/mindcraft) | LLM-plus-Mineflayer agents, profiles, and task fixtures with item/blueprint goals | Adopt the idea of reproducible multi-agent task evaluation, not its conversational or generated-code control architecture |
| [Voyager](https://github.com/MineDojo/Voyager) | Composable skill memory, curriculum, feedback, and persisted progress | Use reusable verified actions and durable knowledge. Jev does not generate the code that Voyager's approach uses |
| [MineDojo](https://github.com/MineDojo/MineDojo) and [MineRL](https://github.com/minerllabs/minerl) | Embodied-agent research environments and task/evaluation infrastructure | Use scenario and observation/action design ideas; do not make their environment stacks runtime dependencies |

Originality comes from the typed decision architecture, native dual-body integration, perception boundaries, permissions, and verifiable action execution. Maintain a source provenance record. Any copied or bundled code requires checking the exact revision's license and attribution obligations; inspiration alone does not require importing a project. No dependency on another harness is selected in this plan.

## 11. Testing strategy from Planetary Sable

The reference uses Java 21, ModDevGradle, scripted loopback servers, hidden clients, in-process key replay, paired client/server assertions, per-run evidence, and cleanup limited to processes the script started. Its current configuration pins NeoForge 21.1.250 and ModDevGradle 2.0.140; these are reference pins, not a claim that they are the latest or automatically the correct JevCraft pins.

Files inspected:

- [Testing guide](<C:/Users/evanw/OneDrive/Documents/ChatGPT/Planetary Sable/docs/TESTING.md>)
- [Client/server baseline launcher](<C:/Users/evanw/OneDrive/Documents/ChatGPT/Planetary Sable/scripts/Test-ClientBaseline.ps1>)
- [Hidden window setup](<C:/Users/evanw/OneDrive/Documents/ChatGPT/Planetary Sable/src/main/java/dev/planetarysable/mixin/testing/HiddenWindowMixin.java>)
- [Hidden mouse handling](<C:/Users/evanw/OneDrive/Documents/ChatGPT/Planetary Sable/src/main/java/dev/planetarysable/mixin/testing/HiddenMouseMixin.java>)
- [Ordinary-input replay](<C:/Users/evanw/OneDrive/Documents/ChatGPT/Planetary Sable/src/main/java/dev/planetarysable/testing/client/BaselineInputReplay.java>)

Adapt the testing approach into JevCraft-owned worlds and scripts. Do not import planetary physics, dependency forks, project-specific publishing instructions, or installed-profile assumptions. Reverify hidden rendering on this project's stack before client tests. Never steal desktop focus or manipulate the user's running game.

Four evidence layers:

1. **Deterministic contracts:** fake Jev responses for permissions, candidate matching, conflicting decisions, cancellation, rate limiting, memory isolation, and transaction state. Inject timeouts, malformed transport responses, out-of-order completion, and changed targets. Run shared contracts against both provider adapters, covering boolean/Noul mapping, distributions, unavailable metadata, provider switching, credential isolation, and optional fallback without duplicate accepted decisions.
2. **Server/GameTests:** actual collisions, mining costs/drops, menu transactions, eating/combat, spawn grants, cap enforcement, protected interactions, portals, death and restart. NeoForge provides the in-world GameTest framework. [GameTests](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)
3. **Real hidden clients:** takeover instruction entry, stop behavior, normal movement/use packets, inventory synchronization, observer rendering, vanilla-server connection, and full-edition multiplayer. Server-only tests cannot establish these behaviors.
4. **Live Jev evaluation:** fixed and held-out worlds/prompts; one, five, and ten actors; repeated runs; success rate, completion time, unsafe/invalid action attempts, stuck time, fresh-decision latency, tick overhead, and cost. Run both Vercel Gateway and direct TypeSafe routes, prioritizing Vercel as the default, and record provider/model identity in every result. Separate intelligence failure from harness failure.

Essential adversarial fixtures include an unopened chest with tempting contents, ore behind a wall, unauthorized public orders, forged name prefixes, cross-Jev private instructions, simultaneous egg use at capacity, death during an API request, competing chest users, delayed inventory acknowledgment, disconnect while holding use, and restart after a partially completed transfer.

Publish reproducible manifests: source/build/model version, scenario and world seed, action traces, observation provenance, request timing and usage, assertions, logs, and appropriate screenshots. Keep failed evidence. A successful build, mocked response test, or prepared launch is never labeled a successful live-Jev gameplay test.

Provisional performance gates: zero game-thread HTTP waits; stop takes effect within one client tick; no unbounded request backlog; no duplicated items in deterministic transaction/restart tests; ten-agent tests preserve 20 TPS on the agreed reference hardware under a bounded scenario. Establish a baseline and tune observation/pathfinding budgets before assigning a numeric overhead target.

## 12. Implementation sequence and completion gates

| Stage | Work | Completion evidence |
|---|---|---|
| 0. Specification and baseline | Pin toolchain, establish two artifact variants, define shared inference contracts and provider configuration, create scripted test launches and fake APIs | Empty client/server boot, client-only join to unmodified compatible server, hidden-client prerequisite verified; Vercel default and direct provider selectable |
| 1. Resolve technical risks | Real Jev latency/schema/goal interpretation probes on BOTH providers, Vercel evaluation transport proof; custom entity interaction context; programmatic chest/crafting; `/msg` name routing; player-like ticking region | Mixed typed questions and real Jev-directed movement/mining verified on both routes; custom body takes damage, eats, dies, operates a chest/crafting table; normal player `/msg` preserved; remote chunks stay loaded with tested block/entity ticking |
| 2. Shared runtime and takeover | Observations, candidate registry, asynchronous decision loop, cancellation, instruction overlay, basic locomotion/mining/placement | Player follows text goal, mines and places normally; immediate stop; delayed responses cannot resume control; remote compatible server path verified |
| 3. Companion foundation | Spawn egg/grants, unique names, registry/cap, role lists, public/private chat, survival body, moving chunk regions and save/load | Two independent named Jevs obey their own owners, hear public chat, isolate private chat, persist, keep working away from humans, and cannot duplicate eggs or items |
| 4. Survival and inventory competence | Recipe graph, acquisition/delivery, equipment, containers/workstations, combat, food, exploration and recovery | Logs-to-tools-to-resources task; requested chest delivery; survival/combat fixtures; concurrent inventory cases in both modes |
| 5. Full action surface and building | Remaining movement/travel/workstations, mounts/portals, text/maps, creative, teleport permissions, blueprint construction | Capability matrix covers all agreed vanilla families with mechanical tests and separate Jev outcome measurements |
| 6. Ten-Jev operation | Scheduling, ten distant loading regions, task reservations, cost controls, reconnect/restart recovery, server compatibility matrix | Repeated one/five/ten-agent runs, bounded queues/cost/ticket footprints, stable server/client timing, permissions/cap and loaded regions restored across restart |
| 7. Release hardening | Packaging, configuration/migrations, supported-mod adapters, installation guide and evidence report | Fresh-install/reinstall/save migration tests; full supported matrix passes; unresolved compatibility explicitly documented |

Stages 0–1 are feasibility gates, not throwaway demos. If the embodiment proof fails, revise that architecture before expanding the action library. If Jev cannot resolve a class of instructions reliably, improve candidates/questions and evaluate again; do not hide the issue by introducing an unrequested LLM.

The first satisfying vertical slice should exist before the full survival library: type “collect four logs” in takeover mode, then issue the same order to a named companion, observe normal mining and pickup, and stop either mid-action. This proves both bodies share useful intelligence while preserving the much larger required scope.

No defensible calendar estimate follows from documentation alone. Stage 1 measures the two largest uncertainties—Jev's Minecraft decision quality and player-compatible entity mechanics—after which work can be estimated by tested capability families.

## 13. Risks to track explicitly

| Risk | How the plan addresses it |
|---|---|
| Type-safe but incorrect decisions | Local validity checks, held-out prompts/worlds, calibrated task-specific thresholds, verified outcomes |
| Server custom-entity mechanics diverge from players | Early interaction-context proof and mechanical comparison against actual player behavior |
| Complicated instructions exceed candidate coverage | Candidate recall tests, compositional goal graph, precise unsupported/clarification replies |
| Resource packs or unusual mods expose information differently | Conservative observation contract, per-channel coverage and explicit adapters |
| Ten-agent quota/latency/cost | Account benchmark, shared scheduler, adaptive frequency, token accounting and configurable budget |
| Gateway evaluation interface differs or changes | Separate adapters, pinned experimental SDK/protocol where applicable, schema contract fixtures, live tests for both routes |
| Inventory duplication or cross-agent state | One source of truth, per-agent context, transaction reconciliation and restart tests |
| Private information or unauthorized instructions cross boundaries | UUID-based roles, routing before inference, provenance-aware memory, isolation tests |
| Remote servers reject automation or interfaces differ | Client-only packaging and published compatibility matrix; respect authoritative denials |
| Remote Jevs stop simulating or consume excessive chunks | Required moving loading/ticking regions, player-proximity audits, persistent ticket recovery, ten-distant-Jev benchmarks |

The recommended starting point is Stage 0 followed immediately by the Stage 1 feasibility proofs. The final product remains the full dual-mode, player-capable mod described here; intermediate milestones are evidence checkpoints, not substitutions for that scope.
