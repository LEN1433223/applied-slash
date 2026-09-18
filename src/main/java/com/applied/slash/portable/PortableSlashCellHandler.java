package com.applied.slash.portable;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;

/**
 * 把「Slash 元件」接到 AE2 的存储单元体系。
 *
 * <p>注册依据(javap 实证):便携元件的菜单宿主
 * {@code PortableCellMenuHost$CellStorageSupplier#get()} 调用的正是
 * {@code StorageCells.getCellInventory(stack, saveProvider)} ⇒ 只要注册自己的 {@code ICellHandler},
 * AE2 的便携界面就会用我们的库存。
 *
 * <p>与驱动器元件同样的约束:我们的物品**不实现 {@code IBasicCellItem}**,
 * 否则会被 AE2 自带的 {@code BasicCellHandler} 抢走。
 */
public final class PortableSlashCellHandler implements ICellHandler {
    public static final PortableSlashCellHandler INSTANCE = new PortableSlashCellHandler();

    private PortableSlashCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PortableSlashCellItem;
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider container) {
        return isCell(stack) ? new PortableSlashCellInventory(stack, container) : null;
    }
}