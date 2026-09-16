# Applied Slash · SlashBlade Storage Cell

**English** | [简体中文](README.md)

> **Applied Slash** is an [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) addon that adds storage cells built for **type-heavy** workloads.
> AE2's built-in storage cells are hard-clamped to **63 types**. But items like SlashBlades, enchanted gear, and tools with durability or custom names are **each their own storage key** — 63 is nowhere near enough. This mod uses a custom cell inventory to raise the type limit into the thousands, and moves the payload into world-side sharded storage.

---

## Compatibility

| Component | Version |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+** (optional dependency) |
| SlashBlade: Resharpened | **2.0.7+** (optional dependency; only needed for the SlashBlade cell) |
| Environment | Client + dedicated server |

**The mod loads fine without AE2 or SlashBlade.** Without AE2 no cell item is registered (a log line says so). Without SlashBlade the cell still exists but accepts nothing (the tooltip states this explicitly).

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
./gradlew build           # output: build/libs/applied_slash-1.0.0.jar
```

Development / verification tasks:

| Command | Purpose |
|---|---|
| `./gradlew runClient` | Development client |
| `./gradlew runServer` | Development server |
| `./gradlew runGameTestServer` | 12 GameTests (capacity, over-limit rejection, reload, insert priority, filter semantics, …) |
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

---

## Documentation

| File | Contents |
|---|---|
| [`STORAGE-DESIGN.md`](STORAGE-DESIGN.md) | Path-by-path walkthrough of the storage logic + a side-by-side comparison against AE2's native cells, the creative cell, and third-party high-type cells (with bytecode evidence) |
| [`PERFORMANCE-TESTING.md`](PERFORMANCE-TESTING.md) | Three-tier performance testing handbook (self-test → GameTest → in-game) |

*(Both are currently written in Chinese.)*

---

## Not yet verified (stated plainly)

- **Production deployment**: only moddev's `runServer` / `runGameTestServer` have been exercised. Dropping `build/libs/*.jar` into a real NeoForge server's `mods/` folder has **not** been tested.
- **Client frame cost with thousands of terminal entries**: not measured (client UI is outside the automated verification scope).
- **Memory and save cost with real blade data** (as opposed to the narrow synthetic payload): the self-test uses synthetic blades; real blades carry more data.
- **Dedicated server without AE2**: only the code-level guard was constructed, never actually run.

---

## License

[MIT](LICENSE).

Third-party dependencies (Applied Energistics 2, SlashBlade, NeoForge, and any build-time libraries) keep their own licenses and are **not** redistributed in this repository.
