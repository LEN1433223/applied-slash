package com.applied.slash.charged.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 原版经典 GUI 面板的绘制原语 —— <b>只画几何,不含任何语义</b>。
 *
 * <h2>为什么是"原版"而不是 AE2</h2>
 * 上一版照 AE2 的 IPC 面板做了灰蓝立体边 + 帮助钮 + 多行读数,做得出来,但读起来不像
 * "一个熔炉那样的机器界面",而像一块信息面板。这一版回到原版 GUI 的语言:
 * <b>白灰面板 + 1 px 双层立体边 + 凹陷槽位 + 一支火焰 + 一支箭头</b>,
 * 只有这样才和玩家背包、箱子、熔炉长得是一家人。
 *
 * <h2>色值来源</h2>
 * 不是抄贴图,是按原版 {@code gui/container.png} 的明度关系重新定的纯色值:
 * <pre>
 *   面板:外框 #373737 → 上/左内白 #FFFFFF → 下/右内暗 #555555 → 底 #C6C6C6
 *   槽位:上/左内暗 #373737 → 下/右内白 #FFFFFF → 底 #8B8B8B
 * </pre>
 * <b>槽位与面板的立体方向是相反的</b> —— 面板"凸"、槽位"凹"。这一点抄错就会看起来像一堆方块贴纸,
 * 所以两个方法({@link #drawPanel} / {@link #drawSlot})各自写清楚,不共用一条通用路径。
 *
 * <p>全类只有 {@code GuiGraphics#fill}(1×1 色块),<b>不复制、不内嵌任何贴图</b>:
 * {@code tools/resources.gradle} 断言本模组 {@code textures/**} 下每个 PNG 都是 16×16,
 * 而这些图形只有 1 px 线,画色块比加贴图更准、还能跟随主题色。
 */
final class VanillaPanel {

    private VanillaPanel() {
    }

    // ==================================================================
    // 色板(原版 GUI 的明度关系)
    // ==================================================================

    /** 面板外框 / 内层暗线。 */
    static final int BORDER = 0xFF373737;
    /** 面板上/左内白。 */
    static final int INNER_LIGHT = 0xFFFFFFFF;
    /** 面板下/右内暗。 */
    static final int INNER_DARK = 0xFF555555;
    /** 面板底。 */
    static final int FACE = 0xFFC6C6C6;

    /** 槽位底(比面板暗)。 */
    static final int SLOT_FACE = 0xFF8B8B8B;
    /**
     * 仪表的凹槽底。
     *
     * <p><b>为什么这么深</b>:#2E2E2E 而不是与槽位同色(#8B8B8B)。仪表里压的是**亮色填充**
     * (浅青/浅绿/浅琥珀/浅红),而浅色压在 #8B8B8B 上只有 2.2~2.6:1 —— 整条读数会糊掉。
     * 凹槽压到 #2E2E2E 之后,填充对它是 7~9:1,轨道对面板 7.9:1(凹槽边界也清清楚楚)。
     */
    static final int TRACK = 0xFF2E2E2E;

    /** 槽位/槽框尺寸(内容 16×16 + 四周 1 px)= 原版 {@code Slot} 的 18×18。 */
    static final int SLOT_FRAME = 18;

    // ==================================================================
    // 原语
    // ==================================================================

    /**
     * 一块<b>凸起</b>的原版面板:外框 → 上/左内白 → 下/右内暗 → 底面。
     *
     * <p>层序不能换:内白与内暗都画在外框的**内圈**,且暗线必须后画 —— 否则左上角两者
     * 会互相覆盖,角上会留一个亮像素(像素级可见)。
     */
    static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, FACE);
        frame(guiGraphics, x, y, width, height, BORDER, INNER_LIGHT, INNER_DARK);
    }

    /**
     * 一个<b>凹陷</b>的槽位:与 {@link #drawPanel} 相反 —— 上/左压暗、下/右提亮。
     *
     * <p>传进来的是<b>槽框左上角</b>(即 {@code Slot.x - 1} / {@code Slot.y - 1}),
     * 框内 16×16 正好是物品绘制区。
     */
    static void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        drawSlot(guiGraphics, x, y, SLOT_FRAME);
    }

    /** 槽位的变体尺寸(燃烧条那种比槽窄的凹槽用得到)。 */
    static void drawSlot(GuiGraphics guiGraphics, int x, int y, int size) {
        guiGraphics.fill(x, y, x + size, y + size, SLOT_FACE);
        frame(guiGraphics, x, y, size, size, BORDER, BORDER, INNER_LIGHT);
    }

    /**
     * 凹槽 + 填充(进度条:缓冲条用)。
     *
     * <p>槽框是**一圈 1 px 深线**,没有内层高光 —— 这与 {@link #drawSlot} 不同,是有意的:
     * 轨道只有 6 px 高,再画内层高光就只剩 4 px 填充,而那条高光在深色轨道上也不像"受光"
     * 而像"脏边"。槽位的 18×18 放得下双层内衬,仪表放不下。
     *
     * <p>填充从内区左端起、按 1 px 圆整,所以填充边界永远落在像素上;比例 ≤ 0 时<b>一个像素都不画</b>
     * (空的轨道本身就是"没有"的表示,不需要再画一个 0 宽的块)。
     */
    static void drawBar(GuiGraphics guiGraphics, int x, int y, int width, int height, double ratio, int fillArgb) {
        guiGraphics.fill(x, y, x + width, y + height, TRACK);
        guiGraphics.fill(x, y, x + width, y + 1, BORDER);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        guiGraphics.fill(x, y, x + 1, y + height, BORDER);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, BORDER);

        int innerW = width - 2;
        int filled = (int) Math.round(innerW * clamp01(ratio));
        if (filled > 0) {
            guiGraphics.fill(x + 1, y + 1, x + 1 + Math.min(innerW, filled), y + height - 1, fillArgb);
        }
    }

    /**
     * 逐像素掩码:一支火焰 / 一支箭头这种"原版机器图标"。
     *
     * <p>用掩码而不是贴图:① 掩码可以按状态换色(点着 = 橙黄、没点 = 灰),
     * 贴图得准备两张;② 不为一个 13×13 的图标去改 {@code verifyResources} 的贴图校验链。
     *
     * @param pixels {@code {x, y}} 对,bool 型掩码由调用方给出(见 {@code BladeChargerScreen})
     */
    static void drawMask(GuiGraphics guiGraphics, int x, int y, int[][] pixels, int argb) {
        for (int[] pixel : pixels) {
            guiGraphics.fill(x + pixel[0], y + pixel[1], x + pixel[0] + 1, y + pixel[1] + 1, argb);
        }
    }

    /** 空心的矩形掩码(轮廓):{@link #drawMask} 的配套,用于"轮廓 + 内部填充"两层的图标。 */
    static void drawMaskOutline(GuiGraphics guiGraphics, int x, int y, int[][] pixels, int argb) {
        drawMask(guiGraphics, x, y, pixels, argb);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 1 px 立体边:外框 → 上/左一色 → 下/右一色。
     *
     * <p>{@code topLeft} / {@code bottomRight} 两个参数就是"凸"与"凹"的全部差别
     * ({@link #drawPanel} 传 白/暗,{@link #drawSlot} 传 暗/白)。
     */
    private static void frame(GuiGraphics guiGraphics, int x, int y, int width, int height,
            int border, int topLeft, int bottomRight) {
        // 外框
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);

        // 上/左内线
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, topLeft);
        guiGraphics.fill(x + 1, y + 1, x + 2, y + height - 1, topLeft);

        // 下/右内线
        guiGraphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, bottomRight);
        guiGraphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, bottomRight);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
