package com.applied.slash.cell;

import java.util.UUID;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.BladeCellSpec;
import com.applied.slash.SlashBladeCellItem;
import com.applied.slash.vault.BladeVaultStore;
import com.applied.slash.vault.VaultContents;

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
 * 拔刀剑专用存储元件的库存实现(自研,不走 AE2 的 {@code BasicCellInventory})。
 *
 * <p><b>为什么自研</b>:AE2 的 {@code BasicCellInventory} 在构造器里把类型数硬钳到 63,
 * 上万把刀的需求无法通过继承实现;而且它每次落盘都会把完整键表写进物品组件。
 * 自研后我们同时拿到三件事:类型上限自定、写入路径可控、持久化格式自定。
 *
 * <p><b>优先入库的实现依据</b>:{@code NetworkStorage.insert} 是两遍扫描,第一遍只插
 * {@code isPreferredStorageFor(...) == true} 的存储;驱动器里的单元被 {@code DriveWatcher}
 * (继承 {@code MEInventoryHandler})包装,而它会把这个调用**透传**给被包装的库存。
 * 因此在这里对拔刀剑返回 true,刀就会优先落进本盘。
 *
 * <p><b>性能约定</b>:
 * <ul>
 *   <li>{@code getAvailableStacks} 每 tick 会被 AE2 的全网缓存重建调用 → 只做裸遍历;</li>
 *   <li>插入/取出不序列化任何东西,只改内存表 + 标脏;</li>
 *   <li>{@code persist()} 只写一份几十字节的摘要组件,与存量无关(O(1))。</li>
 * </ul>
 */
public final class SlashBladeCellInventory implements StorageCell {
    private final ItemStack cellStack;
    private final ISaveProvider container;

    private UUID vaultId;
    private VaultContents contents;
    private boolean resolved;

    public SlashBladeCellInventory(ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.vaultId = cellStack.get(AppliedSlashComponents.VAULT_ID.get());
    }

    /**
     * 惰性解析世界侧刀库。客户端(取不到服务端)返回 {@code null},调用方据此退化为只读空盘,
     * 绝不在客户端伪造内容。
     */
    private VaultContents contents() {
        if (!resolved) {
            if (vaultId == null) {
                vaultId = SlashBladeCellItem.assignVaultId(cellStack);
            }
            VaultContents resolvedContents = BladeVaultStore.get(vaultId);
            // 只有真正拿到刀库才锁定:否则(客户端、存档尚未就绪)本实例会退化为永久空盘
            if (resolvedContents != null) {
                contents = resolvedContents;
                resolved = true;
            }
        }
        return contents;
    }

    /**
     * 拔刀剑**永远先问本盘**。
     *
     * <p>依据(字节码实证):{@code NetworkStorage.insert} 的第一遍扫描只检查
     * {@code isPreferredStorageFor(...)},命中就直接插入 —— 这一遍**完全不看优先级**;
     * 只有落选者才进入按优先级排序的第二遍。而 AE2 自带的 {@code BasicCellInventory}
     * **不覆盖**这个方法(走默认 false),所以只要这里返回 true,任何全局优先级更高的普通盘
     * 都插不到前面来。
     *
     * <p>为什么可以无条件返回 true(而不是"有把握收下才承认"):真正收不下时的拒绝由
     * {@link #insert} 负责 —— 它在"原始键不在、规范化后仍不在、且类型已满"时才拒收;
     * 规范化后能命中已有条目的刀**即使类型已满也会被收下**,所以这里不会制造
     * "同一把刀被别的盘收走、规范化副本却留在本盘"的语义裂缝。
     * 唯一代价是满盘时多一次注定返回 0 的调用(纳秒级)。
     *
     * <p>需要留意的竞争者:AE2 的**创造存储元件**也覆盖了本方法;网络里装着创造元件时,
     * 第一遍的遍历顺序决定谁先拿到。普通存储元件不会(它们不覆盖)。
     */
    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return key instanceof AEItemKey itemKey && BladeIdentity.isBlade(itemKey);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(key instanceof AEItemKey itemKey) || !BladeIdentity.isBlade(itemKey)) {
            return 0L;
        }
        VaultContents vault = contents();
        if (vault == null) {
            return 0L;
        }

        AEItemKey target = itemKey;
        long existing = vault.amountOf(itemKey);
        if (existing <= 0 && BladeIdentity.mayNormalize(itemKey)) {
            // 首次见到这把刀:规范化(剥离运行时状态)后再查一次。
            // mayNormalize 便宜检查在前,绝大多数插入(已有键/无易变组件)不走拷贝。
            target = BladeIdentity.normalize(itemKey);
            existing = vault.amountOf(target);
        }
        if (existing <= 0 && !vault.hasRoomForNewType(AppliedSlashConfig.maxTypes())
                && !AppliedSlashConfig.acceptBeyondMaxTypes()) {
            // 超限拒收(默认)。开启 acceptBeyondMaxTypes 后一律收下:
            // 因为第一遍扫描本盘永远优先,拔刀剑从此不会再进其它盘,代价是类型数无上限。
            return 0L;
        }

        long room = BladeCellSpec.MAX_AMOUNT_PER_TYPE - existing;
        if (room <= 0) {
            return 0L;
        }
        long accepted = Math.min(amount, room);
        if (mode == Actionable.MODULATE && accepted > 0) {
            vault.add(target, accepted);
            afterChange(vault);
        }
        return accepted;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(key instanceof AEItemKey itemKey)) {
            return 0L;
        }
        VaultContents vault = contents();
        if (vault == null) {
            return 0L;
        }

        long stored = vault.amountOf(itemKey);
        if (stored <= 0) {
            // 规范化兜底 —— **SIMULATE 也必须走这一步**。
            //
            // 踩坑记录:原先这里写的是 `if (mode != Actionable.MODULATE || !mayNormalize) return 0L;`,
            // 也就是只在真正取出时才规范化、模拟探测直接返回 0。而 AE2 的取出/合成/导出链路
            // **第一步几乎总是 SIMULATE 探测**,一旦这里返回 0,它就判定"网络里没有这件东西",
            // 于是根本不会调用第二步 —— 表现为:**终端里看得见这把刀,却取不出来**。
            // 触发条件:发起方手里的键带着运行时状态(BLADE_RUNTIME_STATE),与库里的规范化键不同形
            // (拿一把用过的刀写样板、或用它去合成/导出时必然如此)。
            //
            // 代价:mayNormalize 是便宜检查(只是 has(组件)),不含运行时状态的键一步就返回,不付任何拷贝成本。
            if (!BladeIdentity.mayNormalize(itemKey)) {
                return 0L;
            }
            AEItemKey normalized = BladeIdentity.normalize(itemKey);
            if (normalized.equals(itemKey)) {
                return 0L;
            }
            if (mode != Actionable.MODULATE) {
                // 模拟探测:按规范化后的键如实回答"能取出多少",让 AE2 继续走到真正的取出
                return Math.min(amount, vault.amountOf(normalized));
            }
            long extracted = vault.remove(normalized, amount);
            if (extracted > 0) {
                afterChange(vault);
            }
            return extracted;
        }

        if (mode != Actionable.MODULATE) {
            return Math.min(amount, stored);
        }
        long extracted = vault.remove(itemKey, amount);
        if (extracted > 0) {
            afterChange(vault);
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        VaultContents vault = contents();
        if (vault != null) {
            vault.forEach(out);
        }
    }

    @Override
    public CellState getStatus() {
        VaultContents vault = contents();
        if (vault != null) {
            if (vault.count() == 0) {
                return CellState.EMPTY;
            }
            return vault.hasRoomForNewType(AppliedSlashConfig.maxTypes()) ? CellState.NOT_EMPTY : CellState.TYPES_FULL;
        }
        // 客户端/世界不可用:按物品摘要推断,不谎报"空"
        return summaryState();
    }

    @Override
    public double getIdleDrain() {
        return BladeCellSpec.IDLE_DRAIN;
    }

    @Override
    public void persist() {
        VaultContents vault = contents();
        if (vault != null) {
            writeSummary(vault);
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("item.applied_slash.slash_blade_cell");
    }

    /**
     * 变更后:让驱动器标脏,并按需刷新物品上的摘要组件。
     *
     * <p><b>为什么这里也写摘要(而不是只在 {@code persist()} 里写)</b>:
     * 客户端拿不到世界侧刀库,元件 tooltip 与 LED 染色都只能读物品上的摘要;
     * 若像 AE2 原生单元那样"只在 persist 时写组件",客户端显示会滞后到下一次驱动器保存。
     * 实测该写在插入路径里只占 ~1 µs(整条插入 5–7 µs),且 {@code writeSummary} 在内容未变时直接返回,
     * 因此这里选择"每次变更即刷新",换取客户端显示实时准确。
     */
    private void afterChange(VaultContents vault) {
        writeSummary(vault);
        if (container != null) {
            container.saveChanges();
        }
    }

    private void writeSummary(VaultContents vault) {
        // 载荷丢失 = 物品上还记着有内容,但世界侧刀库里已经查不到(跨存档复制、存档回滚)
        boolean missing = vault.typeCount() == 0 && SlashBladeCellItem.summaryCount(cellStack) > 0;
        SlashBladeCellItem.writeSummary(cellStack, vault.typeCount(), vault.count(),
                AppliedSlashConfig.maxTypes(), missing);
    }

    private CellState summaryState() {
        int types = SlashBladeCellItem.summaryTypes(cellStack);
        long count = SlashBladeCellItem.summaryCount(cellStack);
        if (count <= 0) {
            return CellState.EMPTY;
        }
        return types >= SlashBladeCellItem.summaryMax(cellStack) ? CellState.TYPES_FULL : CellState.NOT_EMPTY;
    }
}
