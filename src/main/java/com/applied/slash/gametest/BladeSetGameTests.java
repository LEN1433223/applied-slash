package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 四把数据包刀与按刀切换刀光色板的契约测试(holder 类;实现见 {@link BladeSetTestSupport})。 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class BladeSetGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    private BladeSetGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void allFourBladesAreLoaded(GameTestHelper helper) {
        BladeSetTestSupport.allFourBladesAreLoaded(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void paletteIsPerBlade(GameTestHelper helper) {
        BladeSetTestSupport.paletteIsPerBlade(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void maplePaletteHasNoRed(GameTestHelper helper) {
        BladeSetTestSupport.maplePaletteHasNoRed(helper);
    }
}