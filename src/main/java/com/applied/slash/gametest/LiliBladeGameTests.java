package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 拔刀剑「莉莉」的契约测试(holder 类)。
 *
 * <p>约定与 {@link SlashBladeCellGameTests} 相同:holder 类的方法签名**只能有
 * {@link GameTestHelper} 一个参数**,实现放在非 holder 类 {@link LiliBladeTestSupport} 里。
 * 原因是重锋/NeoForge 会在扫描期加载被引用的类,holder 的签名里一旦出现第三方类型,
 * 前置缺席时就会 NoClassDefFoundError —— 与本模组既有的做法保持一致。
 *
 * <p>每条断言对应玩家能看到的一件事:
 * <ul>
 *   <li>数据包定义真的被加载(刀存在);</li>
 *   <li>面板伤害 = 14.13;</li>
 *   <li>**没有附魔**、**没有 SA**、**没有 SE**(不是"禁用",而是定义里就没有);</li>
 *   <li>模型/贴图就是复用丛雨丸的那两份。</li>
 * </ul>
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class LiliBladeGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    private LiliBladeGameTests() {
    }

    /** 数据包定义在场,且能构建出一把非空的拔刀剑。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void liliDefinitionIsLoaded(GameTestHelper helper) {
        LiliBladeTestSupport.liliDefinitionIsLoaded(helper);
    }

    /** 伤害 = 14.13(baseAttackModifier;refine=0 时面板即该值)。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void liliHasExactDamage(GameTestHelper helper) {
        LiliBladeTestSupport.liliHasExactDamage(helper);
    }

    /** 零附魔。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void liliHasNoEnchantments(GameTestHelper helper) {
        LiliBladeTestSupport.liliHasNoEnchantments(helper);
    }

    /** 无 SA(切刀技为空)+ 无 SE(特殊效果为空)+ 无特殊刀身类型。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void liliHasDriveHorizontalSa(GameTestHelper helper) {
        LiliBladeTestSupport.liliHasDriveHorizontalSa(helper);
    }

    /** 模型/贴图指向我们复用的那份丛雨丸资产。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void liliUsesReusedMurasameAssets(GameTestHelper helper) {
        LiliBladeTestSupport.liliUsesReusedMurasameAssets(helper);
    }
}
