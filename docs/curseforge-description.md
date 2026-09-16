# CurseForge 项目描述(可直接粘贴)

> 用法:CurseForge 项目页 → **Description** 编辑器 ← 粘贴下面「正文」部分。编辑器支持 Markdown(标题、表格、代码块、粗体),粘贴后检查一遍表格渲染是否正常。
> 建议:**英文正文放最前**(CurseForge 是英文受众为主),中文段落接在后面;如果你只面向中文玩家,只留中文那一段也行。

---

## ✅ 正文(从下一行开始整段复制)

# Applied Slash — SlashBlade & Unstackable Storage Cells

**Storage built for type-heavy workloads.** AE2's own storage cells are hard-clamped to **63 types**. But SlashBlades, enchanted gear, and worn tools are *each their own storage key* — 63 is nowhere near enough. Applied Slash adds two ME storage cells with a custom cell inventory that raises the type limit into the thousands and moves the payload into world-side sharded storage.

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

## Compatibility

| Component | Version |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+** *(required to use the cells)* |
| SlashBlade: Resharpened | **2.0.7+** *(optional — only the blade cell needs it)* |
| Environment | Client **and** dedicated server |

Without AE2 nothing is registered (a log line says so). Without SlashBlade the cells still exist but accept nothing — the tooltip states this instead of pretending the cell is empty.

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

---

## Known limitations (please read)

1. **Cells cannot be pre-filled, and their payload cannot be moved by copying the item.** Blade data lives world-side; the cell item is a key. Copying a cell into another save yields an empty shell, and the world side reports "blade data not found in this save" — stated openly in the tooltip.
2. **No cell-workbench partitioning and no upgrade cards** (fuzzy / inverter / equal distribution / void). Deliberate: partitioning is meaningless for a cell that only accepts specific items, and a whitelist with no effect would be a fake feature.
3. **Bundled dependencies are not included** — install AE2 (and SlashBlade if you want the blade cell) separately.

---

## Source & license

* Source: https://github.com/LEN1433223/applied-slash
* License: **MIT**

Third-party dependencies (Applied Energistics 2, SlashBlade, NeoForge) keep their own licenses and are not redistributed here.

---

# 简体中文

**Applied Slash** 为 AE2 添加两个**面向"类型数"的 ME 存储元件**。AE2 原生存储单元的类型被硬编码钳在 **63**,而拔刀剑、附魔装备、带耐久或命名的工具**每一件都是独立的存储键** —— 63 远远不够。本模组用自研库存把类型上限抬到几千,并把载荷移到世界侧分片存储。

### 两个元件

* **SlashBlade存储元件**:只收拔刀剑(物品标签 + `ItemSlashBlade` 类判定双保险,覆盖附属刀)。默认 **5000 类型**。**拔刀剑进入网络时永远先问本盘**,与其它盘的全局优先级无关。入库时**剥离运行时状态**,同一把刀不论战斗状态都算一个键 —— 这是压低每 tick 网络成本最有效的手段。
* **不可堆叠物品存储元件**:只收不可堆叠物品(`getMaxStackSize() == 1`),并**排除拔刀剑**(刀永远进刀盘)与**存储元件类物品**(不出现元件套元件)。默认 **2000 类型**。**不剥离任何状态** —— 工具耐久、附魔、名字就是它的身份。

### 兼容性

Minecraft **1.21.1** · NeoForge **21.1.250+** · AE2 **19.2.17+**(使用元件必须装)· SlashBlade **2.0.7+**(可选,只有刀盘需要)· 客户端与专用服务端皆可。

### 性能(实测)

每 tick 的全网缓存重建成本取决于**整个网络可见的不同键数**,单盘实测:200 类型 0.011 ms、1000 → 0.090、2000 → 0.292、5000 → 0.959。这笔开销的大头在 **AE2 自己的 `KeyCounter`** 里,不在本模组 —— 所以唯一的旋钮就是"别让网络里同时挂着太多独一无二的键",这也是类型上限存在的原因,且可配置。

**整合包作者建议**:繁忙的包里把 `maxTypes` 设成 **1000–2000**(0.09–0.29 ms/tick);TPS 有余量再上 5000。`stripRuntimeState` 保持开启,`acceptBeyondMaxTypes` 保持关闭(它是性能护栏)。

### 已知限制

物品**无法预置、也无法靠复制物品转移载荷**(元件物品只是一个钥匙,复制到别的存档只会得到空壳,世界侧会明确提示);**不支持工作台分区与升级卡**(刻意取舍,避免"装了没效果"的假功能);**不包含依赖**,请自行安装 AE2(需要刀盘再装 SlashBlade)。

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
| Mod loader | NeoForge |
| License | MIT |
| Environment | Client and Server |
| Relation: Applied Energistics 2 | **Required**(没它元件不注册 —— 功能上必需,虽然技术上能加载) |
| Relation: SlashBlade: Resharpened | Optional |
| Relation: NeoForge | Required |
| Source URL | https://github.com/LEN1433223/applied-slash |

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
