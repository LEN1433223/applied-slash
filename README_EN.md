# Applied Slash · SlashBlade Storage Cell

**English** | [简体中文](README.md)

> **Applied Slash** is an [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) addon that adds storage cells built for **type-heavy** workloads.
> AE2's built-in storage cells are hard-clamped to **63 types**. But items like SlashBlades, enchanted gear, and tools with durability or custom names are **each their own storage key** — 63 is nowhere near enough. This mod uses a custom cell inventory to raise the type limit into the thousands, and moves the payload into world-side sharded storage.

**New in 1.1.0** — **five charged SlashBlades** plus a **Blade Charger**: energy is the blade's consumable, a blade with no charge is completely unusable, and the charger draws power from your ME network. See the "Charged Blades" section below.

---

## Compatibility

| Component | Version |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+** (**required**) |
| SlashBlade: Resharped | **2.0.7+** (**required** — both the blade cell and the charged blades need it) |
| Environment | Client + dedicated server |

> **Since 1.1.0 both are required dependencies** (in 1.0.0 they were optional). The five charged blades extend `ItemSlashBlade` — blade state *is* the blade's core state — and both the cells and the charger rely on AE2's grid and energy APIs. The runtime `ModList.get().isLoaded(...)` guard is still there, but it no longer serves as an "optional dependency" check: it is now **class-loading isolation**, so that classes referencing `appeng.*` / `mods.flammpfeil.*` are only loaded after the guard passes.

---

## The two cells

### 1. SlashBlade Storage Cell — `applied_slash:slash_blade_cell`

- **Accepts SlashBlades only** (item tag `slashblade:swords` plus an `ItemSlashBlade` class check — a two-layer test that also covers addon blades)
- Default type limit **5000** (configurable, 64..20000)
- **Key-level insert priority**: incoming SlashBlades **always ask this cell first**, regardless of any other storage's global priority
  (Evidence: the first pass of AE2's `NetworkStorage.insert` only checks `isPreferredStorageFor` and **ignores priority entirely**; AE2's own `BasicCellInventory` does not override that method.)
- **Strips runtime state** (on by default): the same blade is treated as one storage key no matter what combat state it is in, which keeps the type count from inflating. This is the single most effective way to keep the per-tick cost down.

### 2. Unstackable Item Storage Cell — `applied_slash:unstackable_item_cell`

- **Accepts non-stackable items only** (`getMaxStackSize() == 1`), and **excludes SlashBlades** (so blades always land in the blade cell — ownership is decided by rules, never by iteration order) **and storage-cell items** (no cell-in-cell nesting)
- Default type limit **2000** (configurable, 64..20000)
- **Strips nothing**: a tool's durability, enchantments and custom name *are* its identity. Not a single byte is removed
  ⇒ every differently-worn tool occupies one type slot, so `unstackableMaxTypes` is the real capacity limit

---

## Charged Blades (new in 1.1.0)

Five chargeable SlashBlades plus one Blade Charger: energy is the blade's consumable. **A blade with no charge is completely unusable** — only a charged blade can cut.

### The five blades

| Registry name | Name | Energy cap |
|---|---|---|
| `applied_slash:charged_blade_pulse` | Charged Blade · Pulse | 200 |
| `applied_slash:charged_blade_resonance` | Charged Blade · Resonance | 400 |
| `applied_slash:charged_blade_surge` | Charged Blade · Surge | 800 |
| `applied_slash:charged_blade_overcharge` | Charged Blade · Overcharge | 1600 |
| `applied_slash:charged_blade_singularity` | Charged Blade · Singularity | 3200 |

All five blades and the charger are added to **this mod's creative tab**.

### Shared values

| Property | Value | Basis |
|---|---|---|
| Displayed damage | **13.14** (all of them) | Blade state `baseAttackModifier = 13.14` (with `refine = 0`) |
| Starting enchantments | **none** | The blade state carries no enchantments |
| Attack speed | **identical** to Resharped's own `slashblade:slashblade` | Constructor arguments taken byte-for-byte from Resharped's registration site: `ItemTierSlashBlade(40, 4.0F)` / `attackDamageIn = 4` / `attackSpeedIn = 0.0F`, i.e. **4.0 attacks per second** at full speed |
| Cost per **hit** | **1 point** of energy | Config `chargedBladeAttackCost`; only a real hit is charged (the charging entry point is `hurtEnemy`, whose call site is confirmed at bytecode level) |
| Energy conversion | **1 point = 100 AE** | Config `chargedBladeAePerPoint`; a fully charged Singularity ≈ **320 kAE** |

### Zero energy means completely unusable

Three layers are active at once:

1. **Sealed blade** — at zero energy the sealed flag is set, so Resharped itself blocks the blade;
2. **Attack damage zeroed by an event** — damage is forced to 0 through Resharped's native damage event;
3. **Right-click does not trigger the SA** — the special attack is blocked as well.

### Blade Charger — `applied_slash:blade_charger`

* A block that attaches to the **ME network**: it has its own internal energy buffer and **actively draws from the ME network** (via AE2's generic grid path, `extractAEPower`) to charge the blade in its slot;
* Default charge rate **1 point/tick** (`chargedBladeChargePerTick`);
* Idle drain **1 AE/t** (`chargedBladeIdleDrain`, managed by the AE2 grid node — drawn whenever the node is online).

### Insert quantization: why charged blades don't blow up your cells

When a charged blade is stored in the SlashBlade Storage Cell, its energy is **rounded down into 10 levels** (`chargedBladeQuantizeLevels = 10`); **full energy is kept exact** (`chargedBladeQuantizeKeepFull = true`), which guarantees "store it full, take it out full".

The reason: **the energy value becomes part of the AE2 storage key**, so without quantization every single energy value would be a distinct type. Quantization caps the number of keys a blade can occupy at **11** (0, level×1 … level×9, full), suppressing key inflation from energy differences at the source.

### Art attribution

The models/textures of these five blades **reuse assets shipped with SlashBlade: Resharped** (`agito` / `dios` / `muramasa` / `sange` / `yamato`). Copyright belongs to the original author **`flammpfeil`** and the Resharped maintainers; this mod only **references** those assets and does not redistribute them.

### Automated verification evidence

* **GameTests 20/20 pass**: 12 storage-cell tests + 8 charged-blade/charger tests, with real assertions;
* `./gradlew build` succeeds and `./gradlew verifyResources` passes **all 26 checks**;
* Measured performance (headless dedicated server, single cell): `getAvailableStacks` ≈ **0.29 ms** at 2000 types and ≈ **0.96 ms** at 5000 types; a single insert ≈ **4.14 µs**;
* Version **1.1.0**, artifact `applied_slash-1.1.0.jar`.

> All of the above comes from automated builds / tests / the headless self-test. **Client-side presentation (screens, tooltips, feel, looks) is outside the automated scope** and is for players to verify in game.

---

## Configuration (`config/applied_slash-common.toml`)

| Option | Default | Description |
|---|---|---|
| `maxTypes` | `5000` | Type limit of the SlashBlade cell |
| `unstackableMaxTypes` | `2000` | Type limit of the unstackable cell |
| `stripRuntimeState` | `true` | Strip the blade runtime-state component on insert (**strongly recommended to keep on**) |
| `acceptBeyondMaxTypes` | `false` | Whether to keep accepting new blades once the type limit is reached. **Off = performance guard**; On = keeps accepting, type count becomes unbounded and the per-tick cost grows linearly |

**Measured per-tick network-cache rebuild cost** (`getAvailableStacks`, single cell):

| Types | Best-case time |
|---|---|
| 200 | 0.011 ms |
| 1000 | 0.090 ms |
| 2000 | 0.292 ms |
| 5000 | 0.959 ms (dedicated server measured: 1.247 ms average / 5.820 ms worst) |

> The bulk of that cost happens inside **AE2's own `KeyCounter`** (measured: the "KeyCounter.add side only" alone averages 1.9 ms for 5000 keys) — not in this mod. We cannot remove it; we can only control **how many distinct keys are visible on the network at once**. That is the entire reason the type limit exists. The cost scales with the **total number of distinct keys visible to the whole network** (AE2 merges keys across storages), so several full cells do not cost independent amounts.

### Charged blade / charger options (new in 1.1.0)

| Option | Default | Description |
|---|---|---|
| `chargedBladeAttackCost` | `1` | Energy points consumed per **hit** (shared by all five blades) |
| `chargedBladeMaxPulse` | `200` | Energy cap of Charged Blade · Pulse |
| `chargedBladeMaxResonance` | `400` | Energy cap of Charged Blade · Resonance |
| `chargedBladeMaxSurge` | `800` | Energy cap of Charged Blade · Surge |
| `chargedBladeMaxOvercharge` | `1600` | Energy cap of Charged Blade · Overcharge |
| `chargedBladeMaxSingularity` | `3200` | Energy cap of Charged Blade · Singularity |
| `chargedBladeChargePerTick` | `1` | Energy points the charger feeds the blade per tick |
| `chargedBladeAePerPoint` | `100` | AE required per 1 point of blade energy |
| `chargedBladeIdleDrain` | `1.0` | Idle drain of the charger (AE/t) |
| `chargedBladeQuantizeLevels` | `10` | Quantization levels on insert: energy is rounded down to a multiple of `cap / levels` |
| `chargedBladeQuantizeKeepFull` | `true` | Full energy is quantized exactly (no loss) |

> All 11 live in the same `applied_slash-common.toml` (one config file = one load/reload path).

---

## Design notes

| Aspect | Approach |
|---|---|
| Type limit | A **custom cell inventory** that does not extend `BasicCellInventory` (whose constructor clamps types to 63). The cell item deliberately does **not** implement `IBasicCellItem`, otherwise AE2's built-in handler would claim it and clamp it |
| Payload location | **16 world-side `SavedData` shards** (`applied_slash_blade_vault_0..15`). The item only carries a `vault_id` and a few-dozen-byte summary |
| Item NBT | **Constant 1089 bytes**, independent of how much is stored |
| Save granularity | Sharded by blade hash — **a change rewrites only the 1/16 that got dirty** |
| Client display | The server writes a **summary component**; the client reads tooltips and the LED from it and **never touches world-side data** |
| Thread safety | World-side access is guarded by `server.isSameThread()` (in single-player the client render thread can also see a non-null server) |
| Counter consistency | `recount()` after loading shards from disk; entries are looked up by content, not by assuming `hashCode` survives a save/load round-trip |

**Why `AEItemKey`'s hash cannot be used as the lookup basis**: entries are sharded by `|hashCode| % 16`, and AE2's key hash is derived from MC's component patch, which may no longer correspond after a save/load round-trip. Lookups therefore **try the hash bucket first and scan the remaining buckets on a miss** — buckets are only a save-granularity optimisation.

---

## Building

```bash
# Requires JDK 21
./gradlew build           # output: build/libs/applied_slash-1.1.0.jar
```

Development / verification tasks:

| Command | Purpose |
|---|---|
| `./gradlew runClient` | Development client |
| `./gradlew runServer` | Development server |
| `./gradlew runGameTestServer` | 20 GameTests (12 for the cells: capacity, over-limit rejection, reload, insert priority, filter semantics, …; 8 for the charged blades/charger: unusable at zero energy, usable after charging, per-hit cost, quantization and storage), all with real assertions |
| `./gradlew runServer -PselfTest` | **Headless dedicated-server self-test**: capacity, rejection, insert throughput, per-tick traversal cost, serialized size, shard writes — then shuts the server down |
| `./gradlew verifyResources` | Resource-chain check: models/parent chain/texture existence + PNG headers (16×16 / 8-bit / RGBA / non-interlaced) |
| `./gradlew generateTextures` / `texturePreviews` | Regenerate pixel-art textures and 8× previews (on a checkerboard background) |
| `./gradlew importArt` | Convert a large image in `art/` into a 16×16 texture |

> The release jar **excludes the `dev/` and `gametest/` packages**, so `/appliedslash testcell`, `-PselfTest`, `-PmodelDump` and similar developer diagnostics **are only available in development builds**.

---

## Known limitations (please read)

1. **Cells cannot be pre-filled, and their payload cannot be transferred by copying the item.** Blade data lives world-side; the cell item is merely a key. Copying a cell into another save yields an empty shell and the world side reports "blade data not found in this save" (stated in the tooltip rather than pretending the cell is empty).
2. **No cell-workbench partitioning and no upgrade cards** (fuzzy / inverter / equal distribution / void). This is a deliberate trade-off: partitioning is meaningless for a cell that only accepts specific items, and registering a whitelist that has no effect would ship a fake feature.
3. **`maxTypes` is a performance guard, not decoration.** Size it to your server (see the table above), and do not enable `acceptBeyondMaxTypes` without a limit.
4. **Ownership between the two cells is deterministic**: blades always go to the blade cell; the unstackable cell excludes them — never dependent on AE2's iteration order.
5. **Stored charged blades lose a little energy to quantization**: energy is rounded down into 10 levels, so a single store costs at most one level minus 1 (≤ 10 % of the cap); full energy is kept exact and therefore lossless. Raise `chargedBladeQuantizeLevels` to 40 for ≤ 2.5 % loss (at the cost of up to 41 keys per blade).
6. **1.1.0 is a breaking change**: AE2 and SlashBlade moved from optional to **required**. Packs that only used the Unstackable Item Storage Cell and have one of them missing will be stopped by FML at startup — make sure both are installed before upgrading.

---

## Documentation

| File | Contents |
|---|---|
| [`STORAGE-DESIGN.md`](STORAGE-DESIGN.md) | Path-by-path walkthrough of the storage logic + a side-by-side comparison against AE2's native cells, the creative cell, and third-party high-type cells (with bytecode evidence) |
| [`PERFORMANCE-TESTING.md`](PERFORMANCE-TESTING.md) | Three-tier performance testing handbook (self-test → GameTest → in-game) |
| [`FACTS-CHARGED-BLADES.md`](FACTS-CHARGED-BLADES.md) | API fact table for the charged blades (every signature obtained via `javap`; unverified items are marked as such) |

*(All three are currently written in Chinese.)*

---

## Not yet verified (stated plainly)

- **Production deployment**: only moddev's `runServer` / `runGameTestServer` have been exercised. Dropping `build/libs/*.jar` into a real NeoForge server's `mods/` folder has **not** been tested.
- **Client frame cost with thousands of terminal entries**: not measured (client UI is outside the automated verification scope).
- **Memory and save cost with real blade data** (as opposed to the narrow synthetic payload): the self-test uses synthetic blades; real blades carry more data.
- **Dedicated server without AE2**: only the code-level guard was constructed, never actually run (since 1.1.0 AE2 is a required dependency and the guard's job is class-loading isolation).
- **Client-side behaviour of the charged blades and the charger**: the charger screen, blade/charger tooltips and the charging visuals are **outside the automated verification scope** and need a player to accept them in game. What the automation does cover is server-side logic and item behaviour (the 8 charged-blade/charger GameTest assertions).

---

## License

[MIT](LICENSE).

Third-party dependencies (Applied Energistics 2, SlashBlade, NeoForge, and any build-time libraries) keep their own licenses and are **not** redistributed in this repository.

The models and textures of the five charged blades **reuse assets shipped with SlashBlade: Resharped** (agito / dios / muramasa / sange / yamato). Copyright belongs to the original author **`flammpfeil`** and the Resharped maintainers; this mod only **references** them — it neither redistributes them nor claims any rights over them.
