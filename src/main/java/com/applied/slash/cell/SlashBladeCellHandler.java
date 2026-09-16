package com.applied.slash.cell;

import com.applied.slash.SlashBladeCellItem;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;

/**
 * 把本模组的元件物品接到 AE2 的存储单元体系上。
 *
 * <p>注册依据({@code javap} 实证):AE2 的驱动器槽位放行条件是
 * {@code StorageCells.isCellHandled(stack)}(见 {@code DriveBlockEntity$CellValidInventoryFilter.allowInsert}),
 * 而 {@code StorageCells.getHandler} 按注册顺序返回第一个 {@code isCell} 命中的 handler。
 *
 * <p><b>关键约束</b>:AE2 自带的 {@code BasicCellHandler} 抢先判定
 * {@code getItem() instanceof IBasicCellItem},所以本模组的元件物品**绝不能实现 IBasicCellItem**
 * ——否则会被原生 handler 抢走,类型数又被钳回 63。
 */
public final class SlashBladeCellHandler implements ICellHandler {
    public static final SlashBladeCellHandler INSTANCE = new SlashBladeCellHandler();

    private SlashBladeCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SlashBladeCellItem;
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider container) {
        if (!isCell(stack)) {
            return null;
        }
        return new SlashBladeCellInventory(stack, container);
    }
}
