package com.applied.slash.gametest;

import com.applied.slash.AppliedBlades;
import com.applied.slash.rainbow.LiliRainbowColor;
import com.applied.slash.se.InventoryDamageTransferLogic;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * 四把数据包刀(莉莉 / 枫 / 栞 / 星奈)与"按刀切换刀光色板"的测试。
 *
 * <p>四把刀走的是**同一套机制**:物品 = 重锋原生 {@code slashblade:slashblade}、模型 = sange.obj、
 * 贴图 = 各自的 UV 图集、共享同一个 SE。这里把它们逐条钉住,避免以后改一把漏三把。
 */
final class BladeSetTestSupport {
    private BladeSetTestSupport() {
    }

    /** 四把刀都能从数据包注册表构建出来,且都是有 SE 的妖刀。 */
    static void allFourBladesAreLoaded(GameTestHelper helper) {
        var regs = helper.getLevel().registryAccess();
        for (String name : AppliedBlades.ALL) {
            ItemStack blade = AppliedBlades.stack(regs, name);
            helper.assertTrue(!blade.isEmpty(), "刀身定义没加载: " + name);
            var state = BladeStateAccess.of(blade).orElseThrow();
            helper.assertTrue(state.hasSpecialEffect(InventoryDamageTransferLogic.SE_ID),
                    name + " 应带共享 SE " + InventoryDamageTransferLogic.SE_ID);
            helper.assertTrue(state.getBaseAttackModifier() > 0.0F,
                    name + " 的基础伤害应大于 0,实际 " + state.getBaseAttackModifier());
        }
        // 四把基础伤害各不相同 ⇒ 确认它们不是同一把刀的别名
        java.util.Set<Float> attacks = new java.util.HashSet<>();
        for (String name : AppliedBlades.ALL) {
            attacks.add(BladeStateAccess.of(AppliedBlades.stack(regs, name)).orElseThrow().getBaseAttackModifier());
        }
        helper.assertTrue(attacks.size() == 4, "四把刀的伤害应彼此不同,实际 " + attacks);
        helper.succeed();
    }

    /** 色板按刀切换:四把刀的基础色各自映射到 12 色板,且互不相同。 */
    static void paletteIsPerBlade(GameTestHelper helper) {
        int[] lili = LiliRainbowColor.paletteFor(0xF5768B);
        int[] maple = LiliRainbowColor.paletteFor(0xFF8C1A);
        int[] shiori = LiliRainbowColor.paletteFor(0xF0B49C);
        int[] seina = LiliRainbowColor.paletteFor(0xB98CF5);
        int[][] all = {lili, maple, shiori, seina};
        for (int i = 0; i < all.length; i++) {
            helper.assertTrue(all[i].length == 12, "第 " + i + " 套色板应为 12 档,实际 " + all[i].length);
        }
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (int[] p : all) {
            distinct.add(java.util.Arrays.toString(p));
        }
        helper.assertTrue(distinct.size() == 4, "四把刀的色板应互不相同");
        // 认不出的基础色要退回默认色板(不是崩溃/空指针)
        helper.assertTrue(LiliRainbowColor.paletteFor(0x123456).length == 12, "未知基础色应退回默认色板");
        helper.succeed();
    }

    /** **枫的刀光必须是橙色系,不含红**(用户明确要求)。 */
    static void maplePaletteHasNoRed(GameTestHelper helper) {
        int[] maple = LiliRainbowColor.paletteFor(0xFF8C1A);
        for (int c : maple) {
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            // 橙/金:paint 上 R > G > B,而且**绿通道不能低**(纯红的绿通道很低)
            helper.assertTrue(r > g && g >= b, "枫的刀光应是橙金(R>G>=B):" + Integer.toHexString(c));
            helper.assertTrue(g >= 0x80, "枫的刀光绿通道过低 = 偏红,不符合要求:" + Integer.toHexString(c));
        }
        helper.succeed();
    }
}