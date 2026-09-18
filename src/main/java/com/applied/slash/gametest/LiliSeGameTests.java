package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 莉莉 SE「背包伤害转移」的契约测试(holder 类;实现见 {@link InventoryTransferTestSupport})。
 *
 * <p>对应玩家能看到的三件事:①SE 注册成功;②背包里每把其它拔刀剑各贡献 15% 基础伤害;
 * ③非拔刀剑与自身不计入。算术是纯函数,所以这里不需要真玩家。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class LiliSeGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    private LiliSeGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void seIsRegistered(GameTestHelper helper) {
        InventoryTransferTestSupport.seIsRegistered(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void bonusSumsOtherBlades(GameTestHelper helper) {
        InventoryTransferTestSupport.bonusSumsOtherBlades(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void bonusSkipsSelfAndNonBlades(GameTestHelper helper) {
        InventoryTransferTestSupport.bonusSkipsSelfAndNonBlades(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void bonusReachesAttribute(GameTestHelper helper) {
        InventoryTransferTestSupport.bonusReachesAttribute(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void bonusOnlyCountsHotbar(GameTestHelper helper) {
        InventoryTransferTestSupport.bonusOnlyCountsHotbar(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void bonusComponentRoundTrip(GameTestHelper helper) {
        InventoryTransferTestSupport.bonusComponentRoundTrip(helper);
    }
}