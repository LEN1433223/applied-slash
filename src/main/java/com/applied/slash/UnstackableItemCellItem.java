package com.applied.slash;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 「不可堆叠物品存储元件」的物品本体 —— 拔刀剑存储元件的**平行实现**(按用户要求另写一份,不重构现有元件)。
 *
 * <p>与 {@link SlashBladeCellItem} 的关系:
 * <ul>
 *   <li><b>共用同一对物品组件</b>({@link AppliedSlashComponents#VAULT_ID} / {@link AppliedSlashComponents#VAULT_SUMMARY})
 *       与同一个世界侧分片存储({@code com.applied.slash.vault.*},键是 {@code AEItemKey},与拔刀剑无关);</li>
 *   <li>摘要的 NBT 键名刻意保持与 {@link SlashBladeCellItem} **完全一致**(types/count/max/missing),
 *       这样两个元件类的读法互通,任一元件换用另一个类来读摘要都拿得到同样的数字;</li>
 *   <li>差别只在**语义**:接受范围、类型上限的配置项、以及最重要的"绝不规范化"。</li>
 * </ul>
 *
 * <p>和拔刀剑元件一样,它**刻意不实现任何 AE2 物品接口**(尤其不实现 {@code IBasicCellItem}):
 * 一旦实现就会被 AE2 自带的 {@code BasicCellHandler} 抢先接管,类型数被钳回 63(见 {@code cell.UnstackableCellHandler})。
 */
public class UnstackableItemCellItem extends Item {
    /** 与 {@link SlashBladeCellItem} 保持一致的摘要键名(两边可互读,见类注释)。 */
    private static final String TAG_TYPES = "types";
    private static final String TAG_COUNT = "count";
    private static final String TAG_MAX = "max";
    private static final String TAG_MISSING = "missing";

    public UnstackableItemCellItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // 容量行:第一个数是**类型数**(不同组件的物品算不同类型 —— 本元件不剥离任何组件),
        // 第三个数是**总件数**。与拔刀剑元件的文案区分成"种/件"而不是"种/把"。
        tooltip.add(Component.translatable("item.applied_slash.unstackable_item_cell.tooltip.usage",
                        summaryTypes(stack), summaryMax(stack), summaryCount(stack))
                .withStyle(ChatFormatting.GRAY));
        if (summaryMissing(stack)) {
            tooltip.add(Component.translatable("item.applied_slash.unstackable_item_cell.tooltip.missing")
                    .withStyle(ChatFormatting.RED));
        }
        // 刻意没有"未检测到拔刀剑"那一行:本元件不依赖 SlashBlade,
        // 拔刀剑缺席时它只是少排除一类物品(拔刀剑判定会被 SlashBladeBlades 自行短路),照常可用。
    }

    /**
     * 首次使用时分配存储标识。守卫条件与 {@link SlashBladeCellItem#assignVaultId} 一致
     * (服务端存在 <b>且当前就是服务端线程</b>):单机下客户端渲染线程也能拿到非 null 的
     * {@code MinecraftServer},只判"server != null"会让客户端那份物品栈写下与服务端不一致的随机 UUID。
     */
    public static UUID assignVaultId(ItemStack stack) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return null;
        }
        UUID id = UUID.randomUUID();
        stack.set(AppliedSlashComponents.VAULT_ID.get(), id);
        return id;
    }

    public static int summaryTypes(ItemStack stack) {
        CompoundTag tag = stack.get(AppliedSlashComponents.VAULT_SUMMARY.get());
        return tag == null ? 0 : tag.getInt(TAG_TYPES);
    }

    public static long summaryCount(ItemStack stack) {
        CompoundTag tag = stack.get(AppliedSlashComponents.VAULT_SUMMARY.get());
        return tag == null ? 0L : tag.getLong(TAG_COUNT);
    }

    public static boolean summaryMissing(ItemStack stack) {
        CompoundTag tag = stack.get(AppliedSlashComponents.VAULT_SUMMARY.get());
        return tag != null && tag.getBoolean(TAG_MISSING);
    }

    /**
     * 该盘当前生效的类型上限。由服务端写入摘要,所以客户端看到的是**服务端真实配置值**;
     * 摘要还没写过时回落到本元件自己的配置项 {@link AppliedSlashConfig#unstackableMaxTypes()}
     * —— 刻意不回落到 {@link BladeCellSpec#MAX_TYPES}(绝对上限 20000),否则新盘会显示
     * "0 / 20000 种"而实际只收 2000,等于向玩家撒谎。也刻意不回落到拔刀剑的
     * {@code maxTypes}:两个元件的容量是独立配置的。
     */
    public static int summaryMax(ItemStack stack) {
        CompoundTag tag = stack.get(AppliedSlashComponents.VAULT_SUMMARY.get());
        if (tag == null || !tag.contains(TAG_MAX)) {
            return AppliedSlashConfig.unstackableMaxTypes();
        }
        return tag.getInt(TAG_MAX);
    }

    /** 写摘要:内容未变化时直接返回,避免每次存取都产生一次组件写入(与拔刀剑元件同样的取舍)。 */
    public static void writeSummary(ItemStack stack, int types, long count, int max, boolean missing) {
        if (summaryTypes(stack) == types && summaryCount(stack) == count
                && summaryMax(stack) == max && summaryMissing(stack) == missing) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_TYPES, types);
        tag.putLong(TAG_COUNT, count);
        tag.putInt(TAG_MAX, max);
        tag.putBoolean(TAG_MISSING, missing);
        stack.set(AppliedSlashComponents.VAULT_SUMMARY.get(), tag);
    }
}
