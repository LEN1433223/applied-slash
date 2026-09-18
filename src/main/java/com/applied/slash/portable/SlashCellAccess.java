package com.applied.slash.portable;

import java.util.List;

import com.applied.slash.AppliedSlashComponents;

import net.minecraft.world.item.ItemStack;

/**
 * 读取「Slash 元件」内容的**无 AE2 依赖**助手。
 *
 * <p>刻意不引用任何 {@code appeng.*}:SE 的加伤逻辑要用它,而那条逻辑在 AE2 缺席时也必须能加载。
 */
public final class SlashCellAccess {
    /** 元件容量:8 把(条目数)。 */
    public static final int CAPACITY = 8;

    private SlashCellAccess() {
    }

    /** 这个物品栈是不是本模组的手持元件。 */
    public static boolean isSlashCell(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SlashCellLike;
    }

    /** 元件里存着的刀(只读副本;空则返回空列表)。 */
    public static List<ItemStack> bladesIn(ItemStack cell) {
        if (!isSlashCell(cell)) {
            return List.of();
        }
        List<ItemStack> stored = cell.get(AppliedSlashComponents.PORTABLE_SLASH_CELL_CONTENTS.get());
        return stored == null ? List.of() : stored;
    }
}