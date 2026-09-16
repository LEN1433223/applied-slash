package com.applied.slash.charged;

import java.util.List;

import com.applied.slash.AppliedSlash;

import mods.flammpfeil.slashblade.item.ItemTierSlashBlade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 5 把充能拔刀剑的注册。
 *
 * <p>自持一个 {@link DeferredRegister.Items}(命名空间仍是 {@code applied_slash};
 * <b>同命名空间可以有多个 DeferredRegister</b>,不必挤进 {@code AppliedSlashAe2.ITEMS})。
 *
 * <p><b>门卫</b>:本类是引用 {@code ItemSlashBlade} 的地方之一(经 5 个子类),
 * 所以 {@link #register} 的调用点被 {@code AppliedSlash.isSlashBladeLoaded()} 包住 ——
 * 沿用项目既有的「对前置类的引用集中在门卫之后」约定。改 required 之后该分支必然为真,
 * 但保留结构以免破坏「前置缺席也不 NoClassDefFoundError」这条既有性质。
 */
public final class ChargedBladeItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedSlash.MODID);

    /**
     * 物品属性:单件不可堆叠。
     *
     * <p><b>刻意不写 {@code durability(...)}</b>:{@code SwordItem}/{@code TieredItem} 已按 tier 赋耐久,
     * 而我们用 {@link ChargedBladeItem#damageItem} 覆写让耐久不生效。
     *
     * <p><b>刻意与重锋本体不同</b>:重锋本体注册时用的是裸 {@code new Item.Properties()}
     * (⇒ 默认 {@code stacksTo(64)}),我们用 {@code stacksTo(1)} —— 因为每把刀的
     * <b>能量是一个数据组件</b>,允许堆叠会产生「同栈不同能量」的歧义;
     * 这一条是设计选择,不是从重锋抄来的值,故在此显式标注。
     */
    private static final Item.Properties PROPS = new Item.Properties().stacksTo(1);

    /**
     * 构造实参 —— <b>逐字节取自重锋本体的注册处</b>(host 用 {@code javap -c} 实证,非推测):
     *
     * <pre>
     * // mods.flammpfeil.slashblade.registry.SlashBladeItems#lambda$static$16
     * new ItemSlashBlade(
     *     new ItemTierSlashBlade(40, 4.0F),   // bipush 40 / ldc float 4.0f
     *     4,                                  // iconst_4
     *     0.0F,                               // fconst_0
     *     new Item.Properties())
     * </pre>
     *
     * <p>三条实参的语义(依据 {@code ItemSlashBlade#getDefaultAttributeModifiers} 的两分支):
     * <ul>
     *   <li>{@code tier} = {@code ItemTierSlashBlade(uses = 40, attack = 4.0F)}:带刀身数据时
     *       <b>不参与伤害</b>(走 {@code state.getBaseAttackModifier()});其 40 点耐久对本模组也无意义
     *       ({@code damageItem} 被覆写);</li>
     *   <li>{@code attackDamageIn = 4}:只在「刀身数据缺席」的兜底分支生效
     *       (该分支为 {@code attackDamageIn + tier.getAttackDamageBonus()});</li>
     *   <li>{@code attackSpeedIn = 0.0F}:<b>一直生效</b>(ATTACK_SPEED 修饰直接用该值)
     *       ⇒ 4.0 次/秒满攻速,与原版剑的 -2.4 明显不同,因此<b>必须</b>照抄重锋的值,
     *       手感才与 {@code slashblade:slashblade} 一致。</li>
     * </ul>
     *
     * <p>面板伤害由刀身数据决定:{@code setBaseAttackModifier(13.14F)} + {@code refine = 0}
     * ⇒ 修饰值 {@code 13.14 - 1},叠加玩家基础攻击 1 ⇒ 面板正好 <b>13.14</b>。
     */
    private static final Tier TIER = new ItemTierSlashBlade(40, 4.0F);
    private static final int ATTACK_DAMAGE_IN = 4;
    private static final float ATTACK_SPEED_IN = 0.0F;

    public static final DeferredItem<ChargedBladePulseItem> CHARGED_BLADE_PULSE = ITEMS.registerItem(
            ChargedBladeSpec.PULSE.id(),
            props -> new ChargedBladePulseItem(TIER, ATTACK_DAMAGE_IN, ATTACK_SPEED_IN, props),
            PROPS);

    public static final DeferredItem<ChargedBladeResonanceItem> CHARGED_BLADE_RESONANCE = ITEMS.registerItem(
            ChargedBladeSpec.RESONANCE.id(),
            props -> new ChargedBladeResonanceItem(TIER, ATTACK_DAMAGE_IN, ATTACK_SPEED_IN, props),
            PROPS);

    public static final DeferredItem<ChargedBladeSurgeItem> CHARGED_BLADE_SURGE = ITEMS.registerItem(
            ChargedBladeSpec.SURGE.id(),
            props -> new ChargedBladeSurgeItem(TIER, ATTACK_DAMAGE_IN, ATTACK_SPEED_IN, props),
            PROPS);

    public static final DeferredItem<ChargedBladeOverchargeItem> CHARGED_BLADE_OVERCHARGE = ITEMS.registerItem(
            ChargedBladeSpec.OVERCHARGE.id(),
            props -> new ChargedBladeOverchargeItem(TIER, ATTACK_DAMAGE_IN, ATTACK_SPEED_IN, props),
            PROPS);

    public static final DeferredItem<ChargedBladeSingularityItem> CHARGED_BLADE_SINGULARITY = ITEMS.registerItem(
            ChargedBladeSpec.SINGULARITY.id(),
            props -> new ChargedBladeSingularityItem(TIER, ATTACK_DAMAGE_IN, ATTACK_SPEED_IN, props),
            PROPS);

    private ChargedBladeItems() {
    }

    /** 注册物品;必须在 mod 构造期、且已被 SlashBlade 门卫保护的情况下调用。 */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /**
     * 5 把刀,顺序 = 能量上限从小到大(创造栏按这个顺序陈列)。
     *
     * <p>只在注册完成之后调用(创造栏 {@code displayItems} 的 lambda 运行时已注册完毕)。
     * 前置缺席时返回空表,保证「前置不在场也不炸」。
     */
    public static List<ChargedBladeItem> all() {
        if (!AppliedSlash.isSlashBladeLoaded()) {
            return List.of();
        }
        return List.of(
                CHARGED_BLADE_PULSE.get(),
                CHARGED_BLADE_RESONANCE.get(),
                CHARGED_BLADE_SURGE.get(),
                CHARGED_BLADE_OVERCHARGE.get(),
                CHARGED_BLADE_SINGULARITY.get());
    }
}
