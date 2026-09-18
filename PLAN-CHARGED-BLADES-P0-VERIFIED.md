# 充能拔刀剑 · P0 实证结论(host 用 javap 逐条查证)

> **现状(2026-02,mod 1.2.0)**:这 5 把充能拔刀剑已于 1.2.0 **整体删除**(取而代之的是数据包刀「莉莉」),
> 因此本文里"我们的刀怎么落地"的结论(例如 `setBaseAttackModifier(13.14F)`)只对已删除的代码成立。
> 但本文的 **javap 取证结论本身(重锋 `ItemSlashBlade` 的伤害分支、构造实参等)依然有效**,
> 仍可作为阅读重锋伤害公式的依据。

> 本文是 `PLAN-CHARGED-BLADES.md` 的**配套事实表**。方案里凡标 `NEEDS_JAVAP` 的 P0 项,
> 以本文结论为准;**本文未列出的签名仍然未实证**,实现时不得凭记忆书写。
> 全部结论来自 `javap`(slashblade-2.0.7-1.21.1.jar / ae2-19.2.17.jar / neoforge-21.1.250-merged.jar)。

---

## 1. 伤害 13.14 怎么落地(已缩小到一次校准)

`ItemSlashBlade.getDefaultAttributeModifiers(ItemStack)` 的方法体显示**两条分支**:

**分支一:物品栈上没有刀身数据(state == null)**
```
ATTACK_DAMAGE 修饰 = attackDamageIn + tier.getAttackDamageBonus()
ATTACK_SPEED  修饰 = attackSpeedIn
```
⇒ 这条只为"没有刀身数据的残刀"服务,我们的刀**总是带刀身数据**,不走这条。

**分支二:有刀身数据**
```java
b      = state.getBaseAttackModifier();
refine = state.getRefine();
if (state.isBroken()) {
    extra = -0.5f - b;
} else {
    rate  = SwordType.from(stack).contains(SwordType.FIERCEREDGE) ? 0.1f : 0.05f;
    extra = b * (1 - 1/(1 + rate * refine));      // 字节码:1 - 1/(1+rate*refine) 再 * b
}
// 其后把 b 与 extra 合成最终的 ATTACK_DAMAGE 修饰(偏移 175 之后,未逐行展开)
```

**结论(可直接实施,但需一次游戏内校准)**:
- 令 `refine = 0` ⇒ `extra = 0` ⇒ 刀身只贡献 `baseAttackModifier` ✓
- 因此:**`setBaseAttackModifier(13.14F)` + `refine = 0`** 是正确方向;
- **待校准的一点**:MC 的玩家基础攻击力为 1,而 `SwordItem` 的修饰是"加在基础值上"的
  ⇒ 最终显示伤害可能是 `13.14` 或 `14.14`,取决于这段合成是否已扣掉那 1。
  **实施时先按 `baseAttackModifier = 12.14F` 试,游戏内看 tooltip/属性面板把数字调到正好 13.14**
  (方案里那个 `-1 + 1` 的推断即来自此处,尚未逐行确认)。
- `isBroken()` 分支会给出**负修饰**(`-0.5 - b`)⇒ 断刀几乎打不出伤害,这是重锋原生行为 ✓ 不要覆盖。

## 2. "没能量完全不能使用"的三层实现(全部已实证存在)

| 层 | 证据 | 用法 |
|---|---|---|
| **原生封印** | `ISlashBladeState.isSealed()` 被 `ItemSlashBlade` 自身调用;`SwordType.SEALED` 枚举存在 | 能量归零 ⇒ 置 `sealed`;充能 ⇒ 解除。**这是首选**,由重锋自己拦 |
| **事件取消** | `net.neoforged.neoforge.event.entity.player.AttackEntityEvent extends PlayerEvent implements ICancellableEvent`(有 `getTarget()`) | 兜底:没能量时 `event.setCanceled(true)` ⇒ 完全不能攻击 |
| **方法覆盖** | `ItemSlashBlade` 的 `onLeftClickEntity(ItemStack, Player, Entity)` 与 `use(Level, Player, InteractionHand)` 都是 public、可覆盖 | 第二层兜底(右击剑技);**返回语义未实证**,只做补充不要做主力 |

**扣费时机**:只有"真正命中"才扣 1 点。`hurtEnemy(ItemStack, LivingEntity, LivingEntity)` 可覆盖 ⇒
**建议在 `hurtEnemy` 里扣费**,而把 `AttackEntityEvent` 只用于"没能量就取消"。
⚠️ `onLeftClickEntity` / `hurtEnemy` 的调用顺序与返回值语义**仍未实证** ⇒ 实施时先写一条 GameTest 或加临时日志确认,不要凭直觉。

## 3. 无初始附魔

`BladeStateData.defaultBewitched(boolean)` 字段存在(`SwordType.BEWITCHED` 与之对应)
⇒ **新刀默认刀身数据里设 `defaultBewitched = false`** ✓(这就是"默认不附魔"的开关)

## 4. 外观:模型/贴图逐把可指定(已实证)

`BladeStateData` 有 `Optional<ResourceLocation> texture` 与 `Optional<ResourceLocation> model`;
`ISlashBladeState` 有 `setTexture(ResourceLocation)` / `setModel(ResourceLocation)`
⇒ **5 个物品各自设 `setModel` / `setTexture` 指向重锋现成资源即可,无需自造 MQO** ✓
(待办:host 列一次 slashblade jar 内的 `model/*.mqo` 与 `texture/**` 清单,挑 5 个不同外观)

## 5. 充能方块的 AE2 接线(名字全部实证存在)

| 用途 | 已实证的 API |
|---|---|
| 方块实体拿网格节点 | `GridHelper.createManagedNode(T owner, IGridNodeListener<T> listener)`;配套 `GridHelper.onFirstTick(T, Consumer<T>)`、`GridHelper.getNodeHost(Level, BlockPos)`、`GridHelper.getExposedNode(Level, BlockPos, Direction)` |
| 方块实体实现 | `IInWorldGridNodeHost`(抽象方法只有 `IGridNode getGridNode(Direction)`) |
| 节点标志 | `GridFlags.REQUIRE_CHANNEL` 等枚举 |
| **接受 AE(推荐路线)** | 实现 `appeng.api.networking.energy.IAEPowerStorage`(`injectAEPower(double, Actionable)` / `getAEMaxPower()` / `getAECurrentPower()` / `isAEPublicPowerStorage()` / `getPowerFlow()` / 默认 `getPriority()`)⇒ **AE2 的能量服务会自动往里注入**,我们只管从自己的缓冲里放电给刀 ✓ 比主动抽取更简单、更符合 AE2 机器惯例 |
| 主动抽取(备选) | `IEnergyService`(含 `injectPower`、`getStoredPower`、`isNetworkPowered` …)与 `IEnergySource.extractAEPower(double, Actionable, PowerMultiplier)` |
| **省代码的基类** | AE2 自带 `appeng.blockentity.AEBaseBlockEntity`、`appeng.blockentity.grid.AENetworkBlockEntity`、`appeng.blockentity.grid.AENetworkInvBlockEntity` ⇒ **`AENetworkInvBlockEntity` 直接给到"网格节点 + 内部库存"**,强烈建议继承它而不是从零接管网格 |

## 6. 依赖与元数据

- `slashblade` 与 `ae2` 的 `type` 由 `optional` 改 `required`(用户已确认)。
- ⚠️ **风险**:只用「不可堆叠物品存储元件」的现有用户会被强制安装重锋与 AE2 ⇒ 需在 Release 说明里写清;
  现有 GameTest 里"AE2 缺席仍可加载"的降级断言(若有)要相应调整。

## 7. 仍未实证(留给下一轮,实施前必须消掉)

1. ~~`getDefaultAttributeModifiers` 偏移 175 之后的合成细节~~ ⇒ **已在第 8 节闭合**
2. `onLeftClickEntity` / `hurtEnemy` / `AttackEntityEvent` 三者的**调用顺序与返回值语义**(部分缓解:见第 8 节新增的 `UpdateAttackEvent`)
3. `IManagedGridNode` 的创建参数(通道/标志/暴露面)与 `AENetworkInvBlockEntity` 的构造要求(创建与配置方法已实证,构造要求仍未查)
4. ~~`IAEPowerStorage` 是否需要在节点上注册为服务~~ ⇒ **已在第 8 节闭合**
5. slashblade jar 内可用的 `model/*.mqo` 与贴图清单(挑 5 个外观)—— **仍未查**(host 的列举脚本写错了变量,下一轮补)
6. ~~`BladeStateData` 里哪些字段属于"持久化"~~ ⇒ **已在第 8 节闭合**

---

# 第 8 节 · 第二轮闭合(host 再次 javap)

## 8.1 伤害 13.14 —— **完全实证,无需游戏内校准** ✅

`getDefaultAttributeModifiers` 的**带刀身数据分支**完整字节码如下(偏移 94..235):

```java
b          = state.getBaseAttackModifier();
refine     = state.getRefine();
double extra = state.isBroken()
        ? (-0.5 - b)                                    // 断刀:负贡献
        : b * (1.0 - 1.0 / (1.0 + rate * refine));       // rate = FIERCEREDGE ? 0.1 : 0.05

double value = b + extra - 1;                           // ← 偏移 175..183:fadd 后 dconst_1 / dsub

// 关键:这里还会**发一个原生事件**,并采用它给出的数值
var ev = new SlashBladeEvent.UpdateAttackEvent(stack, state, value);
NeoForge.EVENT_BUS.post(ev);
ATTACK_DAMAGE 修饰 = ev.getNewDamage();                 // ADD_VALUE / MAINHAND
```

**⇒ 令 `refine = 0`:**
- `extra = b * (1 - 1/1) = 0`
- 修饰值 `value = b - 1`
- MC 玩家基础攻击力为 1 ⇒ **最终显示伤害 = 1 + (b - 1) = b**

**⇒ 结论:`setBaseAttackModifier(13.14F)` + `refine = 0` 就精确得到 13.14**,不需要"试 12.14"这一轮校准。
(方案里 A 推出的 `b + b(1-1/(1+rate·refine)) - 1 + 1` 与此一致,那个"+1"就是玩家基础值 ✓)

**额外收获**:`mods.flammpfeil.slashblade.event.SlashBladeEvent$UpdateAttackEvent`
有 `getNewDamage()`(构造参数为 `ItemStack, ISlashBladeState, double`)⇒ **重锋自己会发这个事件**,
我们因此多了一个**官方的伤害/攻击行为挂钩点**,可用于"没能量时把伤害压到 0"或做能量强度影响伤害之类的设计,
比覆盖方法更干净 ✓

## 8.2 持久化 vs 运行时的组件归属 ✅

```java
SlashBladeDataComponents.BLADE_STATE_DATA     -> BladeStateData        // 持久化(我们的存储元件不剥离)
SlashBladeDataComponents.BLADE_RUNTIME_STATE  -> BladeRuntimeStateData // 运行时(我们的存储元件会剥离)
```
⇒ **两个组件是分开的** ✓

**推论(直接影响能量设计)**:`BladeStateData` 是**记录、字段固定**,我们**不能往里加字段** ✗
⇒ **能量必须放在我们自己的组件里**(例如 `applied_slash:blade_charge`,整型)✓
- 它是普通物品组件 ⇒ **随物品持久化** ⇒ 满足用户选择的 (B)"入库取出后能量保留" ✓
- 同时也**会进入 AE2 的存储键** ⇒ 量化(10 档)是必需的缓解 ✓
- 与 `BLADE_RUNTIME_STATE` 无关,所以不会和我们既有的"剥离运行时状态"逻辑冲突 ✓

## 8.3 AE2 节点服务的注册方式 ✅

```java
GridHelper.createManagedNode(T owner, IGridNodeListener<T> listener)   // 创建受管节点
IManagedGridNode.create(Level, BlockPos)                              // 落地到世界
IManagedGridNode.setFlags(GridFlags...) / setExposedOnSides(Set<Direction>)
IManagedGridNode.setIdlePowerUsage(double) / setVisualRepresentation(...)
IManagedGridNode.addService(Class<T extends IGridNodeService>, T)     // ← 答案:用这个注册能量服务
```
⇒ **`addService(IAEPowerStorage.class, this)` 即可把方块的能量缓冲挂到网络上**,
AE2 的能量服务随后会自动注入(见第 5 节),我们只需从缓冲里放电给刀 ✓

## 8.4 仍未闭合(下一轮)
1. ~~攻击钩子的执行顺序~~ ⇒ **已在第 9 节闭合,并据此修正了设计**
2. ~~`AENetworkInvBlockEntity` 构造要求~~ ⇒ **已在第 9 节以"换基类"的方式闭合**
3. ~~slashblade jar 内模型/贴图清单~~ ⇒ **已在第 9 节闭合(5 个外观已选定)**

---

# 第 9 节 · 第三轮闭合(host再次 javap)—— P0 全部结束

## 9.1 攻击链实证:**两条钩子不能用,改用原生封印 + 原生伤害事件** ✅(设计修正)

`Player.attack(Entity)` 的全类扫描结果:
- ✅ **`ItemStack.hurtEnemy(LivingEntity, Player)` 在偏移 1177 被调用**,紧随其后 1227 调 `ItemStack.postHurtEnemy(...)`
  ⇒ **"只有真正命中才扣 1 点能量"应实现在 `hurtEnemy`** —— 这条有调用点实证 ✓
- ❌ **在整个 `Player` 类里扫不到 `onLeftClickEntity` 与 `AttackEntityEvent` 的触发**
  ⇒ 之前方案把 `AttackEntityEvent` 当"主保证"是**错的**,不能依赖它(至少不能依赖"它在 Player.attack 里被触发"这一假设)

**取而代之的两条已实证机制(都直接可用)**:
| 机制 | 证据 | 作用 |
|---|---|---|
| **原生 `sealed`(封印)** | `ItemSlashBlade` 自身调用 `ISlashBladeState.isSealed()`;`SwordType.SEALED` 存在 | 能量归零 ⇒ 封印 ⇒ 重锋自己拦住使用 ✓ **主保证** |
| **原生 `SlashBladeEvent.UpdateAttackEvent`** | 在 `ItemSlashBlade.getDefaultAttributeModifiers` 内部 `NeoForge.EVENT_BUS.post(...)` 后取 `getNewDamage()` | 需要时把伤害压到 0(或做能量强度影响伤害)✓ **第二保证** |
| `hurtEnemy` 覆盖 | 调用点已实证(见上) | **扣费唯一入口** ✓ |

⇒ **网络/事件类守卫一律不作为主保证**;`onLeftClickEntity` 与 `AttackEntityEvent` 若日后要用,必须先单独证明其触发路径。

## 9.2 充能方块:不依赖未确认的 grid 基类 ✅

- `AENetworkInvBlockEntity` / `AENetworkBlockEntity` 的成员枚举在本轮**未取到**(javap 无输出),**不再作为依赖**;
- 改用**已实证**的路线:基类 `appeng.blockentity.AEBaseBlockEntity`
  (构造器实证:`AEBaseBlockEntity(BlockEntityType<?>, BlockPos, BlockState)`)
  + 自行实现 `appeng.api.networking.IInWorldGridNodeHost`(唯一抽象方法 `IGridNode getGridNode(Direction)`)
  + `GridHelper.createManagedNode(owner, listener)` 建受管节点
  + `IManagedGridNode.create(Level, BlockPos)` / `setFlags` / `setExposedOnSides` / `setIdlePowerUsage`
  + `addService(IAEPowerStorage.class, this)` 注册能量缓冲 ✓

## 9.3 5 把刀的外观:用重锋现成资源(已选定)✅

jar 内 `assets/slashblade/model/` 下既有模型也有**同名贴图** ⇒ 逐把设 `setModel` + `setTexture` 即可,无需自造资产:

| 刀 | 建议模型 | 建议贴图 |
|---|---|---|
| 充能刀·脉冲 | `slashblade:model/named/agito.obj` | `slashblade:model/named/agito_true.png` |
| 充能刀·谐振 | `slashblade:model/named/dios/dios.obj` | `slashblade:model/named/dios/dios.png` |
| 充能刀·涌流 | `slashblade:model/named/muramasa/muramasa.obj` | `slashblade:model/named/muramasa/muramasa.png` |
| 充能刀·超荷 | `slashblade:model/named/sange/sange.obj` | `slashblade:model/named/` 下同名贴图(实施时确认文件名) |
| 充能刀·奇点 | `slashblade:model/named/yamato.obj` | 同上(`named/` 下同名贴图,实施时确认) |

⚠️ 实施前需 host 用一条命令确认后两个的**贴图文件名**(本轮只列到前 18 项);
⚠️ 模型/贴图是**重锋自己的资源**,在 CurseForge/mcmod 描述里应说明"外观复用重锋自带模型",避免被误认为盗用素材。

---

## P0 状态:全部闭合 ✅

第 1 轮闭合 4 条、第 2 轮闭合 4 条、第 3 轮闭合 3 条并**修正 2 处设计**(攻击守卫、充能方块基类)。
**下一阶段可以直接进入 B(骨架)**,出口标准:`build` + `verifyResources` 绿、现有 12 条 GameTest 不回归。
