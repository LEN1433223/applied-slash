# Applied Slash · SlashBlade 存储元件

[English](README_EN.md) | **简体中文**

> **Applied Slash** — 为 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 添加**面向"类型数"的专用存储元件**。
> AE2 原生存储单元的类型数被硬编码钳在 **63**;而拔刀剑、附魔装备、带耐久/命名的工具这类物品**几乎每一件都是独立的存储键**,63 远远不够。本模组用自研库存把类型上限抬到几千,并把载荷移到世界侧分片存储。

**English TL;DR** — An AE2 addon for MC 1.21.1 / NeoForge that adds storage cells for **type-heavy** workloads (thousands of unique SlashBlades, or any non-stackable items), bypassing AE2's 63-type clamp with a custom cell inventory and world-side sharded storage.

---

## 兼容性

| 项目 | 版本 |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+**(可选依赖) |
| SlashBlade: Resharpened | **2.0.7+**(可选依赖,只有拔刀剑元件需要它) |
| 环境 | 客户端 + 专用服务端 |

**AE2 或 SlashBlade 缺席时模组仍可正常加载**:缺 AE2 则不注册元件(并在日志给出提示),缺 SlashBlade 则元件存在但不接受任何物品(tooltip 会明说)。

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
./gradlew build           # 产物:build/libs/applied_slash-1.0.0.jar
```

开发/验证任务:

| 命令 | 用途 |
|---|---|
| `./gradlew runClient` | 开发客户端 |
| `./gradlew runServer` | 开发服务端 |
| `./gradlew runGameTestServer` | 12 条 GameTest(容量/拒收/重载/优先入库/过滤语义等) |
| `./gradlew runServer -PselfTest` | **无头专用服务器自检**:容量、拒收、插入吞吐、每 tick 遍历成本、序列化体积、分片落盘,跑完自动关服 |
| `./gradlew verifyResources` | 资源链自检:模型/父链/贴图存在性 + PNG 头(16×16 / 8bit / RGBA / 非交错) |
| `./gradlew generateTextures` / `texturePreviews` | 重新生成像素贴图与 8 倍预览(带棋盘格底) |
| `./gradlew importArt` | 把 `art/` 下的大图转换成 16×16 贴图 |

> 发布版 jar **不含 `dev/` 与 `gametest/` 两个包**,因此 `/appliedslash testcell`、`-PselfTest`、`-PmodelDump` 等开发诊断**只在开发构建中可用**。

---

## 已知限制(请先读这段)

1. **物品无法预置、也无法靠复制物品转移载荷**。刀身数据在世界侧,元件物品只是一个钥匙 —— 把元件复制到另一个存档只会得到一把空壳,世界侧会显示"本存档内找不到刀身数据"(tooltip 明确提示,而不是假装空盘)。
2. **不支持元件工作台分区,也不支持升级卡**(fuzzy / inverter / 均分 / 虚空)。这是刻意取舍:分区对"只收某类物品"的盘无意义,而注册了没效果的白名单等于提供假功能。
3. **`maxTypes` 是性能护栏,不是装饰**。在大型整合包里请按服务器规模定档(见上表);不要打开 `acceptBeyondMaxTypes` 却不设上限。
4. **两个元件的归属规则是确定的**:刀永远进刀盘,不可堆叠元件排除刀 —— 不依赖 AE2 的遍历顺序。

---

## 文档

| 文件 | 内容 |
|---|---|
| [`STORAGE-DESIGN.md`](STORAGE-DESIGN.md) | 存取逻辑逐路径剖析 + 与 AE2 原生盘 / 创造元件 / 第三方大类型盘的横向对照(含字节码实证出处) |
| [`PERFORMANCE-TESTING.md`](PERFORMANCE-TESTING.md) | 三层性能测试手册(自检 → GameTest → 真机) |

---

## 尚未验证的部分(如实标注)

- **真机生产环境启动**:本仓库只验证过 moddev 的 `runServer` / `runGameTestServer`;把 `build/libs/*.jar` 放进真实 NeoForge 服务器 `mods/` 的独立部署**尚未实测**。
- **客户端在数千条目终端里的帧成本**:未测(客户端界面不在自动验证范围内)。
- **真实刀身数据(非合成窄载荷)的内存与存档成本**:自检使用的是窄载荷合成刀,真实刀的数据更重。
- **AE2 缺席的专用服务端**:只做了代码层面的门卫构造,未实跑。

---

## 许可

本项目采用 [MIT 许可证](LICENSE)。

> ⚠️ **上传前请打开 `LICENSE` 文件,把 `<YOUR NAME OR ORGANIZATION>` 替换成你的名字或组织名** —— 版权行是 MIT 许可证的法律要件,不能留占位符。
> GitHub 检测到 `LICENSE` 文件后会自动在仓库侧栏显示 "MIT license"。

第三方依赖(Applied Energistics 2、SlashBlade、NeoForge 及构建期库)各自遵循其原有许可证,**不在本仓库内再分发**。
