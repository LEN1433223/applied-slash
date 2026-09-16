# 充能拔刀剑 —— 已实证 API 事实表(host 用 javap 取证,2026-02 基线)

> 全部条目由 host 在 `javap -p` 上直接读取,非记忆、非推测。**未列出的签名一律不得凭记忆写**。
> 取证来源:
> - MC/NeoForge:`build/moddev/artifacts/neoforge-21.1.250-merged.jar` + `neoforge-21.1.250-universal.jar`
> - AE2:`libs/ae2-19.2.17.jar`
> - 重锋:`libs/slashblade-2.0.7-1.21.1.jar`
> - 原始 dump 文本:`build/facts/{state,itemblade,events,assets,itemmc}.txt`

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
| `ISlashBladeState#setSealed(boolean)` | 封刀旗标 —— 空能量禁用靠它 |
| `ISlashBladeState#setDefaultBewitched(boolean)` | 初始附魔旗标(要"无初始附魔"须置 false) |
| `ISlashBladeState#setDestructable(boolean)` | |
| `ISlashBladeState#setBaseAttackModifier(float)` | 传 `13.14F` + `refine=0` ⇒ 显示伤害正好 13.14 |
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
> 本模组以**引用方式**复用其贴图资源,发布说明(CurseForge / mcmod)中必须标注来源与致谢。

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
- **`attackSpeedIn = 0.0F` 是关键**:它一直生效(4.0 次/秒满攻速),与版原版剑的 `-2.4` 不同 ⇒ 本模组 5 把刀照抄该值。
- 本模组的有意偏离:属性用 `stacksTo(1)`(因为能量是数据组件,堆叠会产生"同栈不同能量"歧义);
  重锋本体是裸 `Properties`(默认 64)。此为设计选择,已在 `ChargedBladeItems` 注释中标明。

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
