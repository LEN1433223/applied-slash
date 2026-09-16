package com.applied.slash.charged;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.minecraft.world.item.ItemStack;

/**
 * 充能刀的出厂数据工厂。
 *
 * <h2>为什么必须有一个工厂(而不是靠配方/创造栏直接给 ItemStack)</h2>
 * <b>硬约束(实证)</b>:{@code ItemSlashBlade.setDamage}(偏移 2131–2133)与
 * {@code damageItem}(偏移 2173–2175)都会 {@code BladeStateAccess.of(stack).orElseThrow()}
 * —— 一个**没有刀身数据**的本模组刀一旦走耐久路径就是 {@code NoSuchElementException} 崩服。
 * 所以「任何时刻都不允许存在缺刀身数据的本模组刀」,三条创建路径全部覆盖:
 *
 * <table border="1">
 *   <tr><th>创建路径</th><th>处理</th></tr>
 *   <tr><td>创造栏</td><td>{@link #fresh}(item) —— 默认数据 + 满能量</td></tr>
 *   <tr><td>配方 / {@code /give} / 数据包</td>
 *       <td>把 {@code BLADE_STATE_DATA} 与 {@code BLADE_ENERGY} 注册成**物品默认组件**
 *           ({@code Item.Properties#component(...)},N26 仍未闭合 ⇒ 本期靠下一行的自愈兜住)</td></tr>
 *   <tr><td>兜底自愈</td><td>{@link #ensureInitialized}:缺刀身数据就补默认、能量缺席视作满、越界 clamp;
 *       调用点 = {@link ChargedBladeItem#onCraftedBy} / {@link ChargedBladeItem#inventoryTick}(服务端分支)
 *       / {@link ChargedBladeItem#use} 的门禁入口 / 充能方块的每 tick 结算</td></tr>
 * </table>
 *
 * <h2>默认刀身数据逐字段(与 PLAN §3.3 一致)</h2>
 * <pre>
 * translationKey      = spec.nameKey()          // 必须形如 item.applied_slash.charged_blade_pulse
 * baseAttackModifier  = 13.14F                  // 见 ChargedBladeSpec.BASE_ATTACK_MODIFIER
 * refine / killCount / proudSoul = 0 / 0 / 0    // refine&gt;0 会立刻破坏 13.14 的公式
 * broken              = false                   // 断刀分支会让伤害变成 -0.5 - b
 * defaultBewitched    = false                   // 「无初始附魔」+ 不显示「妖刀」
 * destructable        = false                   // 避免耐久/broken 语义混入
 * sealed              = (energy == 0)           // 由能量推导,必须与能量一起写死
 * model / texture     = spec.model() / spec.texture()   // 可能为 null ⇒ 跳过(用重锋默认解析)
 * setNonEmpty()                                 // 现有代码已实证这个 setter
 * </pre>
 * <b>不要写</b>附魔、不要写 {@code slashArtsKey}(SA 清空与否需用户拍板,见 N20)、不要写 durability。
 *
 * <p>写法照抄项目现有 {@code dev/BladeTestFactory.java} 第 52–59 行:
 * {@code BladeStateAccess.of(stack).ifPresent(state -> {...})} —— setter 会写回物品本身
 * (组件缺席时 {@code of()} 返回空,此时本工厂无法写入,见下面的 NEEDS_HOST_CHECK)。
 */
public final class ChargedBladeFactory {
    private ChargedBladeFactory() {
    }

    /**
     * 创造栏/命令用的出厂成品:默认刀身数据 + <b>满能量</b>。
     *
     * <p>「满能量」是刻意选择:创造栏拿到就是能用的。
     */
    public static ItemStack fresh(ChargedBladeItem item) {
        ItemStack stack = new ItemStack(item);
        applyDefaults(stack, item.spec());
        ChargedBladeEnergy.set(stack, item.maxEnergy());
        return stack;
    }

    /**
     * 缺什么补什么;不缺就把越界值拉回区间(不做无谓写入)。
     *
     * <p><b>「缺席 = 满」只作用于能量组件</b>:{@code BLADE_ENERGY} 缺席视作满能量并写回,
     * 否则 {@code /give} 出来的刀一上手就是废刀;而刀身数据缺席(或只是空数据)时按
     * {@link #applyDefaults} 补一份默认数据。
     *
     * <p>对<b>已经初始化过</b>的刀不会覆写任何字段 —— 玩家的精炼 / 杀敌 / 耀魂必须原样保留,
     * 这也是为什么判定用的是 {@code ISlashBladeState#isEmpty()}(重锋自己的「还没有真实刀身数据」旗标)
     * 而不是无脑 applyDefaults。
     */
    public static void ensureInitialized(ItemStack stack) {
        if (!(stack.getItem() instanceof ChargedBladeItem blade)) {
            return;
        }
        if (needsDefaults(stack)) {
            applyDefaults(stack, blade.spec());
        }
        int max = Math.max(0, blade.maxEnergy());
        if (!ChargedBladeEnergy.has(stack)) {
            // 「缺席 = 满」:一次性补满,之后的读写都只看组件
            ChargedBladeEnergy.set(stack, max);
            return;
        }
        int current = ChargedBladeEnergy.get(stack);
        if (current < 0 || current > max) {
            // 越界(改过配置、或手改过 NBT)⇒ clamp 回 [0, max]
            ChargedBladeEnergy.set(stack, Math.min(current, max));
        }
    }

    /**
     * 写入一份完整的默认刀身数据(不碰能量)。
     *
     * <p>{@code sealed} 由**当前**能量推导:调用方随后若写能量,
     * {@link ChargedBladeEnergy#set} 会再同步一次 —— 两者都遵循同一条不变式
     * 「sealed 是能量的只读投影」,所以先后顺序不会留下不一致。
     */
    public static void applyDefaults(ItemStack stack, ChargedBladeSpec spec) {
        if (stack.isEmpty() || spec == null) {
            return;
        }
        BladeStateAccess.of(stack).ifPresent(state -> fillDefaults(state, stack, spec));
    }

    /** 真正写字段的地方:单独一个方法便于把「捕获 stack」这件事显式化。 */
    private static void fillDefaults(ISlashBladeState state, ItemStack stack, ChargedBladeSpec spec) {
        state.setTranslationKey(spec.nameKey());
        state.setBaseAttackModifier(ChargedBladeSpec.BASE_ATTACK_MODIFIER);
        state.setRefine(0);
        state.setKillCount(0);
        state.setProudSoulCount(0);
        state.setBroken(false);
        state.setDefaultBewitched(false);
        state.setDestructable(false);
        if (spec.model() != null) {
            state.setModel(spec.model());
        }
        if (spec.texture() != null) {
            state.setTexture(spec.texture());
        }
        state.setSealed(ChargedBladeEnergy.get(stack) <= 0);
        state.setNonEmpty();
    }

    /**
     * 该栈是否还没有真实刀身数据(需要补默认值)。
     *
     * <p>判定用 {@code ISlashBladeState#isEmpty()}(重锋自己的旗标,与 {@code setNonEmpty()} 配对):
     * 这样「默认数据还没写过」与「玩家已经养出来的刀」能被区分开,不会把后者洗回出厂值。
     *
     * <p>// NEEDS_HOST_CHECK: {@code BladeStateAccess.of(stack)} 对「完全没有
     * {@code BLADE_STATE_DATA} 组件的裸栈」返回的是空 Optional 还是一个可写视图,本项目没有实证
     * (N14/N26 未闭合)。若返回空,本方法会走 {@code orElse(true)} 分支去调 {@link #applyDefaults},
     * 而那一层 {@code ifPresent} 同样写不进去 —— 即「裸栈自愈」在那种情况下失效,
     * 需要 host 用 {@code javap BladeStateAccess} 定论;真正彻底的解法是在物品注册处
     * 用 {@code Item.Properties#component(BLADE_STATE_DATA, BladeStateData.DEFAULT)} 给一个默认组件。
     *
     * <p>// NEEDS_HOST_CHECK(第二条,反向风险): 本判定**必须**在写过一次默认值之后翻成 false ——
     * 若 {@code setNonEmpty()} 之后 {@code isEmpty()} 仍为 true,{@link #ensureInitialized} 会每 tick
     * 重写一遍默认字段,把玩家养出来的精炼 / 杀敌 / 耀魂洗掉(并且白烧组件写入)。
     * 判据:进游戏后把刀用一阵,看 tooltip 的刀铭 / 精炼行是否被重置为 0。
     * 若确实会翻不回来,把本方法的判定换成
     * {@code !stack.has(SlashBladeDataComponents.BLADE_STATE_DATA.get())}(组件缺席才算缺数据)。
     */
    private static boolean needsDefaults(ItemStack stack) {
        return BladeStateAccess.of(stack).map(ISlashBladeState::isEmpty).orElse(true);
    }
}
