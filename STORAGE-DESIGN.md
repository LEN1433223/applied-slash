# SlashBlade存储元件 —— 存取逻辑与同类对照

> **现状(2026-02,mod 1.2.0)**:本文件只讲存储元件,**内容仍然有效**(AE2 侧未改动)。
> 与玩法相关的变更:1.1.0 的 5 把充能拔刀剑已整体删除,现为一把数据包刀「莉莉」;
> 充能器保留,改为为**任意**拔刀剑充能。见 README。

本文只写**实现依据可验证**的内容:结论要么来自本仓库代码,要么来自对 `ae2-19.2.17.jar` / 已合并 NeoForge jar 的 `javap` 反查。第三方实现里无法实证的部分会明确标注为"常见做法"。

---

## 一、身份与接入点

| 环节 | 实现 | 依据 |
|---|---|---|
| 判定"这是不是一个存储单元" | `SlashBladeCellHandler.isCell` = `stack.getItem() instanceof SlashBladeCellItem` | `StorageCells.getHandler` 返回**注册顺序**中第一个 `isCell` 命中的 handler |
| 驱动器槽位放行 | AE2 的 `DriveBlockEntity$CellValidInventoryFilter.allowInsert` → `StorageCells.isCellHandled(stack)` | 所以只要 handler 注册成功,槽位就会放行 |
| 库存实例 | `new SlashBladeCellInventory(stack, container)` | 自研,不走 `BasicCellInventory` |

**硬约束**:元件物品**绝不能实现 `IBasicCellItem`**。AE2 自带的 `BasicCellHandler` 在注册顺序上更早,它用 `getItem() instanceof IBasicCellItem` 抢判定;被它抢走后类型数会被 `BasicCellInventory` 构造器里的 `bipush 63` 硬钳回 63 —— 上万把刀的需求直接落空。

---

## 二、存(insert)全路径

`insert(AEKey key, long amount, Actionable mode, IActionSource source)`

1. **拒收前置**:`amount <= 0` / 不是 `AEItemKey` / 不是拔刀剑 → 返回 0。
2. **惰性解析刀库**:`contents()` 读物品上的 `vault_id` 组件;没有就分配一个(带"必须服务端线程"守卫);再向 `BladeVaultStore` 要内容。**解析失败(客户端、存档未就绪)直接返回 0**,绝不伪造内容。
3. **规范化只在"新键"上做**:`existing = amountOf(key)`;仅当 `existing <= 0` 且 `mayNormalize(key)` 为真时,才 `normalize(key)`(剥离运行时状态组件)并**再查一次**。
   - 热路径上重复存入同一把刀 **不做任何拷贝**:`mayNormalize` 是便宜检查(`stack.has(BLADE_RUNTIME_STATE)`),命中已有键就直接跳过。
4. **类型上限**:仍是新类型且 `!hasRoomForNewType(maxTypes)` → **拒收**(返回 0,让网络回落到其它存储)。
5. **单类型上限**:`room = MAX_AMOUNT_PER_TYPE - existing`,接受 `min(amount, room)`(`MAX_AMOUNT_PER_TYPE = 1_000_000`)。
6. **`Actionable.SIMULATE` 全程只算不写**;只有 `MODULATE` 才 `vault.add(...)`。
7. **变更后**:`afterChange()` → 写摘要组件 + `container.saveChanges()`(让驱动器标脏,进入它的持久化流程)。

**为什么插入时也写摘要**(而不是像 AE2 原生盘那样只在 `persist()` 写):客户端拿不到世界侧刀库,tooltip 与 LED 只能读物品上的摘要。实测这次写入约 1 µs(整条插入 5–7 µs),且内容未变时 `writeSummary` 直接返回。

---

## 三、取(extract)全路径

`extract(AEKey key, long amount, Actionable mode, IActionSource source)`

1. 未命中(`stored <= 0`):
   - `SIMULATE` → 立刻返回 0。**这是热路径**:驱动器包装器每次插入试探都会走一遍取出模拟,这里不做规范化、不分配对象。
   - `MODULATE` 且 `mayNormalize` → 规范化一次再查(`normalize` 结果与入参相同则直接返回 0,避免空转)。
2. 命中:
   - `SIMULATE` → `min(amount, stored)`,不修改任何状态。
   - `MODULATE` → `vault.remove(key, amount)` → `afterChange()`。

---

## 四、优先入库(键级,不是全局优先级)

- **机制**:`NetworkStorage.insert` 是**两遍扫描**。第一遍(反查偏移 73..164)遍历挂载的存储,**只问 `isPreferredStorageFor(...)`**:为真就当场 `insert`,**这一遍完全不看优先级**;为假的对象被收进 `secondPassInventories`,才在第二遍里按优先级(`PRIORITY_SORTER`,基于 `Integer.compare`)处理。
- **我们的规则**:`key instanceof AEItemKey && BladeIdentity.isBlade(key)` → **无条件 `true`**。
  - 也就是说:**拔刀剑永远先问本盘**,其它盘哪怕全局优先级更高也插不到第一遍里。
  - 依据:AE2 自带的 `BasicCellInventory` **不覆盖** `isPreferredStorageFor`(走默认 false);驱动器包装器 `DriveWatcher extends MEInventoryHandler` → `DelegatingMEInventory.isPreferredStorageFor` 把问题透传到我们的库存。
  - 为什么可以无条件承认:真正收不下时的拒绝在 `insert` 里 —— 只有"原始键不在、规范化后仍不在、且类型已满(且未开启溢出)"才拒收;**规范化后能命中已有条目的刀即使类型已满也会被收下**,所以不会制造"同一把刀被别的盘收走、规范化副本却留在本盘"的语义裂缝。代价只是满盘时多一次注定返回 0 的调用。
- **收不下时怎么办**(唯一会让刀跑到别处的情况):
  - `acceptBeyondMaxTypes = false`(默认):拒收 → AE2 第二遍把它交给别的盘,或让它留在原处(不丢东西)。
  - `acceptBeyondMaxTypes = true`:**满盘也继续收**,拔刀剑从此不会再进其它盘。类型数不再有上限,每 tick 全网重建成本随类型数线性上升(实测 2000 ≈ 0.25 ms / 5000 ≈ 0.75 ms / 10000 ≈ 1.8 ms / 20000 ≈ 5.5 ms)。
- **AE2 没有"否决其它存储"的 API**:我们不接受时,第二遍仍会问别的盘,拦不住。因此"100% 统一进入本盘"只能靠"永远收得下"(即上面的开关)。
- **竞争者**:AE2 的**创造存储元件**也覆盖了 `isPreferredStorageFor`,网络里装着它时第一遍由遍历顺序决定谁先拿到;普通存储元件不覆盖,不构成竞争。

---

## 五、每 tick 热路径

`getAvailableStacks(KeyCounter out)` 会被 AE2 的全网缓存(`StorageService.onServerEndTick` → `updateCachedStacks`)每 tick 调用:

- 实现是 `vault.forEach(out)`:遍历 16 个桶的条目集,`out.add(key, count)`,**零中间集合、零 NBT 解析**。
- **成本只随条目数(类型数)增长,与总把数无关**。
- 实测:`getAvailableStacks` 1k 类型 0.09 ms / 5k 0.75–1.4 ms / 10k 1.75 ms / 20k 3.9–5.2 ms(见 `PERFORMANCE-TESTING.md`)。

**相同 NBT 的刀堆叠是更省的一侧**:AE2 的键 = 物品 + 数据组件(不含数量),相同 NBT 天然折叠为一个键;`VaultContents.add` 命中已有键时只 `put(key, cur + amount)`,`typeCount` 不变、只标脏 1/16 分片 —— 插 1 把与插 100 万把同 NBT 的成本相同。

---

## 六、状态、耗电、摘要

| 项 | 服务端 | 客户端 |
|---|---|---|
| `getStatus()` | 直接读刀库:`count()==0 → EMPTY`;否则 `hasRoomForNewType(maxTypes()) ? NOT_EMPTY : TYPES_FULL` | 读物品上的**摘要组件**推断(不会谎报"空") |
| LED 染色 | —— | `AppliedSlashAe2Client.getColor`:tint 1 层按摘要状态取色,**alpha 必须补成 FF**(见下) |
| `getIdleDrain()` | 1.5 AE/t | —— |
| `persist()` | 只写一份几十字节摘要,`O(1)`,与存量无关 | —— |

**摘要组件**:`{types, count, max, missing}`。`missing` = "物品记着有内容,但世界侧刀库查不到"(跨存档复制、存档回滚)→ tooltip 给出明确提示,而不是假装空盘。

> 踩坑记录:MC 1.21.1 的 `ItemRenderer.renderQuadList` 把 tint 值按 RGBA 四通道写进顶点(`FastColor$ARGB32.alpha(tint)` → `putBulkData(..., alpha, ...)`),**tint 的 alpha 就是顶点 alpha**。染色回调返回 `0xFFFFFF`(alpha=00)会让整件物品全透明,且日志无任何报错。

---

## 七、持久化:世界侧分片

**物品上只有钥匙**:`vault_id`(UUID)+ 摘要 → **物品 NBT 恒定 1089 B,与存了多少把无关**(实测)。

**数据在世界侧**:16 块 `SavedData` 分片 `applied_slash_blade_vault_0..15`,桶下标 = `|AEKey.hashCode()| % 16`(哈希由 AE2 缓存在键里)。

- **为什么按"刀"再分片**:一个元件可能上万把刀;若全放在同一份 `SavedData` 里,世界自动存档要一次性序列化全部刀(实测 5k 把 ≈ 86 ms、2 万把 ≈ 345 ms,直接堵主线程)。分片后一次变更只重写 ~1/16:实测 5k 把 **6–8 ms/单块** vs 全量 54–61 ms。
- **格式**:`{cells:[{id:<uuid>, entries:[{key:<AEItemKey 标签>, count:<long>}]}]}`,每块只存属于自己桶的条目。
- **容错**:单条 `key` 解析失败只丢这一条,不让整盘报废。
- **计数一致性**:分片从磁盘读回时桶里可能已有上万条,而 `typeCount/totalCount` 只由 `add/remove` 增量维护 → 解析后必须 `recount()` 重算,否则重开世界计数从 0 开始(状态灯谎报"空"、类型上限失效)。
- **线程守卫**:`BladeVaultStore.storage()` 要求 `server != null && server.isSameThread()`。单机下客户端渲染线程也能看到非 null 的 server,只判 `server != null` 会对活着的 `DimensionDataStorage` 做跨线程读写(存档期可能 CME)。
- **缓存**:已解析刀库缓存 256 个,服务器实例变化即整体清空(避免持有旧世界数据);不清缓存的话,"装满 5000 把的盘每被解析一次就扫 5000 条"。

**代价(必须知道)**:盘的内容**不住在物品里** —— 把元件物品复制到另一个存档只会得到一把"空壳",世界侧显示 `missing`。也因此**无法预先做出"装了 5000 把刀的盘"当物品发放**;测试用 `/appliedslash testcell <count>` 在本存档内现场生成。

---

## 八、与其它 ME 存储的对照

| 维度 | AE2 原生存储元件 | AE2 创造元件 | 第三方"大类型 / 不限类型"盘(常见做法) | 本元件 |
|---|---|---|---|---|
| 类型上限来源 | 构造器硬编码 **63**(`bipush 63`) | 无限(inventory 报送极大值,内部常量 `2147483647`) | 绕过 63 钳制,但载荷仍在物品 NBT ⇒ 受 NBT/同步/存档膨胀限制 | **自研库存**,`maxTypes`(默认 5000,绝对 20000);瓶颈是每 tick 全网重建成本 |
| 载荷位置 | 物品 NBT(键表) | 无真实载荷(它是**源头**不是仓库) | 物品 NBT(更大) | **世界侧 16 分片 SavedData** |
| 物品 NBT 随存量增长 | 是 | —— | 是(更严重) | **否**(恒定 1089 B) |
| 单次落盘代价 | 与条目数正相关 | —— | 与条目数正相关 | **~1/16 分片**(5k 把 6–8 ms) |
| 优先入库 | 全局 `IPriorityHost` | 深度优先被抢 | 全局优先级 | **键级 `isPreferredStorageFor`** |
| 状态显示 | 客户端查库存,`persist()` 后才同步 | —— | 同原生 | **服务端写摘要组件,客户端直接读(实时)** |
| 元件工作台分区 | 支持 | —— | 多数支持 | **刻意不支持**(对"只收拔刀剑"无意义) |
| 升级卡(fuzzy/inverter/均分/虚空) | 支持 | —— | 多数支持 | **刻意不支持**(不注册白名单,避免"装了没反应"的假功能) |
| 能否预置成物品 | 能(内容随 NBT) | —— | 能 | **不能**(首次使用时绑定刀库) |
| 跨存档复制物品 | 内容跟着走 ✓ | —— | 内容跟着走 ✓ | **载荷留在原存档** → 显示 `missing` |
| 接受哪些物品 | 任意 | 任意 | 任意 | **只收拔刀剑**(物品标签 + `ItemSlashBlade` 双保险,标签只列了 5 把基础刀,具名刀/附属刀靠类判定) |
| 相同 NBT 合并 | 是 | —— | 是 | 是,**并在首次入库时额外剥离运行时状态组件**再合并 |

### 一句话总结

**普通盘是"把内容写进物品",我们是"物品只是一个钥匙,内容住在世界里"。**

由此换来三个别人做不到的事:

1. **类型数可以上万**(5000 默认 / 20000 上限),因为不受物品 NBT 与 63 钳制约束;
2. **物品 NBT 恒定小**,每次同步/落盘都不随存量膨胀;
3. **键级优先入库**,让拔刀剑第一次进网就落本盘(而不是靠全局优先级硬抢)。

代价也有三个,而且都必须自己兜住:

1. **不能预置、不能靠复制物品转移载荷**(⇒ `missing` 检测 + tooltip 提示);
2. **内容依赖存档**(⇒ 服务端线程守卫、服务器切换清缓存);
3. **一致性全靠自己维护**(⇒ `recount()` 重算计数、分片容错、摘要组件)。

---

## 九、参数速查

| 参数 | 值 | 位置 |
|---|---|---|
| 生效类型上限 | 5000(可调 64..20000) | `AppliedSlashConfig.maxTypes` |
| 绝对类型上限 | 20000 | `BladeCellSpec.MAX_TYPES` |
| 单类型最大堆叠 | 1,000,000 | `BladeCellSpec.MAX_AMOUNT_PER_TYPE` |
| 空闲耗电 | 1.5 AE/t | `BladeCellSpec.IDLE_DRAIN` |
| 刀库分片数 | 16 | `BladeCellSpec.VAULT_SHARDS` |
| 剥离运行时状态 | 默认开 | `AppliedSlashConfig.stripRuntimeState` |
| 超限是否继续收 | 默认关(关 = 满盘拒收,刀可能落到别的盘) | `AppliedSlashConfig.acceptBeyondMaxTypes` |
| 解析缓存上限 | 256 个刀库 | `BladeVaultStore.CACHE_LIMIT` |
