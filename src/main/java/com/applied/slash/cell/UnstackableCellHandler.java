package com.applied.slash.cell;

import com.applied.slash.UnstackableItemCellItem;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;

/**
 * 把「不可堆叠物品存储元件」接到 AE2 的存储单元体系上 —— {@link SlashBladeCellHandler} 的平行实现。
 *
 * <p>注册依据同拔刀剑元件(字节码实证,见 PLAN §1 R2):驱动器的槽位放行条件是
 * {@code StorageCells.isCellHandled(stack)},而 {@code StorageCells.getHandler} 按注册顺序返回
 * 第一个 {@code isCell} 命中的 handler。两个 handler 的 {@code isCell} 判定互斥(各自认自己的物品类),
 * 所以注册顺序对它们没有影响。
 *
 * <p><b>关键约束</b>(同前):AE2 自带的 {@code BasicCellHandler} 抢先判定
 * {@code getItem() instanceof IBasicCellItem},所以本模组的元件物品**绝不能实现 IBasicCellItem**
 * ——否则会被原生 handler 抢走,类型数又被钳回 63。
 */
public final class UnstackableCellHandler implements ICellHandler {
    public static final UnstackableCellHandler INSTANCE = new UnstackableCellHandler();

    private UnstackableCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof UnstackableItemCellItem;
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider container) {
        if (!isCell(stack)) {
            return null;
        }
        return new UnstackableCellInventory(stack, container);
    }
}
