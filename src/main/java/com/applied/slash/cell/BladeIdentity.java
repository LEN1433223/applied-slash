package com.applied.slash.cell;

import com.applied.slash.SlashBladeBlades;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 键层面的拔刀剑身份。
 *
 * <p>两条要点:
 * <ol>
 *   <li>判定走"标签 + 物品类"双保险,且**不构造 ItemStack**({@link AEItemKey#getItem()} 与
 *       {@link AEItemKey#isTagged} 都是零拷贝),保证插入热路径尽量便宜;</li>
 *   <li>规范化:入库前剥离刀的运行时状态组件,让同一把刀在不同运行时态下是同一个键
 *       (否则类型数会虚增、每键载荷变大)。</li>
 * </ol>
 */
public final class BladeIdentity {
    private BladeIdentity() {
    }

    public static boolean isBlade(AEKey key) {
        return key instanceof AEItemKey itemKey && isBlade(itemKey);
    }

    public static boolean isBlade(AEItemKey key) {
        if (key.isTagged(SlashBladeBlades.SWORDS_TAG)) {
            return true;
        }
        return SlashBladeBlades.isSlashBladeItem(key.getItem());
    }

    /**
     * 该键是否<b>可能</b>被规范化成另一个键(即带运行时状态组件)。
     * 便宜检查,热路径用它避开无谓的 {@code stack.copy()};返回 false 时 {@link #normalize} 必然原样返回。
     */
    public static boolean mayNormalize(AEItemKey key) {
        return SlashBladeBlades.hasVolatileState(key.getReadOnlyStack());
    }

    /**
     * 返回规范化后的键;若本来就不需要改动,原样返回入参(不产生额外分配)。
     *
     * <p>代价:仅在"该键尚未存在于刀库"时才会走到拷贝 + 重建键,重复存入同一把刀走快路径。
     */
    public static AEItemKey normalize(AEItemKey key) {
        ItemStack stack = key.getReadOnlyStack();
        if (stack.isEmpty()) {
            return key;
        }
        ItemStack copy = stack.copy();
        SlashBladeBlades.stripVolatileState(copy);
        if (ItemStack.isSameItemSameComponents(copy, stack)) {
            return key;
        }
        return AEItemKey.of(copy);
    }
}
