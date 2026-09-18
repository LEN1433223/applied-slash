package com.applied.slash.portable;

import com.applied.slash.AppliedSlash;

import appeng.items.tools.powered.AbstractPortableCell;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.menu.me.common.MEStorageMenu;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;

/**
 * 手持「Slash 元件」——**继承 AE2 的便携元件**(分支 A)。
 *
 * <p>复用策略(javap 实证):
 * <ul>
 *   <li>菜单类型传 AE2 自己的 {@code MEStorageMenu.PORTABLE_ITEM_CELL_TYPE}
 *       (AE2 的便携物品元件用的就是它)⇒ **整套便携界面(菜单/屏幕/客户端注册)直接复用**;</li>
 *   <li>供电由基类提供({@code AEConfig.getPortableCellBattery()}),玩家在 AE2 充电器里充电;</li>
 *   <li>库存不走 AE2 的 {@code BasicCellInventory},而是由 {@link PortableSlashCellHandler}
 *       注册进 {@code StorageCells},界面的菜单宿主取到的就是我们的 8 格库存。</li>
 * </ul>
 *
 * <p>刻意**不实现** {@code IBasicCellItem}:否则会被 AE2 自带的 {@code BasicCellHandler} 抢走
 * (与驱动器元件同一个坑,见 {@code SlashBladeCellHandler} 的说明)。
 */
public class PortableSlashCellItem extends AbstractPortableCell implements SlashCellLike {
    /** 便携界面配色:藕紫。 */
    private static final int DEFAULT_COLOR = 0xC9A7B8;
    /** 充电速率(AE/t)。 */
    private static final double CHARGE_RATE = 200.0D;

    public PortableSlashCellItem(Item.Properties properties) {
        super(MEStorageMenu.PORTABLE_ITEM_CELL_TYPE, properties, DEFAULT_COLOR);
    }

    @Override
    public ResourceLocation getRecipeId() {
        // 便携元件没有拆解配方;返回自身 id 即可(AE2 仅在拆解时用)
        return ResourceLocation.fromNamespaceAndPath(AppliedSlash.MODID, "portable_slash_cell");
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return CHARGE_RATE;
    }

    /**
     * 悬停信息:容量用量 + 机制说明。
     *
     * <p>用量取自物品组件(内容是同步的,所以客户端也能正确显示),不需要访问任何世界侧数据。
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int used = SlashCellAccess.bladesIn(stack).size();
        tooltip.add(Component.translatable("item.applied_slash.portable_slash_cell.tooltip.usage",
                used, SlashCellAccess.CAPACITY).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.applied_slash.portable_slash_cell.tooltip.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.applied_slash.portable_slash_cell.tooltip.exclusive")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 元件工作台的分区模式。
     *
     * <p>本元件按"条目"存刀、没有分区语义,但 AE2 的 {@code ICellWorkbenchItem} 要求实现它
     * (AE2 自家便携元件也是把值写进 {@code AEComponents.STORAGE_CELL_FUZZY_MODE} 组件)。
     * 这里照做,以便 AE2 的界面能正常读写该设置。
     */
    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        stack.set(AEComponents.STORAGE_CELL_FUZZY_MODE, mode);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        FuzzyMode mode = stack.get(AEComponents.STORAGE_CELL_FUZZY_MODE);
        return mode == null ? FuzzyMode.IGNORE_ALL : mode;
    }
}