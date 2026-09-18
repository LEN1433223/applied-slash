package com.applied.slash.charged.client;

import com.applied.slash.charged.BladeEnergy;
import com.applied.slash.charged.block.BladeChargerBlockEntity;
import com.applied.slash.charged.block.BladeChargerMenu;
import com.applied.slash.charged.block.BladeChargerRegistry;
import com.applied.slash.charged.block.ChargerMath;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 充能方块的界面 —— <b>纯自绘,不用任何 GUI 贴图</b>。
 *
 * <p>布局(PLAN §6.3):
 * <pre>
 * ┌─────────────────────────────┐
 * │  拔刀剑充能器                │  container.applied_slash.blade_charger
 * │  [刀槽]   ▮▮▮▮▮▯▯▯▯▯ 132/200 │  guiGraphics.fill 画 8×52 能量条 + 2px 边框
 * │  状态:充能中(100 AE/点)     │  4 态:充能中 / 已满 / 缺电 / 未放刀
 * │  ── 玩家背包 ──              │
 * └─────────────────────────────┘
 * </pre>
 *
 * <p><b>能量条画的是「槽里那把刀」的能量</b>(不是本机的 AE 缓冲):这与玩家的直觉一致
 * (放刀进去看它涨),也与物品栏里的能量条同口径(物品条宽走 {@code ChargerMath#barWidth},
 * 界面的 52px 条走本类的 {@code filledHeight},阈值配色两边一致)。
 * 客户端能读到刀的组件:槽内容由原版菜单机制整栈同步(含数据组件)。
 *
 * <p>为什么不用贴图:{@code verifyResources} 要求本模组每个 PNG 都是 16×16,
 * 一张 176×166 的 GUI 背景会直接让自检失败(PLAN §6.3 的风险 R7)。
 *
 * <p>本类只在客户端加载 —— 唯一调用点是 {@code AppliedSlashClient} 的
 * {@code RegisterMenuScreensEvent} 转发。
 */
public class BladeChargerScreen extends AbstractContainerScreen<BladeChargerMenu> {

    // ---- 配色(全部 ARGB;纯自绘,不依赖任何贴图) ----
    private static final int PANEL_BG = 0xF01A1A22;
    private static final int PANEL_BORDER = 0xFF4A4A5A;
    private static final int SLOT_BG = 0xFF0C0C10;
    private static final int SLOT_BORDER = 0xFF3A3A48;
    private static final int BAR_BORDER = 0xFF6A6A80;
    private static final int BAR_EMPTY = 0xFF23232C;
    private static final int BAR_LOW = 0xFFE04B4B;
    private static final int BAR_MID = 0xFFF0C24B;
    private static final int BAR_HIGH = 0xFF3FE08A;
    private static final int TEXT_MAIN = 0xFFE8E8F0;
    private static final int TEXT_DIM = 0xFF9A9AA8;

    /** 能量条:8×52 的填充区 + 四周各 2px 边框。 */
    private static final int BAR_X = 100;
    private static final int BAR_Y = 17;
    private static final int BAR_WIDTH = 8;
    private static final int BAR_HEIGHT = 52;
    private static final int BAR_BORDER_WIDTH = 2;

    /** 文字区(避开刀槽与能量条)。 */
    private static final int TEXT_X = 50;
    private static final int TEXT_ENERGY_Y = 20;
    private static final int TEXT_STATUS_Y = 34;

    /** 刀槽的底衬(与 {@code BladeChargerMenu} 里那个槽的坐标一致:26,35)。 */
    private static final int SLOT_BLADE_X = 26;
    private static final int SLOT_BLADE_Y = 35;
    private static final int SLOT_SIZE = 16;

    /** 语言键(与 lang 文件一致,PLAN §9.3)。 */
    // 能量读数用的是充能器自己的键(5 把充能刀删除后,刀身上那套 tooltip 键也随之删除)
    private static final String KEY_ENERGY = "applied_slash.blade_charger.value.energy";
    private static final String KEY_STATUS_IDLE = "applied_slash.blade_charger.status.idle";
    private static final String KEY_STATUS_CHARGING = "applied_slash.blade_charger.status.charging";
    private static final String KEY_STATUS_FULL = "applied_slash.blade_charger.status.full";
    private static final String KEY_STATUS_NO_AE = "applied_slash.blade_charger.status.no_ae";

    public BladeChargerScreen(BladeChargerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * 转发目标(照 {@code AppliedSlashClient.registerItemColors} 的转发模式):
     * 由客户端入口在 AE2 门卫内调用,避免服务端类加载到客户端类。
     *
     * <p>签名已由事实表 §3 实证:
     * {@code RegisterMenuScreensEvent#register(MenuType<? extends M>, MenuScreens$ScreenConstructor<M, U>)}
     * —— 方法引用 {@code BladeChargerScreen::new} 正是那个构造器形状(界面继承
     * {@code MenuAccess<M>},满足 {@code U extends Screen & MenuAccess<M>})。
     */
    public static void register(RegisterMenuScreensEvent event) {
        event.register(BladeChargerRegistry.BLADE_CHARGER_MENU.get(), BladeChargerScreen::new);
    }

    /**
     * 自绘背景面板 / 刀槽底衬 / 能量条 / 状态行。
     *
     * <p>只画在 {@code renderBg} 里:原版会在它之后画标题与背包标签
     * ({@code renderLabels}),所以标题不会被我们的面板盖住。
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        // 面板 + 外框(1px)
        guiGraphics.fill(left, top, left + this.imageWidth, top + this.imageHeight, PANEL_BG);
        guiGraphics.fill(left, top, left + this.imageWidth, top + 1, PANEL_BORDER);
        guiGraphics.fill(left, top + this.imageHeight - 1, left + this.imageWidth, top + this.imageHeight, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, top + this.imageHeight, PANEL_BORDER);
        guiGraphics.fill(left + this.imageWidth - 1, top, left + this.imageWidth, top + this.imageHeight, PANEL_BORDER);

        // 刀槽底衬(槽本身由原版槽位渲染画在上面)
        guiGraphics.fill(left + SLOT_BLADE_X - 1, top + SLOT_BLADE_Y - 1,
                left + SLOT_BLADE_X + SLOT_SIZE + 1, top + SLOT_BLADE_Y + SLOT_SIZE + 1, SLOT_BG);
        guiGraphics.fill(left + SLOT_BLADE_X - 1, top + SLOT_BLADE_Y - 1,
                left + SLOT_BLADE_X + SLOT_SIZE + 1, top + SLOT_BLADE_Y, SLOT_BORDER);
        guiGraphics.fill(left + SLOT_BLADE_X - 1, top + SLOT_BLADE_Y + SLOT_SIZE,
                left + SLOT_BLADE_X + SLOT_SIZE + 1, top + SLOT_BLADE_Y + SLOT_SIZE + 1, SLOT_BORDER);

        ItemStack blade = this.menu.getSlot(BladeChargerMenu.SLOT_BLADE).getItem();
        int energy = BladeEnergy.get(blade);
        int max = BladeEnergy.max(blade);

        // 能量条:先边框、再空槽、最后按比例从底部往上填
        int barLeft = left + BAR_X;
        int barTop = top + BAR_Y;
        int barRight = barLeft + BAR_WIDTH;
        int barBottom = barTop + BAR_HEIGHT;
        guiGraphics.fill(barLeft - BAR_BORDER_WIDTH, barTop - BAR_BORDER_WIDTH,
                barRight + BAR_BORDER_WIDTH, barBottom + BAR_BORDER_WIDTH, BAR_BORDER);
        guiGraphics.fill(barLeft, barTop, barRight, barBottom, BAR_EMPTY);
        int filled = filledHeight(energy, max);
        if (filled > 0) {
            guiGraphics.fill(barLeft, barBottom - filled, barRight, barBottom, barColor(energy, max));
        }

        // 文字:能量行 + 状态行
        if (!blade.isEmpty()) {
            guiGraphics.drawString(this.font, Component.translatable(KEY_ENERGY, energy, max),
                    left + TEXT_X, top + TEXT_ENERGY_Y, TEXT_MAIN);
        }
        guiGraphics.drawString(this.font, statusLine(), left + TEXT_X, top + TEXT_STATUS_Y, TEXT_DIM);
    }

    /** 状态行的 4 态文案(取自方块实体同步过来的 {@code ContainerData})。 */
    private Component statusLine() {
        int state = this.menu.getData().get(BladeChargerBlockEntity.DATA_STATE);
        int aePerPoint = this.menu.getData().get(BladeChargerBlockEntity.DATA_AE_PER_POINT);
        return switch (state) {
            case ChargerMath.STATE_CHARGING -> Component.translatable(KEY_STATUS_CHARGING, aePerPoint);
            case ChargerMath.STATE_FULL -> Component.translatable(KEY_STATUS_FULL);
            case ChargerMath.STATE_NO_AE -> Component.translatable(KEY_STATUS_NO_AE);
            default -> Component.translatable(KEY_STATUS_IDLE);
        };
    }

    /** 填充高度(像素):{@code BAR_HEIGHT * energy / max},clamp 到 [0, BAR_HEIGHT]。 */
    private static int filledHeight(int energy, int max) {
        if (max <= 0 || energy <= 0) {
            return 0;
        }
        int filled = (int) Math.round(BAR_HEIGHT * (double) Math.min(energy, max) / (double) max);
        return Math.max(0, Math.min(BAR_HEIGHT, filled));
    }

    /** 按比例上色(与物品能量条同一套阈值)。 */
    private static int barColor(int energy, int max) {
        if (max <= 0) {
            return BAR_LOW;
        }
        float ratio = (float) energy / (float) max;
        if (ratio >= 0.5F) {
            return BAR_HIGH;
        }
        if (ratio >= 0.2F) {
            return BAR_MID;
        }
        return BAR_LOW;
    }
}
