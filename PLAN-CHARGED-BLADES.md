# PLAN-CHARGED-BLADES — 5 把「充能拔刀剑」+ AE 充能方块（方案 v1，A 阶段）

- 项目根：`D:\Agnet_all\Agnet_mod2\appliedslash-template-1.21.1`
- 平台：**NeoForge 21.1.250 / MC 1.21.1 / Java 21**（现代线 1.20.5–1.21.x：JDK 21、`META-INF/neoforge.mods.toml`、ModDevGradle 2.0.147）
- 前置：AE2 `19.2.17`、GuideME `21.1.14`、SlashBlade: Resharpened `2.0.7-1.21.1`（本方案后两者与 AE2 全部改为 **required**）
- 本文档只做规划与取证登记：**不含可编译交付物**；B 写骨架、C 写实现、D 做审查与构建验证。

## 证据分级（全文遵守）

| 标记 | 含义 |
|---|---|
| **[实]** | host 已用 `javap` 实证（`build/asverify/javap-report.txt`、`build/asverify/dep-report.txt`），本文直接采用，不再重复取证 |
| **[本]** | 本项目现有代码已读（给出文件与行号），可直接复用 |
| **[网]** | 网络检索到的线索，**只作线索**，不作论证依据 |
| **NEEDS_JAVAP-N#** | 需要 host 用 `javap`/`jar tf` 取证后才能定稿的点，见 **附录 B**（编号 N1…N40） |

**最重要的两条既有实证（本方案一切设计的地基）**

1. **[实]** `ItemSlashBlade#getDefaultAttributeModifiers(ItemStack)` 字节码（`javap-report.txt` 偏移 1911–2057）完整给出了伤害公式 —— 所以「伤害=13.14」可以**算出来**，不靠猜。
2. **[实]** `ItemSlashBlade#hurtEnemy` 恒返回 `true` 且只做「命中后的刀身处理」（偏移 2259–2269）；`onLeftClickEntity` 走 `BladeStateAccess.of(stack).filter(s -> !s.onClick())` → `progressCombo` → 返回 `isPresent()`（偏移 2107–2120、3495–3531）。**这两处的返回值语义必须在 MC 侧查清**（N23/N24），方案对此给了两种语义各自的写法 + 一个不依赖该语义的主门禁。

---

## 1. 交付物总览

| # | 需求（用户已拍板） | 落地机制（一句话） |
|---|---|---|
| R1 | 5 把独立刀，各自继承 `ItemSlashBlade`，伤害统一 13.14，无初始附魔 | 1 个基类 `ChargedBladeItem extends ItemSlashBlade` + 5 个具体子类；`setBaseAttackModifier(13.14F)` + `refine=0` + `defaultBewitched=false`（§3） |
| R2 | 充能上限 + 每次攻击消耗 1（可配）；没能量完全不能用 | 能量放**本模组持久化数据组件** `applied_slash:blade_energy`；门禁四层（右键 / 攻击事件 / onLeftClickEntity / sealed 表现层），扣费唯一位置 = `hurtEnemy`（§4、§5） |
| R3 | 新增专用充能方块，吸附 ME 网络，1 刀槽，从网络取 AE 充能，带 GUI | 照 AE2 自带 **Charger** 逐段抄结构：网格连接方块实体 + 单槽 + `IEnergyService.extractAEPower` + 自绘 GUI（§6） |
| R4 | 能量存持久化刀身数据（存元件再取出保留） | 组件随 `AEItemKey.toTag/fromTag` 整栈往返 **[本]** `vault/BladeVaultShard.java:91,61` ⇒ 天然保留 |
| R5 | 入库量化到 10 档 | `BladeIdentity.normalize` **+ `BladeIdentity.mayNormalize`**（后者才是关键开关）+ 纯函数 `quantize`（§7） |
| R6 | slashblade / ae2 改 required；5 刀 + 方块进本模组创造栏 | `src/main/templates/META-INF/neoforge.mods.toml` 两处 `type` 改动；`AppliedSlash.MAIN_TAB.displayItems` 追加 6 项（§8） |

---

## 2. 关键决策（含被否方案，供 D 复核）

| 决策点 | 选择 | 理由 / 被否方案 |
|---|---|---|
| 5 把刀是「1 基类 + 5 子类」还是「1 类 5 实例」 | **1 抽象基类 + 5 个子类** | 每把刀将来可能要独立覆写（SA、外观、上限调整）而不碰别人；与项目现有「平行实现、显式类」风格一致（`cell/UnstackableIdentity` 注释明确写了这个偏好）。1 类 5 实例也能满足「5 个独立物品」，但每把刀的差异只能靠外部 spec 传参，后续特化会变成 if-else |
| 能量存哪 | **自研持久化组件 `applied_slash:blade_energy`（Int）** | 见 §4.1 的候选对比表；`BladeStateData` 是 record，18 个组件里**没有任何自由整型字段**（[实] host 给出的字段清单），无法复用来存能量 |
| 没能量的门禁 | **`AttackEntityEvent` 取消为主保证**，`use()` 覆写 + `onLeftClickEntity` 覆写为双保险，`sealed` 只作表现层 | `sealed` 是否真的拦伤害**无任何实证**（N12/N15/N24），把硬需求压在一个未验证的布尔位上不可接受 |
| 耐久 | `destructable=false` + 覆写 `damageItem` 返回 0 + 监听 `BreakEvent` 取消 | 「破刀」会让 `getDefaultAttributeModifiers` 走 `newDamage = -0.5 - base` 分支（[实] 偏移 1966–1973）⇒ 伤害变负、13.14 被破坏；且 `damageItem` 在 `isBroken() && isDestructable()` 时 `stack.shrink(1)` 直接销毁物品（[实] 2240–2249）。能量已是唯一资源，再加耐久 = 双资源 + 负伤害 |
| 量化档位定义 | `{0, p, 2p, …, 9p} ∪ {max}`，`p = max/10`（≤11 个可能取值/把刀） | 比「纯 10 等分」多了「满能量精确」这一个点：满能量的刀入库取出仍是满能量（最常见的场景无损）。5 把默认上限 200/400/800/1600/3200 都能被 10 整除 ⇒ `p` = 20/40/80/160/320 |
| 模型/贴图 | 物品图标 = 我们自己的 16×16 PNG（`tools/textures.gradle` 生成）；**刀身渲染**先用重锋现成资源名，逐把区分放第 2 步 | 需求允许「五把共用同一个」。刀身资源的引用格式**未实证**（N17/N18），先跑通「不写 model/texture 用默认」，再替换 |

---

## 3. 5 把「充能拔刀剑」

### 3.1 物品 id、类与构造参数

| 物品 id | 类 | 中文名 | 英文名 | 能量上限 |
|---|---|---|---|---|
| `applied_slash:charged_blade_pulse` | `ChargedBladePulseItem` | 充能刀·脉冲 | Charged Blade: Pulse | 200 |
| `applied_slash:charged_blade_resonance` | `ChargedBladeResonanceItem` | 充能刀·谐振 | Charged Blade: Resonance | 400 |
| `applied_slash:charged_blade_surge` | `ChargedBladeSurgeItem` | 充能刀·涌流 | Charged Blade: Surge | 800 |
| `applied_slash:charged_blade_overcharge` | `ChargedBladeOverchargeItem` | 充能刀·超荷 | Charged Blade: Overcharge | 1600 |
| `applied_slash:charged_blade_singularity` | `ChargedBladeSingularityItem` | 充能刀·奇点 | Charged Blade: Singularity | 3200 |

**基类签名（照抄）**

```java
public abstract class ChargedBladeItem extends ItemSlashBlade {
    protected ChargedBladeItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);   // [实] ItemSlashBlade(Tier,int,float,Item$Properties) 存在
    }
    public abstract String bladeId();      // "applied_slash:charged_blade_pulse"
    public abstract String nameKey();      // "item.applied_slash.charged_blade_pulse"
    public int maxEnergy() { /* 读配置,见 §4.4 */ }
}
```

- `Item.Properties`：`new Item.Properties().stacksTo(1)`。**不要**自己写 `durability(...)`：`SwordItem`/`TieredItem` 已按 tier 赋耐久，而我们用 `damageItem` 覆写让耐久不生效（§5.4）。
- `tier`：用重锋自己那把刀用的 tier（**N19**）；退路 `Tiers.DIAMOND` —— tier 只影响耐久/修复材料/附魔度，`state` 存在时**完全不参与伤害**（[实] 偏移 1957–2057 用的是 `state.getBaseAttackModifier()`，只有 state 缺席时才用 `attackDamageIn + tier.getAttackDamageBonus()`）。
- `attackDamageIn` / `attackSpeedIn`：抄重锋本体的取值（**N19**）。`attackDamageIn` 只在「state 缺席」分支生效，属于兜底值。
- 注册：新建 `charged/ChargedBladeItems.java`，自己持有一个 `DeferredRegister.Items`（namespace 仍为 `applied_slash`；**同 namespace 可以有多个 DeferredRegister**，不必挤进 `AppliedSlashAe2.ITEMS`），只在该类里引用 `ItemSlashBlade`，并在 mod 构造函数里用 `if (AppliedSlash.isSlashBladeLoaded())` 门卫调用 `register(bus)` —— 沿用项目既有的「对前置类的引用集中在门卫后」约定（[本] `AppliedSlash.java:75-86`）。

### 3.2 伤害 = 13.14 的完整推导（[实]，不需再猜）

`getDefaultAttributeModifiers` 的 state 分支（`getBaseAttackModifier()` = `b`，`getRefine()` = `r`）：

```
broken == true                    → mod = -0.5 - b
broken == false                   → rate = SwordType.contains(FIERCEREDGE) ? 0.1f : 0.05f
                                    mod  = b * (1 - 1/(1 + rate*r))
newDamage = b + mod - 1.0                       // ← 再经 SlashBladeEvent.UpdateAttackEvent.getNewDamage()
属性 ATTACK_DAMAGE modifier = newDamage（ADD_VALUE, MAINHAND）
面板/实际攻击力 = 1.0(玩家基础) + newDamage + 其它 modifier
```

代入 `r = 0`、`broken = false`：

```
mod       = b * (1 - 1/1) = 0
newDamage = b - 1.0
总攻击力  = 1.0 + (b - 1.0) = b
```

**结论：`setBaseAttackModifier(13.14F)` + `setRefine(0)` + 不进入 broken ⇒ 13.14。** 注意点：

- `13.14f` 是 float，(double) 展开为 13.139999866485596，工具提示用两位小数格式化 ⇒ 显示 **13.14**；实际结算时 `Player#attack` 里是 `(float)` 取值 ⇒ 13.14f。**不要**写成 13.1400001 之类「凑小数」。
- 三个会破坏 13.14 的路径必须堵住：`broken=true`（§5.4）、`FIERCEREDGE`（玩家给刀加 SE 时由重锋置位 ⇒ 属第三方/玩家操作，记入风险 R10）、`SlashBladeEvent.UpdateAttackEvent` 被其它模组监听改写（同样记 R10）。
- `refine` 一旦 >0，公式变成 `b + b*(1-1/(1+0.05r)) - 1`，13.14 立刻不成立 ⇒ **不允许精炼值进入默认数据**，也不要在充能逻辑里改 refine。

### 3.3 默认刀身数据的写入（含「绝不能没有刀身数据」这条硬约束）

**硬约束（[实]）**：`setDamage`（偏移 2131–2133）与 `damageItem`（偏移 2173–2175）都会 `BladeStateAccess.of(stack).orElseThrow()` —— 一个**没有刀身数据**的本模组刀一旦走耐久路径就是 **NoSuchElementException 崩服**。因此「任何时刻都不允许存在缺刀身数据的本模组刀」，三条创建路径全部覆盖：

| 创建路径 | 处理 |
|---|---|
| 创造栏 | `output.accept(ChargedBladeFactory.fresh(item))` —— 工厂写满默认数据 + 能量（默认给满，见下） |
| 配方 / `/give` / 数据包 | 若 `Item.Properties#component(...)` 在 1.21.1 可用（**N26**）⇒ 把 `BLADE_STATE_DATA` 与 `BLADE_ENERGY` 注册成**物品默认组件**，任何途径产生的栈天生就带数据；不可用则退到下一行 |
| 兜底自愈 | `ChargedBladeFactory.ensureInitialized(stack)`：缺 `BLADE_STATE_DATA` 就补默认、能量越界就 clamp；调用点 = `onCraftedBy`、`inventoryTick`（`!level.isClientSide` 分支）、`use` / 攻击门禁入口。`inventoryTick` 覆盖了「任何持久化容器里的刀」 |

**默认数据逐字段（`BladeStateAccess.of(stack).ifPresent(state -> {...})`，写法照抄 [本] `dev/BladeTestFactory.java:52-59`）**

| 字段 | 值 | 依据 |
|---|---|---|
| `translationKey` | `"item.applied_slash.charged_blade_pulse"` 等 5 个 | **[实]** `getDescriptionId(stack)` = `state.getTranslationKey()`（orElseGet 回落 `super`）；`getBladeId(stack)` = `parseBladeID` = `substring(5).replaceFirst("\\.", ":")` ⇒ 必须是「5 字符前缀 + `ns.path`」的 description-id 形式。**反例**：写成 `applied_slash.charged_blade.pulse` 会得到 `ed_slash.charged_blade...` 垃圾 RL |
| `baseAttackModifier` | `13.14F` | §3.2 |
| `refine` / `killCount` / `proudSoul` | `0` / `0` / `0` | 保 13.14；「无初始附魔」的语义也不该带耀魂/杀敌 |
| `defaultBewitched` | `false` | **[实]** `SwordType.BEWITCHED` 由它决定（`updateRarity`/`appendSwordType`/`appendSlashArt` 都看 BEWITCHED）⇒ 无初始附魔 + 不显示「妖刀」 |
| `destructable` | `false` | **[实]** 无 state 时 `isDestructable` 就是 false；显式写死避免耐久语义混入（§2 决策） |
| `sealed` | 由能量推导：`energy == 0` ⇒ true，否则 false | §5.1；**必须由 `quantize` 一并写死**，否则同能量两栈会因 sealed 不同变成两个键 |
| `texture` / `model` | 第一阶段**不写**（保持默认解析）；第二阶段统一写一对实证存在的资源名 | §3.4、N17/N18 |
| `setNonEmpty()` | 调用 | 现有代码已实证这个 setter（[本] `BladeTestFactory:58`） |
| `slashArtsKey` | 保持默认 | **N13**（`BladeStateData.DEFAULT` 的默认 SA）；若默认 SA 非「无」，用户只要求「无初始附魔」，SA 是否清空需用户拍板 ⇒ 列 N20 备查，本方案**不改** |
| 附魔 | 什么都不写 | 无初始附魔 |

初始能量：**给满**（`maxEnergy`）。理由：创造栏拿到就是能用的；`/give` 出来的刀第一次 `inventoryTick` 也会补满（自愈逻辑对「能量组件缺席」视作满，写成「缺席 = 满」而不是「缺席 = 0」，否则玩家一拿到就是废刀）。

> `ensureInitialized` 的「缺席=满」只作用于**能量组件**；`BLADE_STATE_DATA` 缺席时按默认数据补齐，能量按满补。

### 3.4 外观（先用最简单的办法）

两件事必须分开，否则会各自跑偏：

1. **物品图标**（背包/创造栏/手持）＝ MC 物品模型，**由我们提供**：`assets/applied_slash/models/item/charged_blade_pulse.json`（`parent: minecraft:item/handheld`，`textures.layer0 = applied_slash:item/charged_blade_pulse`）+ 5 张 **16×16** PNG（`tools/textures.gradle` 生成，沿用脚本里那套色标 palette）。16×16 是硬约束：`tools/resources.gradle` 的 `verifyResources` 会对本模组每个 PNG 断言 16×16/8bit/非交错。5 张用**配色区分**（脉冲=青蓝、谐振=紫罗兰、涌流=琥珀金、超荷=赤红、奇点=暗紫+高光点），刀身骨架像素一致 ⇒ 一眼能区分但工作量小。
2. **刀身渲染**（世界里那把刀的样子）＝ SlashBlade 自己的 `BladeStateData.model` / `.texture` 系统。**它怎么解释这两个 ResourceLocation 目前未实证**（N17/N18）。执行顺序：
   - 里程碑 M1：**不写** model/texture，用默认解析跑通，确认没有紫黑格/崩溃；
   - 里程碑 M2：host 导出重锋 jar 内 `assets/slashblade/**` 的 model 与 texture 清单（N18）并确认引用格式（N17），再：
     - 若清单里有 5 组可用 ⇒ 逐把写（最优）；
     - 只有 1 组确定可用 ⇒ **5 把共用同一对**（需求明确允许）；
     - 一组都确定不了 ⇒ 维持不写，外观差异靠物品图标承担，并如实写进文档。

**待查清单（host 可直接产出）**

- `jar tf libs\slashblade-2.0.7-1.21.1.jar | findstr /i "assets/slashblade/model"`（**N18**）
- `jar tf libs\slashblade-2.0.7-1.21.1.jar | findstr /i "textures"`（**N18**）
- 消费 `getModel()/getTexture()` 的类：`jar tf ... | findstr /i "renderer model"` 后逐个 `javap -p -c`（**N17**）

### 3.5 注册与创造栏

- 物品：§3.1 的 `ChargedBladeItems`（SlashBlade 门卫内）。
- 创造栏：改 `AppliedSlash.MAIN_TAB` 的 `displayItems`（[本] `AppliedSlash.java:57-62`），在现有两个元件之后追加：

```java
for (ChargedBladeItem blade : ChargedBladeItems.all()) {
    output.accept(ChargedBladeFactory.fresh(blade));      // 带默认刀身数据 + 满能量
}
output.accept(ChargerRegistry.BLADE_CHARGER_ITEM.get());
```

- 注意：`MAIN_TAB` 的注册现在挂在 `isAe2Loaded()` 分支里（[本] `AppliedSlash.java:77-82`）。`displayItems` 的 lambda 只有在创造界面构建时才执行，那时 SlashBlade 类才被加载 —— 两个前置都是 required，所以安全；但**要把这句话写成代码注释**，避免后人以为可以在 lambda 里随便引用前置类。

---

## 4. 能量存储

### 4.1 放在哪：自研持久化组件（候选对比表）

| 候选 | 随物品走（存元件再取出） | 键膨胀 | 结论 |
|---|---|---|---|
| 复用 `BladeStateData.proudSoul`（耀魂） | 能 | 无 | ✗ 语义污染：耀魂被重锋本体与附属读/写，tooltip 会显示成耀魂数，第三方按耀魂做的功能会被带跑 |
| 复用 `BladeStateData.sealed` | 能 | 无 | ✗ 只有 1 bit，上限 3200 需要 ≥13 个状态 |
| 仿 `VAULT_ID` 再做一张世界侧能量表 | 能 | **最差**：每个新 UUID 就是一个新存储键 ⇒ 每把刀独占类型；且**复制物品 = 两份共享表项 = 能量复制漏洞** | ✗ |
| **自研 Int 组件 `applied_slash:blade_energy`** | 能（`AEItemKey.toTag/fromTag` 整栈往返，[本] `BladeVaultShard:91,61`） | 量化后 ≤11 键/种刀 | ✅ 采用 |

「存在持久化的刀身数据里」的落地口径：**就是物品上的持久化数据组件**（`persistent(...)` + `networkSynchronized(...)`），不是运行时状态组件、不是世界侧表。`stripVolatileState` 只删 `BLADE_RUNTIME_STATE`，**不碰**我们的组件（[本] `SlashBladeBlades.java:85-87`）。

注册写法（照抄 [本] `AppliedSlashComponents.java`）：

```java
public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BLADE_ENERGY =
        COMPONENTS.registerComponentType("blade_energy",
                builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
```

### 4.2 读写 API（全部纯函数，便于 GameTest）

```java
public final class ChargedBladeEnergy {
    public static int get(ItemStack stack);              // 组件缺席 → 0（「0 点」由解码决定，见 §3.3 的「缺席=满」只在 ensureInitialized 里生效一次）
    public static int max(ItemStack stack);              // 由 ChargedBladeItem#maxEnergy()（读配置）
    public static void set(ItemStack stack, int energy); // clamp 到 [0, max]；同时按 energy 同步 sealed 位
    public static boolean canUse(ItemStack stack);       // get(stack) >= costPerAttack()（默认 1）
    public static int quantize(int energy, int max, int levels, boolean keepFull); // 纯函数，§7.1
    public static boolean needsQuantizing(ItemStack stack); // 组件存在 && 值不是档位值
    public static void quantizeInPlace(ItemStack stack);    // 入库规范化时用：能量→档位 + sealed 同步
}
```

### 4.3 tooltip 与能量条

- `appendHoverText`：先 `super.appendHoverText(...)`（保留重锋的刀铭/耀魂/精炼/SE 行，[实] 偏移 2434–2454），再追加：
  - `item.applied_slash.charged_blade.tooltip.energy` → 「能量 %s / %s」（灰）
  - 能量 0 时追加 `...tooltip.empty` → 「能量耗尽：无法使用」（红）
  - 常驻一行灰字 `...tooltip.quantize` → 「入库按 1/10 档记录（取出取下界，最多少 %s 点）」（§7.3）
- 能量条：基类 `isBarVisible` 恒 false（[实] 2355–2358），我们覆写成 true；`getBarWidth` 返回 `round(energy/max*13)`，`getBarColor` 按比例返回绿/黄/红（**N25** 确认 1.21.1 的签名与语义）。
- 客户端一致性警告（写进 §6.4 配置说明）：`max` 来自 **COMMON 配置**，专用服务器改了配置而客户端没改时，客户端的 `/上限` 会显示不一致（能量数值本身是同步的，不受影响）。接受该偏差；若要绝对精确，升级路径是「把 max 一起写进组件（`record BladeEnergy(int energy, int max)`）」，代价是配置变更会让同种刀多出若干键 ⇒ **本期不做**。

### 4.4 配置项（全部加进 [本] `AppliedSlashConfig`，沿用同一个 SPEC 与同一个 `refresh`）

| 键 | 默认 | 范围 | 说明 |
|---|---|---|---|
| `chargedBladeAttackCost` | `1` | 1..100 | 每次命中消耗 |
| `chargedBladeMaxPulse` | `200` | 1..1_000_000 | 脉冲上限 |
| `chargedBladeMaxResonance` | `400` | 1..1_000_000 | 谐振上限 |
| `chargedBladeMaxSurge` | `800` | 1..1_000_000 | 涌流上限 |
| `chargedBladeMaxOvercharge` | `1600` | 1..1_000_000 | 超荷上限 |
| `chargedBladeMaxSingularity` | `3200` | 1..1_000_000 | 奇点上限 |
| `chargedBladeChargePerTick` | `1` | 1..1000 | 充能方块每 tick 给刀充的点数 |
| `chargedBladeAePerPoint` | `100` | 1..1_000_000 | 每点能量的 AE 成本（⇒ 满充 3200 点 = 320 kAE，1 点/t 时约 160 秒） |
| `chargedBladeIdleDrain` | `1.0` | 0..1000 | 方块空闲耗电 AE/t（节点在线即耗） |
| `chargedBladeQuantizeLevels` | `10` | 2..100 | 量化档数 |
| `chargedBladeQuantizeKeepFull` | `true` | — | 满能量取精确值（不向下取整） |

- `refresh` 里同步缓存 11 个 `volatile` 值，写法照抄现有 4 项（[本] `AppliedSlashConfig.java:104-113`，注意 `catch (IllegalStateException)` 保留）。
- 派生量 `perBucket = max(1, max / levels)`——5 个默认上限都能被 10 整除；用户改成不能整除的值时按 `max/levels` 取整，`keepFull` 仍保证「满=满」。
- 不做第二个 SPEC：单一配置文件 = 单一加载/重载路径。

---

## 5. 「没能量完全不能用」

### 5.1 sealed 的定位：表现层 + 附加信号，**不是**主门禁

**[实]** 与 sealed 有关的全部证据：`SwordType.SEALED` 会让 `appendSwordType` **直接 return**（不加任何刀类型行，偏移 2545–2549）、让 `appendSlashArt` 不显示 SA（2464–2468）；`setDamage` 里出现 `isBroken() && !isSealed()` 的组合（2136–2145）；`BladeStateData` 有 `sealed(boolean)`、`ISlashBladeState` 有 `isSealed()`（从 `setDamage` 调用点看出来的，[实] 2141）。

**但**「sealed 是否阻止 vanilla 攻击伤害 / 阻止 SA / 阻止什么」在本项目的实证里**没有任何依据** ⇒ **N12/N15/N24** 三处取证之前，不许把硬需求（「完全不能攻击」）托付给它。

本方案的用法：**sealed 是能量的只读投影** —— `energy == 0 ⇔ sealed == true`。好处：① 与重锋的 UI 语义自洽（封印刀不显示 SA，正好符合「没能量不能用」）；② 若重锋内部确实按 sealed 拦了什么，我们白拿一层保护；③ 它是身份的一部分，但由能量推导 ⇒ 不额外制造键（§7.2 必须显式写死，否则会分裂）。

### 5.2 门禁四层（纵深防御，任何一层单独失效都不至于「能照常打」）

| 层 | 位置 | 行为 |
|---|---|---|
| **L1 右键/使用** | `use(Level, Player, InteractionHand)` 覆写 | 能量 < 消耗 ⇒ **不调用** `super.use(...)`，`return InteractionResultHolder.fail(stack)`（+ 服务端 actionbar 提示、1 秒节流）。`FAIL` 是基类自己也在用的返回路径（[实] 2081–2105），安全 |
| **L2 攻击取消（主保证）** | 游戏总线 `AttackEntityEvent`（**N27** 确认类名/是否双端） | 攻击者主手是本模组充能刀且能量 < 消耗 ⇒ `event.setCanceled(true)`。这条**不依赖重锋任何内部语义**，是「完全不能攻击」的兜底 |
| **L3 `onLeftClickEntity` 覆写** | 物品类 | 能量 < 消耗 ⇒ 不调用 super（连段不推进）、按 **N23 结论**返回「取消攻击」的值；有能量 ⇒ `return super.onLeftClickEntity(...)`（保住重锋的 `progressCombo` + `L_CLICK` 行为） |
| **L4 sealed 投影** | 数据层 | 能量归零时同步 `sealed=true`（§4.2 的 `set` 里做），给重锋内部逻辑与 tooltip 一致的表现 |

**L3 的两种语义（必须由 N23 定，代码二选一，宿主/审查方按字节码勾）**

```java
// 变体 A：Player#attack 里「true ⇒ 取消这次攻击」
@Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
    if (!ChargedBladeEnergy.canUse(stack)) return true;   // 取消
    return super.onLeftClickEntity(stack, player, entity);
}
// 变体 B：Player#attack 里「true ⇒ 已处理，继续走 vanilla 伤害」
@Override public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
    if (!ChargedBladeEnergy.canUse(stack)) return false;  // 取消（由 N23 决定）
    return super.onLeftClickEntity(stack, player, entity);
}
```

> 为什么两种都无害：**L2 才是主保证**。若 L3 判错，后果只是「重锋连段推进与否」的差异，不会出现「没能量却能打」。

**若 L2 的实证发现 `AttackEntityEvent` 不适合（例如不在客户端/服务端我们需要的时机触发）**，退路优先级：
1. **N28** `LivingIncomingDamageEvent`（`setCanceled(true)`）——伤害结算入口的第二个服务端截断点；
2. 最后手段：覆写 `getDefaultAttributeModifiers` 让 depleted 刀的 `ATTACK_DAMAGE` modifier = 0 —— **100% 拦住 vanilla 伤害路径**，但 tooltip 会显示 0 攻击力（与「伤害统一 13.14」的文字要求冲突），且玩家加锐利等附魔仍会加伤害。**这条必须由用户拍板后才能用**（记入 R1）。

### 5.3 扣费唯一位置 = `hurtEnemy`（不会重复扣、不会漏扣）

**[实]** `ItemSlashBlade.hurtEnemy(ItemStack, LivingEntity, LivingEntity)` 恒 `return true`，并在 state 存在时执行命中处理（偏移 2259–2269）。vanilla 只在 `target.hurt(...)` **成功**后才调用它（**N23** 的第 3 问确认调用点）。

```java
@Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    if (attacker.level() instanceof ServerLevel) {          // 只在服务端改数据（双端都跑时也不会双扣）
        int cost = AppliedSlashConfig.chargedBladeAttackCost();
        int energy = ChargedBladeEnergy.get(stack);
        if (energy < cost) {                                // 理论上被 L2 挡住；这里是防御
            return super.hurtEnemy(stack, target, attacker);
        }
        ChargedBladeEnergy.set(stack, energy - cost);       // 顺带:归零 ⇒ sealed=true
    }
    return super.hurtEnemy(stack, target, attacker);
}
```

- **不重复扣**：L1/L2/L3/L4 全部**只读不写**；写操作只在这一处。
- **不漏扣**：命中路径唯一，每次真命中扣 1。
- **「只有真正命中才扣」成立**：目标处于无敌帧/`hurt()` 返回 false 时 vanilla 不调用 `hurtEnemy`（N23 确认）⇒ 不扣。
- 归零时的玩家反馈：actionbar 提示 + 1 秒节流（按玩家缓存上次提示 tick，放在 `AppliedSlashEvents` 里，用 `WeakHashMap<Player,Integer>` 或玩家附件；**N32** 确认 `displayClientMessage(Component, boolean)`）。

### 5.4 耐久 / broken（必须处理，否则 13.14 会自己崩掉）

| 处理 | 内容 |
|---|---|
| 默认数据 `destructable=false` | 防止 `damageItem` 里 `isBroken() && isDestructable()` 的 `stack.shrink(1)`（[实] 2240–2249） |
| 覆写 `damageItem` | `public <T extends LivingEntity> int damageItem(ItemStack s, int amount, T e, Consumer<Item> onBroken) { return 0; }` ⇒ 永不磨损、永不 broken、顺手绕过 `orElseThrow` 崩溃路径（[实] 2173–2175） |
| 监听 `SlashBladeEvent.BreakEvent` | 对本模组刀 `setCanceled(true)`（**N16** 确认可取消性与取栈方法），双保险（即使别处调了 `setDamage` 也不会置 broken） |

---

## 6. 充能方块（`applied_slash:blade_charger`）

### 6.1 类结构

| 文件 | 职责 |
|---|---|
| `charged/block/BladeChargerBlock.java` | 方块本体：`Properties.of().strength(3.5F)`、朝向（若有）、`useWithoutItem` 打开菜单（**N31**）、破坏时掉落槽内刀 |
| `charged/block/BladeChargerBlockEntity.java` | 网格连接节点 + 1 个物品槽 + 每 tick 充能 + `ContainerData`/同步 + 掉落逻辑（**N2/N3/N4/N5/N6/N7/N9**） |
| `charged/block/BladeChargerRegistry.java` | `DeferredRegister.Blocks` / `DeferredRegister.Items`（BlockItem）/ `DeferredRegister<BlockEntityType<?>>` / `DeferredRegister<MenuType<?>>` 四个注册器 + `register(IEventBus)`；**只在这个类里引用 `appeng.*`** |
| `charged/block/BladeChargerMenu.java` | `AbstractContainerMenu`：槽 0 = 刀槽、玩家背包、`ContainerData` |
| `charged/block/ChargerMath.java` | **纯函数**：`ChargeTick chargeTick(int energy, int max, long availableAe, int aePerPoint, int perTick)` ⇒ `{newEnergy, aeConsumed}`（GameTest 可测） |
| `charged/client/BladeChargerScreen.java` | 自绘界面（**不用贴图**，见 §6.3）；`RegisterMenuScreensEvent`（**N30**）由 `AppliedSlashClient` 转发（照 [本] `AppliedSlashClient.registerItemColors` 的转发模式） |
| `charged/ChargedBladeEvents.java` | 游戏总线：L2 攻击取消、BreakEvent、actionbar 节流 |

**注册时机**：方块的类引用 `appeng.*` ⇒ 必须在 AE2 门卫内（现在 AE2 是 required，但保留该结构以免破坏「前置缺席也不会 NoClassDefFoundError」的既有性质）。在 `AppliedSlashAe2.register(IEventBus)` 末尾追加 `BladeChargerRegistry.register(bus);`（[本] `AppliedSlashAe2.java:40-42`），并在 `registerIntegration()` 里做「注册期之后」的接线（例如 BER、扳手行为，如需要）。

### 6.2 网格连接与取能（全部 NEEDS_JAVAP，给 host 一份可执行清单）

| 需求 | 参考实现路径 | 待证 |
|---|---|---|
| 吸在 ME 网络上 | 照 AE2 自带 **Charger** 的方块 + 方块实体结构逐段抄（它是「网格连接 + 单槽 + 耗 AE 充能」的最接近先例） | **N1**（先定位类名） |
| 网格连接 | `IGridConnectedBlockEntity` + `IManagedGridNode`（创建、`setIdlePowerUsage`、`setExposedOnSides`、`setFlags`、`setVisualRepresentation`） | **N2/N3/N4/N8** |
| 取能 | `IEnergyService.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier)`；节点→网格→能量服务 | **N5/N6/N11** |
| 归因 | `IActionSource`（机器来源） | **N7** |
| 槽位 | AE2 的 `InternalInventory`（首选，AE2 原生机器一致）或 NeoForge `ItemStackHandler`；若走 AE2 风格菜单还需 `AEBaseMenu`/`AppEngSlot` | **N9** |
| 频道 | 决策：**占用 1 个频道**（与 AE2 用电机器一致）；不做无频道特例 | N3 里一并确认 `setFlags`/`AECableType` |

**降级路径（写成里程碑，避免一上来就卡死）**：
M3a「最小可点亮节点」→ 只让方块实体被网格接受、能在 demo 网络里显示为已供电（日志打 `node.isActive()`/`isPowered()`）→ M3b 接 `extractAEPower` 与充能数学 → M3c 菜单与界面。

### 6.3 充能逻辑（纯函数 + 每 tick 调用）

```
每 tick（服务端、BE tick）:
  刀 = slot0；若空 或 不是 ChargedBladeItem → 状态 IDLE，不耗电
  e = energy(刀)；max = max(刀)
  若 e >= max                       → 状态 FULL，不耗电
  需要点 = min(config.chargePerTick, max - e)
  需要 AE = 需要点 * config.aePerPoint
  实取 AE = energyService.extractAEPower(需要 AE, MODULATE, multiplier)
  若 实取 AE < config.aePerPoint     → 状态 NO_AE（缺电），不充（不足一点就不充，避免半点的余数语义）
  否则 点 = min(需要点, 实取 AE / config.aePerPoint)
       set(刀, e + 点)；setChanged()；状态 CHARGING
```

- **不留内部缓存**：按 `(max - e)` 精确取电，避免「多取了 AE 却没地方放」。
- 空闲耗电 = `config.idleDrain`，交给节点自身（`setIdlePowerUsage`，**N3**）。
- 零分配、每 tick 一次 `extractAEPower`；槽里的刀不在网络中，所以与 §7 的键膨胀无关。

**界面（自绘，不用贴图）**

```
┌─────────────────────────────┐
│  拔刀剑充能器                │   ← Component.translatable("container.applied_slash.blade_charger")
│  [刀槽]   ▮▮▮▮▮▯▯▯▯▯ 132/200 │   ← guiGraphics.fill 画 8×52 能量条 + 边框（2px）
│  状态：充能中（100 AE/点）    │   ← 状态行，4 态：充能中 / 已满 / 缺电 / 未放刀
│  ── 玩家背包 ──              │
└─────────────────────────────┘
```

- `ContainerData`：`energy`、`max`、`state`（0..3）、`aePerPoint`（4 个 int 足够）。
- **刻意不引入 GUI 贴图**：`verifyResources` 会要求本模组每个 PNG 都是 16×16（[本] `tools/resources.gradle:186-192`），一张 176×166 的 GUI 背景会直接让自检失败；自绘 `fill` 既零资源又零风险。若将来确实要贴图，必须同时改 `verifyResources`：把 `assets/applied_slash/textures/gui/**` 加入白名单并允许指定尺寸（给出改动点，属可选任务）。
- 方块本身的外观仍走资源链（`blockstates` + `models/block/*` + 16×16 贴图），文件名必须与 `registerItem("blade_charger")` 一致，否则 `verifyResources` 第 1 项会点名（见 §9.2）。

**掉落与破坏**：破坏时把槽里的刀 `Block.popResource` 掉出（否则吞刀）；`BlockEntity#setRemoved` 里清理节点；1.21.1 的 `Block#playerWillDestroy` / `affectNeighborsAfterRemoval` 签名见 **N31**。

### 6.4 配方

沿用 [本] `data/applied_slash/recipe/slash_blade_cell.json` 的 1.21.1 格式（`type: crafting_shaped`、`neoforge:conditions` 两条 `mod_loaded`、`result: {id, count}`）。

- 方块：`ae2:charger`（AE2 的充能器，语义最贴）+ `ae2:calculation_processor` + `slashblade:proudsoul_sphere`，形状 `PSP / SCS / PSP`（S=耀魂球、P=计算处理器、C=充能器）。
- 5 把刀：升级链，第 1 把（脉冲）= `slashblade:slashblade` + 耀魂 + 处理器；第 n 把 = 第 n-1 把 + 上层材料（谐振/涌流/超荷/奇点逐级加料）。
- **AE2 侧物品 id 必须先实证**（`ae2:charger`、`ae2:energy_acceptor` 等）：**N35/N36**（导出 AE2 jar 的 lang 键或 `appeng.core.definitions.AEItems` 字段清单）。已实证可用的两个对照点：`ae2:calculation_processor`、`ae2:item_cell_housing`（[实] `dep-report.txt`）。
- 配方产物**不带组件**（1.21.1 配方 result 写完整组件 patch 过于脆弱且未实证）⇒ 依赖 §3.3 的**默认组件 + 自愈**补齐刀身数据与能量。

---

## 7. 与存储元件的联动（量化）

### 7.1 档位定义（纯函数，唯一实现点）

```java
// 档位集合 {0, p, 2p, …, 9p} ∪ {max}，p = max(1, max / levels)
public static int quantize(int energy, int max, int levels, boolean keepFull) {
    if (max <= 0) return 0;
    if (energy <= 0) return 0;
    if (keepFull && energy >= max) return max;
    int p = Math.max(1, max / levels);
    int b = Math.min(levels - 1, (energy - 1) / p);   // 0..levels-1
    return b * p;
}
```

- 5 把刀默认：`p` = 20 / 40 / 80 / 160 / 320；档位值 = `{0,20,…,180,200}`、`{0,40,…,360,400}`、…（各 ≤11 个值）。
- **向下取整**（只减不增）⇒ 不可能靠「入库→取出」刷能量；损失上界 = `p - 1`（≤ 上限的 10%）。满档特例保证「满能量的刀取出仍满」。
- 若用户不接受丢能量：两条备选（写进风险 R5，不由实现者私自改）——① 把 `quantizeLevels` 调到 40（损失 ≤ 2.5%，键数 ≤41/种）；② 不量化、精确值 + 类型上限拒收（会把 5000 类型预算吃光）。

### 7.2 具体改哪里（**不是只有一个 normalize**）

| 位置 | 改动 | 为什么必须改 |
|---|---|---|
| `cell/BladeIdentity.normalize(AEItemKey)` | `stripVolatileState(copy)` 之后追加 `ChargedBladeEnergy.quantizeInPlace(copy)`（能量→档位值 + `sealed` 按档位写死） | 入库/取出两侧统一形态；**必须同时写死 sealed**，否则「能量同为档位值但 sealed 不同」的两栈会被当成两个键 |
| `cell/BladeIdentity.mayNormalize(AEItemKey)` | `SlashBladeBlades.hasVolatileState(stack) \|\| ChargedBladeEnergy.needsQuantizing(stack)` | **这才是控制键膨胀的开关**：`mayNormalize == false` 时 `insert` 根本不会调用 `normalize`（[本] `SlashBladeCellInventory.java:110`），原始精确能量键会被原样收下 ⇒ 每个能量值一个类型，量化形同虚设 |
| `AppliedSlashConfig.stripRuntimeState` 的关系 | 量化**独立**于该开关：`mayNormalize` 里的能量判定**不能**挂在 `stripRuntimeState` 之下 | 否则关掉「剥离运行时状态」会连带关掉量化，静默制造类型爆炸 |
| `cell/SlashBladeCellInventory.java` | **不改**（insert/extract 已经正确地经 `mayNormalize` → `normalize`） | 少改少风险；D 只需复核「取出侧 SIMULATE 也走 normalize」这条既有契约仍成立（[本] 147–167 行注释已记录该踩坑） |
| `AppliedSlashComponents.java` | 新增 `BLADE_ENERGY` | §4.1 |
| `charged/ChargedBladeEnergy.java` | 新增 `quantize/needsQuantizing/quantizeInPlace` | 单一实现点，GameTest / tooltip 共用 |
| （可选）`BladeIdentity.normalize` 顶部加 `if (!mayNormalize(key)) return key;` | 省一次 `stack.copy()` | 现有两个调用点都已先做 `mayNormalize`，故行为等价；**D 需确认没有第三方调用 normalize**（当前无外部调用者） |

**门卫注意**（项目既有约定的复用）：`needsQuantizing` 需要 `instanceof ChargedBladeItem`，即会触发 `ItemSlashBlade` 的类加载 ⇒ 必须像 `SlashBladeBlades.isItemSlashBlade` 那样，把该判定放进**被 `ModList.isLoaded("slashblade")` 包住的独立方法**里（[本] `SlashBladeBlades.java:76-92` 的写法），否则破坏「SlashBlade 缺席也不 NoClassDefFoundError」的性质。

**新契约（写进测试）**：`normalize` 之后必须仍然满足「带任意能量的原始键做 `extract(..., SIMULATE, ...) > 0`」（既有回归 `simulateExtractAcceptsRuntimeKey` 的形态 + 能量维度）。

### 7.3 玩家体验怎么表述

- 刀 tooltip：「能量 137 / 800」+ 灰字「入库按 1/10 档记录（取出取下界，最多少 79 点）」。
- 元件 tooltip：不改（已显示类型/总件数）；类型数不再被能量放大，是「量化」的可见收益。
- README/CF 描述加一节「能量与存储」：一段话讲清「能量随刀走、入库量化到 10 档、满能量无损、越接近满损失越小」。
- 一句话结论给玩家：**满能量的刀入库取出仍是满的**；不带满的刀最多损失 1/10 档。

---

## 8. 依赖与元数据

### 8.1 `neoforge.mods.toml`（模板，`ProcessResources` 展开）

文件：`src/main/templates/META-INF/neoforge.mods.toml`（[本] 已读）

- 第 88–93 行 ae2 块：`type="optional"` → **`type="required"`**，保留 `versionRange="[19.2.17,)"`、`ordering="AFTER"`；建议补 `reason="Applied Slash 的存储元件与充能方块依赖 AE2 的网格与能量 API"`。
- 第 96–101 行 slashblade 块：`type="optional"` → **`type="required"`**，保留 `versionRange="[2.0.7,)"`、`ordering="AFTER"`；补 `reason="5 把充能拔刀剑继承 ItemSlashBlade，刀身数据是刀的核心状态"`。
- 两个块都**不要**动 `side`（保持 `BOTH`：服务端与客户端都必需）。

### 8.2 代码门卫**保留**

`AppliedSlash.isAe2Loaded()/isSlashBladeLoaded()`（[本] `AppliedSlash.java:77-97`）不只是依赖声明，还承担**类加载隔离**职责（引用 `appeng.*` / `ItemSlashBlade` 的类必须只在门卫之后加载）。改 required 后这些分支实际必然为真 ⇒ 相关日志/文案（例如元件 tooltip 的「未检测到拔刀剑本体」）成为不可达路径：**本期保留**（无行为副作用），只在注释里标注「necessary only as class-load guard」。

### 8.3 版本与文档同步

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `mod_version=1.0.0` → `1.1.0`（新物品/新方块/新配置 = minor；但依赖变 required 是破坏性 ⇒ 在 changelog 明确写「升级前请确认已装 AE2 与 SlashBlade」） |
| `README.md` | 第 18/19 行依赖表「可选依赖」→「必需」；第 22 行「AE2 或 SlashBlade 缺席时模组仍可正常加载」整段改写；新增「充能刀/充能方块/能量与量化」章节 + 配置表 11 行 |
| `README_EN.md` | 同步上述内容 |
| `docs/curseforge-description.md` | 第 42/45 行（optional 标注）、第 110 行（「SlashBlade 2.0.7+（可选，只有刀盘需要）」）、第 140 行（`Relation: SlashBlade: Resharpened | Optional`）同步为 Required；第 86/120 行的「不包含依赖，请自行安装」保持，但把「(需要刀盘再装 SlashBlade)」删掉；新增 5 把刀 + 充能方块的介绍 |
| `PERFORMANCE-TESTING.md` / `STORAGE-DESIGN.md` | 不改（可在 STORAGE-DESIGN 里补一节「能量维度与量化」，可选） |

### 8.4 风险说明（改 required 影响谁）

- **受影响人群**：只用「不可堆叠物品存储元件」、且**没装 SlashBlade**（或没装 AE2）的现有用户 —— 升级后 FML 会直接拦下启动并提示缺前置。这是用户已拍板的变更，**我们的责任是把它讲清楚**：CF/Modrinth 的 description「依赖」区块 + changelog 首行 + README 反白提示。
- **副作用**：required 会让「服务端装了、客户端没装」的玩家连不进来（缺失必需前置），文档里一并说明。
- **不推荐的替代**：保留一套「optional 元数据 + 运行期降级」的双轨（维护成本高、且 5 把刀与方块在没有 AE2 时本就是死物）。

---

## 9. 资源清单

### 9.1 逐文件（新增/修改）

| 类别 | 路径 | 数量 | 说明 |
|---|---|---|---|
| 模型·物品 | `assets/applied_slash/models/item/charged_blade_{pulse,resonance,surge,overcharge,singularity}.json` | 5 | `parent: minecraft:item/handheld`，`textures.layer0` 指本模组贴图 |
| 模型·方块 | `assets/applied_slash/models/block/blade_charger.json` | 1 | `parent: minecraft:block/cube`（或 `cube_all`），贴图自绘 |
| 方块状态 | `assets/applied_slash/blockstates/blade_charger.json` | 1 | 单态 `variants: {"": {"model": "applied_slash:block/blade_charger"}}` |
| 模型·方块物品 | `assets/applied_slash/models/item/blade_charger.json` | 1 | `{"parent": "applied_slash:block/blade_charger"}`（**必须存在**：verifyResources 按 `registerItem("blade_charger")` 找它） |
| 贴图 | `assets/applied_slash/textures/item/charged_blade_*.png` | 5 | 16×16，`tools/textures.gradle` 生成 |
| 贴图 | `assets/applied_slash/textures/block/blade_charger_side.png`、`_top.png` | 2 | 16×16，同上（若用 `cube_all` 只需 1 张） |
| 语言 | `assets/applied_slash/lang/zh_cn.json`、`en_us.json` | 2 | 见 §9.3 |
| 配方 | `data/applied_slash/recipe/charged_blade_*.json`、`blade_charger.json` | 6 | 格式照抄现有 `slash_blade_cell.json` |
| 标签（可选） | `data/slashblade/tags/item/swords.json` | 1 | 把 5 个 id 加进重锋的刀标签（**不加 `replace`**，MC 会合并多个数据包的同名标签）。好处：第三方按 `slashblade:swords` 认刀的功能（刀架/附属）能认到我们；风险：标签语义变更史需要复核 ⇒ 列为可选，由用户/D 决定 |
| 标签（可选） | `data/applied_slash/tags/item/charged_blades.json` | 1 | 自用标签，便于将来数据包扩展 |
| 生成脚本 | `tools/textures.gradle` | 改 | `palette` 追加 6~8 个色标 + 5 张刀 + 1~2 张方块贴图 + `texturePreviews` 里加 8× 预览 |
| 资源自检 | `tools/resources.gradle` | 改（**仅当**做 GUI 贴图时） | 把 `textures/gui/**` 加入白名单并允许指定尺寸；不做 GUI 贴图则**不改** |

### 9.2 `verifyResources` 会怎么检查（[本] `tools/resources.gradle`）

1. 扫源码里 `registerItem("id"` 并断言 `models/item/<id>.json` 存在 ⇒ 5 把刀 + 方块物品的模型名必须与 id 完全一致。**注意**：现有正则只匹配 `registerItem("..."`，所以方块物品请用 `registerItem("blade_charger", props -> new BlockItem(BLOCK.get(), props), ...)` 这种写法（而不是 `registerSimpleBlockItem`），否则它不会被自检覆盖（也可以顺手把正则扩展成同时匹配 `registerSimpleBlockItem`，属可选）。
2. 代码里 `":block/xxx"` 字面量必须有对应模型文件（若我们在代码里拼 `applied_slash:block/blade_charger` 就会命中这条）。
3. 每个模型 JSON 可解析、`parent` 链可达（`minecraft:item/handheld` 在原版 assets 里，可达）、`textures` 引用能找到 PNG。
4. 本模组每个 PNG 必须 16×16 / 8bit / 非交错 ⇒ 5 张刀 + 1~2 张方块贴图必须由 `generateTextures` 生成（或按同一规格手绘），**不要**直接丢一张 128×128 的美术图（要走 `tools/art-import.gradle` 的话也得先缩到 16×16；`art-import` 的导入源图存在 `art/`，本条按需）。

### 9.3 语言键（中英各一份，键名必须与代码/刀身数据完全一致）

| 键 | zh_cn | en_us |
|---|---|---|
| `item.applied_slash.charged_blade_pulse` | 充能刀·脉冲 | Charged Blade: Pulse |
| `item.applied_slash.charged_blade_resonance` | 充能刀·谐振 | Charged Blade: Resonance |
| `item.applied_slash.charged_blade_surge` | 充能刀·涌流 | Charged Blade: Surge |
| `item.applied_slash.charged_blade_overcharge` | 充能刀·超荷 | Charged Blade: Overcharge |
| `item.applied_slash.charged_blade_singularity` | 充能刀·奇点 | Charged Blade: Singularity |
| `item.applied_slash.charged_blade.tooltip.energy` | 能量 %s / %s | Energy %s / %s |
| `item.applied_slash.charged_blade.tooltip.empty` | 能量耗尽：无法使用 | Out of energy: unusable |
| `item.applied_slash.charged_blade.tooltip.quantize` | 入库按 1/10 档记录（取出取下界，最多少 %s 点） | Stored in 1/10 steps (rounded down on withdrawal, loses up to %s) |
| `block.applied_slash.blade_charger` | 拔刀剑充能器 | Blade Charger |
| `container.applied_slash.blade_charger` | 拔刀剑充能器 | Blade Charger |
| `applied_slash.blade_charger.status.idle` | 未放刀 | No blade |
| `applied_slash.blade_charger.status.charging` | 充能中（%s AE/点） | Charging (%s AE per point) |
| `applied_slash.blade_charger.status.full` | 已充满 | Fully charged |
| `applied_slash.blade_charger.status.no_ae` | 网络缺电 | Not enough AE |
| `applied_slash.charged_blade.no_energy_hint` | 能量耗尽：无法攻击 | Out of energy: cannot attack |

**硬约束**：`item.applied_slash.charged_blade_*` 这 5 个键**同时**是刀身数据的 `translationKey`（§3.3）⇒ 代码里的常量与 lang 键必须同源（建议在 `ChargedBladeSpec` 里放 `id` 与 `nameKey`，lang 手写但由 GameTest/D 逐条比对）。

---

## 10. 风险与验收

### 10.1 风险清单

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| R1 | `onLeftClickEntity` 返回语义未定（N23）、`hurtEnemy` 双端调用未知（N23） ⇒ 「没能量不能攻击」可能看起来生效实则没挡，或挡掉后重锋连段坏掉 | **高** | L2 `AttackEntityEvent` 作主保证；N23 一出就定稿 L3 分支；GameTest 断言「伤害是否真的没发生」（用 mock 玩家攻击僵尸，比对血量） |
| R2 | 充能方块的 AE2 网格/能量 API 签名未实证（N1–N11） | **高** | 照 AE2 自带 Charger 逐段抄；里程碑 M3a「最小可点亮节点」先行；N1–N11 由 host 一次性导出 |
| R3 | 刀身 `model`/`texture` 的引用格式与现成资源名未实证（N17/N18） | 中 | M1 先不写（用默认解析）；资源清单到手后再逐把替换；最坏退化为「五把共用」或「只用物品图标区分」 |
| R4 | 缺刀身数据的刀 ⇒ `setDamage/damageItem` 的 `orElseThrow` 崩服（[实] 2131/2173） | 中 | §3.3 三条创建路径全盖 + `damageItem` 覆写 + `ensureInitialized` + GameTest `chargedBladeNeverLacksBladeState` |
| R5 | 量化会让「取出时能量变少」（≤ p-1），若用户不接受 | 中 | 满能量无损（keepFull）；`quantizeLevels` 可调大；文案写清。**不由实现者私自改成无损** |
| R6 | 改 required 导致未装 SlashBlade 的老用户启动失败 | 中 | §8.4 文档/描述/changelog 三处明写 |
| R7 | GUI 贴图会撞 `verifyResources` 的 16×16 断言 | 低 | 界面纯自绘（不引入贴图）；要贴图就同时改自检白名单 |
| R8 | 客户端 COMMON 配置与服务器不一致 ⇒ 上限显示偏差 | 低 | 接受；tooltip 只显示「能量/max」，能量数值本身同步；升级路径见 §4.3 |
| R9 | 能量组件进入键 ⇒ 每键载荷增加约 20–40 字节（`AEItemKey.toTag` 写组件补丁） | 低 | 量化后 5 把刀合计 ≤55 键，与 5000 键规模的同步体积（[本] README：≈21.8 B/键）相比可忽略 |
| R10 | 第三方模组监听 `SlashBladeEvent.UpdateAttackEvent` / 玩家给刀加 SE（`FIERCEREDGE` ⇒ rate 0.1）会改变 13.14 | 低 | 文档写明「13.14 是默认数据下的精确值」；不主动加 SE；如需强制锁值再评估（会与重锋玩法冲突） |

### 10.2 可分自动验证 vs 必须游戏内验收

**自动（D 执行）**

```
gradlew.bat build                      # 编译 + 资源打包（首次可能拉依赖，超时按 20 分钟给）
gradlew.bat verifyResources            # 模型/父链/贴图/PNG 规格，新资源全部纳入
gradlew.bat generateTextures           # 需要时重新生成贴图（会顺带跑 texturePreviews）
gradlew.bat runGameTestServer          # 新 8 条 + 现有 12 条全部通过（超时给 15 分钟）
gradlew.bat runServer -PselfTest       # 存储自检不得回归（maxTypes/规范化/重载一致性）— 超时 10 分钟
gradlew.bat build ; gradlew.bat runData?(不需要：本方案不新增数据生成器，配方手写)
```

**必须用户在游戏内验收（逐条给判据）**

| # | 操作 | 期望 |
|---|---|---|
| 1 | 创造栏找 6 个新条目 | 5 把刀（各自图标可区分）+ 充能方块，名字中英正确 |
| 2 | 手持满能量刀看 tooltip | 攻击伤害 13.14、能量 = 上限、无附魔行、无「妖刀」行 |
| 3 | 对僵尸左键 | 正常掉血；每次命中能量 -1（tooltip 可见）；能量条随之下滑 |
| 4 | 把能量打到 0 再左键 | **完全不掉血**、无 SA/连段反应；tooltip 显示「能量耗尽：无法使用」且名字/行表现为封印态 |
| 5 | 0 能量时右键 | 无任何 SA/连段反应 |
| 6 | 充能方块贴到 ME 网络旁 | 方块被网络接受（AE2 的网格可视/终端能看到，节点亮）；AE 被真实消耗（看网络用电表/发电量） |
| 7 | 打开方块 GUI | 刀槽 + 能量条 + 状态行；放刀后能量上涨、满后停止充电；取刀随时可行 |
| 8 | 能量不足的网络 | 状态显示「网络缺电」，不充电 |
| 9 | 破坏方块（槽里有刀） | 刀掉出，不被吞 |
| 10 | 满能量的刀入库→取出 | 仍是满能量 |
| 11 | 137 能量的刀（800 上限）入库→取出 | 变成 130（=floor 到 80 的档位），工具提示/文档与之一致 |
| 12 | 同档能量两把刀（131/139）入库 | 类型数 +1（合并成同一个键），总数 +2 |
| 13 | 刀身外观 | 与重锋本体外观一致或明显可用，无紫黑格/无崩溃 |

### 10.3 分阶段交付（A → B → C → D）

**A（本文件）**：方案 + 决策 + 文件归属 + NEEDS_JAVAP 清单（N1–N40）。
**A′（host，建议在 B 之前做一次）**：用附录 B 的命令把 N1–N11、N17–N19、N23–N27 打完，把结论写回本文件附录 B 的「结论」列或另附 `build/asverify/charged-blade-probe.txt`。**N23/N24/N12 三个不查清，B 的骨架可以照写（门禁写成可切换的两种实现），但 C 不能定稿。**
**B（骨架，必须一次编译通过、不改变现有行为）**：
- 新建 `charged/**` 全部类的骨架（方法体 `throw new UnsupportedOperationException("B 骨架")` 或返回安全默认值，但**注册接线、组件注册、配置项、资源占位、语言键、配方、`BladeIdentity` 的调用点全部就位**）；
- `AppliedSlashComponents` 加 `BLADE_ENERGY`；`AppliedSlashConfig` 加 11 个配置项与缓存；`BladeIdentity.mayNormalize/normalize` 接入新调用点（先调用骨架方法）；
- 6 张 16×16 贴图 + 8 个模型/blockstate/语言键/6 个配方占位；`tools/textures.gradle` 更新；
- 出口标准：`gradlew build` + `verifyResources` 绿；现有 12 条 GameTest 不回归（新物品在创造栏可见即可）。

**C（实现，按里程碑）**
- **M1 刀本体**：5 把刀可拿可打、伤害 13.14、默认数据/能量/自愈、tooltip 与能量条 ⇒ GameTest 1/2/3/6/7。
- **M2 量化入库**：`ChargedBladeEnergy.quantize*` + `BladeIdentity` 两处改动 ⇒ GameTest 4/5 + 现有存储测试不回归。
- **M3 充能方块**：M3a 节点点亮 → M3b 取能 + 充能数学（GameTest 8）→ M3c 菜单 + 自绘界面 + 掉落。

**D（审查与收口）**
- 独立复核：`mayNormalize` 是否真的覆盖能量维度（**这是本方案唯一的「一改就崩/一漏就废」的点**）、`hurtEnemy` 扣费是否只在服务端、门禁四层是否有重复扣费路径、13.14 的公式代入是否与 §3.2 一致（可再跑一次 javap 复核偏移）；
- 跑 §10.2 的全部自动验证 + `-PselfTest`；检查 `neoforge.mods.toml` 生成物（`build/resources/main/META-INF/neoforge.mods.toml`）里 `type="required"` 真的生效；
- 文档三件套（README / README_EN / CF 描述）与配置表同步；
- 输出给用户的验收清单 = §10.2 的 13 条。

---

## 附录 A：文件归属（B 骨架 / C 内容 / D 审查）

| 文件 | 新增/改 | B | C | D |
|---|---|---|---|---|
| `charged/ChargedBladeSpec.java` | 新 | 骨架（id/nameKey/上限字段、5 个常量） | 默认值与上限映射 | 复核 id ↔ lang ↔ translationKey 三者同源 |
| `charged/ChargedBladeEnergy.java` | 新 | 方法签名 + 空实现 | 读写/clamp/sealed 同步/量化三函数 | 复核纯函数无副作用、量化边界 |
| `charged/ChargedBladeItem.java` | 新 | 类骨架 + 覆写清单 | 全部覆写实现（§5.2/5.3/5.4、tooltip、能量条、自愈） | 复核门禁四层与扣费唯一性 |
| `charged/ChargedBlade{Pulse,Resonance,Surge,Overcharge,Singularity}Item.java` | 新 ×5 | 5 个空子类 | `bladeId/nameKey/maxEnergy` + 外观默认值 | 复核构造参数 |
| `charged/ChargedBladeFactory.java` | 新 | 签名 | 默认刀身数据 + `fresh` + `ensureInitialized` | 复核「绝不缺 state」 |
| `charged/ChargedBladeItems.java` | 新 | DeferredRegister + `all()` | 5 个 registerItem + 工厂接线 | 复核 SlashBlade 门卫 |
| `charged/ChargedBladeGate.java` | 新 | 签名 | `canUse/shouldCancelAttack` 纯函数 | 复核与 L2 一致 |
| `charged/ChargedBladeEvents.java` | 新 | 事件方法签名 | AttackEntityEvent / BreakEvent / actionbar 节流 | 复核事件只在服务端改数据 |
| `charged/block/BladeChargerBlock.java` | 新 | 类骨架 | 属性/交互/掉落 | 复核 1.21.1 方块 API |
| `charged/block/BladeChargerBlockEntity.java` | 新 | 类骨架 + 方法桩 | 网格节点、槽、tick 充能、ContainerData | 复核 AE2 API 用法与 N3/N5 |
| `charged/block/BladeChargerRegistry.java` | 新 | 四个 DeferredRegister + register | BlockItem / BE 类型 / MenuType | 复核 `registerItem("blade_charger", ...)` 写法被自检覆盖 |
| `charged/block/BladeChargerMenu.java` | 新 | 类骨架 | 槽位/ContainerData/quickMove | 复核槽位限制 |
| `charged/block/ChargerMath.java` | 新 | 签名 | 纯函数 | 复核取电不超发 |
| `charged/client/BladeChargerScreen.java` | 新 | 类骨架 | 自绘界面 | 复核无 GUI 贴图（自检口径） |
| `AppliedSlash.java` | 改 | 接线调用点（门卫内） | 创造栏 6 项 + 事件注册 | 复核类加载隔离注释 |
| `AppliedSlashAe2.java` | 改 | `BladeChargerRegistry.register` 调用 | — | 复核 AE2 侧引用集中 |
| `AppliedSlashComponents.java` | 改 | `BLADE_ENERGY` 声明 | codec 细节 | 复核 persistent/sync 都设了 |
| `AppliedSlashConfig.java` | 改 | 11 个 value 声明 | 缓存字段与 `refresh` | 复核默认值与范围 |
| `cell/BladeIdentity.java` | 改 | `mayNormalize/normalize` 的新调用点 | 量化接入 + 门卫方法 | **重点复核**（§7.2） |
| `cell/SlashBladeCellInventory.java` | 不改 | — | — | 复核既有契约未被破坏 |
| `command/AppliedSlashCommand.java` | 改（可选） | — | `/appliedslash energy <set/add/max>`（开发期门卫内） | 复核生产分支 |
| `gametest/ChargedBladeGameTests.java` | 新 | holder + 8 个入口 | — | 复核签名不含 AE2/SlashBlade 类型 |
| `gametest/ChargedBladeTestSupport.java` | 新 | 空方法 | 8 条断言实现 | 复核断言口径 |
| `assets/**`（8 个 json + 6~7 张 PNG + 2 个 lang） | 新/改 | 占位文件（合法但最小） | 真实内容 | `verifyResources` |
| `data/**`（6 个配方 + 可选 2 个标签） | 新 | 占位 | 真实材料与形状 | 复核物品 id 实证 |
| `tools/textures.gradle` | 改 | — | 色标 + 贴图 + 预览 | 复核 PNG 规格 |
| `tools/resources.gradle` | 改（仅当要 GUI 贴图） | — | 白名单 | 复核豁免不放松其它检查 |
| `src/main/templates/META-INF/neoforge.mods.toml` | 改 | 两处 required | reason 文案 | 复核生成物 |
| `gradle.properties` | 改 | 版本 1.1.0 | — | 复核 |
| `README.md` / `README_EN.md` / `docs/curseforge-description.md` | 改 | — | 依赖表 + 新章节 | 复核口径一致 |
| `PLAN-CHARGED-BLADES.md` | 新（A 产出） | — | — | 复核 N 项是否闭环 |

---

## 附录 B：NEEDS_JAVAP 清单（40 条）

**通用提示**
- 用法示例（host 已有该流程，报告落在 `build/asverify/`）：
  `javap -p -c -classpath libs\ae2-19.2.17.jar <类>` / `javap -p -classpath build\moddev\artifacts\neoforge-21.1.250-merged.jar <类>`
- 先定位类名再 javap：`jar tf libs\ae2-19.2.17.jar | findstr /i charger`。
- 优先级：**P0** = 不定稿就不能写实现；P1 = 影响体验/兼容；P2 = 备查。

### AE2 侧（N1–N11）

| # | 优先级 | 要查什么 | 命令/方法 | 结论（host 填） |
|---|---|---|---|---|
| N1 | P0 | AE2 自带 **Charger** 的类全名（方块/BE/菜单/界面），作为我们的模板 | `jar tf libs\ae2-19.2.17.jar \| findstr /i charger` | |
| N2 | P0 | 网格连接方块实体的接口全名是否为 `appeng.api.networking.IGridConnectedBlockEntity`；必须实现哪些方法（`getGridNode`/`getActionSource`/`getMainNode`…） | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.IGridConnectedBlockEntity` | |
| N3 | P0 | `IManagedGridNode` 的创建与配置：`create(...)` 的精确签名、`setIdlePowerUsage`、`setExposedOnSides`、`setFlags`、`setVisualRepresentation`、`onReady`、节点 tick 注册 | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.IManagedGridNode` | |
| N4 | P1 | `GridHelper` 提供了哪些工具方法（注册 BE 物品、网格回调、tick 钩子） | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.GridHelper` | |
| N5 | P0 | `IEnergyService` 的方法签名：`extractAEPower(long/ double, Actionable, PowerMultiplier)`、`getAEPower`、`getAvgPowerUsage`；以及从网格取它的入口（`IGrid#getEnergyService()`） | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.energy.IEnergyService` + `...IGrid` | |
| N6 | P0 | `PowerMultiplier` 的常量/构造与语义（机器通常用哪个） | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.energy.PowerMultiplier` | |
| N7 | P0 | `IActionSource` 的机器来源构造（`ActionSource.ofMachine(...)` 之类）与所需参数 | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.security.ActionSource` | |
| N8 | P1 | 是否仍需要实现 `IInWorldGridNodeHost`（老接口），还是只实现 grid connected BE | `javap -p -classpath libs\ae2-19.2.17.jar appeng.api.networking.IInWorldGridNodeHost` | |
| N9 | P1 | 槽位/菜单底座：`InternalInventory`（创建方式）、`AppEngInternalInventory`、`appeng.menu.AEBaseMenu`、`appeng.menu.slot.AppEngSlot` 是否存在与签名 | 逐个 `javap -p` | |
| N10 | P2 | 是否有 wrench/拆装钩子（`IWrenchItem`/`WrenchHook`）需要为我们的方块实现 | `jar tf ... \| findstr /i wrench` | |
| N11 | P1 | 从 `IManagedGridNode` 拿 `IGrid`（`getGrid()`）与 `ITickManager`/`IGrid#getTickManager()` 的可用性（每 tick 充电写在哪） | `javap -p ... appeng.api.networking.IGrid` | |

### SlashBlade 侧（N12–N22）

| # | 优先级 | 要查什么 | 命令 | 结论 |
|---|---|---|---|---|
| N12 | **P0** | `ISlashBladeState` **全方法列表**：确认 `isSealed()/setSealed(boolean)`、`setRefine(int)`、`setDefaultBewitched(boolean)`、`setDestructable(boolean)`、`getModel/setModel`、`getTexture/setTexture`、`setTranslationKey`、`setProudSoulCount`、`setNonEmpty`、`onClick()`、`progressCombo(LivingEntity)` | `javap -p -classpath libs\slashblade-2.0.7-1.21.1.jar mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState` | |
| N13 | P0 | `BladeStateData` 的完整 record 组件与 `DEFAULT` 的默认值（尤其 `slashArtsKey`、`model`、`texture`、`effectColor`、`carryType`） | `javap -p -c ...BladeStateData` | |
| N14 | P1 | `BladeStateAccess` 全方法（`of/getDataOrDefault/setData/updateData/ensureRuntimeComponent`）的精确签名与泛型 | `javap -p ...BladeStateAccess` | |
| N15 | P1 | `SwordType`：`from(ItemStack)` 的判定来源 + 常量集合（SEALED/BEWITCHED/ENCHANTED/FIERCEREDGE/SOULEATER…） | `javap -p -c ...item.SwordType` | |
| N16 | P1 | `SlashBladeEvent$BreakEvent`：是否可取消、`setCanceled` 是否存在、如何取到 `ItemStack` | `javap -p -classpath ... mods.flammpfeil.slashblade.event.SlashBladeEvent$BreakEvent` | |
| N17 | **P0** | 谁消费 `state.getModel()/getTexture()`、引用格式是 `slashblade:model/named/xxx.obj` 还是别的；是否要求同名 json；贴图是否自动加 `textures/` 前缀 | `jar tf ... \| findstr /i "renderer\|model"` 后逐个 `javap -p -c` | |
| N18 | **P0** | 重锋 jar 内 `assets/slashblade/**` 的 **model 与 texture 完整清单**（挑现成资源名用） | `jar tf libs\slashblade-2.0.7-1.21.1.jar \| findstr /i "assets/slashblade"` | |
| N19 | P0 | `slashblade:slashblade` 的注册处取值：`tier`、`attackDamageIn`、`attackSpeedIn`、`Item.Properties`（我们要抄这些参数） | 先 `jar tf ... \| findstr /i "registry\|Item"` 定位，再 `javap -p -c` | |
| N20 | P2 | `SlashArts` 注册表 id 清单（若将来要给刀配 SA） | `javap -p ... mods.flammpfeil.slashblade.slasharts.SlashArts` | |
| N21 | P2 | `ItemTierSlashBlade` 的字段/实例（能否直接复用做我们的 tier） | `javap -p -classpath ... mods.flammpfeil.slashblade.item.ItemTierSlashBlade` | |
| N22 | P1 | `SlashBladeDataComponents`：`BLADE_STATE_DATA` 的组件类型与默认值（确认「无 state」时的行为） | `javap -p -classpath ... mods.flammpfeil.slashblade.capability.slashblade.SlashBladeDataComponents` | |

### MC / NeoForge 侧（N23–N34）与其它（N35–N40）

| # | 优先级 | 要查什么 | 命令 | 结论 |
|---|---|---|---|---|
| N23 | **P0** | `Player#attack` **全文**：① `onLeftClickEntity` 的调用点与返回值语义（true = 取消攻击 还是 true = 继续）；② `hurtEnemy` 的调用点（是否只在 `target.hurt(...)` 成功后）；③ 该方法是否**客户端也执行**（关系到扣费的双端隔离） | `javap -p -c -classpath build\moddev\artifacts\neoforge-21.1.250-merged.jar net.minecraft.world.entity.player.Player` | |
| N24 | **P0** | 重锋自己的左键攻击链路：谁结算伤害、是否经过 `ItemSlashBlade#hurtEnemy`、`sealed`/`broken` 是否在其中拦截 | `jar tf ... \| findstr /i "event\|attack\|combat"` 后 `javap -p -c` | |
| N25 | P1 | `Item#getBarWidth(ItemStack)` / `getBarColor(ItemStack)` / `isBarVisible(ItemStack)` 在 1.21.1 的签名与语义 | `javap -p -c -classpath <merged> net.minecraft.world.item.Item` | |
| N26 | **P0** | `Item$Properties#component(DataComponentType, Object)`（以及 `Item#getDefaultComponents`/`components()`）在 1.21.1 是否存在 | `javap -p -classpath <merged> 'net.minecraft.world.item.Item$Properties'` | |
| N27 | **P0** | `AttackEntityEvent`：全名、`getEntity/getTarget`、是否 `ICancellableEvent`、触发端（客户端/服务端） | `javap -p -classpath <merged> net.neoforged.neoforge.event.entity.player.AttackEntityEvent` | |
| N28 | P1 | `LivingIncomingDamageEvent`：能否 `setCanceled`、如何取 source/entity（L2 的退路） | `javap -p -classpath <merged> net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent` | |
| N29 | P1 | `GameTestHelper`：`makeMockPlayer` / `getLevel` / `spawnEntity` / `assertValueEqual` 等（测试里造攻击者与目标） | `javap -p -classpath <merged> net.neoforged.neoforge.gametest.GameTestHelper` | |
| N30 | P1 | `RegisterMenuScreensEvent` 的注册方法签名；`MenuType` 的创建 API（`IMenuTypeExtension.create` 之类） | `javap -p -classpath <merged> net.neoforged.neoforge.client.event.RegisterMenuScreensEvent` | |
| N31 | P1 | 1.21.1 方块 API：`Block#useWithoutItem`、`Block#playerWillDestroy`、`Block#affectNeighborsAfterRemoval`、`Block#popResource`、`BlockEntity#setRemoved` 的签名 | `javap -p -classpath <merged> net.minecraft.world.level.block.Block` | |
| N32 | P2 | `Player#displayClientMessage(Component, boolean)`（actionbar）与 `ServerLevel` 判定 | 同 N23 | |
| N33 | P2 | `BlockEntityType.Builder.of(...)`、`DeferredRegister.createBlockEntityTypes` 的用法 | `javap -p -classpath <merged> net.minecraft.world.level.block.entity.BlockEntityType` | |
| N34 | P2 | `DataComponentType.Builder#persistent/networkSynchronized`（项目已实证过，仅确认无变化） | 现有代码已用 | |
| N35 | P1 | AE2 可用物品 id（配方材料）：`ae2:charger`、`ae2:energy_acceptor`、`ae2:certus_quartz_crystal`、`ae2:calculation_processor`(=已证) 等 | `jar tf libs\ae2-19.2.17.jar \| findstr lang` 或 `javap -p appeng.core.definitions.AEItems` | |
| N36 | P2 | AE2 是否自带「电能→物品」的现成方块行为可参照（`Charger` 的充能实现细节） | 同 N1 | |
| N37 | P2 | `ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT` 与 tooltip 显示格式（确认 13.14 的显示位数） | `javap -p -classpath <merged> net.minecraft.world.item.component.ItemAttributeModifiers` | |
| N38 | P1 | `SlashBladeEvent$UpdateAttackEvent#getNewDamage` 的实现（是否只是 getter，确认 13.14 不被重锋自己改写） | `javap -p -c ...SlashBladeEvent$UpdateAttackEvent` | |
| N39 | P2 | `ItemStack#onLeftClickEntity/hurtEnemy` 到 `Item` 的转发（确认我们覆写物品方法是正确拦截点） | `javap -p -c -classpath <merged> net.minecraft.world.item.ItemStack` | |
| N40 | P2 | 1.21.1 配方 `result` 是否支持 `components` 字段（若支持，可用配方直接带默认刀身数据，作为自愈之外的第三条路） | `javap -p -c` 查 `net.minecraft.world.item.crafting.ShapedRecipe`/`ItemStack` 的 codec 或看原版带组件配方示例 | |

**统计：40 条**（P0 = N1,N2,N3,N5,N6,N7,N12,N13,N17,N18,N19,N23,N24,N26,N27 共 15 条；P1 = N4,N8,N9,N11,N14,N15,N16,N22,N25,N28,N29,N30,N31,N35,N38 共 15 条；P2 = N10,N20,N21,N32,N33,N34,N36,N37,N39,N40 共 10 条）。

---

## 附录 C：配置项一览（给 C/D 对齐）

```
chargedBladeAttackCost        = 1        // 每次命中消耗
chargedBladeMaxPulse          = 200
chargedBladeMaxResonance      = 400
chargedBladeMaxSurge          = 800
chargedBladeMaxOvercharge     = 1600
chargedBladeMaxSingularity    = 3200
chargedBladeChargePerTick     = 1        // 充能方块每 tick 充点数
chargedBladeAePerPoint        = 100      // 每点 AE 成本
chargedBladeIdleDrain         = 1.0      // 方块空闲 AE/t
chargedBladeQuantizeLevels    = 10
chargedBladeQuantizeKeepFull  = true
```

---

## 附录 D：GameTest 清单（新增 8 条）

| # | 名称 | 断言要点 | 依赖 |
|---|---|---|---|
| 1 | `chargedBladeWithoutEnergyCannotBeUsed` | 0 能量：`canUse==false`；`item.use(level, mockPlayer, MAIN_HAND)` 返回 `FAIL` | N29（mock 玩家）；不可用则退化为纯函数断言 |
| 2 | `chargedBladeAfterChargeCanBeUsed` | 能量 = 1（≥ 消耗）⇒ `canUse==true`、`use` 走 super 分支 | — |
| 3 | `energySpentOnlyOnRealHit` | 直接调 `hurtEnemy(stack, target, attacker)` ⇒ 能量 -1；连续调用到 0 后不再变负且 `sealed==true` | N29 |
| 4 | `energySurvivesStorageRoundTrip` | 满能量（800）入库→取出 = 800；137→130（=floor 到 80 档位）且损失 ≤ `perBucket-1` | 现有存储测试地基 |
| 5 | `sameBucketMergesToOneKey` | 131 与 139（同档）⇒ types 1 / count 2；139 与 161（异档）⇒ types 2 | — |
| 6 | `chargedBladeGateBlocksAttack` | 把 L2 判定抽成纯函数 `shouldCancelAttack(stack)`：0 能量 ⇒ true，≥1 ⇒ false（若 `AttackEntityEvent` 可实例化，再补一条真事件断言） | N27 |
| 7 | `chargedBladeNeverLacksBladeState` | `new ItemStack(item)`（无 state）不崩：`damageItem(...)==0`、`ensureInitialized` 后 `BladeStateAccess.of` 存在、`getDefaultAttributeModifiers` 的伤害 = 13.14 | N26 |
| 8 | `chargerChargeMath` | `chargeTick(750, 800, 巨量 AE, 100, 1)` ⇒ 消耗 50×100 AE、能量 800、不再消耗；AE 不足一点 ⇒ 不充且不扣电 | — |

（现有 12 条必须全部保持绿；`GameTestSupport` 已明确要求 holder 类的方法签名不含前置类型 —— 新 holder 只暴露 `GameTestHelper` 参数，实现放在 `ChargedBladeTestSupport`。）
