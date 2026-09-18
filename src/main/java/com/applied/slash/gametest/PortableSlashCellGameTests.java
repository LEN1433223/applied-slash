package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 手持「Slash 元件」与加伤池的契约测试(holder 类;实现见两个 support 类)。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class PortableSlashCellGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    private PortableSlashCellGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void cellRejectsNonBlades(GameTestHelper helper) {
        PortableCellTestSupport.cellRejectsNonBlades(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void cellHoldsExactlyEight(GameTestHelper helper) {
        PortableCellTestSupport.cellHoldsExactlyEight(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void cellSurvivesReResolution(GameTestHelper helper) {
        PortableCellTestSupport.cellSurvivesReResolution(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void cellNeverMergesIdenticalBlades(GameTestHelper helper) {
        PortableCellTestSupport.cellNeverMergesIdenticalBlades(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void poolIsTenPercentPerBlade(GameTestHelper helper) {
        DamagePoolTestSupport.poolIsTenPercentPerBlade(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void poolCountsCellFirstAndCapsAtEight(GameTestHelper helper) {
        DamagePoolTestSupport.poolCountsCellFirstAndCapsAtEight(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void cellOutsideHotbarDoesNotCount(GameTestHelper helper) {
        DamagePoolTestSupport.cellOutsideHotbarDoesNotCount(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void heldBladeExcludesItself(GameTestHelper helper) {
        DamagePoolTestSupport.heldBladeExcludesItself(helper);
    }
}