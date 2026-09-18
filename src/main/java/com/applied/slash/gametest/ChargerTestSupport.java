package com.applied.slash.gametest;

import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.LiliBlade;
import com.applied.slash.charged.BladeEnergy;
import com.applied.slash.charged.block.ChargerMath;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@link ChargerGameTests} 的实现(非 holder 类)。
 *
 * <p>覆盖两件事:
 * <ol>
 *   <li>改造后的槽位口径 —— 能量上限只对拔刀剑成立({@link BladeEnergy#max});</li>
 *   <li>充电数学(纯函数 {@link ChargerMath#chargeTick})在改造后语义未变。</li>
 * </ol>
 */
final class ChargerTestSupport {
    private ChargerTestSupport() {
    }

    static void bladeEnergyCapOnlyAppliesToBlades(GameTestHelper helper) {
        ItemStack lili = LiliBlade.stack(helper.getLevel().registryAccess());
        helper.assertFalse(lili.isEmpty(), "莉莉没加载,无法验证充能器的槽位口径");

        int liliMax = BladeEnergy.max(lili);
        int configured = Math.max(1, AppliedSlashConfig.chargerMaxEnergy());
        helper.assertTrue(liliMax == configured,
                "拔刀剑的能量上限应是配置值 " + configured + ",实际 " + liliMax);

        // 非拔刀剑不进入这套口径(充能器的 BladeSlot 与 serverTick 都用同一个判定)
        helper.assertTrue(BladeEnergy.max(new ItemStack(Items.STONE)) == 0,
                "非拔刀剑不该有能量上限,实际 " + BladeEnergy.max(new ItemStack(Items.STONE)));

        // 写入会被 clamp 到 [0, max]
        ItemStack blade = lili.copy();
        BladeEnergy.set(blade, liliMax + 5000);
        helper.assertTrue(BladeEnergy.get(blade) == liliMax,
                "超上限写入应被 clamp 到 " + liliMax + ",实际 " + BladeEnergy.get(blade));
        BladeEnergy.set(blade, -100);
        helper.assertTrue(BladeEnergy.get(blade) == 0,
                "负值写入应被 clamp 到 0,实际 " + BladeEnergy.get(blade));

        // 非拔刀剑写入应当被完全忽略(不许把能量写进别人家的物品)
        ItemStack stone = new ItemStack(Items.STONE);
        BladeEnergy.set(stone, 100);
        helper.assertTrue(!BladeEnergy.has(stone), "非拔刀剑不该被写上能量组件");
        helper.succeed();
    }

    static void chargerChargeMathStillHolds(GameTestHelper helper) {
        int max = 800;
        int aePerPoint = 100;
        int perTick = 1;

        // 满能量 ⇒ FULL,且不扣电
        ChargerMath.ChargeTick full = ChargerMath.chargeTick(max, max, 1_000_000L, aePerPoint, perTick);
        helper.assertTrue(full.state() == ChargerMath.STATE_FULL && full.aeConsumed() == 0L,
                "满能量时应为 FULL 且不耗电,实际 " + full);

        // 一点电都没有 ⇒ NO_AE
        ChargerMath.ChargeTick noAe = ChargerMath.chargeTick(0, max, 0L, aePerPoint, perTick);
        helper.assertTrue(noAe.state() == ChargerMath.STATE_NO_AE && noAe.newEnergy() == 0,
                "无电时应为 NO_AE 且不涨能量,实际 " + noAe);

        // 电够 ⇒ 充 perTick 点,消耗 perTick × aePerPoint
        ChargerMath.ChargeTick charge = ChargerMath.chargeTick(0, max, 1_000_000L, aePerPoint, perTick);
        helper.assertTrue(charge.state() == ChargerMath.STATE_CHARGING
                        && charge.newEnergy() == perTick
                        && charge.aeConsumed() == (long) perTick * aePerPoint,
                "有电时应充 " + perTick + " 点并消耗 " + ((long) perTick * aePerPoint) + " AE,实际 " + charge);

        // AE 不足一点 ⇒ NO_AE(不许凭空生成)
        ChargerMath.ChargeTick shortAe = ChargerMath.chargeTick(0, max, aePerPoint - 1L, aePerPoint, perTick);
        helper.assertTrue(shortAe.state() == ChargerMath.STATE_NO_AE && shortAe.aeConsumed() == 0L,
                "AE 不足一点时应为 NO_AE 且不扣电,实际 " + shortAe);

        // 快满时只充到上限,不多充也不多扣
        ChargerMath.ChargeTick topUp = ChargerMath.chargeTick(max - 1, max, 1_000_000L, aePerPoint, perTick);
        helper.assertTrue(topUp.newEnergy() == max,
                "只剩 1 点时应正好补到上限 " + max + ",实际 " + topUp.newEnergy());
        helper.succeed();
    }
}
