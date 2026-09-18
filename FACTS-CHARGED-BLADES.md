# 充能拔刀剑 —— 已实证 API 事实表(host 用 javap 取证,2026-02 基线)

> 全部条目由 host 在 `javap -p` 上直接读取,非记忆、非推测。**未列出的签名一律不得凭记忆写**。
> 取证来源:
> - MC/NeoForge:`build/moddev/artifacts/neoforge-21.1.250-merged.jar` + `neoforge-21.1.250-universal.jar`
> - AE2:`libs/ae2-19.2.17.jar`
> - 重锋:`libs/slashblade-2.0.7-1.21.1.jar`
> - 原始 dump 文本:`build/facts/{state,itemblade,events,assets,itemmc}.txt`

> **现状(2026-02,mod 1.2.0)**:本模组**当前只有一把刀** —— 数据包刀「莉莉」(`applied_slash:lili`),
> 它是重锋原生的 `slashblade:slashblade` + 数据包定义(无自定义物品类),模型/贴图复用丛雨丸
> (来源与授权见 `THIRD-PARTY-NOTICES.md`)。1.1.0 时期围绕**本模组自设的 5 把充能拔刀剑**取得的
> API 实证(第 1–8 节、第 10 节的 OBJ/渲染结论)**全部仍然有效**;
> 而**自设刀身几何 / 刀鞘几何 / 程序化刀身生成器**相关的内容已随那 5 把刀删除,
> 相关小节内均已加注删除说明。AE2 侧(存储元件、充能器)不受影响。

---

## 1. MC 1.21.1(物品)

| 签名 | 备注 |
| --- | --- |
| `Item#isBarVisible(ItemStack)` → `boolean` | 耐久条可见性,**必须与 `getBarWidth` 同时上线** |
| `Item#getBarWidth(ItemStack)` → `int` | 取值域 0..13,13 = 满(原版约定) |
| `Item#getBarColor(ItemStack)` → `int` | 返回 **int**(不是 `FastColor`/`ChatFormatting`),原版用 `0xFF0000` 这类 RGB |
| `Item#onCraftedBy(ItemStack, Level, Player)` → `void` | 合成台产出时调用 |
| `Item#hurtEnemy(ItemStack, LivingEntity, LivingEntity)` → `boolean` | 参数顺序 = (stack, **target**, **attacker**);`Player#attack` 内部调用点已实证 |
| `Item#use(Level, Player, InteractionHand)` → `InteractionResultHolder<ItemStack>` | |
| `Item#getDefaultAttributeModifiers(ItemStack)` → `ItemAttributeModifiers` | 刀本体走的是 `ItemSlashBlade` 的两分支覆写 |
| `Player#displayClientMessage(Component, boolean)` → `void` | 第二参数 true = actionbar |
| `Player#openMenu(MenuProvider)` → `OptionalInt` | 服务端调用 |

## 2. MC 1.21.1(方块 / 方块实体)

| 签名 | 备注 |
| --- | --- |
| `net.minecraft.world.level.block.state.BlockBehaviour#useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` → `InteractionResult` | **protected**;**类在 `.block.state.` 包下**,不是 `block.` |
| `BlockBehaviour#useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)` → `ItemInteractionResult` | protected |
| `Block#getTicker(Level, BlockState, BlockEntityType<T>)` → `BlockEntityTicker<T>` | default,声明在 `Block`;返回 null = 无 ticker |
| `Block#playerWillDestroy(Level, BlockPos, BlockState, Player)` → `BlockState` | 玩家挖掘时调用 —— **掉刀用这里** |
| `Block.popResource(Level, BlockPos, ItemStack)` | **static** |
| `BlockEntity#setRemoved()` → `void` | ⚠️ 区块卸载同样会调用,**不要**把掉刀挂在这里 |
| `BlockEntityType.Builder.of(BlockEntityType$BlockEntitySupplier<? extends T>, Block...)` | |
| `BlockEntityType.Builder#build(com.mojang.datafixers.types.Type<?>)` | 只有带参数这一个重载 ⇒ **`.build(null)` 是合法的** |
| `BlockEntity#getUpdatePacket()` / `#getUpdateTag(HolderLookup$Provider)` | 需要同步时覆写 |

**本映射中不存在**(dump 里没有,别写):`BlockBehaviour#affectNeighborsAfterRemoval`、`Block#onRemove(...)`。

## 3. NeoForge 21.1.250

| 签名 | 备注 |
| --- | --- |
| `IMenuTypeExtension#create(IContainerFactory<T>)` → `MenuType<T>` | static interface method,直接 `IMenuTypeExtension.create(FooMenu::new)` |
| `IContainerFactory<T>#create(int, Inventory, RegistryFriendlyByteBuf)` | 构造器引用需匹配这个形状 |
| `RegisterMenuScreensEvent#register(MenuType<? extends M>, MenuScreens$ScreenConstructor<M, U>)` | mod event bus;`U extends Screen & MenuAccess<M>` |

## 4. AE2 19.2.17

### 能量

| 签名 | 备注 |
| --- | --- |
| `IAEPowerStorage extends IEnergySource, IGridNodeService` | ⇒ 实现它 **必须** 连 `extractAEPower` 一起实现(编译期强制) |
| `IAEPowerStorage#injectAEPower(double, Actionable)` → `double` | 返回**实际接受量** |
| `IAEPowerStorage#getAEMaxPower()` / `#getAECurrentPower()` → `double` | |
| `IAEPowerStorage#isAEPublicPowerStorage()` → `boolean` | |
| `IAEPowerStorage#getPowerFlow()` → `AccessRestriction` | |
| `IAEPowerStorage#getPriority()` → `int`(default) | |
| `IEnergySource#extractAEPower(double, Actionable, PowerMultiplier)` → `double` | |
| `AccessRestriction`: `NO_ACCESS` / `READ` / `WRITE` / `READ_WRITE` + `isAllowExtraction()` / `isAllowInsertion()` | **判定请用 `isAllowExtraction()`,不要写 `== READ_WRITE` 等值比较** |

### 网格节点

| 签名 | 备注 |
| --- | --- |
| `GridHelper.createManagedNode(T, IGridNodeListener<T>)` → `IManagedGridNode` | static,泛型方法 |
| `GridHelper.onFirstTick(T, Consumer<? super T>)` | |
| `GridHelper.getNodeHost(Level, BlockPos)` / `#getExposedNode(Level, BlockPos, Direction)` | |
| `IManagedGridNode#create(Level, BlockPos)` | 必须在服务端/首 tick 调 |
| `IManagedGridNode#setFlags(GridFlags...)` | |
| `IManagedGridNode#setExposedOnSides(Set<Direction>)` | |
| `IManagedGridNode#setIdlePowerUsage(double)` | |
| `IManagedGridNode#setVisualRepresentation(AEItemKey｜ItemStack｜ItemLike)` | 三个重载都在 |
| `IManagedGridNode#addService(Class<T>, T)` | |
| `IManagedGridNode#getNode()` → `IGridNode` | |
| `IGridNodeListener<T>` | **只需实现 `onSaveChanges(T, IGridNode)`**;其余 3 个是 default(另有 `onInWorldConnectionChanged`) |
| `IInWorldGridNodeHost#getGridNode(Direction)` → `IGridNode` | 单方法接口 |

### 方块实体基类

| 签名 | 备注 |
| --- | --- |
| `AEBaseBlockEntity(BlockEntityType<?>, BlockPos, BlockState)` | 唯一构造器形状 ⇒ 子类两参构造器须转发 `super(TYPE.get(), pos, state)` |
| `AEBaseBlockEntity#loadAdditional(CompoundTag, HolderLookup$Provider)` | **final** ⇒ 不能覆写;存档读取只能靠 `saveAdditional` 对称实现 |
| `AEBaseBlockEntity#saveAdditional(CompoundTag, HolderLookup$Provider)` | 可覆写 |
| `AEBaseBlockEntity#onReady()` | 可覆写 |

## 5. SlashBlade 重锋 2.0.7

| 签名 | 备注 |
| --- | --- |
| `ItemSlashBlade(Tier, int attackDamageIn, float attackSpeedIn, Item$Properties)` | **唯一**构造器,4 参 |
| `ItemSlashBlade#use(Level, Player, InteractionHand)` → `InteractionResultHolder<ItemStack>` | |
| `ItemSlashBlade#hurtEnemy(ItemStack, LivingEntity, LivingEntity)` → `boolean` | 母类已实现,子类可先做门禁再 `super` |
| `ISlashBladeState#setSealed(boolean)` | 封刀旗标(1.1.0 的"空能量禁用"曾靠它,该功能已随那 5 把刀删除) |
| `ISlashBladeState#setDefaultBewitched(boolean)` | 初始附魔旗标(要"无初始附魔"须置 false) |
| `ISlashBladeState#setDestructable(boolean)` | |
| `ISlashBladeState#setBaseAttackModifier(float)` | 传 `13.14F` + `refine=0` ⇒ 显示伤害正好 13.14(1.1.0 那 5 把充能刀的用法,已删除;现存的「莉莉」是数据包刀,面板伤害由数据包 `attack_base = 14.13` 提供) |
| `ISlashBladeState#getRefine()` → `int` / `#isBroken()` → `boolean` | |
| `ISlashBladeState#setTexture(ResourceLocation)` / `#setModel(ResourceLocation)` | |
| `ISlashBladeState#setNonEmpty()` | |
| `SlashBladeEvent#getBlade()` → `ItemStack` | 子事件继承取刀 |
| `UpdateAttackEvent#setNewDamage(double)` / `#getNewDamage()` / `#getOriginDamage()`;ctor `(ItemStack, ISlashBladeState, double)` | ⚠️ **没有 `getStack()` / `getState()`** —— 取刀只能用 `getBlade()` |
| `BreakEvent extends SlashBladeEvent implements ICancellableEvent` | ⇒ `event.setCanceled(true)` 可用 |

### 可复用的美术资源真实文件名(`jar tf` 实证)

| 刀 | 模型 | 贴图 |
| --- | --- | --- |
| agito | `model/named/agito.obj` | `agito_true.png` / `agito_false.png` / `agito_rust.png` / `agito_rust_true.png` —— **没有 `agito.png`** |
| dios | `model/named/dios/dios.obj` | `dios/dios.png`(另有 `dios/koseki.png`) |
| muramasa | `model/named/muramasa/muramasa.obj` | `muramasa/muramasa.png`(另有 `sabigatana.png` / `doutanuki.png`) |
| sange | `model/named/sange/sange.obj` | `sange/sange.png`(另有 `black.png` / `white.png`) |
| yamato | `model/named/yamato.obj` | `model/named/yamato.png` —— **yamato 没有子目录** |

> 这五套 obj/png 的版权属于 SlashBlade 重锋(SlashBlade: Resharped)及其原作者 `flammpfeil` / 重锋维护者。
> **1.1.0 的 5 把充能刀曾以"引用方式"复用它们,但那 5 把刀已在 1.2.0 整体删除**;
> 本模组现存的唯一一把刀「莉莉」复用的是**丛雨丸**(Slashblade-Murasame)的 obj/png,
> 授权 MIT、Copyright (c) 2025 CeliaClaire,署名见 `THIRD-PARTY-NOTICES.md`。

---

## 6. 重锋本体刀的注册实参(`javap -c` 逐字节实证)

`mods.flammpfeil.slashblade.registry.SlashBladeItems#lambda$static$16`:

```
new ItemSlashBlade(
    new ItemTierSlashBlade(40, 4.0F),   // bipush 40 / ldc float 4.0f
    4,                                  // iconst_4
    0.0F,                               // fconst_0
    new Item.Properties())              // 裸 Properties,未调 stacksTo ⇒ 默认 64
```

- `mods.flammpfeil.slashblade.item.ItemTierSlashBlade implements Tier`,`public ItemTierSlashBlade(int, float)`
  (字段 `uses` / `attack`;方法 `getUses/getSpeed/getAttackDamageBonus/getEnchantmentValue/getIncorrectBlocksForDrops/getRepairIngredient`)。
- 对照:`ItemSlashBladeDetune`(未开刃)用 `ItemTierSlashBlade(70, 4.0F)`,实参同为 `4` / `0.0F`。
- **`attackSpeedIn = 0.0F` 是关键**:它一直生效(4.0 次/秒满攻速),与版原版剑的 `-2.4` 不同 —— 1.1.0 的 5 把充能刀曾照抄该值,**那 5 把刀已在 1.2.0 删除**。
- (历史,属于已删除代码)本模组当时有意偏离:属性用 `stacksTo(1)`(因为能量是数据组件,堆叠会产生"同栈不同能量"歧义);
  重锋本体是裸 `Properties`(默认 64)。该设计要求随 `ChargedBladeItems` 一并删除。

## 7. AE2 能量网格的取电路径(`javap` 实证)

| 签名 | 备注 |
| --- | --- |
| `IGrid#getEnergyService()` → `IEnergyService` | default 方法 |
| `IEnergyService extends IEnergySource` | ⇒ 机器取电的标准入口就是 `extractAEPower` |
| `IGridNode#getGrid()` → `IGrid` | |
| `IManagedGridNode#getGrid()` → `IGrid`(default)、`isReady()`、`isActive()`、`isOnline()`、`isPowered()`、`getNode()`、`destroy()` | `destroy()` **确实存在**;`setRemoved()` 在 `AEBaseBlockEntity` 上**非 final**(可覆写) |
| `PowerMultiplier`:常量 `ONE` / `CONFIG`;字段 `multiplier`;方法 `multiply(double)` / `divide(double)` | |
| `Actionable`:常量 `SIMULATE` / `MODULATE`;`isSimulate()` / `of(FluidAction)` / `ofSimulate(boolean)` / `getFluidAction()` | |

**`EnergyService` 的结构**(`appeng.me.service.EnergyService`):
字段 `SortedSet<IAEPowerStorage> providers` + `requesters` + `GridEnergyStorage localStorage`;
私有 `addProvider/addRequester` 都按 `getPowerFlow()` 分流,`addNode` 里再用 `isAEPublicPowerStorage()` 决定是否纳入共享池。

⇒ **结论(已据此改实现)**:充能器不再被动等"公开存储被注入",而是走 AE2 机器通用路径
`grid.getEnergyService().extractAEPower(need, SIMULATE/MODULATE, PowerMultiplier.ONE)` 主动取电,
本机缓冲降级为第二来源。`SIMULATE` 先探量 → `MODULATE` 只取用得上的整数点 → 零头退回缓冲,
保证 `got = granted × perPoint + leftover` 恒等,不凭空生成或销毁 AE。

## 8. 已实证的 MC / NeoForge 补充点

| 签名 | 备注 |
| --- | --- |
| `BlockBehaviour#saveAdditional` 的可见性 | `AEBaseBlockEntity#saveAdditional(CompoundTag, Provider)` 是 **public** ⇒ 子类覆写必须 `public`(写 `protected` 会编译失败) |
| `AEBaseBlockEntity#loadAdditional` | **final**;字节码 offset 153 `invokespecial BlockEntity.loadAdditional`,offset 34 `invokespecial BlockEntity.saveAdditional` ⇒ `getPersistentData()`(NeoForgeData)闭环成立 |
| `BlockEntityTicker#tick(Level, BlockPos, BlockState, T)` | 编译期已证:`Block#getTicker` 返回该函数式接口 |
| `Player#openMenu(MenuProvider, Consumer<FriendlyByteBuf>)` | 双参重载**存在**(编译期已证) |

## 9. 刀鞘(鞘)几何的实测基准与三个缺陷(2026-02 基线,工具实测)

> **删除说明(1.2.0)**:本节记录的**自设刀身 / 刀鞘几何设计基线、生成的对比图与据此做的
> `verifyResources` 回归检查,已随 5 把充能拔刀剑与程序化刀身生成器 `tools/blades.gradle`
> (`generateBlades`)整体删除**(连同 `build/blade-preview/*` 的产出)。
> 下面是 1.1.0 时期的历史记录,保留原因只有一个:**重锋参考实现的尺寸数据与 Wavefront 分组陷阱**
> 是对外部素材的实测结论,与"我们那把刀长什么样"无关。现存唯一一把刀「莉莉」不再由生成器产出,
> 而是逐字节复制丛雨丸的 `murasamemaru.obj` / `murasamemaru.png`(见 `THIRD-PARTY-NOTICES.md`)。

取证工具:`tools/obj-stats.ps1`(面归属分组 + 逐 X 分箱断面统计);
同尺度真彩对比图:`build/blade-preview/stand-compare.png`(由 `generateBlades` 生成,
左 = 重锋 yamato 的 blade+sheath 合成,右 = 本模组涌流同姿态合成,同一 px/单位)。**该生成器与产物均已删除。**

### 9.1 为什么必须"按面归属"分组

Wavefront 常把全部 `v` 行写在最前、`g` 行写在中间。**按顶点出现顺序**分组会把整个模型都算进第一个组
(实测 yamato:`<none>` 900 顶点、各命名组 0 顶点,是错的);**按面归属**才得到
`blade` 83 / `sheath` 176 这样的正确值。这是"分组尺寸结论"能用的前提。

### 9.2 参考实现六个刀的实测尺寸(模型单位,1 格 ≈ 157.4)

| 刀 | sheath X 跨度 | sheath 断面高×厚 | blade X 跨度 | blade 高×厚 |
| --- | --- | --- | --- | --- |
| yamato | 260.8 | 12.6 × 7.3(逐箱) | 332.7 | 11.0 × 1.1(刀身段) |
| agito | 270.4 | —(含下绪至 Y+72) | 333.4 | —(handle 组 28.9 × 20.2) |
| yasha | 260.8 | 12.6 × 7.3 | 332.7 | 10.9 × 7.1 |
| muramasa | 266.3 | — | 333.4 | — |
| dios | 251.4 | — | 335.1 | — |
| sange | 264.7 | — | 332.1 | — |

⇒ **鞘是贴住刀身的薄壳**(横断面只比刀身高约 14%),不是粗管。
(注:上表的 1 格 ≈ 157.4 是当时按图标姿势反推的错误尺度,已由第 11.1 节的 **1 格 = 320 单位**取代;
表内数值是模型单位,不受影响。)

### 9.3 三个已修缺陷(针对**已删除**的自设刀身)

1. **鞘做胖了 2.2 倍**:上一版 Y 半轴 19→15、Z 半轴 8.5→7 = 断面 38×17(刀身 17×6.4 的 2.2 倍)
   ⇒ 游戏里读成"一块厚板/一根粗管",玩家判定为"没有刀鞘"。
   改按参考**相对**比例(×1.15/×1.3)后变成 19.6×8.4 ⇒ 玩家第二次反馈"**没有任何改变**"、
   "**收刀时收进了透明的刀鞘**" —— 因为参考的刀身是**薄片**(断面 11×1.1),它的鞘 12.6×7.3
   靠"厚度是刀身 6.6 倍"读出来;我们的刀身本来就有 17×6.4,同比例缩出来的鞘在屏幕上
   和刀身是**同一条**。最终按**可读性**定:**断面 27×14**(横向 1.6 倍、厚度 2.2 倍),
   同时把鞘漆改成**深梅色 + 玫瑰金箍**(见第 10 节:配色对比比尺寸更决定"看不看得出是鞘")。
2. **`SAYA_OFFSET_Y = +6` 的依据是错的**:那个"重锋鞘中心比刀身中心偏 +11.8"是**整组 bbox** 的差,
   而鞘组里含下绪(从栗形向上甩出 18 单位的弧),bbox 上限被它抬到 +44。
   逐箱实测同段对比:yamato 在 X∈[-296,-274] 处刀身 Y 中心 -22.0、鞘 Y 中心 -23.65 ⇒ 真实偏心 **-1.65**。
   现取 **0**(同轴),否则鞘收窄后刀会从鞘底穿出。
3. **鞘口位置错**:上一版鞘口取 92(= 鍔起点)。鞘一收窄,鍔(Y±18 / Z±6)与鎺(Y±6.6 / Z±5)
   都比鞘口(±9.8 / ±4.2)大 ⇒ 鍔盘、鎺套**从鞘壁侧面穿出来**。
   现取鞘口 = **107**(刃身起点 106 之后 1 单位):鎺留在口外当金属箍,刃身整段被鞘包住。

### 9.4 回归保护(verifyResources 第 28 项)—— 已随自设几何一并移除

- **鞘包刀逐顶点判定**(`sheathEnclosure`):把鞘顶点按 X 聚成环,插值出鞘在每处的 Y/Z 包络,
  再逐根刀身顶点判是否穿出。当时结果:**150 点,最小余量 0.72,最大穿出 0.00**。
  该检查在引入时**当场抓到**了鞘口盖在鍔/鎺上这个缺陷(worst = 40.51)。
- **包络只认"整环"**:鞘体环/鞘口环/鞘尻环在每个 X 上有 12 个顶点,而栗形与下绪每 X 只有 2 个。
  若不按顶点数过滤(≥6),会造出"Y 只有 33.4~33.4 的一条下绪细缝"这种**伪包络**,
  把正常刀身顶点误判成穿出 40 单位 —— 这个假阳性实际发生过。

### 9.5 当时(1.1.0)的验证结果

`verifyResources` 45/45 通过(含上述检查)、`build` 成功、`runGameTestServer` **27/27 通过**
(兼容模组全部在位:dummmmmmy / moonlight / jei / mezz_config / codecui / imblocker)。
**这些数字属于 1.1.0 的历史状态;当前基线见 README(1.2.0:GameTest 21/21、verifyResources 22/22)。**

---

## 10. **OBJ 的 v 会被重锋翻转** —— 本模组最关键的一条渲染事实(`javap` 实证)

`mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject#parseTextureCoordinate`
字节码偏移 43-50:

```
43: fconst_1                 // 1.0f
44-47: <取第 2 个字段>
50: fsub                     // 1.0f - v
51: invokespecial TextureCoordinate."<init>":(FF)V
```

即解析 `vt u v` 时存进去的是 **`u, 1-v`**;而渲染侧 `Face.uvOperator` 的默认值是
`Vector4f(1,1,0,0)`(见 `Face` 的 `<clinit>`:偏移 26-33 写 `uvDefaultOperator`,36-39 赋给 `uvOperator`)
= **恒等**,不会把这层翻转抵消。

⇒ **游戏里的采样行 = `1 - v_obj`。**

> 下面 3 段(后果 / 修法 / 回归检查)描述的是**已被删除的**程序化刀身生成器
> (`tools/blades.gradle` / `generateBlades`)与其 `ObjWriter` 的内部约定 ——
> 保留它们只为:日后若重新引入 OBJ 生成,不必再踩同一个坑。**这些代码与检查当前都不在仓库里。**

**后果(实测踩过,自设刀身「涌流」,已删除)**:生成器内部一直按"v = 贴图行比例"的直觉工作(贴图绘制与真彩预览都这么采样),
若写 OBJ 时直接抄这个 v,游戏就会去采样**镜像后的分区**。涌流最初 `blade` 组 v∈[0,0.30]
会被采到行 [0.70,1.00] —— 其中 0.80~1.00 是**未绘制的透明像素**,于是那一段模型在游戏里
直接消失/透出背景,表现就是玩家说的"**透明的刀鞘**"、"**没有任何改变**"。

**修法(属已删除的生成器)**:`ObjWriter.emit` 写文件时补一次 `vt u (1-v)`,生成器内部与预览保持自然约定
(这样 PNG 是正着看的,预览所见 == 游戏所见)。

**回归检查(当时 `verifyResources` 第 30 项,`uvOpaqueCheck`,已随自设几何移除)**:逐组按**游戏采样规则**
(`py = (1-v)*H`)把每个面的 UV 打到贴图上,统计落在 alpha=0 像素上的比例,>2% 即失败。
当时结果:采样 **8545 点,落在透明像素 0 点**。这条检查在修好之前必然报警(blade 组约 2/3 采样点
落在透明行上)。

### 10.1 鞘( sheath 组)到底在哪些渲染路径里被画(字节码全量检索)

以字节序列检索整个 jar,只有 4 个类引用 `sheath` 分组:

| 类 | 用途 |
| --- | --- |
| `SlashBladeTEISR#renderModel` | 刀架 / 地面 / GUI 图标(顺序:`blade` → `sheath`,同一姿势) |
| `LayerMainBlade` | 收刀后**背在背上/挂在腰上**(同样 `blade` → `sheath`) |
| `BladeItemEntityRenderer` | 掉在地上的刀 |
| `ComboStateRegistry` | 连段状态里的引用 |

**`BladeFirstPersonRender`(第一人称手持/收刀动作)完全不引用 `sheath`** ——
所以第一人称下"收刀收进看不见的鞘"是**重锋本体的行为**,不是本模组的缺陷。
这也是为什么"在手上"只能看到裸刀:`renderBlade`(手持分支)只画 `blade*` 组。

### 10.2 `renderModel` 里"画不画鞘"的完整判定(javap 逐条)

```
 0: ldc 0.003125f ; scale               // ★ 1 格 = 320 单位(不是 157.4)
15: ldc 130.0f    ; translate(x,0,0)    // 世界姿态整体 +130
28: SwordType.from(stack) → var7        // 只用于选 blade / blade_damaged
100/105/108: var12=Vec3.ZERO  var13=0   var14=-3.0f   // 刀身:偏移 0、二次旋转 -3°
113/118/121: var15=Vec3.ZERO  var16=0   var17=-3.0f   // ★ 鞘:偏移 0、二次旋转 -3°
126/129: var18=0  var19=0               // 翻转 / 左右
132: var20=1                            // ★ "画鞘"默认值 = 1(与 SwordType 无关)
135: if (!stack.isFramed()) → 575        // 不在展示框/刀架上就保持默认 ⇒ 仍然画鞘
… 刀架分支按 pose 与刀架类型改 18/19/20;
   只有 pose 4(SPIN_ATTACK)与 5(CROUCHING) 把 var20 置 0
803: if (var20 == 0) return              // 只有这两种姿势不画鞘
944: renderOverrided(..., "sheath", …)   // 画鞘
```

⇒ **默认姿势(STANDING)下鞘一定会被渲染,且与刀身同一偏移、同一 -3° 旋转。**

---

## 11. 渲染尺度与"看不看得出鞘"(把可读性变成断言)

### 11.1 尺度:1 格 = 320 单位

`renderModel` 第一条指令 `ldc 0.003125f` + `scale` ⇒ **1/320**。重锋 blade 组 X 跨度 332.7 × 0.003125
= **1.04 格**(刀长约一格)✓ 自洽。本文件早期用的 157.4(= 1/0.00635)是从 `item_blade`
**图标姿势**包围盒反推的,图标姿势自带额外缩放,不能当世界尺度 —— 用错会让"格"的换算差一倍。

### 11.2 换算到屏幕像素(16 px/格)—— 属于**已删除**的自设刀身

> 下表针对的是**本模组自设的刀身/刀鞘几何**(已在 1.2.0 随 5 把充能刀与生成器删除),
> 表内的模型单位数值只应用于那把已不存在的刀;唯一的"参考 yamato 的鞘"一行仍是对外部素材的事实。

| 部位 | 模型单位 | 游戏像素 |
| --- | --- | --- |
| 刀身宽 | 17 | **0.85 px** |
| 刀身厚 | 6.4 | 0.32 px |
| 鞘(31×18) | 31 / 18 | **1.55 / 0.90 px** |
| 参考 yamato 的鞘 | 12.6 | 0.63 px |

⇒ **形状差异在屏幕上是 1 像素级别**。这解释了当时全部三轮返工:把鞘做粗(38×17)会读成"厚板",
做细(20×8)会读成"和刀身一样"。**参考的鞘之所以读得出来,靠的不是粗,而是"深色漆 + 金属箍"的配色对比。**

### 11.3 据此固化的两条断言(verifyResources 第 31、32 项)—— 已随自设几何移除

1. **鞘组采样平均亮度 ≤ 90**(鞘必须是深色漆,不能是浅色);
2. **刀身组与鞘组的采样平均亮度差 ≥ 80**。

当时实测:**刀身 168.1 / 鞘 80.8,差 87.3** ✓。"游戏里看不出刀鞘"当时是构建失败,而不是主观判断。
(采样规则与游戏一致:`uvRegionBrightness` 按 `py = (1-v)*H` 取像素。)
**这两条断言与 `uvRegionBrightness` 都随自设几何删除;当前仅有「莉莉」的 OBJ/PNG(丛雨丸,逐字节复制)。**

### 11.4 1:1 游戏像素模拟图 —— 已删除

`generateBlades` 曾额外输出 `build/blade-preview/stand-pixel-1to1.png`:
按真实比例(0.05 px/单位)分别渲染"仅刀身"与"刀身+鞘"两张游戏像素图,再最近邻放大 10 倍,
作为**不开游戏就能判断"看不看得出鞘"**的判据。**该任务与产物已随 `tools/blades.gradle` 删除。**




---

## 12. 刀鞘配色的实现与量测(2026-02)

丛雨丸原贴图里刀鞘那部分**几乎是纯黑**,所以游戏里鞘读起来"和刀身一样是一根黑管"。
本节记录实测到的像素分布与配色生成器,供以后换色/换刀复用。

### 12.1 鞘用到的贴图像素(实测,游戏采样空间 `行 = 1 - v`)

| 区域 | 像素 | 谁在用 | 原色 |
| --- | --- | --- | --- |
| 主块 `x 0..95, y 0..~500` | ≈42,500 px(占贴图 32.4%) | 鞘主体的绝大多数面 | (23,22,22) 近纯黑 |
| 保护带(主块顶部 `y 0..15`) | **534 px** | 鞘 **与 `blade`/`blade_damaged`/`blade_fragment` 共用** | 同上/绿 |
| 鞘口小块 `x 236..243, y 100..106` | **38 px** | 鞘口那一侧 301 个面(端面/环面,世界 X 与 u/v 都不相关) | 灰白杂色 |

- 鞘长方向:世界 X 与 UV-**v** 的相关系数 **0.940**(与 u 只有 0.656)⇒ **贴图纵向 = 鞘长**;
  锚点:X≈-296(鞘尻)→ v 0.151(行 ≈434);X≈-139 → v 0.622(行 ≈193);X>-139 → 鞘口那 38 px(行 100..106)。
- 与 `effect`(刀光几何)重叠 **93%**、与 `item_blade`(图标)重叠 **65%** ⇒ 重绘鞘会同时改刀光与图标里的鞘
  (`effect` 不在 `renderModel`/`LayerMainBlade` 的绘制集里,所以刀架与背刀路径不受影响)。

### 12.2 生成器 `tools/lili-texture.gradle`(`gradlew generateLiliTexture`)

1. 从 `lili.obj` 的 `sheath` 组**面 UV 光栅化**出掩码(不硬编码坐标),并换算到游戏采样空间(`行 = 1-v`);
2. 挖掉与刀身三组重叠的 534 px(保护集);
3. 掩码内上色:象牙白 `#F4EEEA → #E2D8D4` 的竖向渐变(鞘口亮/鞘尻暗)+ 鞘口整段与鞘尻端、鞘口端的亮金箍 `#F7C948`;
4. 掩码外像素**从 `art/lili_source.png` 整值复制**;
5. 参数:`-PsayaBase/-PsayaEdge/-PsayaTrim/-PsayaGradient/-PsayaKojiFrac/-PsayaMouthFrac/-PsayaOutline`,以及 `-PsayaRestore` 一键还原;
6. 预览输出到 `build/lili-texture-preview/`:掩码叠加、主块 2×、鞘口 8×,以及**两种屏幕尺度的真实像素模拟**
   (背包图标尺度 16 px/格、刀架近距离约 80 px/格)。

### 12.3 `verifyResources` 的鞘贴图断言(与生成器**各自独立实现**的解析/光栅化)

| 断言 | 实测值 |
| --- | --- |
| 掩码外改动像素 | **0** |
| 与刀身共用像素改动 | **0** |
| 鞘掩码平均亮度(象牙白应 ≥ 200) | **216.8** |
| 鞘掩码亮度 − 刀身面取色亮度(应 ≥ 60) | **70.7**(刀身 146.0) |
| 金箍像素占掩码比(应 4%…50%) | **39.1%** |
| 金箍面数(应 ≥ 40) | **282** |

> 亮度口径用**掩码像素平均**而不是"面的平均":鞘的 357 个面里 282 个取到金色,但它们大多是内层/端面,
> 侧视被外壳挡住 ⇒ 按面平均会把看不见的金算进去,把数字压低(实测 185 vs 掩码 217),从而误报。

---

## 13. 鞘壳圆柱化 + 图集重排 + 花纹全覆盖(后续更新,覆盖 §12 的结论)

§12 记录的是"象牙白 + 金箍"那一版;用户随后要求**不要出现纯色段、边缘要像圆柱**,于是:

### 13.1 原壳的真实几何(实测,修正 §11 里用包围盒估的周长)

- 原 `sheath` 组是**三棱柱**:UV 只有 3 列(u ∈ {0.0218, 0.2025, 0.3735})× 5 行 ⇒ 绕一圈 **3 个面** ⇒ 必然有棱边。
- 按**面心 X 分箱的扫掠包络**拟合(按顶点 X 聚类是错的:壳有反り/剪切,聚类会把相邻环混在一起,实测环数虚高到 13、端点 Y 半轴算出 0):
  断面是 **12.6(Y)× 7.2(Z)** 的扁管,**中心线 Y 从 −23.7(鞘尻)到 +3.1(鞘口)**(这就是反り);
  鞘长 260.8 单位 ⇒ 断面周长 ≈ **31.7** 单位 ⇒ **表面比例 1 : 8.2**。
- 断面指数:e = 0.5(圆角矩形)时曲率全挤在四角,**16 段下相邻面夹角 36.8°**(肉眼仍是棱);
  改 e = 0.8 + **20 段** ⇒ **27.5°** ✓ 读作圆柱。最终 20×24 + 封口 = **500 面**。

### 13.2 图集重排(512×1024)

- 原始素材 **1:1** 放在右上象限(256,0);其余 7 组 UV 统一仿射重映射
  **`u' = 0.5u + 0.5`,`v' = 0.5v + 0.5`** ⇒ 外观零变化(面数逐组相等 + 试渲染对照)。
- **坑(实测黑条)**:重锋解析 OBJ 时 `行 = 1 - v`,所以"把素材压到贴图上半部分"在 OBJ 空间里是
  `v' = 0.5 + 0.5v` 而不是 `0.5v` —— 写成 `0.5v` 会把素材映射到行 0.5..1.0,刀身取到空白像素,渲染成纯黑。
- 鞘的专属区域 **129×1024(1 : 8)** 与表面比例 1:8.2 一致 ⇒ **各向同性**,花纹不再被沿鞘长拉伸 1.8 倍;
  且与其它 7 组的 UV 包围盒**不相交**(断言)。

### 13.3 花纹全覆盖(把 38% 白底填掉)

- 源图约 **38% 是白底**,直接用就会"只覆盖一部分";做法三步:
  ① 按区域**半宽统一缩放**(各向同性前提);② **水平镜像**拼成整宽 ⇒ 绕鞘一周接缝 **34.0 → 0.0**;
  ③ **纵向镜像平铺 + N 层(默认 3)错位 darken**(逐通道 min)⇒ 白底被别层的花瓣盖住。
- 实测:区域内**近白像素 0.0%**(断言 ≤10%),接缝 0.0(断言 ≤6)。
- 水印用**镜像替换**底部 140 px(而不是裁掉),长度不损失。

### 13.4 分辨率取舍

1024×2048 图集时 `lili.png` 是 **1.19 MB**、显存 8 MB;而鞘在刀架上只有约 8 px 宽 ⇒ 严重过采样。
降到 **512×1024**(鞘区域 129×1024)后 `lili.png` **296 KB**,肉眼无差别。

---

## AE2 便携元件 API 实测(javap,AE2 19.2.17 / MC 1.21.1)

用于实现手持「Slash 元件」(见 PLAN-PORTABLE-SLASH-CELL.md)。全部为反编译实测,不是记忆:

| 用途 | 结论 |
|---|---|
| 基类 | `appeng.items.tools.powered.AbstractPortableCell(MenuType<?>, Item.Properties, int)`;`extends PoweredContainerItem extends AEBasePoweredItem`,实现 `IMenuItem` + `ICellWorkbenchItem`(后者要求实现 `setFuzzyMode`/`getFuzzyMode`) |
| 菜单类型 | **`appeng.menu.me.common.MEStorageMenu.PORTABLE_ITEM_CELL_TYPE`**(AE2 自家便携物品元件注册时传的就是它)⇒ 便携界面可整套复用 |
| 供电 | 构造时读 `AEConfig.instance().getPortableCellBattery()`;充放电走 `EnergyCellBlockItem.injectAEPower(...)`;充电速率由子类 `getChargeRate(stack)` 决定 |
| 存储挂点 | `PortableCellMenuHost$CellStorageSupplier.get()` → `StorageCells.getCellInventory(ItemStack, ISaveProvider)` ⇒ **注册自己的 `ICellHandler` 即可接管库存**;该内部类是 private,不能直接复用 |
| 存储接口 | `StorageCell extends MEStorage`:`getStatus()` / `getIdleDrain()` / `canFitInsideCell()` / `persist()`;`ICellHandler`:`isCell(ItemStack)` / `getCellInventory(ItemStack, ISaveProvider)` |
| 坑 | 我们的元件**不得实现 `IBasicCellItem`**,否则被 AE2 自带 `BasicCellHandler` 抢走(与驱动器元件同一个坑);`Actionable` 的枚举常量是 **`MODULATE`/`SIMULATE`**(不是 MODIFY) |