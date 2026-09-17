# JevCraft implementation handoff

Last updated: 2026-09-17 (America/Chicago)

## Mission and current state

The active objective is to implement and test the entire `JEVCRAFT_PLAN.md`, with the local Git repository and GitHub repository at `https://github.com/ewitulsk/JevCraft.git`.

The goal is **not complete**. The repository is a broad, tested feasibility implementation, but the capability matrix and verification document intentionally identify remaining plan gaps. Do not redefine completion around the current passing suite.

- Workspace: `D:\MinecraftMods\JevCraft`
- Remote: `origin https://github.com/ewitulsk/JevCraft.git`
- Branch: `main`
- Last pushed implementation commit: `8200db7` (`Add vanilla elytra flight and fall recovery`)
- Current worktree: three partially edited Java files for PvP policy/retreat, plus this handoff document
- Current compile state: red because the interrupted PvP slice calls a not-yet-added `canTarget(LivingEntity)` helper
- Last fully green state: commit `8200db7`, with all 63 GameTests and `gradlew build` passing

The user explicitly asked the current agent to stop and create this handoff. Preserve the partial edits; do not reset or discard them.

## Credential safety

The user supplied a Vercel AI Gateway key in the task conversation. Never copy it into this file, source control, configuration, shell history, logs, test artifacts, or command text. The project expects `AI_GATEWAY_API_KEY` only in the process environment. `.env`, `.env.*`, `secrets/`, `artifacts/`, build outputs, and logs are ignored.

Before every commit, use the existing split-prefix scan pattern without writing the full credential:

```powershell
$needle='vck_'+'83It'
$matches=rg -l --hidden --glob '!.git/**' --glob '!build/**' --glob '!artifacts/**' --fixed-strings $needle .
if($LASTEXITCODE -eq 0){$matches; throw 'credential fragment found'}
if($LASTEXITCODE -ne 1){throw 'secret scan failed'}
```

Live tests are opt-in and must receive the key only through the process environment:

```powershell
gradlew liveGatewaySmoke
gradlew liveGatewayScale
scripts/Test-HiddenTakeover.ps1 -LiveGateway
```

## Pushed milestones

Recent commits, newest first:

- `8200db7` — vanilla elytra flight, attached rocket boost, targeted damage-free landing/fall recovery, cancellation and action arbitration; 63 GameTests
- `1a46168` — explicit yaw-relative strafe, sprint, crouch, native jump, real crawl-sized collision and safe stop under a one-block ceiling; 60 GameTests
- `1c2c789` — fresh paid Vercel/Jev 1/5/10 typed-transport benchmark record
- `61a1d72` — operator-entitled directed creative flight with denial, stop, and revocation; 57 GameTests
- `b6efb74` — persisted operator-gated creative mode and bounded inventory create/delete; 55 GameTests; PowerShell 5.1 script compatibility
- `be0c515` — cancellable owned trident combat
- `ff9ef1a` — cancellable charged crossbow combat
- `6867fab` — shield and throwable splash-potion combat
- `465b70c` — ladder/scaffolding and surface/dive swimming
- `62d523f` — sign and book text actions
- `5b61f5c` — menu-driven inventory split/merge/swap/drop
- `5971871` — beacon activation and companion effect delivery

Earlier commits implement the dual inference providers, scheduler/cancellation/metrics, companion entity and persistence, permissions/chat routing, moving chunk tickets, takeover client, official-server client-only artifact, mining/placement, food/combat, containers, crafting, furnaces, and the remaining workstation slices. Use `git log --oneline` for full history.

## Live Vercel/Jev evidence

Fresh paid testing after account credit was added:

- One mixed typed smoke request to `typesafe-ai/jev`: 3 questions, 429 input tokens, 732 ms
- Scale benchmark: 48 additional requests, concurrency 2, all Choice/Score/Boolean schemas valid, no rate-limit response
- 1 actor × 3: 1,291 input / 222 output tokens, 1,262 ms wall, p50 276 ms, p95 732 ms
- 5 actors × 3: 6,455 input / 1,110 output tokens, 2,804 ms wall, p50 243 ms, p95 461 ms
- 10 actors × 3: 12,910 input / 2,220 output tokens, 3,982 ms wall, p50 235 ms, p95 323 ms
- Total estimated scale-run input cost: about $0.00082624 at the configured $0.04/million input-token estimate

Previously recorded live in-world evidence also includes a companion selecting/mining a visible log and a hidden takeover client making two decisions to mine/collect a log and place cobblestone, with independent server confirmation.

See `docs/BENCHMARKS.md` and `docs/VERIFICATION.md`. These prove transport and narrow gameplay paths, not general Jev competence or the required repeated 1/5/10-agent Minecraft task-success gate. The direct TypeSafe adapter has deterministic fake-endpoint coverage but no live result because no TypeSafe credential was supplied.

## Current uncommitted PvP/retreat slice

Do not assume this slice compiles. `gradlew compileJava` currently reports four errors, all from the missing `canTarget(LivingEntity)` method in `CompanionActionExecutor`.

Modified files:

### `src/main/java/dev/jevcraft/companion/JevCompanion.java`

Already added, not yet tested or committed:

- `playerCombatAllowed`, default false
- getter/setter
- NBT persistence under `PlayerCombatAllowed`

### `src/main/java/dev/jevcraft/JevCraft.java`

Already added, not yet tested or committed:

- persistence harness fixture sets and validates PvP authority
- `/jev pvp <name> enable|disable`
- operator-only `changePvpCapability`

Still needed:

- Register a `LivingIncomingDamageEvent` listener on `NeoForge.EVENT_BUS`.
- If the victim is a `ServerPlayer`, the damage source causing entity is a `JevCompanion`, and that companion has PvP disabled, cancel the damage. This closes incidental owned bow/crossbow/trident/potion hits, not just intentional target selection.
- Import `net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent`.
- Extend the command GameTest: ordinary owner must fail to enable PvP; server operator must succeed.

### `src/main/java/dev/jevcraft/companion/CompanionActionExecutor.java`

Already added, but incomplete:

- `retreatThreat`, `retreatDistance`, `retreatTicks`
- `beginRetreat(...)` validation/setup
- bow/crossbow/trident begin methods now return boolean and reject via missing `canTarget`
- melee calls missing `canTarget`
- splash/lingering potion use is conservatively rejected when PvP is off and a player is within two blocks of the aim point
- cancellation clears retreat state

Finish in this order:

1. Add a tick priority branch after manual/crawl control and before offensive actions:

   ```java
   if(retreatThreat!=null)return tickRetreat();
   ```

2. Add:

   ```java
   private boolean canTarget(LivingEntity target) {
       return target != null && (!(target instanceof Player) || companion.playerCombatAllowed());
   }
   ```

3. Implement `tickRetreat()` with these invariants:

   - stale goal: `INVALID`, `lastFailure="stale_goal"`, cancel
   - dead/removed/different-level threat: successful termination
   - timeout after 240 ticks: `INVALID`, `lastFailure="timeout"`
   - success when `distanceTo(threat) >= retreatDistance`
   - otherwise choose a horizontal point directly away and use native navigation at bounded speed (suggested 1.2)
   - refresh when navigation is done or every ten ticks
   - path failure: `INVALID`, `lastFailure="no_retreat_path"`

4. Add `!canTarget(...)` to `tickRanged`, `tickCrossbow`, and `tickTrident` validity checks so mid-action entitlement revocation cancels and restores charged weapons.

5. Existing callers can ignore the newly boolean begin-method results, so they should still compile after the helper is added.

6. Optionally extract the long aimed-potion condition into a helper, preserving conservative default-deny behavior around players.

### Required tests for this slice

Add GameTests in `src/main/java/dev/jevcraft/testing/JevGameTests.java`:

- Retreat from a stationary threat on a broad platform; initial distance below threshold, terminal `SUCCEEDED`, final distance at or above threshold.
- Default PvP denial for melee, bow, crossbow, trident, and harmful splash potion aimed at a nearby mock server player; no health or inventory/durability/ammunition changes.
- Operator-enabled PvP permits at least one real player damage path, preferably melee plus one owned projectile.
- Revoke PvP during a bow/crossbow/trident charge; `INVALID`, no player damage, exact inventory restoration.
- Extend `companionPersistsIdentityStateAndInventory` so the PvP flag survives NBT round trip.
- Extend the command authority GameTest near the bottom for `/jev pvp` owner denial/operator success.
- Prove the global incoming-damage guard cancels an incidental owned projectile hit while PvP is off.

Then update:

- README command list with `/jev pvp <name> enable|disable`
- README maturity summary
- `docs/CAPABILITY_MATRIX.md` combat row
- `docs/VERIFICATION.md` GameTest count and coverage

Suggested commit: `Add retreat and operator-gated PvP policy`.

## Verification commands

At pushed commit `8200db7`:

```powershell
.\gradlew.bat runGameTestServer
# All 63 required tests passed

.\gradlew.bat build
# Full and client-only artifacts passed
```

After completing the partial slice:

```powershell
.\gradlew.bat runGameTestServer
.\gradlew.bat build
git diff --check
```

Then run the split-prefix credential scan before staging. Do not commit ignored artifacts.

Long-running harnesses:

```powershell
.\scripts\Test-Persistence.ps1
.\scripts\Test-RemoteTicking.ps1
.\scripts\Test-AgentScaling.ps1
.\scripts\Test-HiddenTakeover.ps1
.\scripts\Test-VanillaServerCompatibility.ps1
```

All source `scripts/ProcessArguments.ps1` for Windows PowerShell 5.1 and PowerShell 7 compatibility. Persistence and remote ticking were explicitly reverified under Windows PowerShell 5.1 after that change.

## Architecture map

- `JEVCRAFT_PLAN.md` — requested scope and release gates
- `docs/CAPABILITY_MATRIX.md` — authoritative split between exposed mechanics, deterministic verification, live Jev evidence, and compatibility
- `docs/VERIFICATION.md` — established and non-established claims
- `docs/BENCHMARKS.md` — reproducible scale evidence and limitations
- `src/main/java/dev/jevcraft/JevCraft.java` — registration, commands, server events, verification fixtures
- `src/main/java/dev/jevcraft/companion/JevCompanion.java` — entity/NBT/goals/inventory/death/respawn/tickets
- `src/main/java/dev/jevcraft/companion/CompanionActionExecutor.java` — bounded goal-versioned gameplay actions
- `src/main/java/dev/jevcraft/companion/CompanionInteractionContext.java` — dedicated FakePlayer-backed vanilla interactions/menus
- `src/main/java/dev/jevcraft/companion/JevInferenceHost.java` — provider selection, scheduler, live decision acceptance
- `src/main/java/dev/jevcraft/inference/JsonInferenceProvider.java` — Vercel v4 evaluation and direct TypeSafe dialects
- `src/main/java/dev/jevcraft/client/TakeoverRuntime.java` — client-only takeover mechanics/inference
- `src/main/java/dev/jevcraft/testing/JevGameTests.java` — in-world mechanical suite
- `src/test/java/dev/jevcraft` — deterministic unit/provider contracts
- `scripts/` — end-to-end server/client/persistence/scaling harnesses

Action state is goal-versioned. `acceptGoal` and `stopNow` cancel inference/actions. Every new action should cancel prior transient actions, bound duration, validate inputs and authority again while ticking, preserve exact inventory costs, expose terminal failure reasons, and be tested against game state rather than only return values.

## Remaining plan gaps after PvP/retreat

These remain incomplete or insufficiently proven:

- Goal parsing and autonomous typed goal families are partial; autonomous companion behavior is still narrow.
- General recipe planning, acquisition, prerequisite/tool planning, delivery, recovery, and multi-step survival tasks.
- Automatic recipe graph planning and additional specialized/modded menus.
- Controlled boat travel (generic minecart mount/dismount is proven).
- Broad autonomous portal construction/use decisions (dimension/ticket lifecycle is proven).
- Comprehensive player-equivalent observation: lighting/transparency/resource packs, sound, maps/UI text, and modded channels.
- Third-party chat-mod coexistence.
- User-facing configuration UI, migrations, secure credential references, and interactive connection test.
- Blueprint generation and full multi-block construction verification.
- Broader offline-owner autonomous task completion.
- Live direct-TypeSafe route.
- Repeated 1/5/10-agent Minecraft task-success benchmarks; current scale tests cover transport and ticking separately.
- Full compatibility matrix, release packaging/signing/distribution, upgrade/migration testing, and long-duration soak/failure recovery.
- Broad live Jev decision quality beyond narrow mining/placement paths.

Re-read the full plan before claiming a row or stage complete. API exposure alone is not sufficient evidence.

## Worktree discipline

- Preserve all current edits; do not reset or check them out.
- Use `apply_patch` for edits.
- Stage only intended files and inspect `git diff --cached`.
- Push coherent green slices to `origin/main`.
- Keep limitations honest; never label a fake endpoint, build, or transport-only call as live gameplay success.

This handoff is intended to be committed separately. The three PvP/retreat source files should remain unstaged and uncommitted so the next agent can finish the interrupted slice in place.
