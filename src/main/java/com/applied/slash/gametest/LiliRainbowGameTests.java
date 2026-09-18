package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 动态彩色刀光(粉紫 12 色)的契约测试(holder 类;实现见 {@link RainbowColorTestSupport})。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class LiliRainbowGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    private LiliRainbowGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void paletteIsPinkPurple12(GameTestHelper helper) {
        RainbowColorTestSupport.paletteIsPinkPurple12(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void rainbowAdvancesAndRemembers(GameTestHelper helper) {
        RainbowColorTestSupport.rainbowAdvancesAndRemembers(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void rainbowRestoresOriginal(GameTestHelper helper) {
        RainbowColorTestSupport.rainbowRestoresOriginal(helper);
    }
}