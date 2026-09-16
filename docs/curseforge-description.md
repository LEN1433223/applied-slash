# CurseForge 项目描述(可直接粘贴)

> 用法:CurseForge 项目页 → **Description** 编辑器 ← 粘贴下面「正文」部分。编辑器支持 Markdown(标题、表格、代码块、粗体),粘贴后检查一遍表格渲染是否正常。
> 建议:**英文正文放最前**(CurseForge 是英文受众为主),中文段落接在后面;如果你只面向中文玩家,只留中文那一段也行。

---

## ✅ 正文(从下一行开始整段复制)

# Applied Slash — SlashBlade & Unstackable Storage Cells

**Storage built for type-heavy workloads.** AE2's own storage cells are hard-clamped to **63 types**. But SlashBlades, enchanted gear, and worn tools are *each their own storage key* — 63 is nowhere near enough. Applied Slash adds two ME storage cells with a custom cell inventory that raises the type limit into the thousands and moves the payload into world-side sharded storage.

**1.1.0 adds the gameplay half:** five **charged SlashBlades** you have to keep powered, and a **blade charger** that runs off your ME network.

---

## The two cells

### SlashBlade Storage Cell

* **Accepts SlashBlades only** — a two-layer test (item tag `slashblade:swords` + `ItemSlashBlade` class check) so addon blades are covered too
* Default limit: **5000 types** (configurable 64 – 20000)
* **Always asks this cell first.** Incoming blades land here regardless of any other storage's global priority
* **Runtime state is stripped on insert**, so the same blade counts as one key no matter what combat state it is in — this is the single most effective way to keep the per-tick network cost down

### Unstackable Item Storage Cell

* **Accepts non-stackable items only** (`getMaxStackSize() == 1`)
* **Excludes SlashBlades** (they always go to the blade cell — ownership is decided by rules, never by iteration order)
* **Excludes storage cells** (no cell-in-cell nesting)
* Default limit: **2000 types** (configurable 64 – 20000)
* **Strips nothing.** A tool's durability, enchantments and custom name *are* its identity — so every differently-worn tool takes one type slot

---

## Charged Blades (new in 1.1.0)

Five chargeable SlashBlades plus one Blade Charger. Energy is the blade's consumable: **a blade with no charge is completely unusable**.

| Registry name | Name | Energy cap |
|---|---|---|
| `applied_slash:charged_blade_pulse` | Charged Blade · Pulse | 200 |
| `applied_slash:charged_blade_resonance` | Charged Blade · Resonance | 400 |
| `applied_slash:charged_blade_surge` | Charged Blade · Surge | 800 |
| `applied_slash:charged_blade_overcharge` | Charged Blade · Overcharge | 1600 |
| `applied_slash:charged_blade_singularity` | Charged Blade · Singularity | 3200 |

All five blades and the charger are in **this mod's creative tab**.

**Shared values**

* Displayed damage is **13.14 on every blade** (blade state `baseAttackModifier = 13.14`), with **no starting enchantments**
* Attack speed is **identical to Resharped's own `slashblade:slashblade`** — the constructor arguments are taken byte-for-byte from Resharped's registration site (`ItemTierSlashBlade(40, 4.0F)` / `attackDamageIn = 4` / `attackSpeedIn = 0.0F`, i.e. 4.0 attacks per second)
* **1 point of energy per hit** (config `chargedBladeAttackCost`); only a real hit is charged
* **1 point = 100 AE** (config `chargedBladeAePerPoint`) — a fully charged Singularity costs ≈ **320 kAE**

**Zero energy means completely unusable**, enforced by three layers at once: the blade is **sealed** (Resharped itself blocks it), **attack damage is zeroed by an event**, and **right-click does not trigger the SA**.

**Blade Charger — `applied_slash:blade_charger`**

* Attaches to the **ME network**; it has an internal energy buffer and **actively draws from the network** (AE2's generic grid path, `extractAEPower`) to charge the blade in its slot
* Default rate **1 point/tick** (`chargedBladeChargePerTick`), idle drain **1 AE/t** (`chargedBladeIdleDrain`)

**Insert quantization — why charged blades don't blow up your cells**

Energy becomes part of the AE2 storage key, so without care every energy value would be its own type. On insert, energy is **rounded down into 10 levels** (`chargedBladeQuantizeLevels = 10`), while **full energy is kept exact** (`chargedBladeQuantizeKeepFull = true`) so "store it full, take it out full" always holds. This caps a blade at **11 possible keys** (0, level×1 … level×9, full).

**Art attribution:** the models and textures of these five blades **reuse assets shipped with SlashBlade: Resharped** (`agito` / `dios` / `muramasa` / `sange` / `yamato`). Copyright belongs to the original author **`flammpfeil`** and the Resharped maintainers; this mod only **references** them and does not redistribute them.

---

## Compatibility

| Component | Version |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+** *(required)* |
| SlashBlade: Resharped | **2.0.7+** *(required since 1.1.0 — the blade cell and the charged blades both need it)* |
| Environment | Client **and** dedicated server |

Since **1.1.0**, both AE2 and SlashBlade: Resharped are **required** dependencies (in 1.0.0 they were optional): the charged blades extend `ItemSlashBlade`, and the cells and the charger rely on AE2's grid and energy APIs. Install both before upgrading — a pack that is missing one of them will be stopped by FML at startup.

---

## Performance (measured, not estimated)

The per-tick network cache rebuild is what matters, and it scales with the **number of distinct keys visible on your whole network**. Measured on a dedicated server, single cell:

| Types | Best-case per-tick cost |
|---|---|
| 200 | 0.011 ms |
| 1000 | 0.090 ms |
| 2000 | 0.292 ms |
| 5000 | 0.959 ms |

The bulk of that cost happens inside **AE2's own `KeyCounter`**, not in this mod — so the only real lever is how many distinct keys you keep on the network. That is exactly why the type limits exist, and why they are configurable.

Other measured numbers:

* Cell item NBT: **constant 1089 bytes**, no matter how much is stored
* Payload lives in **16 world-side shards** — a single change rewrites only the dirty 1/16
* Insert: **~4.1 µs** per operation at 5000 types
* 5000 keys sync to the client as **~108 KB**

### For pack developers

| Config option | Default | Notes |
|---|---|---|
| `maxTypes` | `5000` | Blade cell type limit. **Size it to your server** |
| `unstackableMaxTypes` | `2000` | Unstackable cell type limit |
| `stripRuntimeState` | `true` | Keep this on — it merges the same blade across combat states |
| `acceptBeyondMaxTypes` | `false` | Leave off. Turning it on removes the only performance guard |

**Suggestion:** on a busy pack, set `maxTypes` to **1000–2000** (0.09–0.29 ms/tick). 5000 is fine when your tick budget has room.

### Charged blade / charger options (new in 1.1.0)

| Config option | Default | Notes |
|---|---|---|
| `chargedBladeAttackCost` | `1` | Energy points per **hit** |
| `chargedBladeMaxPulse`, `chargedBladeMaxResonance`, `chargedBladeMaxSurge`, `chargedBladeMaxOvercharge`, `chargedBladeMaxSingularity` | `200` / `400` / `800` / `1600` / `3200` | Energy cap of each of the five blades |
| `chargedBladeChargePerTick` | `1` | Energy points the charger feeds the blade per tick |
| `chargedBladeAePerPoint` | `100` | AE per 1 point of blade energy |
| `chargedBladeIdleDrain` | `1.0` | Charger idle drain (AE/t) |
| `chargedBladeQuantizeLevels` | `10` | Energy is rounded down to `cap / levels` on insert; raise it for less loss (more keys) |
| `chargedBladeQuantizeKeepFull` | `true` | Full energy is quantized exactly, so a full blade stays full |

---

## Known limitations (please read)

1. **Cells cannot be pre-filled, and their payload cannot be moved by copying the item.** Blade data lives world-side; the cell item is a key. Copying a cell into another save yields an empty shell, and the world side reports "blade data not found in this save" — stated openly in the tooltip.
2. **No cell-workbench partitioning and no upgrade cards** (fuzzy / inverter / equal distribution / void). Deliberate: partitioning is meaningless for a cell that only accepts specific items, and a whitelist with no effect would be a fake feature.
3. **Bundled dependencies are not included** — install AE2 **and** SlashBlade: Resharped separately (both are required since 1.1.0).
4. **Stored charged blades lose a little energy to quantization.** Energy is rounded down into 10 levels on insert, so one store costs at most one level minus 1 (≤ 10 % of the cap); full energy is stored exactly. Raise `chargedBladeQuantizeLevels` to 40 for ≤ 2.5 % loss (up to 41 keys per blade).
5. **1.1.0 is a breaking change for dependencies.** AE2 and SlashBlade: Resharped moved from optional to **required**, so a pack that used only the Unstackable Item Storage Cell and lacks one of them will be stopped by FML at startup.

---

## Verification

These are automated results, not in-game impressions:

* **GameTests: 20/20 pass** — 12 storage-cell tests + 8 charged-blade/charger tests, with real assertions
* `build` succeeds; `verifyResources` passes **all 26 checks**
* Measured on a **headless dedicated server** (single cell): `getAvailableStacks` ≈ **0.29 ms** at 2000 types, ≈ **0.96 ms** at 5000 types; one insert ≈ **4.14 µs**
* Released version: **1.1.0** (`applied_slash-1.1.0.jar`)

Client-side presentation (screens, tooltips, feel, looks) is outside the automated scope and is for players to judge in game.

---

## Source & license

* Source: https://github.com/LEN1433223/applied-slash
* License: **MIT**

Third-party dependencies (Applied Energistics 2, SlashBlade, NeoForge) keep their own licenses and are not redistributed here.

---

# 简体中文

**Applied Slash** 为 AE2 添加两个**面向"类型数"的 ME 存储元件**。AE2 原生存储单元的类型被硬编码钳在 **63**,而拔刀剑、附魔装备、带耐久或命名的工具**每一件都是独立的存储键** —— 63 远远不够。本模组用自研库存把类型上限抬到几千,并把载荷移到世界侧分片存储。

**1.1.0 补齐了玩法的一半**:5 把需要持续供电的**充能拔刀剑**,以及一台**从 ME 网络取电的拔刀剑充能器**。

### 两个元件

* **SlashBlade存储元件**:只收拔刀剑(物品标签 + `ItemSlashBlade` 类判定双保险,覆盖附属刀)。默认 **5000 类型**。**拔刀剑进入网络时永远先问本盘**,与其它盘的全局优先级无关。入库时**剥离运行时状态**,同一把刀不论战斗状态都算一个键 —— 这是压低每 tick 网络成本最有效的手段。
* **不可堆叠物品存储元件**:只收不可堆叠物品(`getMaxStackSize() == 1`),并**排除拔刀剑**(刀永远进刀盘)与**存储元件类物品**(不出现元件套元件)。默认 **2000 类型**。**不剥离任何状态** —— 工具耐久、附魔、名字就是它的身份。

### 充能拔刀剑(1.1.0 新增)

5 把可充能拔刀剑 + 1 台拔刀剑充能器。能量是刀的消耗品:**没电的刀完全不可用**。

| 注册名 | 中文名 | 能量上限 |
|---|---|---|
| `applied_slash:charged_blade_pulse` | 充能刀·脉冲 | 200 |
| `applied_slash:charged_blade_resonance` | 充能刀·谐振 | 400 |
| `applied_slash:charged_blade_surge` | 充能刀·涌流 | 800 |
| `applied_slash:charged_blade_overcharge` | 充能刀·超荷 | 1600 |
| `applied_slash:charged_blade_singularity` | 充能刀·奇点 | 3200 |

5 把刀与充能器都已进入**本模组的创造模式物品栏**。

**统一数值**:面板伤害统一 **13.14**,**无初始附魔**;攻击速度与重锋本体 `slashblade:slashblade` **完全一致**(构造实参逐字节取自重锋注册处:`ItemTierSlashBlade(40, 4.0F)` / `attackDamageIn = 4` / `attackSpeedIn = 0.0F`,即 4.0 次/秒满攻速);每次**命中**消耗 **1 点**能量(`chargedBladeAttackCost`,只有真正命中才扣);**1 点 = 100 AE**(`chargedBladeAePerPoint`),满充「奇点」≈ **320 kAE**。

**能量为 0 时完全不可用**,三层同时生效:刀被**封印**(由重锋自身拦住)+ **攻击伤害被事件归零** + **右键不触发 SA**。

**拔刀剑充能器 `applied_slash:blade_charger`**:接在 **ME 网络**上的方块,内部有能量缓冲并**主动从网络取电**(走 AE2 网格的通用取电路径 `extractAEPower`)给槽里的刀充能;默认 **1 点/tick**(`chargedBladeChargePerTick`),空闲耗电 **1 AE/t**(`chargedBladeIdleDrain`)。

**入库量化 —— 充能刀为什么不会撑爆元件**:能量值会进入 AE2 的存储键,不量化则每个能量值都是一个新类型。入库时能量**向下取整到 10 档**(`chargedBladeQuantizeLevels = 10`),而**满能量取精确值**(`chargedBladeQuantizeKeepFull = true`),保证「满能量入库 → 取出仍是满的」;每把刀最多只有 **11 个键**(0、档宽×1 … 档宽×9、满)。

**美术来源致谢**:这 5 把刀的模型/贴图**复用 SlashBlade: Resharped(重锋)自带的资源**(`agito` / `dios` / `muramasa` / `sange` / `yamato`),版权归原作者 **`flammpfeil`** 与重锋维护者所有;本模组仅以**引用方式**使用,不重新分发。

### 兼容性

Minecraft **1.21.1** · NeoForge **21.1.250+** · AE2 **19.2.17+**(**必需**)· SlashBlade **2.0.7+**(**必需**,刀盘与充能刀都需要)· 客户端与专用服务端皆可。

**1.1.0 起 AE2 与 SlashBlade 都是必需前置**(1.0.0 里它们是可选):5 把充能刀继承 `ItemSlashBlade`,充能器与存储元件都依赖 AE2 的网格与能量 API。升级前请先确认两者都已安装,否则会被 FML 拦在启动阶段。

### 性能(实测)

每 tick 的全网缓存重建成本取决于**整个网络可见的不同键数**,单盘实测:200 类型 0.011 ms、1000 → 0.090、2000 → 0.292、5000 → 0.959。这笔开销的大头在 **AE2 自己的 `KeyCounter`** 里,不在本模组 —— 所以唯一的旋钮就是"别让网络里同时挂着太多独一无二的键",这也是类型上限存在的原因,且可配置。

**整合包作者建议**:繁忙的包里把 `maxTypes` 设成 **1000–2000**(0.09–0.29 ms/tick);TPS 有余量再上 5000。`stripRuntimeState` 保持开启,`acceptBeyondMaxTypes` 保持关闭(它是性能护栏)。

**充能刀 / 充能器配置项**(与上面的元件配置同在 `applied_slash-common.toml`):`chargedBladeAttackCost`、`chargedBladeMaxPulse`、`chargedBladeMaxResonance`、`chargedBladeMaxSurge`、`chargedBladeMaxOvercharge`、`chargedBladeMaxSingularity`、`chargedBladeChargePerTick`、`chargedBladeAePerPoint`、`chargedBladeIdleDrain`、`chargedBladeQuantizeLevels`、`chargedBladeQuantizeKeepFull`。量化损失嫌大就把 `chargedBladeQuantizeLevels` 调高(代价是更多键),充能太慢就调高 `chargedBladeChargePerTick`(代价是更费 AE)。

### 已知限制

物品**无法预置、也无法靠复制物品转移载荷**(元件物品只是一个钥匙,复制到别的存档只会得到空壳,世界侧会明确提示);**不支持工作台分区与升级卡**(刻意取舍,避免"装了没效果"的假功能);**不包含依赖**,请自行安装 AE2 **与** SlashBlade(**1.1.0 起两者都是必需前置**);**充能刀入库时能量有量化损失**(向下取整到 10 档,单次损失上界 = 档宽 − 1,≤ 上限的 10%;满能量取精确值故无损失,`chargedBladeQuantizeLevels` 调到 40 可降到 ≤ 2.5%)。

### 验证证据(自动化,非游戏内手感)

**GameTest 20/20 通过**(12 条存储元件 + 8 条充能刀/充能器,含真断言);`build` 成功、`verifyResources` **26 项全过**;无头专用服务器单盘实测:`getAvailableStacks` 2000 类型 ≈ **0.29 ms**、5000 类型 ≈ **0.96 ms**,单次 insert ≈ **4.14 µs**;发布版本 **1.1.0**(`applied_slash-1.1.0.jar`)。客户端表现(界面、tooltip、手感、外观)不在自动化范围内,由玩家自行验收。

**许可证 MIT** · 源码 https://github.com/LEN1433223/applied-slash

## ✅ 正文结束

---

## 其余字段(填在项目创建向导里,不是 Description)

| 字段 | 值 |
|---|---|
| Project name | `Applied Slash` |
| Summary | `AE2 storage cells for type-heavy workloads — thousands of unique SlashBlades, or any non-stackable items.` |
| Category | Storage |
| Game version | 1.21.1 |
| Mod loader | **NeoForge only** |
| License | MIT |
| Environment | Client and Server |
| Relation: Applied Energistics 2 | **Required**(**1.1.0 起**为必需前置:元件注册与充能器取电都依赖它) |
| Relation: SlashBlade: Resharped | **Required**(**1.1.0 起**为必需前置:5 把充能刀继承 `ItemSlashBlade`) |
| Relation: NeoForge | Required |
| Source URL | https://github.com/LEN1433223/applied-slash |

## 上传文件时填的 Changelog(1.1.0)

```
Charged Blades + Blade Charger.

Five chargeable SlashBlades (Pulse 200 / Resonance 400 / Surge 800 / Overcharge 1600 / Singularity 3200),
plus the Blade Charger block that attaches to the ME network and actively draws AE from it:
- 13.14 displayed damage on every blade, no starting enchantments, attack speed identical to Resharped's own blade
- Zero energy = completely unusable (sealed blade + damage zeroed by event + no SA on right-click)
- 1 energy point per hit; 1 point = 100 AE, so a fully charged Singularity costs ~320 kAE
- Charger: 1 point/tick by default, 1 AE/t idle drain
- Insert quantization: energy is rounded down into 10 levels (full energy kept exact), so a blade occupies
  at most 11 keys instead of one key per energy value
- All blades and the charger are in this mod's creative tab

BREAKING: AE2 and SlashBlade: Resharped are now REQUIRED dependencies (they were optional in 1.0.0).
Install both before upgrading.

Blade models/textures reuse assets from SlashBlade: Resharped (agito / dios / muramasa / sange / yamato);
copyright belongs to flammpfeil and the Resharped maintainers.

Verification (automated): GameTests 20/20 pass (12 cell + 8 charged blade/charger), build OK,
verifyResources 26/26. Headless dedicated server, single cell: getAvailableStacks ~0.29 ms at 2000 types,
~0.96 ms at 5000 types; one insert ~4.14 us.
```

## 上传文件时填的 Changelog(1.0.0)

```
First release.

Two AE2 storage cells with a custom cell inventory (not affected by AE2's native 63-type clamp):
- SlashBlade Storage Cell: accepts SlashBlades only, 5000 types by default, key-level insert priority,
  strips runtime state so the same blade shares one key.
- Unstackable Item Storage Cell: accepts non-stackable items only, 2000 types by default, strips nothing.

Measured on a headless dedicated server (single cell): 0.011 ms/tick at 200 types, 0.090 at 1000,
0.292 at 2000, 0.959 at 5000. Cell item NBT stays at 1089 bytes; payload lives in 16 world-side
shards so a change rewrites only 1/16.
```
