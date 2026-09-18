# Applied Slash · SlashBlade 存储元件

[English](README_EN.md) | **简体中文**

> **Applied Slash** — 为 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 添加**面向"类型数"的专用存储元件**。
> AE2 原生存储单元的类型数被硬编码钳在 **63**;而拔刀剑、附魔装备、带耐久/命名的工具这类物品**几乎每一件都是独立的存储键**,63 远远不够。本模组用自研库存把类型上限抬到几千,并把载荷移到世界侧分片存储。

**English TL;DR** — An AE2 addon for MC 1.21.1 / NeoForge that adds storage cells for **type-heavy** workloads (thousands of unique SlashBlades, or any non-stackable items), bypassing AE2's 63-type clamp with a custom cell inventory and world-side sharded storage.

**1.3.0 新增玩法** — 拔刀剑「莉莉」+ **拔刀剑充能器**(可为**任意**拔刀剑充能)。莉莉是一把**数据包刀**(复用丛雨丸的模型与贴图);充能器接在 ME 网络上,从网络取电。详见下方两节。

> ⚠️ **升级注意(1.3.0 是破坏性变更)** — 1.1.0 里那 5 把充能拔刀剑(`charged_blade_pulse` / `resonance` / `surge` / `overcharge` / `singularity`)及其全部下游代码已在 1.3.0 **整体删除**。**旧存档里的这 5 把刀会变成未知物品**,升级前请先处理掉它们。

---

## 兼容性

| 项目 | 版本 |
|---|---|
| Minecraft | **1.21.1** |
| NeoForge | **21.1.250+** |
| Applied Energistics 2 | **19.2.17+**(**必需前置**) |
| SlashBlade: Resharped(重锋) | **2.0.7+**(**必需前置**,刀盘、莉莉与充能器都需要它) |
| 环境 | 客户端 + 专用服务端 |

> **1.1.0 起两者都是必需前置**(1.0.0 里它们是可选依赖)。理由:两个存储元件与充能器都依赖 AE2 的网格与能量 API,而刀判定、刀身数据与数据包刀「莉莉」都依赖重锋。运行期仍保留 `ModList.get().isLoaded(...)` 门卫,但它已不再承担"可选依赖"职责,而是**类加载隔离**——引用 `appeng.*` / `mods.flammpfeil.*` 的类只在门卫之后加载。

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

## 拔刀剑·莉莉(1.3.0 新增)

「莉莉」是一把**数据包刀**,不是自定义物品类:物品仍是重锋原生的 `slashblade:slashblade`,刀的定义写在数据包里。

- 定义文件:`src/main/resources/data/applied_slash/slashblade/named_blades/lili.json`,`name = applied_slash:lili` ⇒ 语言键 `item.applied_slash.lili`(中文名「莉莉」,英文 **"Lili"**);
- 进**本模组的创造模式物品栏**,与两个存储元件、充能器并列;
- **面板伤害 14.13**(`attack_base = 14.13`),`max_damage = 100`;
- **SA = 次元斩**(`slash_art = slashblade:judgement_cut`)、**SE = 爱与羁绊**(`special_effects = [applied_slash:inventory_transfer]`)、**无附魔**(`enchantments = []`),`sword_type = []`(无附魔光效)。

### 美术来源致谢

「莉莉」的模型与贴图**完全复用丛雨丸**(Slashblade-Murasame):

- 源仓库:<https://github.com/sangeeeee/Slashblade-Murasame>(`murasamemaru.obj` / `murasamemaru.png`);
- 授权 **MIT**,Copyright (c) 2025 **CeliaClaire**;
- 逐字节复制进来的副本、原始路径与完整许可证文本见根目录 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

### 刀鞘:删掉配件 + 粉黛纹样(当前版本;圆柱化/图集重排已回退)

> **回退说明**:曾尝试"鞘壳重做成 20 段圆柱 + 贴图重排成图集 + 纹样满铺",实测在游戏里出现
> **整块变透明/变黑(包括刀身)**的严重 bug,已按用户要求**整体回退**到上一版。相关代码仍保留在
> `tools/lili-mesh.gradle` 的 `ShellFit`/`CylinderShell` 与 `tools/lili-texture.gradle` 里,**默认不启用**;
> 重做前必须先定位那个渲染问题。回退版的硬约束:**其它 7 组的 UV 与原素材逐一相同** ⇒ 不可能采到图集空白像素。

丛雨丸原壳的鞘那块贴图区域是**近纯黑 (23,22,22)**,读起来"就是一根黑管";鞘上还挂着金属环/栗形/下绪。
本模组两步处理,**只动鞘**:

1. **删掉鞘上的配件几何**(两个金属环 + 栗形 + 下绪挂绳,共 301 面)`gradlew generateLiliMesh`
   —— 按**连通块**判定(保留 X 跨度最大的那一块 = 整根壳 56 面),不写死面数;**几何与 UV 的其余部分与原素材一致**;
   只动 `sheath` 组 ⇒ 手持外观、伤害、属性完全不变。原始模型存档 `art/lili_source.obj`,`-PliliRestoreMesh` 一键还原。
2. **重绘鞘的贴图(粉黛纹样)** `gradlew generateLiliTexture`
   - **底图 = 原始素材 2× 最近邻放大**(256×512 → 512×1024),**UV 一个都不动**;
   - **AI 纹样导入**:`art/saya_ai.png` 按"横向 = 绕鞘一周、纵向 = 鞘长"铺进鞘的 UV 区域;
   - **横向无缝**:统一缩放 + 水平镜像拼接,实测接缝色差 **34.0 → 0.0**;
   - **去水印**:`-PsayaArtCropBottom=110` 用上方内容的**镜像替换**底部(不损失长度);
   - **金箍**仍由程序画在最上层(位置从"实际被画到的鞘长区间"反推)。
- 关键实现细节:掩码来自 `lili.obj` 的 `sheath` 组面 UV 光栅化,并换算到**游戏采样空间**(`行 = 1-v`);
  因为重锋解析 OBJ 时会把 v 翻一次,**凡是按"行"做的换算都要多一次取反**(写成 `v` 会把刀身映射到空白像素 ⇒ 渲染成黑条 —— 实测踩过);
  掩码外的像素从原素材整值复制(原始像素存档 `art/lili_source.png`)。
- 可调参数(贴图):`-PsayaArt / -PsayaArtCropBottom / -PsayaArtLayers / -PsayaArtHardCrop / -PsayaTrimOff / -PsayaRestore`;(几何):`-PliliRestoreMesh`。
- `gradlew verifyResources` 断言:鞘组 1 个连通块且 X 跨度 ≥ 250 / **其余 7 组面数与源存档逐组相等** / 掩码外零改动 / 共用像素零改动 /
  **鞘区域首尾接缝 ≤ 6** / **没有任何组比原素材更多地采样到透明像素**(防"整块变透明/变黑")/ 鞘与刀身平均色 RGB 距离 ≥ 60。
- **实测(当前版本)**:鞘壳 357 → 56 面(删配件后,几何与 UV 不变);贴图 512×1024(原始素材 2×);
  接缝色差 **0.0**;共用像素改动 **0**;掩码外改动 **0**;鞘 (233,184,132) vs 刀身 (141,156,106) ⇒ **RGB 距离 99.4**。
- 预览图在 `build/lili-texture-preview/`:`scale-stand-80px-per-block.png`(刀架尺度真实像素,三格)、`scale-icon-16px-per-block.png`(图标尺度)、
  `parts-map.png` + `parts-map.txt`(每个连通块的编号/面数/坐标对照 —— 以后要"去掉某一块"直接指编号)。


> **一句实话**:按"1 格 = 320 单位"换算,鞘在刀架上只有约 **8 px** 宽、在背包图标里约 **1.5 px** 宽。
> 所以远处看到的是**粉黛色调的花纹管**,具体是玫瑰还是爱心要贴近看(或看上面的预览图)才分辨得出 ——
> 这是模型宽度决定的,不是贴图分辨率的问题。

### 鞘纹样的 AI 提示词(粉黛 + 花瓣爱心)

生成器只认"能平铺的花纹",所以出图要满足四条:**纹样大、纵向拉长、横向无缝、无光影/无背景/无水印**。
已验证可用的一条(出图 864×4576 = 1:5.30,正是鞘的 UV 区域比例):

```
seamless tileable texture, flat orthographic swatch, Japanese maki-e lacquer scabbard surface,
dusty-pink (fendai) palette: base #F7D9E3, rose #E58BAE, dusky aubergine-black #3A3340,
rose-gold #F2C96B thin outlines and gold-dust sprinkles,
motifs: large scattered cherry and rose petals with elegant abstract heart shapes,
petals and hearts arranged along the vertical axis, varied sizes, clean lacquer edges,
seamless in both axes (4-way repeat), flat atlas only — no lighting, no shadows, no highlights,
no perspective, no 3D render, no background, no border, no text, no watermark,
large motifs, elongated vertically, high detail
```

负面提示词:`3D render, perspective, shading, drop shadow, specular highlight, frame, border, background,
mockup, photo, metal reflection, neon, text, watermark, signature, logo, blurry, low-res, cartoon heart emoji`

---

## 拔刀剑充能器 `applied_slash:blade_charger`

充能器**接受任意拔刀剑** —— 莉莉、重锋本体的刀、其它附属模组的刀都能放进槽里,由它用 AE 给刀充能量。充进去的能量存在**本模组自己的物品组件** `applied_slash:blade_energy` 上。

> 该能量**目前没有消费者**(不会被任何玩法读取),是刻意留下的"以后有用"接口。

- 接在 **ME 网络**上的方块:内部自带能量缓冲,**主动从 ME 网络取电**(走 AE2 网格的通用取电路径 `extractAEPower`)给槽里的刀充能;
- 单条刀的能量上限 `chargerMaxEnergy`(**默认 800**);
- **自带发电**:槽 2 放**虞美人**,按进度烧掉并直接产出 KAE 进本机缓冲,所以**没接 ME 网络也能充能**(每朵产出的 AE 见 `chargerPoppyAe`,燃烧时长见 `chargerPoppyBurnTicks`);
- 取电顺序:**网格 → 本机缓冲 → 烧燃料**。能上网时一滴油都不烧;刀已充满 / 缓冲已满时同样不烧;
- 充能速率见 `chargerChargePerTick`;空闲耗电见 `chargerIdleDrain`(由 AE2 网格节点托管,节点在线即耗);
- **界面**:现代极简**浅色**。三条规则贯穿全局 ——① **只有一个视觉锚点**(标题下一条 24px 青线,此外没有任何装饰线条,连分组线都没有);② **能不上文字就不上**(设备状态由**计量条的颜色**表达:青=工作中 / 绿=已满 / 红=缺电;完整诊断放在缓冲条的悬停提示里);③ **一条左基线 + 一条右边界**(三行标签共用 x=50,三行数值右对齐 x=160)。
  - **配色不是"深色取反"**:每个色都按「当文字 ≥ 4.5:1(AA)、当仪表填充对**轨道** ≥ 3.0:1」两个门槛重新定。浅底上的坑很具体 —— 绿色 `#1F9D57` 对面板看着有 3.26:1,但压在浅轨道上只有 2.89:1,整条读数会糊掉,所以语义色统一取更深的一档(绿 `#157A42`、琥珀 `#9A660D`、青 `#0A6E9E`、红 `#CC3B3B`)。
  - **不引入任何贴图**,更不复制/内嵌 AE2 贴图;仍与 AE2 保持一致的部分:`assets/applied_slash/screens/blade_charger.json` 是 AE2 屏样式文档的形状(槽位坐标以它为声明、以 `BladeChargerMenu` 常量为准)。
  - 几何、配色、对比度**三组自检**在 `design/check_mockup.js`(`node design/check_mockup.js`),它把原型与 Java 常量逐项比对;运行期另有 `BladeChargerScreen#verifyLayout()` 守卫(含"快捷栏必须 = 背包 + 54")。原型页:`design/blade_charger_mockup.html`(1×–4× 缩放、实时调节、深浅切换)。

### 入库量化:带能量的刀为什么不会撑爆存储元件

被充过能的刀存进 Slash存储元件时,能量会**向下取整到若干档**(`bladeEnergyQuantizeLevels`);**满能量取精确值**(`bladeEnergyQuantizeKeepFull`),保证「满能量入库 → 取出仍是满的」。

理由是:**能量值会进入 AE2 的存储键**,不量化则每一个能量值都会变成一个新类型。量化把每把刀可能出现的键数压到"档数 + 1"个,从源头压制"能量差异导致键膨胀"。

### 自动化验证证据

- **GameTest 21/21 通过**:存储元件语义与莉莉相关断言共 19 条,含真断言;
- `./gradlew build` 成功、`./gradlew verifyResources` **22 项全过**;
- 实测性能(无头专用服务器,单个存储元件):`getAvailableStacks` 2000 类型 ≈ **0.29 ms**、5000 类型 ≈ **0.96 ms**;单次 insert ≈ **4.14 µs**;
- 版本 **1.3.0**,产物 `applied_slash-1.3.0.jar`。

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

### 充能器 / 刀能量配置项(1.3.0 改名收敛)

| 配置项 | 默认 | 说明 |
|---|---|---|
| `chargerMaxEnergy` | `800` | 充能器给**单把刀**充能的上限(任意拔刀剑共用这一条上限) |
| `chargerChargePerTick` | `1` | 充能器每 tick 给刀充入的能量点 |
| `chargerAePerPoint` | `100` | 每 1 点刀能量需要的 AE |
| `chargerIdleDrain` | `1.0` | 充能器空闲耗电(AE/t,由 AE2 网格节点托管) |
| `chargerPoppyAe` | `5000` | 烧掉 1 朵虞美人产出的 AE |
| `chargerPoppyBurnTicks` | `200` | 烧掉 1 朵虞美人所需的刻数(20 刻 = 1 秒) |
| `bladeEnergyQuantizeLevels` | `10` | 入库量化档数:能量向下取整到 `上限/档数` 的整数倍 |
| `bladeEnergyQuantizeKeepFull` | `true` | 满能量的刀量化时取精确值(不计损失) |

> 上表默认值取自 `AppliedSlashConfig` 的 `defineInRange/define` 实参(与生成出来的 `applied_slash-common.toml` 一致)。
> 1.1.0 的旧名(`chargedBladeMax*`、`chargedBladeAttackCost`、`chargedBladeChargePerTick` 等)**已全部失效** —— 按刀分档的能量上限与"每次命中扣能量"随那 5 把充能刀一起删除。
> 这 8 项与上面的元件配置同在同一个 `applied_slash-common.toml` 里(单一配置文件 = 单一加载 / 重载路径)。

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
./gradlew build           # 产物:build/libs/applied_slash-1.3.0.jar
```

开发/验证任务:

| 命令 | 用途 |
|---|---|
| `./gradlew runClient` | 开发客户端 |
| `./gradlew runServer` | 开发服务端 |
| `./gradlew runGameTestServer` | 21 条 GameTest(存储元件容量/拒收/重载/优先入库/过滤语义 12 条 + 莉莉 5 条 + 充能器回归 2 条 + 兼容模组 2 条),全部含真断言;当前 **21/21 通过** |
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
5. **被充过能的刀入库时能量有量化损失**:向下取整到 `bladeEnergyQuantizeLevels` 档,单次损失上界 = 档宽 − 1;满能量取精确值故无损失。嫌损失大可以把档数调大(代价是每把刀可能占的键数随之增加)。
6. **1.1.0 是破坏性变更**:AE2 与 SlashBlade 由可选依赖改为**必需前置**。此前只用「不可堆叠物品存储元件」、没装其中之一的整合包,升级后会被 FML 拦在启动阶段 —— 升级前请先确认两者都已安装。
7. **1.3.0 也是破坏性变更**:1.1.0 的 5 把充能拔刀剑(`charged_blade_pulse / resonance / surge / overcharge / singularity`)已整体删除,**旧存档里的它们会变成未知物品**(不再是任何物品的注册名)。

---

## 文档

| 文件 | 内容 |
|---|---|
| [`STORAGE-DESIGN.md`](STORAGE-DESIGN.md) | 存取逻辑逐路径剖析 + 与 AE2 原生盘 / 创造元件 / 第三方大类型盘的横向对照(含字节码实证出处) |
| [`PERFORMANCE-TESTING.md`](PERFORMANCE-TESTING.md) | 三层性能测试手册(自检 → GameTest → 真机) |
| [`FACTS-CHARGED-BLADES.md`](FACTS-CHARGED-BLADES.md) | 重锋 / AE2 / MC 的 API 事实表(全部签名来自 `javap` 取证,未实证项单独标注);自设刀身几何与生成器部分已标注为"已删除" |
| [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) | 「莉莉」复用丛雨丸模型/贴图的第三方声明与 MIT 许可证全文 |

---

## 尚未验证的部分

- **真机生产环境启动**:本仓库只验证过 moddev 的 `runServer` / `runGameTestServer`;把 `build/libs/*.jar` 放进真实 NeoForge 服务器 `mods/` 的独立部署**尚未实测**。
- **客户端在数千条目终端里的帧成本**:未测(客户端界面不在自动验证范围内)。
- **真实刀身数据(非合成窄载荷)的内存与存档成本**:自检使用的是窄载荷合成刀,真实刀的数据更重。
- **AE2 缺席的专用服务端**:只做了代码层面的门卫构造,未实跑(1.1.0 起 AE2 已是必需前置,门卫职责改为类加载隔离)。
- **充能器与莉莉的客户端表现**:充能器界面、刀与充能器的 tooltip、充能动画/观感等**不在自动化验证范围内**,需玩家实际游戏验收。自动化覆盖到的是服务端逻辑与物品行为(GameTest 共 21 条断言)。

---

## 许可

本项目采用 [MIT 许可证](LICENSE)。

第三方依赖(Applied Energistics 2、SlashBlade、NeoForge 及构建期库)各自遵循其原有许可证,**不在本仓库内再分发**。

「莉莉」的刀身模型与贴图**复用丛雨丸**(Slashblade-Murasame,`murasamemaru.obj` / `murasamemaru.png`),授权 **MIT**,Copyright (c) 2025 **CeliaClaire**;本模组以**逐字节复制**的方式内嵌这两个文件(仅改名),完整来源、原始路径与许可证全文见 [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md)。

## 手持「Slash 元件」(AE2 便携元件)

把刀从快捷栏收进元件,**减少快捷栏渲染压力**,同时保持甚至提高「莉莉」的加伤收益。

| 项 | 规则 |
|---|---|
| 类型 | **AE2 存储元件**(继承 AE2 便携元件):**需要充电**,在 AE2 充电器里充;右键打开 **AE2 便携元件界面** |
| 容量 | **8 把拔刀剑**;只接受拔刀剑;**同 NBT 也不合并**,各占一格(每条目一个隐藏唯一标记) |
| 加伤 | 元件内每把刀为「莉莉」提供该刀**基础伤害的 10%**;快捷栏里的刀同价(也 10%) |
| 上限 | 两处**共享 8 把上限,元件内优先**,不足再用快捷栏补足 |
| 位置 | 元件**必须在快捷栏(0..8)**才计入;放在主背包不算 |
| 自身 | 被手持的那把莉莉不计入自己 |
| 性能 | 玩家 tick 只算快捷栏 9 格签名(含元件内容哈希),**内容没变就不重算、不写组件、不发同步包** |

例:元件里 2 把 + 快捷栏 1 把 = 3 把 ⇒ 莉莉面板伤害 +30% 的基础伤害之和;快捷栏 9 把刀时只算 8 把。