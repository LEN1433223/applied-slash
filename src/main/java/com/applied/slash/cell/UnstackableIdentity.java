package com.applied.slash.cell;

import com.applied.slash.SlashBladeBlades;
import com.applied.slash.SlashBladeCellItem;
import com.applied.slash.UnstackableItemCellItem;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import net.minecraft.world.item.ItemStack;

/**
 * 「不可堆叠物品存储元件」的键谓词(拔刀剑元件 {@link BladeIdentity} 的平行实现)。
 *
 * <p>与拔刀剑元件最大的不同:<b>这里没有任何规范化</b>。
 * 拔刀剑元件的 {@code normalize()} 会剥离刀身运行时状态组件(因为那是纯战斗状态,会虚增类型数);
 * 而不可堆叠物品的耐久、附魔、自定义名称、容器内容等**就是它的身份** —— 一把用了 3 点耐久的镐
 * 和一把全新的镐是两件不同的物品,剥离任何一个字节都会把两件不同的东西合并成一件。所以本元件
 * 全程直接用 {@code AEItemKey} 原样存取,不做任何拷贝或改写。
 */
public final class UnstackableIdentity {
    private UnstackableIdentity() {
    }

    public static boolean isAccepted(AEKey key) {
        return key instanceof AEItemKey itemKey && isAccepted(itemKey);
    }

    public static boolean isAccepted(AEItemKey key) {
        // getReadOnlyStack() 是零拷贝(键内部就持有这个栈),不会因为判定而产生分配
        return isAccepted(key.getReadOnlyStack());
    }

    /** 谓词本体:不可堆叠 <b>且</b> 不是拔刀剑 <b>且</b> 不是存储元件类物品。 */
    public static boolean isAccepted(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1) {
            return false;
        }
        // 拔刀剑有专用元件(它不做状态剥离,与本元件的语义相反,必须以本盘为准让位)
        if (SlashBladeBlades.isSlashBlade(stack)) {
            return false;
        }
        return !isStorageCell(stack);
    }

    /**
     * 排除"存储元件类物品",否则元件会被吞进储存盘(玩家把一盘子元件塞进另一块盘里,
     * 既无意义又会让驱动器槽位里的元件凭空消失)。
     *
     * <p>两层判定:
     * <ol>
     *   <li>先显式认本模组自己的两个元件物品 —— 这一步不依赖 AE2 的 handler 注册时机
     *       ({@code StorageCells.addCellHandler} 在 {@code FMLCommonSetupEvent} 里才执行);</li>
     *   <li>再问 AE2 的 {@code StorageCells.isCellHandled(ItemStack)}:AE2 原生单元
     *       ({@code BasicCellHandler.isCell} 判 {@code instanceof IBasicCellItem},见 PLAN §1 R2 的 javap 证据)
     *       与其他附属注册的 handler 都能被这一句覆盖,不必逐个猜物品类型。</li>
     * </ol>
     *
     * <p>该调用在本项目里已有编译通过的先例:{@code gametest.GameTestSupport} 用它做
     * "驱动器会不会接受这个元件"的断言(见 PLAN §6 的 {@code rejectsNonBlades})。
     * 它只遍历 handler 列表做 {@code isCell} 判定,不会创建任何库存实例,也不会改状态。
     */
    public static boolean isStorageCell(ItemStack stack) {
        if (stack.getItem() instanceof UnstackableItemCellItem) {
            return true;
        }
        if (stack.getItem() instanceof SlashBladeCellItem) {
            return true;
        }
        return StorageCells.isCellHandled(stack);
    }
}
