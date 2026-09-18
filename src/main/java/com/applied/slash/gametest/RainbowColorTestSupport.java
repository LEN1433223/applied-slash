package com.applied.slash.gametest;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.LiliBlade;
import com.applied.slash.rainbow.LiliRainbowColor;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 动态彩色刀光(粉紫 12 色)的测试实现。
 *
 * <p>可测性来自设计:{@link LiliRainbowColor#advance}/{@link LiliRainbowColor#restore} 都是
 * 只依赖一个 {@link ItemStack} 的静态方法,不需要玩家、不需要挥砍。
 */
final class RainbowColorTestSupport {
    private RainbowColorTestSupport() {
    }

    /** 色板自检:12 档、都是粉紫(绿通道最小)、且首尾衔接平滑。 */
    static void paletteIsPinkPurple12(GameTestHelper helper) {
        int[] p = LiliRainbowColor.PALETTE;
        helper.assertTrue(p.length == 12, "色板应为 12 档,实际 " + p.length);
        for (int i = 0; i < p.length; i++) {
            int r = (p[i] >> 16) & 0xFF;
            int g = (p[i] >> 8) & 0xFF;
            int b = p[i] & 0xFF;
            helper.assertTrue(g < r && g < b, "第 " + i + " 档不是粉紫(绿通道不是最小):" + Integer.toHexString(p[i]));
        }
        int wrap = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            wrap = Math.max(wrap, Math.abs(((p[0] >> shift) & 0xFF) - ((p[11] >> shift) & 0xFF)));
        }
        helper.assertTrue(wrap <= 48, "色板首尾衔接跳变过大(最大通道差 " + wrap + "),循环会有突兀感");
        helper.succeed();
    }

    /** 每次攻击推进一档:颜色在色板内变化,且原始颜色被记住。 */
    static void rainbowAdvancesAndRemembers(GameTestHelper helper) {
        ItemStack lili = LiliBlade.stack(helper.getLevel().registryAccess());
        helper.assertTrue(!lili.isEmpty(), "莉莉没加载");
        var state = BladeStateAccess.of(lili).orElseThrow();
        int original = state.getColorCode();

        helper.assertTrue(LiliRainbowColor.advance(lili), "第一次推进应成功");
        int c1 = state.getColorCode();
        Integer remembered = lili.get(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get());
        helper.assertTrue(remembered != null && remembered == original,
                "应记住原始颜色码 " + original + ",实际 " + remembered);

        helper.assertTrue(LiliRainbowColor.advance(lili), "第二次推进应成功");
        int c2 = state.getColorCode();
        helper.assertTrue(c1 != c2, "两次攻击后颜色应不同(实际都是 " + Integer.toHexString(c1) + ")");
        helper.assertTrue(inPalette(c1) && inPalette(c2),
                "颜色必须来自色板:c1=" + Integer.toHexString(c1) + " c2=" + Integer.toHexString(c2));

        // 非拔刀剑不参与
        helper.assertTrue(!LiliRainbowColor.advance(new ItemStack(Items.STONE)),
                "石头不该被改色");
        helper.succeed();
    }

    /** 可还原:关掉机制后 restore 能把颜色写回原值,并清掉存档组件。 */
    static void rainbowRestoresOriginal(GameTestHelper helper) {
        ItemStack lili = LiliBlade.stack(helper.getLevel().registryAccess());
        var state = BladeStateAccess.of(lili).orElseThrow();
        int original = state.getColorCode();

        LiliRainbowColor.ENABLED = false;
        try {
            helper.assertTrue(!LiliRainbowColor.advance(lili), "关掉开关后不该再改色");
        } finally {
            LiliRainbowColor.ENABLED = true;
        }
        helper.assertTrue(LiliRainbowColor.advance(lili), "开回来后应能改色");
        // 记住的是原始值(上面那次改色前记的)
        helper.assertTrue(LiliRainbowColor.restore(lili), "应能还原");
        helper.assertTrue(state.getColorCode() == original,
                "还原后颜色码应为 " + original + ",实际 " + state.getColorCode());
        helper.assertTrue(lili.get(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get()) == null,
                "还原后应清掉存档组件");
        helper.assertTrue(!LiliRainbowColor.restore(lili), "已还原过就不该再次还原");
        helper.succeed();
    }

    /**
     * 是否属于色板。
     *
     * <p>注意:重锋的 {@code getColorCode()} 返回的是 **ARGB**(高位 0xFF 是 alpha),
     * 而色板里是 24 位 RGB ⇒ 比较时必须只取低 24 位(实测踩过这个坑)。
     */
    private static boolean inPalette(int color) {
        int rgb = color & 0xFFFFFF;
        for (int c : LiliRainbowColor.PALETTE) {
            if ((c & 0xFFFFFF) == rgb) {
                return true;
            }
        }
        return false;
    }
}