package com.applied.slash.portable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.applied.slash.AppliedSlash;
import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.SlashBladeBlades;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 手持「Slash 元件」的库存实现:**最多 8 把拔刀剑,同 NBT 也不合并**。
 *
 * <p><b>为什么需要唯一标记</b>:AE2 的存储键是 {@code AEItemKey}(物品 + 组件)。两把 NBT 完全相同的刀
 * 会得到**同一个键**并被 AE2 合并成一格 —— 与"8 把、不堆叠"的需求冲突。
 * 做法:入库时给每把刀打一个隐藏的唯一标记({@link AppliedSlashComponents#PORTABLE_ENTRY_ID}),
 * 使它们的键彼此不同;对外汇报与取出时把标记剥掉,玩家看到的仍是普通拔刀剑。
 *
 * <p><b>容量口径</b>:按**条目数**(8),不是按堆叠数 —— 8 个条目各自占一格,取出/插入以 1 把为单位。
 *
 * <p><b>持久化:立即写,不做延迟</b>。这是踩过严重 bug 后的结论 ——
 * AE2 的便携界面每次取库存都会重新 {@code StorageCells.getCellInventory(stack, saveProvider)},
 * 而 {@code PortableCellMenuHost} 的缓存键是**物品栈对象**;手持物品每次拿到的常是不同对象
 * ⇒ 会构造出**新的库存实例**。若把插入只记在内存里、等 {@code persist()} 才写组件,
 * 新实例读到的就是旧内容 —— 玩家看到的现象正是"刀放进去就消失了"。
 * 因此这里每次 insert/extract 都**当场读写物品组件**,并调用 {@code saveChanges()} 通知宿主。
 * 便携件的内容只有 ≤ 8 条,这点写入开销可以忽略。
 */
public final class PortableSlashCellInventory implements StorageCell {
    /** 条目上限:8 把(与 {@link SlashCellAccess#CAPACITY} 同一口径)。 */
    public static final int CAPACITY = SlashCellAccess.CAPACITY;
    /** 空闲耗电(AE/t):便携件很轻。 */
    private static final double IDLE_DRAIN = 0.5D;

    private final ItemStack cellStack;
    private final ISaveProvider container;

    public PortableSlashCellInventory(ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
    }

    /**
     * 读内容:**每次都从组件现读**(不做跨调用的缓存)。
     *
     * <p>不缓存是刻意的:AE2 可能用不同的库存实例交替访问同一个元件,
     * 缓存会让其中一个实例用陈旧内容覆盖另一个的写入 —— 那就是"刀消失"的成因。
     */
    private List<ItemStack> contents() {
        List<ItemStack> stored = cellStack.get(AppliedSlashComponents.PORTABLE_SLASH_CELL_CONTENTS.get());
        return stored == null ? new ArrayList<>() : new ArrayList<>(stored);
    }

    /** 写内容:**当场落盘**,并通知宿主保存(便携件内容 ≤ 8 条,写入可忽略)。 */
    private void commit(List<ItemStack> list) {
        cellStack.set(AppliedSlashComponents.PORTABLE_SLASH_CELL_CONTENTS.get(), List.copyOf(list));
        if (container != null) {
            container.saveChanges();
        }
    }

    /** 对外可见的键:剥掉我们的隐藏标记(玩家不该看到它,AE2 的网络也该按普通刀匹配)。 */
    private static AEKey visibleKey(ItemStack stored) {
        ItemStack copy = stored.copyWithCount(1);
        copy.remove(AppliedSlashComponents.PORTABLE_ENTRY_ID.get());
        return AEItemKey.of(copy);
    }

    private static void stamp(ItemStack blade) {
        blade.set(AppliedSlashComponents.PORTABLE_ENTRY_ID.get(), UUID.randomUUID());
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(key instanceof AEItemKey itemKey)) {
            return 0;
        }
        ItemStack blade = itemKey.toStack();
        // 只收拔刀剑:非刀一律拒绝(返回 0 = 一点都没进)
        if (!SlashBladeBlades.isSlashBlade(blade)) {
            return 0;
        }
        List<ItemStack> list = contents();
        int free = CAPACITY - list.size();
        if (free <= 0) {
            return 0;
        }
        long accepted = Math.min(amount, free);
        if (mode == Actionable.SIMULATE) {
            return accepted;
        }
        for (long i = 0; i < accepted; i++) {
            ItemStack entry = blade.copyWithCount(1);
            if (entry.isEmpty()) {
                continue;
            }
            stamp(entry);            // 每把一个新标记 ⇒ 同 NBT 也不合并
            list.add(entry);
        }
        commit(list);                // **立即落盘**:别的库存实例马上就能读到
        return accepted;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) {
            return 0;
        }
        List<ItemStack> list = contents();
        long extracted = 0L;
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < list.size() && extracted < amount; i++) {
            if (visibleKey(list.get(i)).equals(key)) {
                hits.add(i);
                extracted++;
            }
        }
        if (extracted == 0 || mode == Actionable.SIMULATE) {
            return extracted;
        }
        // 从后往前删,避免下标错位;顺手把隐藏标记剥掉再交出去
        for (int j = hits.size() - 1; j >= 0; j--) {
            ItemStack taken = list.remove((int) hits.get(j));
            taken.remove(AppliedSlashComponents.PORTABLE_ENTRY_ID.get());
        }
        commit(list);
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (ItemStack stored : contents()) {
            if (stored.isEmpty()) {
                continue;
            }
            out.add(visibleKey(stored), 1L);   // 每把算 1 个
        }
    }

    @Override
    public CellState getStatus() {
        int n = contents().size();
        if (n <= 0) {
            return CellState.EMPTY;
        }
        return n >= CAPACITY ? CellState.TYPES_FULL : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return IDLE_DRAIN;
    }

    /**
     * 接口要求的方法。**我们的写入是即时的**(见 {@link #commit}),所以这里只需把当前内容再写一遍;
     * AE2 在合适时机调用它,起到"再确认一次"的作用。
     */
    @Override
    public void persist() {
        commit(contents());
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item." + AppliedSlash.MODID + ".portable_slash_cell");
    }
}