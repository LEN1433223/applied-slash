package com.applied.slash.cell;

import java.util.UUID;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.BladeCellSpec;
import com.applied.slash.UnstackableItemCellItem;
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
 * 「不可堆叠物品存储元件」的库存实现 —— {@link SlashBladeCellInventory} 的**平行实现**
 * (用户明确要求另写一份,不重构现有元件)。
 *
 * <p>自研而非继承 {@code BasicCellInventory} 的原因同拔刀剑元件:AE2 的基类在构造器里把类型数
 * 硬钳到 63,且每次落盘都整表写物品组件。本类同样不受该钳制,落盘只写一份几十字节的摘要。
 *
 * <p>与拔刀剑元件的四处差异:
 * <ol>
 *   <li><b>接受范围</b>:{@link UnstackableIdentity#isAccepted} —— 不可堆叠、非拔刀剑、非存储元件类;</li>
 *   <li><b>绝不规范化</b>:入库/出库全程直接用原键,不拷贝、不剥离任何组件(工具耐久/附魔/名字即身份);</li>
 *   <li><b>独立类型上限</b>:{@link AppliedSlashConfig#unstackableMaxTypes()}(默认 2000);</li>
 *   <li>刻意**不**复查 {@link AppliedSlashConfig#acceptBeyondMaxTypes()}:那个开关的语义是
 *       "拔刀剑一律不落别处,即使超限也收",与不可堆叠物品无关,本元件超限即拒收(让网络把物品
 *       放到别的盘或留在原处)。</li>
 * </ol>
 *
 * <p>复用而不重写的部分:世界侧分片存储 {@link BladeVaultStore} + {@link VaultContents}
 * (键是 {@code AEItemKey}、按 UUID 分片,与拔刀剑无关),以及物品组件
 * {@link AppliedSlashComponents#VAULT_ID} / {@link AppliedSlashComponents#VAULT_SUMMARY}。
 */
public final class UnstackableCellInventory implements StorageCell {
    private final ItemStack cellStack;
    private final ISaveProvider container;

    private UUID vaultId;
    private VaultContents contents;
    private boolean resolved;

    public UnstackableCellInventory(ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.vaultId = cellStack.get(AppliedSlashComponents.VAULT_ID.get());
    }

    /**
     * 惰性解析世界侧存储。客户端(取不到服务端)返回 {@code null},调用方据此退化为只读空盘,
     * 绝不在客户端伪造内容 —— 与拔刀剑元件完全一致。
     */
    private VaultContents contents() {
        if (!resolved) {
            if (vaultId == null) {
                vaultId = UnstackableItemCellItem.assignVaultId(cellStack);
            }
            VaultContents resolvedContents = BladeVaultStore.get(vaultId);
            // 只有真正拿到库才锁定,否则(客户端、存档尚未就绪)本实例会退化为永久空盘
            if (resolvedContents != null) {
                contents = resolvedContents;
                resolved = true;
            }
        }
        return contents;
    }

    /**
     * 本元件接受的键**无条件**先问本盘。
     *
     * <p>依据与拔刀剑元件相同(字节码实证,见 PLAN §1 R1):{@code NetworkStorage.insert} 是两遍扫描,
     * 第一遍只插 {@code isPreferredStorageFor(...) == true} 的存储、**完全不看优先级**;驱动器里的单元被
     * {@code DriveWatcher}(继承 {@code MEInventoryHandler})包装,而它把这个调用透传给被包装的库存。
     * 所以只要这里返回 true,任何全局优先级更高的普通盘都插不到前面来。
     *
     * <p>为什么可以无条件返回 true:真正收不下时的拒绝由 {@link #insert} 负责(类型已满且是新键时返回 0),
     * 那时 AE2 会走第二遍扫描把物品放进别的盘 —— 也就是"超限不淘汰、不吞物品"。
     * 唯一代价是满盘时多一次注定返回 0 的调用(纳秒级)。
     */
    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return UnstackableIdentity.isAccepted(key);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(key instanceof AEItemKey itemKey) || !UnstackableIdentity.isAccepted(itemKey)) {
            return 0L;
        }
        VaultContents vault = contents();
        if (vault == null) {
            return 0L;
        }

        // 注意:这里**没有**规范化步骤(拔刀剑元件有)。本元件的键就是物品栈本身,
        // 带不同组件的同一物品算不同键 —— 这是刻意的,见 UnstackableIdentity 的说明。
        long existing = vault.amountOf(itemKey);
        if (existing <= 0 && !vault.hasRoomForNewType(AppliedSlashConfig.unstackableMaxTypes())) {
            return 0L;
        }

        long room = BladeCellSpec.MAX_AMOUNT_PER_TYPE - existing;
        if (room <= 0) {
            return 0L;
        }
        long accepted = Math.min(amount, room);
        if (mode == Actionable.MODULATE && accepted > 0) {
            vault.add(itemKey, accepted);
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

        // 不做规范化,也不复查谓词:已经在库里的键必须能原样取出
        // (谓词将来若变化,也绝不能把已经存进去的东西变成取不出来的死数据)。
        long stored = vault.amountOf(itemKey);
        if (stored <= 0) {
            return 0L;
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
            return vault.hasRoomForNewType(AppliedSlashConfig.unstackableMaxTypes())
                    ? CellState.NOT_EMPTY : CellState.TYPES_FULL;
        }
        // 客户端/世界不可用:按物品摘要推断,不谎报"空"
        return summaryState();
    }

    /** 复用拔刀剑元件的空闲耗电常量(AE/t);两盘的耗电策略一致。 */
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
        return Component.translatable("item.applied_slash.unstackable_item_cell");
    }

    /** 变更后:让驱动器标脏,并刷新物品上的摘要组件(理由同拔刀剑元件:客户端读不到世界侧数据)。 */
    private void afterChange(VaultContents vault) {
        writeSummary(vault);
        if (container != null) {
            container.saveChanges();
        }
    }

    private void writeSummary(VaultContents vault) {
        // 载荷丢失 = 物品上还记着有内容,但世界侧库里已经查不到(跨存档复制、存档回滚)
        boolean missing = vault.typeCount() == 0 && UnstackableItemCellItem.summaryCount(cellStack) > 0;
        UnstackableItemCellItem.writeSummary(cellStack, vault.typeCount(), vault.count(),
                AppliedSlashConfig.unstackableMaxTypes(), missing);
    }

    private CellState summaryState() {
        int types = UnstackableItemCellItem.summaryTypes(cellStack);
        long count = UnstackableItemCellItem.summaryCount(cellStack);
        if (count <= 0) {
            return CellState.EMPTY;
        }
        return types >= UnstackableItemCellItem.summaryMax(cellStack) ? CellState.TYPES_FULL : CellState.NOT_EMPTY;
    }
}
