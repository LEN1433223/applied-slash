package com.applied.slash.charged;

import com.applied.slash.AppliedSlash;

import net.minecraft.resources.ResourceLocation;

/**
 * 5 把「充能拔刀剑」的静态说明(物品 id / 语言键 / 默认能量上限 / 刀身外观)。
 *
 * <p><b>为什么单独一个类</b>:同一个刀的信息被三处消费 —— 物品注册({@link ChargedBladeItems})、
 * 创造栏出厂数据({@link ChargedBladeFactory})、刀身默认数据里的 {@code translationKey}
 * (它同时就是语言键,见 PLAN-CHARGED-BLADES §3.3 的硬约束)。把三者放在同一处,
 * 「物品 id ↔ 语言键 ↔ translationKey 三者同源」才是可核查的。
 *
 * <p><b>translationKey 的格式是硬约束</b>(实证:{@code ItemSlashBlade.parseBladeID} 会
 * {@code substring(5)} 再 {@code replaceFirst("\\.", ":")})⇒ 必须是
 * 「5 字符前缀 + <code>ns.path</code>」的 description-id 形式,即
 * {@code item.applied_slash.charged_blade_pulse}。写成
 * {@code applied_slash.charged_blade.pulse} 会解析出垃圾 ResourceLocation。
 *
 * <p>本类是**纯数据**,没有任何方法调用前置模组;对前置类的引用只发生在
 * {@link ChargedBladeItems}(被 SlashBlade 门卫保护)。
 */
public record ChargedBladeSpec(
        /** 物品注册 id(不带命名空间),同时也是贴图/模型文件名。 */
        String id,
        /** 语言键 = 刀身数据的 translationKey,例如 {@code item.applied_slash.charged_blade_pulse}。 */
        String nameKey,
        /** 默认能量上限;运行期以配置项为准(见 {@link ChargedBladeItem#maxEnergy()})。 */
        int defaultMaxEnergy,
        /** 刀身模型(重锋资源名);null 表示这一阶段不写(用重锋默认解析)。 */
        ResourceLocation model,
        /** 刀身贴图(重锋资源名);null 表示这一阶段不写(用重锋默认解析)。 */
        ResourceLocation texture) {

    /** 本模组命名空间。 */
    public static final String NAMESPACE = AppliedSlash.MODID;

    /**
     * 基础攻击力。**13.14 是实证过的精确值,不要再减去 1**(事实表 §8.1):
     * {@code getDefaultAttributeModifiers} 的带刀身数据分支算的是
     * {@code value = b + extra - 1},而 MC 玩家基础攻击力为 1 ⇒ 最终显示伤害 = b。
     * 前置条件是 {@code refine == 0} 且不进入 broken 分支 —— 见 {@link ChargedBladeFactory}。
     */
    public static final float BASE_ATTACK_MODIFIER = 13.14F;

    // ------------------------------------------------------------------
    // 刀身外观(事实表 §5 的美术资源表,文件名逐个来自 `jar tf` 实证;
    // 五套 obj/png 的版权属于 SlashBlade 重锋,发布说明里必须标注来源)。
    // 注意两个坑:agito **没有** agito.png(只有 agito_true/false/rust 等变体)、
    // yamato 的贴图**没有子目录**(就是 model/named/yamato.png,而 obj 也在同级)。
    // ------------------------------------------------------------------
    private static final ResourceLocation MODEL_PULSE =
            ResourceLocation.parse("slashblade:model/named/agito.obj");
    private static final ResourceLocation TEXTURE_PULSE =
            ResourceLocation.parse("slashblade:model/named/agito_true.png");

    private static final ResourceLocation MODEL_RESONANCE =
            ResourceLocation.parse("slashblade:model/named/dios/dios.obj");
    private static final ResourceLocation TEXTURE_RESONANCE =
            ResourceLocation.parse("slashblade:model/named/dios/dios.png");

    private static final ResourceLocation MODEL_SURGE =
            ResourceLocation.parse("slashblade:model/named/muramasa/muramasa.obj");
    private static final ResourceLocation TEXTURE_SURGE =
            ResourceLocation.parse("slashblade:model/named/muramasa/muramasa.png");

    private static final ResourceLocation MODEL_OVERCHARGE =
            ResourceLocation.parse("slashblade:model/named/sange/sange.obj");
    /** 实证存在(`build/facts/assets.txt` 的 `assets/slashblade/model/named/sange/sange.png`)。 */
    private static final ResourceLocation TEXTURE_OVERCHARGE =
            ResourceLocation.parse("slashblade:model/named/sange/sange.png");

    private static final ResourceLocation MODEL_SINGULARITY =
            ResourceLocation.parse("slashblade:model/named/yamato.obj");
    /** 实证存在(同上 `model/named/yamato.png`);注意 yamato 没有子目录。 */
    private static final ResourceLocation TEXTURE_SINGULARITY =
            ResourceLocation.parse("slashblade:model/named/yamato.png");

    public static final ChargedBladeSpec PULSE = new ChargedBladeSpec(
            "charged_blade_pulse", "item.applied_slash.charged_blade_pulse", 200,
            MODEL_PULSE, TEXTURE_PULSE);
    public static final ChargedBladeSpec RESONANCE = new ChargedBladeSpec(
            "charged_blade_resonance", "item.applied_slash.charged_blade_resonance", 400,
            MODEL_RESONANCE, TEXTURE_RESONANCE);
    public static final ChargedBladeSpec SURGE = new ChargedBladeSpec(
            "charged_blade_surge", "item.applied_slash.charged_blade_surge", 800,
            MODEL_SURGE, TEXTURE_SURGE);
    public static final ChargedBladeSpec OVERCHARGE = new ChargedBladeSpec(
            "charged_blade_overcharge", "item.applied_slash.charged_blade_overcharge", 1600,
            MODEL_OVERCHARGE, TEXTURE_OVERCHARGE);
    public static final ChargedBladeSpec SINGULARITY = new ChargedBladeSpec(
            "charged_blade_singularity", "item.applied_slash.charged_blade_singularity", 3200,
            MODEL_SINGULARITY, TEXTURE_SINGULARITY);

    /** 带命名空间的完整物品 id,例如 {@code applied_slash:charged_blade_pulse}。 */
    public String fullId() {
        return NAMESPACE + ":" + id;
    }

    /** 物品模型路径,例如 {@code applied_slash:item/charged_blade_pulse}。 */
    public ResourceLocation itemModel() {
        return ResourceLocation.parse(NAMESPACE + ":item/" + id);
    }

    /** 物品图标贴图路径,例如 {@code applied_slash:item/charged_blade_pulse}。 */
    public ResourceLocation itemTexture() {
        return itemModel();
    }
}
