# Applied Slash · SlashBlade 存储元件

[English](README_EN.md) | **简体中文**

> **Applied Slash** — 为 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 添加**面向"类型数"的专用存储元件**。
> AE2 原生存储单元的类型数被硬编码钳在 **63**;而拔刀剑、附魔装备、带耐久/命名的工具这类物品**几乎每一件都是独立的存储键**,63 远远不够。本模组用自研库存把类型上限抬到几千,并把载荷移到世界侧分片存储。

**English TL;DR** — An AE2 addon for MC 1.21.1 / NeoForge that adds storage cells for **type-heavy** workloads (thousands of unique SlashBlades, or any non-stackable items), bypassing AE2's 63-type clamp with a custom cell inventory and world-side sharded storage.

**1.1.0 新增玩法** — **5 把充能拔刀剑** + **拔刀剑充能器**:能量是刀的消耗品,没电的刀完全不可用,充能器接在 ME 网络上从网络取电。详见下方「充能拔刀剑」一节。

---

## 兼容性

| 项目 | 版本 |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+**(**必需前置**) |
| SlashBlade: Resharped(重锋) | **2.0.7+**(**必需前置**,刀盘与充能刀都需要它) |
| 环境 | 客户端 + 专用服务端 |

> **1.1.0 起两者都是必需前置**(1.0.0 里它们是可选依赖)。理由:5 把充能拔刀剑继承 `ItemSlashBlade`、刀身数据是刀的核心状态;充能器与存储元件都依赖 AE2 的网格与能量 API。运行期仍保留 `ModList.get().isLoaded(...)` 门卫,但它已不再承担"可选依赖"职责,而是**类加载隔离**——引用 `appeng.*` / `mods.flammpfeil.*` 的类只在门卫之后加载。

---

## 两个元件

### 1. SlashBlade存储元件 `applied_slash:slash_blade_cell`

- **只收拔刀剑**(物品标签 `slashblade:swords` + `ItemSlashBlade` 类判定双保险,能覆盖附属刀)
- 默认类型上限 **5000**(可调 64..20000)
- **键级优先入库**:拔刀剑进入网络时**永远先问本盘**,与其它盘的全局优先级无关
  (依据:AE2 的 `NetworkStorage.insert` 第一遍扫描只检查 `isPreferredStorageFor`,**这一遍完全不看优先级**;而 AE2 自带的 `BasicCellInventory` 不覆盖该方法)
- **剥离运行时状态**(默认开):同一把刀在不同战斗状态下算同一个存储键,避免类型数虚增 —— 这是压低每 tick 成本最有效的手段

### 2. 不可堆叠物品存储元件 `applied_slash:unstackable_item_cell`

- **只收不可堆叠物品**(`getMaxStackSize() == 1`),并**排除拔刀剑**(让刀永远进刀盘,归属只由规则决定)**和存储元件类物品**(防止"元件套元件")
- 默认类型上限 **2000**(可调 64..20000)
- **不做任何状态剥离**:工具耐久、附魔、自定义名称就是它的身份,一个字节都不动
  ⇒ 每件不同状态的工具各占一个类型位,`unstackableMaxTypes` 就是它的真实容量上限

---

## 充能拔刀剑(1.1.0 新增)

5 把可充能的拔刀剑 + 1 台拔刀剑充能器:能量是刀的消耗品,**没电的刀完全不可用**,有电才能挥砍。

### 5 把刀

| 注册名 | 中文名 | 能量上限 |
|---|---|---|
| `applied_slash:charged_blade_pulse` | 充能刀·脉冲 | 200 |
| `applied_slash:charged_blade_resonance` | 充能刀·谐振 | 400 |
| `applied_slash:charged_blade_surge` | 充能刀·涌流 | 800 |
| `applied_slash:charged_blade_overcharge` | 充能刀·超荷 | 1600 |
| `applied_slash:charged_blade_singularity` | 充能刀·奇点 | 3200 |

5 把刀与充能器都已进入**本模组的创造模式物品栏**。

### 统一数值

| 项目 | 值 | 依据 |
|---|---|---|
| 面板伤害 | **13.14**(统一) | 刀身数据 `baseAttackModifier = 13.14`(配合 `refine = 0`) |
| 初始附魔 | **无** | 刀身不预置任何附魔 |
| 攻击速度 | 与重锋本体 `slashblade:slashblade` **完全一致** | 构造实参逐字节取自重锋注册处:`ItemTierSlashBlade(40, 4.0F)` / `attackDamageIn = 4` / `attackSpeedIn = 0.0F`,即 **4.0 次/秒满攻速** |
| 每次**命中**消耗 | **1 点**能量 | 配置项 `chargedBladeAttackCost`;只有真正命中才扣(扣费入口是 `hurtEnemy`,调用点已在字节码层面确认) |
| 能量换算 | **1 点 = 100 AE** | 配置项 `chargedBladeAePerPoint`;满充「奇点」≈ **320 kAE** |

### 能量为 0 时完全不可用

三层同时生效,缺一不可:

1. **封刀(sealed)**——能量归零时置封印旗标,由重锋自身拦住;
2. **攻击伤害被事件归零**——通过重锋原生的伤害事件把伤害压到 0;
3. **右键不触发 SA**——刀技同样被拦住。

### 拔刀剑充能器 `applied_slash:blade_charger`

- 接在 **ME 网络**上的方块:内部自带能量缓冲,**主动从 ME 网络取电**(走 AE2 网格的通用取电路径 `extractAEPower`)给槽里的刀充能;
- 默认充能速率 **1 点/tick**(`chargedBladeChargePerTick`);
- 空闲耗电 **1 AE/t**(`chargedBladeIdleDrain`,由 AE2 网格节点托管,节点在线即耗)。

### 入库量化:充能刀为什么不会撑爆存储元件

充能刀存进 Slash存储元件时,能量会**向下取整到 10 档**(`chargedBladeQuantizeLevels = 10`);**满能量取精确值**(`chargedBladeQuantizeKeepFull = true`),保证「满能量入库 → 取出仍是满的」。

理由是:**能量值会进入 AE2 的存储键**,不量化则每一个能量值都会变成一个新类型。量化把每把刀可能出现的键数压到 **11 个**(0、档宽×1 … 档宽×9、满),从源头压制"能量差异导致键膨胀"。

### 美术来源致谢

这 5 把刀的模型/贴图**复用 SlashBlade: Resharped(重锋)自带的资源**(`agito` / `dios` / `muramasa` / `sange` / `yamato`),版权归原作者 **`flammpfeil`** 与重锋维护者所有;本模组仅以**引用方式**使用这些素材,不重新分发。

### 自动化验证证据

- **GameTest 20/20 通过**:12 条存储元件 + 8 条充能刀/充能器,含真断言;
- `./gradlew build` 成功、`./gradlew verifyResources` **26 项全过**;
- 实测性能(无头专用服务器,单个存储元件):`getAvailableStacks` 2000 类型 ≈ **0.29 ms**、5000 类型 ≈ **0.96 ms**;单次 insert ≈ **4.14 µs**;
- 版本 **1.1.0**,产物 `applied_slash-1.1.0.jar`。

> 上述证据全部来自自动化构建 / 测试 / 无头服务器自检;**客户端表现(界面、tooltip、手感、外观观感)不在自动化覆盖范围内**,由玩家自行验收。

---

## 配置(`config/applied_slash-common.toml`)

| 配置项 | 默认 | 说明 |
|---|---|---|
| `maxTypes` | `5000` | 拔刀剑元件的类型上限 |
| `unstackableMaxTypes` | `2000` | 不可堆叠元件的类型上限 |
| `stripRuntimeState` | `true` | 入库时剥离拔刀剑运行时状态组件(**强烈建议保持开启**) |
| `acceptBeyondMaxTypes` | `false` | 类型满后是否继续接收新刀。**关 = 性能护栏**;开 = 满盘也收、类型数无上限,每 tick 成本会线性上升 |

**实测每 tick 全网缓存重建成本**(`getAvailableStacks`,单盘):

| 类型数 | 最优耗时 |
|---|---|
| 200 | 0.011 ms |
| 1000 | 0.090 ms |
| 2000 | 0.292 ms |
| 5000 | 0.959 ms(服务端实测 平均 1.247 / 最差 5.820) |

> 这笔成本的大头发生在 **AE2 自己的 `KeyCounter`** 里(实测"仅 KeyCounter.add 侧"平均 1.9 ms / 5000 键),不在本模组。**我们无法消除它,只能控制"网络里同时有多少个不同的键"** —— 这就是类型上限存在的全部理由。成本随**整个网络可见键数**增长(AE2 把跨存储的键合并计数),所以多张满盘叠加不是各自独立。

### 充能刀 / 充能器配置项(1.1.0 新增)

| 配置项 | 默认 | 说明 |
|---|---|---|
| `chargedBladeAttackCost` | `1` | 充能刀每次**命中**消耗的能量点(5 把刀共用) |
| `chargedBladeMaxPulse` | `200` | 充能刀·脉冲的能量上限 |
| `chargedBladeMaxResonance` | `400` | 充能刀·谐振的能量上限 |
| `chargedBladeMaxSurge` | `800` | 充能刀·涌流的能量上限 |
| `chargedBladeMaxOvercharge` | `1600` | 充能刀·超荷的能量上限 |
| `chargedBladeMaxSingularity` | `3200` | 充能刀·奇点的能量上限 |
| `chargedBladeChargePerTick` | `1` | 充能器每 tick 给刀充入的能量点 |
| `chargedBladeAePerPoint` | `100` | 每 1 点刀能量需要的 AE |
| `chargedBladeIdleDrain` | `1.0` | 充能器空闲耗电(AE/t) |
| `chargedBladeQuantizeLevels` | `10` | 入库量化档数:能量向下取整到 `上限/档数` 的整数倍 |
| `chargedBladeQuantizeKeepFull` | `true` | 满能量的刀量化时取精确值(不计损失) |

> 11 项都在同一个 `applied_slash-common.toml` 里(单一配置文件 = 单一加载 / 重载路径)。

---

## 设计要点

| 维度 | 做法 |
|---|---|
| 类型上限 | **自研库存**,不继承 `BasicCellInventory`(其构造器把类型钳到 63);元件物品**刻意不实现 `IBasicCellItem`**,否则会被 AE2 原生 handler 抢走 |
| 载荷位置 | **世界侧 16 片 `SavedData`**(`applied_slash_blade_vault_0..15`);元件物品只带 `vault_id` + 一份几十字节摘要 |
| 物品 NBT | **恒定 1089 B**,不随存量增长 |
| 落盘粒度 | 按刀哈希分片,**一次变更只重写变脏的那 1/16** |
| 客户端显示 | 服务端写**摘要组件**,客户端直接读 tooltip 与 LED,**不访问世界侧数据** |
| 线程安全 | 世界侧访问有 `server.isSameThread()` 守卫(单机下客户端渲染线程也能看到 server) |
| 计数一致性 | 分片从磁盘读回后 `recount()` 重算;条目按内容查找(不依赖 `hashCode` 跨存档稳定) |

**为什么 `AEItemKey` 的哈希不能当作查找依据**:条目用 `|hashCode| % 16` 分片,而 AE2 的键哈希建立在 MC 的组件补丁上;跨存档往返后哈希可能不再对应 —— 因此查找路径会**先试哈希桶、未命中再扫其余桶**,桶只作为存档粒度的优化。

---

## 构建

```bash
# 需求:JDK 21
./gradlew build           # 产物:build/libs/applied_slash-1.1.0.jar
```

开发/验证任务:

| 命令 | 用途 |
|---|---|
| `./gradlew runClient` | 开发客户端 |
| `./gradlew runServer` | 开发服务端 |
| `./gradlew runGameTestServer` | 20 条 GameTest(12 条存储元件:容量/拒收/重载/优先入库/过滤语义等;8 条充能刀/充能器:空能量禁用、充能后可用、命中扣费、量化与入库等),全部含真断言 |
| `./gradlew runServer -PselfTest` | **无头专用服务器自检**:容量、拒收、插入吞吐、每 tick 遍历成本、序列化体积、分片落盘,跑完自动关服 |
| `./gradlew verifyResources` | 资源链自检:模型/父链/贴图存在性 + PNG 头(16×16 / 8bit / RGBA / 非交错) |
| `./gradlew generateTextures` / `texturePreviews` | 重新生成像素贴图与 8 倍预览(带棋盘格底) |
| `./gradlew importArt` | 把 `art/` 下的大图转换成 16×16 贴图 |

> 发布版 jar **不含 `dev/` 与 `gametest/` 两个包**,因此 `/appliedslash testcell`、`-PselfTest`、`-PmodelDump` 等开发诊断**只在开发构建中可用**。

---

## 已知限制

1. **物品无法预置、也无法靠复制物品转移载荷**。刀身数据在世界侧,元件物品只是一个钥匙 —— 把元件复制到另一个存档只会得到一把空壳,世界侧会显示"本存档内找不到刀身数据"(tooltip 明确提示,而不是假装空盘)。
2. **不支持元件工作台分区,也不支持升级卡**(fuzzy / inverter / 均分 / 虚空)。这是刻意取舍:分区对"只收某类物品"的盘无意义,而注册了没效果的白名单等于提供假功能。
3. **`maxTypes` 是性能护栏,不是装饰**。在大型整合包里请按服务器规模定档(见上表);不要打开 `acceptBeyondMaxTypes` 却不设上限。
4. **两个元件的归属规则是确定的**:刀永远进刀盘,不可堆叠元件排除刀 —— 不依赖 AE2 的遍历顺序。
5. **充能刀入库时能量有量化损失**:向下取整到 10 档,单次损失上界 = 档宽 − 1(≤ 上限的 10%);满能量取精确值故无损失。嫌损失大可把 `chargedBladeQuantizeLevels` 调到 40(损失 ≤ 2.5%,代价是每把刀最多 41 个键)。
6. **1.1.0 是破坏性变更**:AE2 与 SlashBlade 由可选依赖改为**必需前置**。此前只用「不可堆叠物品存储元件」、没装其中之一的整合包,升级后会被 FML 拦在启动阶段 —— 升级前请先确认两者都已安装。

---

## 文档

| 文件 | 内容 |
|---|---|
| [`STORAGE-DESIGN.md`](STORAGE-DESIGN.md) | 存取逻辑逐路径剖析 + 与 AE2 原生盘 / 创造元件 / 第三方大类型盘的横向对照(含字节码实证出处) |
| [`PERFORMANCE-TESTING.md`](PERFORMANCE-TESTING.md) | 三层性能测试手册(自检 → GameTest → 真机) |
| [`FACTS-CHARGED-BLADES.md`](FACTS-CHARGED-BLADES.md) | 充能拔刀剑的 API 事实表(全部签名来自 `javap` 取证,未实证项单独标注) |

---

## 尚未验证的部分

- **真机生产环境启动**:本仓库只验证过 moddev 的 `runServer` / `runGameTestServer`;把 `build/libs/*.jar` 放进真实 NeoForge 服务器 `mods/` 的独立部署**尚未实测**。
- **客户端在数千条目终端里的帧成本**:未测(客户端界面不在自动验证范围内)。
- **真实刀身数据(非合成窄载荷)的内存与存档成本**:自检使用的是窄载荷合成刀,真实刀的数据更重。
- **AE2 缺席的专用服务端**:只做了代码层面的门卫构造,未实跑(1.1.0 起 AE2 已是必需前置,门卫职责改为类加载隔离)。
- **充能刀与充能器的客户端表现**:充能器界面、刀与充能器的 tooltip、充能动画/观感等**不在自动化验证范围内**,需玩家实际游戏验收。自动化覆盖到的是服务端逻辑与物品行为(GameTest 的 8 条充能刀/充能器断言)。

---

## 许可

本项目采用 [MIT 许可证](LICENSE)。

第三方依赖(Applied Energistics 2、SlashBlade、NeoForge 及构建期库)各自遵循其原有许可证,**不在本仓库内再分发**。

5 把充能拔刀剑的模型与贴图**复用 SlashBlade: Resharped(重锋)自带资源**(agito / dios / muramasa / sange / yamato),版权归原作者 **`flammpfeil`** 与重锋维护者所有;本模组仅以**引用方式**使用,不重新分发、不主张其著作权。
