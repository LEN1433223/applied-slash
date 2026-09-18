package com.applied.slash.gametest;

import com.applied.slash.AppliedSlashAe2;
import com.applied.slash.LiliBlade;
import com.applied.slash.portable.PortableSlashCellInventory;
import com.applied.slash.portable.SlashCellAccess;

import appeng.api.config.Actionable;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.stacks.AEItemKey;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 手持「Slash 元件」的存储语义测试(容量 8 / 只收刀 / 同 NBT 不合并)。
 *
 * <p>直接驱动库存实现(AE2 的 {@code StorageCell}),不经界面 —— 界面的接线由 AE2 保证。
 */
final class PortableCellTestSupport {
    private PortableCellTestSupport() {
    }

    private static ItemStack newCell(GameTestHelper helper) {
        ItemStack cell = AppliedSlashAe2.PORTABLE_SLASH_CELL.get().getDefaultInstance();
        helper.assertTrue(!cell.isEmpty(), "便携元件没注册");
        return cell;
    }

    private static PortableSlashCellInventory inventoryOf(ItemStack cell) {
        return new PortableSlashCellInventory(cell, null);
    }

    /** 非拔刀剑一律拒绝(石头/钻石各试一次)。 */
    static void cellRejectsNonBlades(GameTestHelper helper) {
        ItemStack cell = newCell(helper);
        PortableSlashCellInventory inv = inventoryOf(cell);
        long stone = inv.insert(AEItemKey.of(new ItemStack(Items.STONE)), 1, Actionable.MODULATE, null);
        long diamond = inv.insert(AEItemKey.of(new ItemStack(Items.DIAMOND)), 1, Actionable.MODULATE, null);
        helper.assertTrue(stone == 0 && diamond == 0,
                "非拔刀剑必须被拒绝,实际 stone=" + stone + " diamond=" + diamond);
        inv.persist();
        helper.assertTrue(SlashCellAccess.bladesIn(cell).isEmpty(), "拒绝后内容应为空");
        helper.succeed();
    }

    /** 最多 8 把:塞第 9 把时返回 0,且内容恰为 8。 */
    static void cellHoldsExactlyEight(GameTestHelper helper) {
        ItemStack cell = newCell(helper);
        PortableSlashCellInventory inv = inventoryOf(cell);
        for (int i = 0; i < 8; i++) {
            ItemStack blade = LiliBlade.stack(helper.getLevel().registryAccess());
            helper.assertTrue(!blade.isEmpty(), "莉莉没加载");
            long accepted = inv.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, null);
            helper.assertTrue(accepted == 1, "第 " + (i + 1) + " 把应被接受,实际 " + accepted);
        }
        inv.persist();
        helper.assertTrue(SlashCellAccess.bladesIn(cell).size() == 8,
                "容量应为 8,实际 " + SlashCellAccess.bladesIn(cell).size());

        long ninth = inv.insert(AEItemKey.of(LiliBlade.stack(helper.getLevel().registryAccess())), 1, Actionable.MODULATE, null);
        helper.assertTrue(ninth == 0, "第 9 把必须被拒绝,实际接受 " + ninth);
        inv.persist();
        helper.assertTrue(SlashCellAccess.bladesIn(cell).size() == 8, "第 9 把不该进内容");
        helper.succeed();
    }

    /**
     * **回归测试**:插入后重新解析库存实例,内容必须还在。
     *
     * <p>这条对应一次严重 bug:AE2 的便携界面每次都会重新
     * {@code StorageCells.getCellInventory(stack, saveProvider)},而菜单宿主的缓存键是物品栈对象
     * —— 手持物品每次拿到的常是不同对象 ⇒ 会构造**新的库存实例**。
     * 当时把插入只记在内存里、等 {@code persist()} 才写组件,于是玩家看到"刀放进去就消失了"。
     *
     * <p>现在插入即时写组件,所以:①走 AE2 真实解析路径拿到的库存能插入;
     * ②**重新解析**出来的新实例立刻就能看到那把刀。
     */
    static void cellSurvivesReResolution(GameTestHelper helper) {
        ItemStack cell = newCell(helper);
        var first = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(first != null, "AE2 应能解析出我们的库存(cell handler 注册失效了?)");

        ItemStack blade = LiliBlade.stack(helper.getLevel().registryAccess());
        helper.assertTrue(!blade.isEmpty(), "莉莉没加载");
        long accepted = first.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, null);
        helper.assertTrue(accepted == 1, "插入应成功,实际 " + accepted);

        // ① 内容必须已经写进组件(而不是等 persist)
        helper.assertTrue(SlashCellAccess.bladesIn(cell).size() == 1,
                "插入后组件里应立刻有 1 把,实际 " + SlashCellAccess.bladesIn(cell).size());

        // ② 重新解析一个**新实例**(界面每次取库存都会这样),它必须看得到那把刀
        var second = StorageCells.getCellInventory(cell, null);
        KeyCounter counter = new KeyCounter();
        second.getAvailableStacks(counter);
        // 汇报的键是剥掉隐藏标记后的普通刀 ⇒ 直接用同样的键查询
        long total = counter.get(AEItemKey.of(blade));
        helper.assertTrue(total == 1L,
                "重新解析后的实例应看到 1 把刀(0 = 又回到「放进去就消失」),实际 " + total);
        helper.succeed();
    }
    /** 同 NBT 也不合并:同一把刀插两次 ⇒ 占 2 格,且标记不同。 */
    static void cellNeverMergesIdenticalBlades(GameTestHelper helper) {
        ItemStack cell = newCell(helper);
        PortableSlashCellInventory inv = inventoryOf(cell);
        ItemStack blade = LiliBlade.stack(helper.getLevel().registryAccess());
        AEItemKey key = AEItemKey.of(blade);
        inv.insert(key, 1, Actionable.MODULATE, null);
        inv.insert(key, 1, Actionable.MODULATE, null);
        inv.persist();
        var stored = SlashCellAccess.bladesIn(cell);
        helper.assertTrue(stored.size() == 2,
                "同一把刀插两次应占 2 格(NBT 相同也不合并),实际 " + stored.size());
        var id0 = stored.get(0).get(com.applied.slash.AppliedSlashComponents.PORTABLE_ENTRY_ID.get());
        var id1 = stored.get(1).get(com.applied.slash.AppliedSlashComponents.PORTABLE_ENTRY_ID.get());
        helper.assertTrue(id0 != null && id1 != null && !id0.equals(id1),
                "每个条目都应有不同的唯一标记");
        helper.succeed();
    }
}