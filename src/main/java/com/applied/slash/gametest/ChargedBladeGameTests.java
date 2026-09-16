package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 充能刀 / 充能方块的 GameTest 入口(新增 8 条)。跑法同既有 12 条:{@code gradlew runGameTestServer}。
 *
 * <p><b>本类刻意不出现任何 AE2 与 SlashBlade 类型</b>(与 {@link SlashBladeCellGameTests} 同理):
 * NeoForge 在开发模式会扫描并 {@code Class.forName} 加载 {@code @GameTestHolder} 类,
 * {@code getDeclaredMethods()} 会解析该类**全部方法的签名** —— 签名里一旦含前置类型,
 * 没装前置的开发客户端就会 {@code NoClassDefFoundError} 崩在启动阶段(已实测)。
 *
 * <p>因此实现全部在 {@link ChargedBladeTestSupport}(非 holder,不会被扫描)里,
 * 这里只留 {@code GameTestHelper} 入口 —— <b>本类的方法签名因此必须永远只有
 * {@code GameTestHelper} 一个参数</b>,加参数/换类型都会重新引入上面那条崩溃。
 *
 * <p><b>状态(C 已完成)</b>:8 条全部是真实断言,能真的失败;断言口径见
 * {@link ChargedBladeTestSupport} 的类注释。下面每条的方法注释写的是**该条要求成立的契约**,
 * 不是当前实现的样子。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class ChargedBladeGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 400;

    private ChargedBladeGameTests() {
    }

    /** 0 能量:canUse == false;右键返回 FAIL。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void chargedBladeWithoutEnergyCannotBeUsed(GameTestHelper helper) {
        ChargedBladeTestSupport.chargedBladeWithoutEnergyCannotBeUsed(helper);
    }

    /** 能量 = 1(≥ 消耗)⇒ canUse == true、use 走 super 分支。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void chargedBladeAfterChargeCanBeUsed(GameTestHelper helper) {
        ChargedBladeTestSupport.chargedBladeAfterChargeCanBeUsed(helper);
    }

    /** 直接调 hurtEnemy ⇒ 能量 -1;打到 0 后不再变负且 sealed == true。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void energySpentOnlyOnRealHit(GameTestHelper helper) {
        ChargedBladeTestSupport.energySpentOnlyOnRealHit(helper);
    }

    /** 满能量入库→取出 = 满;137 → 80 档位且损失 ≤ 档宽-1。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void energySurvivesStorageRoundTrip(GameTestHelper helper) {
        ChargedBladeTestSupport.energySurvivesStorageRoundTrip(helper);
    }

    /** 同档能量(131/139)合并成一个键;异档(139/161)两个键。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void sameBucketMergesToOneKey(GameTestHelper helper) {
        ChargedBladeTestSupport.sameBucketMergesToOneKey(helper);
    }

    /** 纯函数门禁:0 能量 ⇒ shouldZeroDamage == true;≥1 ⇒ false。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void chargedBladeGateBlocksAttack(GameTestHelper helper) {
        ChargedBladeTestSupport.chargedBladeGateBlocksAttack(helper);
    }

    /** {@code new ItemStack(item)}(无 state)不崩:damageItem == 0、5 把刀都齐。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void chargedBladeNeverLacksBladeState(GameTestHelper helper) {
        ChargedBladeTestSupport.chargedBladeNeverLacksBladeState(helper);
    }

    /** 充能数学:满能量不耗电;AE 不足一点不充也不扣。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void chargerChargeMath(GameTestHelper helper) {
        ChargedBladeTestSupport.chargerChargeMath(helper);
    }
}
