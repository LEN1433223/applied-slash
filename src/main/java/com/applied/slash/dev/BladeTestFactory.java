package com.applied.slash.dev;

import com.applied.slash.AppliedSlashAe2;
import com.applied.slash.SlashBladeCellItem;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 测试元件工厂:产出"已经装了 N 把不同拔刀剑"的存储元件,用于真机压测。
 *
 * <p><b>为什么必须现场生成</b>:按设计决策,刀身数据存在世界侧 {@code SavedData}(元件物品只带 UUID),
 * 因此**无法提供一个离线的成品磁盘文件** —— 内容必须在目标存档里创建。这个工厂就是干这件事的,
 * 由 op 命令 {@code /appliedslash testcell <数量>} 调用。
 *
 * <p>生成的刀带**真实刀身数据**(刀铭/耀魂/杀敌数/精炼/基础攻击),不是只有名字的空壳 ——
 * 否则每键载荷会显著偏小,测出来的客户端同步量与 tick 成本都会偏乐观。
 */
public final class BladeTestFactory {
    private BladeTestFactory() {
    }

    /** {@code accepted} 实际接受数、{@code types}/{@code total} 为元件摘要读数。 */
    public record FillResult(ItemStack cell, int accepted, int types, long total, long elapsedNanos) {
        public double millis() {
            return elapsedNanos / 1_000_000.0;
        }
    }

    /**
     * 造一把与 index 绑定的测试刀:每把的刀身数据都不同,因此是独立的存储键。
     * SlashBlade 缺席时返回空栈。
     */
    public static ItemStack syntheticBlade(int index) {
        Item bladeItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("slashblade:slashblade"));
        if (bladeItem == null || bladeItem == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack blade = new ItemStack(bladeItem);
        // BladeStateAccess.of 返回的是组件支持的视图,setter 会写回物品本身
        BladeStateAccess.of(blade).ifPresent(state -> {
            state.setTranslationKey("applied_slash.test.blade." + index);
            state.setProudSoulCount(100 + index);
            state.setKillCount(index % 1_000);
            state.setRefine(index % 10);
            state.setBaseAttackModifier(4.0F + (index % 7));
            state.setNonEmpty();
        });
        blade.set(DataComponents.CUSTOM_NAME, Component.literal("测试刀 #" + index));
        return blade;
    }

    /** 生成一个已装入 {@code count} 把不同刀的元件(每把 1 件,数量与类型数均为 count)。 */
    public static FillResult createFilledCell(int count) {
        ItemStack cell = new ItemStack(AppliedSlashAe2.SLASH_BLADE_CELL.get());
        StorageCell inventory = StorageCells.getCellInventory(cell, null);
        if (inventory == null) {
            return new FillResult(cell, 0, 0, 0L, 0L);
        }
        IActionSource source = IActionSource.empty();
        int accepted = 0;
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            ItemStack blade = syntheticBlade(i);
            if (blade.isEmpty()) {
                break;
            }
            if (inventory.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, source) == 1L) {
                accepted++;
            }
        }
        long elapsed = System.nanoTime() - start;
        return new FillResult(cell, accepted, SlashBladeCellItem.summaryTypes(cell),
                SlashBladeCellItem.summaryCount(cell), elapsed);
    }
}
