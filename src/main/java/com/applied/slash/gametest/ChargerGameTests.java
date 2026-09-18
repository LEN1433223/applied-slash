package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 拔刀剑充能器在"去掉 5 把自设充能刀"之后的回归测试(holder 类)。
 *
 * <p>为什么需要它:充能器**保留**了下来,但它的槽位口径从"只认本模组充能刀"改成"认任意拔刀剑"。
 * 原来覆盖充电数学与槽位口径的 13 条测试随充能刀一起删掉了 —— 这里补回**不依赖充能刀**的那部分,
 * 保证留下这台机器不是"没人管"的状态。
 *
 * <p>实现放在 {@link ChargerTestSupport}(非 holder 类),沿用本模组既有约定。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class ChargerGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    private ChargerGameTests() {
    }

    /** 能量上限只对**拔刀剑**成立:莉莉有上限,石头没有(这是充能器槽位口径的语义核心)。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void bladeEnergyCapOnlyAppliesToBlades(GameTestHelper helper) {
        ChargerTestSupport.bladeEnergyCapOnlyAppliesToBlades(helper);
    }

    /** 充电数学(纯函数)仍然按既有的三条语义工作:满 ⇒ FULL、没电 ⇒ NO_AE、有电 ⇒ 逐点充。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void chargerChargeMathStillHolds(GameTestHelper helper) {
        ChargerTestSupport.chargerChargeMathStillHolds(helper);
    }
}
