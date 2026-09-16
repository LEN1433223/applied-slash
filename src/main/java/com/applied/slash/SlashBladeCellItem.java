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
 * SlashBlade存储元件的物品本体。
 *
 * <p>它**刻意不实现任何 AE2 物品接口**:
 * <ul>
 *   <li>实现 {@code IBasicCellItem} 会被 AE2 原生 handler 抢走并把类型数钳回 63(见 {@code SlashBladeCellHandler});</li>
 *   <li>不实现 {@code ICellWorkbenchItem} 意味着不能用元件工作台做分区 —— 对"只存拔刀剑"的盘来说分区本无意义;</li>
 *   <li>不注册升级卡白名单,因此本盘暂不支持安装升级卡(避免"装了没效果"的假功能)。</li>
 * </ul>
 *
 * <p>物品上只带两样东西:指向世界侧刀库的 {@code vault_id},以及一份几十字节的用量摘要
 * (给 tooltip 和客户端 LED 染色用,因为客户端读不到服务端刀库)。
 */
public class SlashBladeCellItem extends Item {
    private static final String TAG_TYPES = "types";
    private static final String TAG_COUNT = "count";
    private static final String TAG_MAX = "max";
    private static final String TAG_MISSING = "missing";

    public SlashBladeCellItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // 容量行:第一个数是**类型数**(不同的刀身数据算不同类型),第三个数是**总把数**。
        // 早期文案把类型数写成"...把",看起来像"存了 7 把却合计 10 把",已改为"...种"。
        tooltip.add(Component.translatable("item.applied_slash.slash_blade_cell.tooltip.usage",
                        summaryTypes(stack), summaryMax(stack), summaryCount(stack))
                .withStyle(ChatFormatting.GRAY));
        if (summaryMissing(stack)) {
            tooltip.add(Component.translatable("item.applied_slash.slash_blade_cell.tooltip.missing")
                    .withStyle(ChatFormatting.RED));
        }
        if (!AppliedSlash.isSlashBladeLoaded()) {
            tooltip.add(Component.translatable("item.applied_slash.slash_blade_cell.tooltip.no_slashblade")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 首次使用时分配刀库标识。
     *
     * <p>守卫条件必须与 {@code BladeVaultStore} 一致(服务端存在 <b>且当前就是服务端线程</b>):
     * 单机环境下客户端渲染线程同样能取到 {@code MinecraftServer},若只判"server != null",
     * 客户端会往客户端那份物品栈上写一个随机 UUID,与服务端那份不一致。
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
     * 该盘当前生效的类型上限。由服务端写入摘要,因此客户端看到的是**服务端真实配置值**;
     * 摘要还没写过时回落到**当前生效的配置上限**。
     *
     * <p>这里刻意**不**回落到 {@link BladeCellSpec#MAX_TYPES}(绝对上限 20000):
     * 新盘会因此在工具提示上写"0 / 20000 把",而实际只收 5000 —— 等于向玩家撒谎。
     * 回落到 {@link AppliedSlashConfig#maxTypes()} 后,提示里的分母就是真正会生效的容量;
     * 写摘要时的短路比较也依然成立:{@link #writeSummary} 拿它对比的正是即将写入的同一个值。
     */
    public static int summaryMax(ItemStack stack) {
        CompoundTag tag = stack.get(AppliedSlashComponents.VAULT_SUMMARY.get());
        if (tag == null || !tag.contains(TAG_MAX)) {
            return AppliedSlashConfig.maxTypes();
        }
        return tag.getInt(TAG_MAX);
    }

    /** 写摘要:内容未变化时直接返回,避免每次存取都产生一次组件写入。 */
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
