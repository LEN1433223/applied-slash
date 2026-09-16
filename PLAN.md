# PLAN v3.1 — 拔刀剑专用 AE2 ME 存储元件(已实现 · 已实测 · 已独立审查)

> v1(继承 `BasicStorageCell`)因 **AE2 把类型数硬钳在 63** 被推翻;v2 定架构;v3 落地并实测;v3.1 = 并入独立审查(阶段 D)发现的问题与修复。
> 所有 AE2 机制结论均由 `javap` 反编译 `libs/ae2-19.2.17.jar`(与 GitHub `AppliedEnergistics/Applied-Energistics-2` 1.21.1 分支同源)取证,并经阶段 D 独立复核。

- 工程:`D:\Agnet_all\Agnet_mod2\appliedslash-template-1.21.1`
- 平台:NeoForge 21.1.250 / MC 1.21.1 / Java 21;前置 AE2 `19.2.17`、GuideME `21.1.18`、SlashBlade: Resharpened `2.0.7-1.21.1`
- 自检入口:`gradlew runServer -PselfTest`(启动开发服务端 → 跑完自检 → 自动关服,约 20 秒)

---

## 1. 三个需求的落地机制(字节码证据,阶段 D 独立复核 PASS)

### R1 刀优先进本盘 —— 键级优先,不靠优先级滑块
| 结论 | 证据 |
|---|---|
| `NetworkStorage.insert` 两遍扫描:第一遍只插 `isPreferredStorageFor(...) == true` 的存储,其余延后;第二遍按优先级**降序**(`Integer.compare(b, a)`) | `NetworkStorage.insert` / `lambda$static$0` |
| 驱动器里的单元被 `DriveWatcher extends MEInventoryHandler` 包装,`MEInventoryHandler.isPreferredStorageFor` **末段透传给被包装的库存**(→ `DelegatingMEInventory.isPreferredStorageFor` → 委托对象的 `MEStorage.isPreferredStorageFor`) | 字节码偏移 25–48 |

→ `SlashBladeCellInventory.isPreferredStorageFor` 对刀返回 true(类型满时 false,让网络回落)。自检实测:刀 = true、非刀 = false。

### R2 上万把 —— 必须自研库存(硬阻塞)
| 结论 | 证据 |
|---|---|
| `BasicCellInventory` 构造器把 `maxItemTypes` **硬钳到 63**(下限 1);`getTotalItemTypes()` 返回钳后值 → 继承路线声明多少类型都无效 | 构造器偏移 36–68;`getTotalItemTypes` 偏移 0–5 |
| 自研是公开 API 正路 | `StorageCells.addCellHandler(ICellHandler)` |
| 驱动器槽位放行条件 = `StorageCells.isCellHandled(stack)` → **无需实现任何 AE2 物品接口** | `DriveBlockEntity$CellValidInventoryFilter.allowInsert` 偏移 0–19 |
| **元件物品绝不能实现 `IBasicCellItem`**,否则被 AE2 原生 handler 抢先(`BasicCellInventory.isCell` = `instanceof IBasicCellItem`),63 钳制回归 | `BasicCellInventory.isCell` / `BasicCellHandler.isCell` |

### R3 性能 —— 成本不在"广播",在三个具体位置
| 事实 | 证据/实测 | 对策 |
|---|---|---|
| 网络级**不是**每次变更都广播:tick 末一次 diff,只通知关心该键的节点 | `StorageService.onServerEndTick` → `updateCachedStacks()` → 变更键才 `postWatcherUpdate` → `interestManager.get(key)` | 无需自建节流;不要主动调 `invalidateCache()` |
| 但有终端/监视器在线时,**每 tick 无条件全量重建**全网 KeyCounter,成本 ∝ 网络键总数且超线性 | 见 §2 曲线 | ① `getAvailableStacks` 裸遍历零解析;② 类型上限**可配** |
| AE2 原生单元落盘 = **整表写物品组件**(`List<GenericStack>`,每项含完整 ItemStack+组件) | `BasicCellInventory.persist()` → `i.set(AEComponents.STORAGE_CELL_INV, list)` | 元件只带 UUID + 摘要;**刀身数据放世界侧 SavedData** |
| 每键持有完整 ItemStack | `AEItemKey` 字段 `stack`(hashCode 已缓存,且**含组件** → 按刀散桶有效) | 剥离运行时组件;同刀只占一个键 |

---

## 2. 实测数据(`runServer -PselfTest`;测试刀为**窄载荷**,真实刀身数据更重,故下列均为下界)

| 指标 | 实测 | 判定 |
|---|---|---|
| `isCellHandled` / 库存类 | true / `SlashBladeCellInventory` | ✅ |
| 非刀插入 / `isPreferredStorageFor` | 0 / false;刀 = 1 / true | ✅ R1 |
| 同刀重复插入 | 类型 1,总数 4 | ✅ |
| 运行时组件规范化(同刀两态) | 两栈组件确实不同,插入后类型仍 = 1 | ✅ 不虚增类型 |
| 超限插入 / 类型数 | 0 / 不变(5000 上限) | ✅ 拒收不淘汰 |
| **重新挂载一致性(阶段 D 的 P1 回归)** | 重新解析后 可见键 5000、状态 `TYPES_FULL`(非 EMPTY)、超限仍拒收 0、计数与摘要一致 | ✅ |
| 插入吞吐 | **3.5–6.9 µs/次** | ✅ |
| 元件物品序列化体积 | **1089 字节,且不随存量增长** | ✅(AE2 原生单元为数十 MB 级) |
| 每 tick 重建成本(`getAvailableStacks`) | 200→0.009–0.017 ms;1k→0.08–0.10;2k→0.25–0.39;5k→**0.75–1.43**(最优/平均);10k→**1.75**(最优);20k→**3.94**(最优)/ 5.15(平均) | ⚠️ **超线性**,决定容量上限 |
| 客户端同步体积 | 5k 键 = **108,873 B**;20k 键 = **448,872 B**(≈21.8 B/键,窄载荷) | ⚠️ 与 类型数×每键载荷 成正比 |
| 全盘序列化(存档期) | 5k 把 = 77.8–86.1 ms;20k 把 = **185.9 ms** | → 靠分片解决 |
| **分片落盘(单块最大 / 合计)** | 5k 把 = **5.4–7.9 ms** / 54–61 ms;20k 把 = **10.2 ms** / 95.1 ms | ✅ 单次存档阻塞 ≈ 全量的 1/16 |

**口径说明**:上表 `getAvailableStacks` 的"最优"是 100 次中的最小值(稳态);**平均值在默认 5000 类型下会到 1.4–1.5 ms、最差可达 7–9 ms**(GC/缓存抖动),自检自身打印的 `< 1.0 ms` 期望只对"最优"成立 —— 这一点由阶段 D 指出并已在本表更正。
**10k / 20k 两行需要把 `maxTypes` 配到 20000 才能复现**(默认 5000 时自检会跳过这些检查点)。

**为什么按"刀"再分片**:只按元件 UUID 分片时,单个大盘的全部条目仍在一块里,存档要一次性序列化 5k 把(≈86 ms)→ 20k 把≈186 ms,直接堵主线程。改为 `bucket = floorMod(AEKey.hashCode(), 16)`,改一把刀只标脏它所在的那一块。

**容量取舍(配置项,非写死)**:`maxTypes` 默认 **5000**(≈0.75–1.4 ms/tick、≈106 KB 同步),可调到 20000(≈3.9–5.2 ms/tick、≈438 KB)。上万把"能做",但要为每 tick 成本买单 —— 这是 AE2 `KeyCounter` 的固有开销(每键约 250–600 ns),不在本模组,无法从我们这侧消除。

---

## 3. 决策落地

| # | 决策 | 落地 |
|---|---|---|
| 1 | 自包含性 | 载荷放世界侧 `SavedData`,元件只带 UUID + 摘要;跨存档复制→空盘 + tooltip 红色警示(`missing` 标记已真正写入,修复见 §6-②) |
| 2 | 运行时组件 | **默认剥离** `BLADE_RUNTIME_STATE`,持久化刀身数据(刀铭/耀魂/附魔/耐久/SA)全留;`stripRuntimeState=false` 可关闭 |
| 3 | 规模 | 上限可配(默认 5000 / 最大 20000);超限**拒收** |
| 4 | 网络可见键 | 保持标准 `AEItemKey`(物品语义完整,存储总线/输入输出总线/合成零改造) |

---

## 4. 文件结构

```
src/main/java/com/applied/slash/
├── AppliedSlash.java                 mod 入口:软依赖门卫、组件/配置注册、创造标签页、自检触发
├── AppliedSlashClient.java           客户端入口(FMLClientSetupEvent + 染色注册转发)
├── AppliedSlashAe2.java              AE2 接线:物品注册(仅在 AE2 在场时类加载)、挂 ICellHandler、驱动器模型
├── AppliedSlashAe2Client.java        LED 层染色(按摘要组件上色,与 AE2 getColor 语义一致)
├── AppliedSlashConfig.java           maxTypes / stripRuntimeState(注释里带实测曲线)
├── AppliedSlashComponents.java       vault_id(UUID)、vault_summary(types/count/max/missing)
├── BladeCellSpec.java                绝对上限、每类型上限、空闲耗电、分片数
├── SlashBladeBlades.java             刀判定(标签+类判定双保险)+ 运行时组件剥离
├── SlashBladeCellItem.java           元件物品(刻意不实现任何 AE2 物品接口)
├── cell/SlashBladeCellHandler.java   ICellHandler
├── cell/SlashBladeCellInventory.java 自研 StorageCell(优先存储/无 63 钳制/O(1) 落盘)
├── cell/BladeIdentity.java           AE2 键层身份 + 规范化
├── vault/BladeVaultStore.java        世界侧入口(同线程守卫 + 计数重算)+ 分片计时诊断
├── vault/BladeVaultShard.java        单块分片(按刀分桶)
├── vault/VaultContents.java          单盘内存视图(16 桶,O(1) 读写、recount)
├── command/AppliedSlashCommand.java  测试命令 /appliedslash testcell <数量> [玩家](权限等级 2)
├── gametest/SlashBladeCellGameTests.java  GameTest 入口(只含 GameTestHelper 签名,见 §9-①)
├── gametest/GameTestSupport.java      GameTest 实现(AE2 类型只出现在这里,避免被开发模式客户端扫描)
├── dev/BladeTestFactory.java         测试元件工厂:产出"已装 N 把真刀"的元件(压测入口)
├── dev/GuideMeStartupPatch.java      可选开发补丁:消除 GuideME 首次资源重载 NPE(默认关闭)
└── dev/SlashBladeCellSelfTest.java   服务端自检(-Dappliedslash.selftest=true)
tools/textures.gradle                 贴图像素图源码 + 生成任务(gradlew generateTextures / texturePreviews)
PERFORMANCE-TESTING.md                性能测试手册(自检 / GameTest / 真机压测三段式)
```

资源:2 个模型 JSON、3 张自绘贴图(元件本体 / 状态灯 / 驱动器单元面)、`lang/{en_us,zh_cn}.json`(9 键 ↔ 代码 9 个 `translatable` 键)、配方 JSON(带 `neoforge:mod_loaded` 双条件)。

**贴图**:像素图"源码"在 `tools/textures.gradle`(16×16 字符矩阵 + 调色板),`gradlew generateTextures` 生成 PNG,`gradlew texturePreviews` 输出 8 倍放大预览 + "本体叠加染色状态灯"的合成预览供肉眼验收。
题材:AE2 式单元外壳(亮灰顶面板 + 内窗 + 底部金属插脚)里**斜嵌一把太刀** —— 银刃带刀锋高光、金色鍔、暗红缠柄,主体用妖刀紫。
注意 AE2 的物品染色只给 `tintIndex == 1` 上色(`BasicStorageCell.getColor` 对其它索引返回 `0xFFFFFF`),所以**本体层必须自带颜色**,状态灯层用白色、由 `CellState.getStateColor()` 运行时染色。
三个模型/资源的贴图引用**全部指向本模组**(`applied_slash:item/...`、`applied_slash:block/...`),资源里不再有任何 `ae2:` 贴图依赖(仅配方保留 AE2 物品 id 作为材料)。
GameTest 结构模板:`gametest-structures/empty.snbt`(仓库权威副本),`build.gradle` 的 `prepareGameTestStructures` 任务在跑测试前自动同步到 `run/gameteststructures/`。

---

## 5. 阶段 D 独立审查结论

- **构建**:`build --rerun-tasks` 真实重编译 exit 0。
- **自检复现**:全部 `[SELFTEST]` 通过;并在 `maxTypes=20000` 下补测了 10k/20k 两行。
- **三条载荷性结论**:3a(63 钳制)、3b(优先存储透传)、3c(驱动器放行条件)**全部 PASS**,证据由其独立 `javap` 取得。
- **资源与残留**:产物 jar `appeng/*` 与 `mods/*` 泄漏均为 0;配方在运行期被正常加载(日志 `Loaded 1856 recipes`,无本模组报错);无任何已删符号残留。

---

## 6. GameTest 验收(客观验收证据)

跑法:`gradlew runGameTestServer`(无显示环境可用,跑完自动退出;失败会以非零码退出)。实测输出:

```
Running test batch 'defaultBatch:0' (6 tests)...
========= 6 GAME TESTS COMPLETE IN 753.0 ms ======================
All 6 required tests passed :)
BUILD SUCCESSFUL
```

| 测试 | 断言的语义 |
|---|---|
| `rejectsNonBlades` | `StorageCells.isCellHandled` 为真(驱动器会接受);非刀插入返回 0、不声明优先存储、不留下内容 |
| `acceptsBladesAndClaimsPriority` | 刀插入为 1;`isPreferredStorageFor` 为真(才走 `NetworkStorage` 第一遍扫描);同刀追加 3 → 类型 1 / 总数 4;网络可见键数为 1 |
| `normalizesRuntimeState` | `BladeStateAccess.ensureRuntimeComponent` 确实改变了组件(前提成立),插入后**类型数仍为 1**、数量累加到同一键 |
| `enforcesLimitAndSurvivesReload` | 填满配置上限后可容纳数 = 上限;第 N+1 个键被拒收;**重新解析刀库后**可见键数 = 上限、状态非 EMPTY、**上限判定仍生效**(P1 回归)|
| `survivesMissingPayload` | 指向不存在刀库的元件表现为空盘且可正常入库(不崩) |
| `testCellFactoryProducesDistinctBlades` | 工厂产出的元件确实装了 N 把**互不相同**的刀;合成刀与素刀**组件不同**(证明刀身数据真写进去了,否则压测载荷偏小);摘要与重新解析后的可见键数一致 |

### 6.1 真机压测用的"已装好刀的磁盘"怎么拿

**不存在可下载的成品磁盘文件**:按设计决策 1,刀身数据存在世界侧 `SavedData`、元件物品只带 UUID,
内容必须在目标存档里创建。所以提供 op 命令现场生成:

```
/appliedslash testcell 5000            # 给自己(需要权限等级 2)
/appliedslash testcell 5000 <玩家>      # 给指定玩家(控制台也能用)
```

- 生成的每把刀都带**真实刀身数据**(刀铭 `translationKey`、耀魂、杀敌数、精炼、基础攻击),
  与素刀组件不同 —— 这样客户端同步量与 tick 成本才是真实量级。
- 注意:`maxTypes` 默认 5000,装 5000 把正好占满(状态灯会变 `TYPES_FULL`、再加新刀会被拒收)。
  想在装满后继续加,先把 `maxTypes` 调到 10000/20000 再生成。

**两个踩过的坑(记录以免重犯)**:
1. GameTest 的结构模板**不是数据包资源**,而是运行目录下的 `gameteststructures/<path>.snbt`
   (`StructureTemplateManager.loadFromTestStructures` → `loadFromSnbt(id, Paths.get("gameteststructures"))`)。
2. NeoForge 默认给模板名加**测试类名前缀**:`template="empty"` 会被解析成 `applied_slash:slashbladecellgametests.empty`。
   用 `@PrefixGameTestTemplate(false)` 关掉,才能直接用 `empty`。

---

## 7. 审查发现的问题与处置

| # | 严重度 | 问题 | 处置 |
|---|---|---|---|
| ① | **P1** | `VaultContents` 的 `typeCount/totalCount` 只做增量维护,而 `BladeVaultStore.get` 每次都新建视图并直接注册桶 —— 落盘条目从不计入。后果:**重开世界后计数归零** → 状态灯谎报 EMPTY、**类型上限形同虚设(可无限超装)**、tooltip 全 0 | **已修**(阶段 D):新增 `VaultContents.recount()`,在 `get()` 末尾按桶实际内容重算。**已补回归测试**:自检第 (5b) 项重新解析同一元件并断言"可见键数一致 / 状态非 EMPTY / 超限仍拒收 / 计数与摘要一致",全部通过 |
| ② | P2 | 摘要 `missing` 恒为 false → 决策 1 承诺的"跨存档复制后 tooltip 可诊断"实际不可见 | **已修**:`missing = (刀库类型数 == 0 && 摘要总数 > 0)` |
| ③ | P2 | 单机环境下客户端渲染线程也能拿到非 null 的 server → 会对活着的 `DimensionDataStorage`/`HashMap` 跨线程读写(存档期可能 CME) | **已修**:`BladeVaultStore.storage()` 增加 `server.isSameThread()` 守卫 + `overworld() == null` 判断 |
| ④ | P3 | `SlashBladeCellInventory.contents()` 无论成败都置 `resolved=true` → 首次取不到刀库(存档未就绪)会终身退化为空盘不再重试 | **已修**:只有真正拿到刀库才锁定 `resolved` |
| ⑤ | P3 | `VaultContents.loadEntry` 是死代码(修 ① 后计数由 `recount` 负责),留着易被误用成重复计数 | **已删** |
| ⑥ | P3 | `BladeVaultShard.bucket()` 为每个用过的 UUID 在 16 块分片里各留一个空 map 且不清理(文件不涨,内存按"曾制造过的元件数"线性涨) | **未修·已知**:长期单人档影响可忽略;若要紧,可在 `save()` 后对空桶做一次按访问时间的清理 |

---

## 8. 未验证 / 已知限制(诚实标注)

1. **未在真实客户端验证**驱动器插拔、终端浏览、存储总线进出的体感;客户端成本用协议级数据(5k 键 = 106 KB)度量代替。
2. **客户端同步量与类型数成正比**:真实刀身 NBT 更大时,5000 把的同步量会显著高于 106 KB。要压下来需要自定义紧凑 `AEKeyType`(决策 4 的备选,代价是丢物品语义、自动化需重做)。
3. **不支持升级卡与元件工作台分区**:刻意不做,避免"装了没效果"的假功能。
4. **元件不自包含**:复制元件物品到别的存档得到空盘 + 警示;同一 UUID 被复制则两盘共享同一份数据。
5. **每 tick 重建成本是 AE2 固有**(`KeyCounter` 每键 250–600 ns),我们只能控制"网络里有几把刀"。
6. **崩溃可能丢失最近几分钟的增量**(只写脏分片 + 世界存档周期),与 AE2 原生单元同量级。
7. 无 AE2 启动的软依赖路径是**静态结论**(阶段 D 逐条核对类加载链未发现提前触达),未做"移除 AE2 后启动"的活体实测。
8. **开发世界里的数字会偏高**:每跑一次自检都会往 `run/world` 里留下一个 5000 把的刀库,分片落盘成本按"该分片内所有刀库的数据量"计,所以重复跑之后单块耗时会从 ~6 ms 涨到 ~14 ms。要在干净世界量真实数字,先删 `run/world/data/applied_slash_blade_vault_*.dat` 并重置该世界。

---

## 9. 自我审计:本模组自身的问题(第二轮独立排查)

这一节记录**不依赖任何外部因素**、单纯从自己代码里查出来的缺陷与处置。

| # | 严重度 | 问题 | 处置 |
|---|---|---|---|
| ① | **P1(已实测崩溃)** | NeoForge 在开发模式会在**客户端初始化时**遍历 `@GameTestHolder` 类并 `Class.forName` 加载它,而 `getDeclaredMethods()` 会解析该类**全部方法的签名**。我们的 GameTest 类里有几个带 AE2 返回类型的私有辅助方法,于是"没装 AE2 的开发客户端"启动即 `NoClassDefFoundError: appeng/api/stacks/AEKey`(崩溃报告见 `run/crash-reports/`)。**这是纯粹的自身设计问题** | 测试入口类改为**只含 `GameTestHelper` 签名**并转调;所有 AE2 类型集中在非 holder 的 `GameTestSupport`。修复后 GAME TEST 6/6 仍全绿 |
| ② | P2 | 类型已满时 `isPreferredStorageFor` 直接返回 false;若玩家手里那把刀带"运行时状态组件"(其规范化副本已在库中),网络会把**同一把刀**放进别的普通盘 —— 与"同刀不增类型"的设计目标矛盾 | 类型满时先做一次廉价判定(`mayNormalize`,仅当栈里真有易变组件才拷贝),规范化命中已有键则继续声明优先存储;新增 GameTest 断言覆盖 |
| ③ | P2 | 插入/取出的规范化路径**无条件 `stack.copy()`**(热路径分配),即使这把刀根本没有运行时组件 | 新增 `SlashBladeBlades.hasVolatileState`(廉价检查)+ `BladeIdentity.mayNormalize`,不可规范化时不拷贝 |
| ④ | P2 | 配置监听器写成 lambda:`modEventBus.addListener((ModConfigEvent e) -> ...)`。NeoForge 靠泛型参数推断事件类型,而 **lambda 不携带泛型签名** → 监听器可能漏掉 `ModConfigEvent` 或被每个事件调用一次 | 改为"参数类型明确的方法 + 方法引用"`AppliedSlashConfig::refresh` |
| ⑤ | P2 | `BladeVaultStore.get()` 每次调用都做 16 次分片查表 + `recount()`(**O(n) 全表遍历**)。而它会被 `StorageCells.getCellInventory` 的每个调用者触发(驱动器重挂载、元件染色回调、各类 UI) | 增加按 UUID 缓存(服务器实例变化即失效,上限 256 条),解析与重算各只做一次 |
| ⑥ | P3 | `assignVaultId` 只判 `getCurrentServer() == null`。单机下**客户端渲染线程**也能拿到非 null 的 server → 客户端会给客户端那份物品栈写一个随机 UUID,与服务端不一致 | 守卫条件与存储侧对齐:`server == null \|\| !server.isSameThread()` 才返回 null |
| ⑦ | P3 | `AppliedSlash.<clinit>` 里创建的两个 lambda 引用了 `AppliedSlashAe2`(AE2 类);虽然 lambda 体是惰性解析、当前不崩,但这是一个"AE2 缺席时随时可能炸"的隐患 | 记录在案;调用点已全部在 `isAe2Loaded()` 门卫内,后续若重构应把该标签页整体移进 AE2 侧类 |
| ⑧ | 已评估·不改 | 每次变更都写摘要组件(而 AE2 原生单元只在 `persist()` 写) | 保留:客户端读不到世界侧刀库,tooltip 与 LED 只能读物品摘要;实测该写入只占插入路径约 1 µs(整条 5–7 µs),换来客户端显示实时准确。已在代码里写明取舍理由 |

**排查过但没找到问题的项**(以免重复怀疑):插入/取出无循环与阻塞、无同步块;`SlashBladeCellInventory` 的所有分支都对 `contents() == null`(客户端/存档未就绪)安全;`BladeVaultStore.storage()` 有跨线程守卫;`BladeIdentity.normalize` 在关闭剥离配置时是幂等的;`VaultContents` 的桶下标恒在 `[0, 15]`(同源于同一常量);模型的 `credit`/`name` 等字段与 AE2 自身模型一致(原生加载器忽略未知字段)。
