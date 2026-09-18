package com.applied.slash.gametest;

import com.applied.slash.AppliedSlashAe2;
import com.applied.slash.LiliBlade;
import com.applied.slash.portable.PortableSlashCellInventory;
import com.applied.slash.se.InventoryDamageTransfer;
import com.applied.slash.se.InventoryDamageTransferLogic;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * 加伤池口径测试:元件内优先、快捷栏补足、合计 ≤ 8 把、每把 10%。
 */
final class DamagePoolTestSupport {
    private DamagePoolTestSupport() {
    }

    private static ItemStack cellWith(GameTestHelper helper, int blades) {
        ItemStack cell = AppliedSlashAe2.PORTABLE_SLASH_CELL.get().getDefaultInstance();
        PortableSlashCellInventory inv = new PortableSlashCellInventory(cell, null);
        for (int i = 0; i < blades; i++) {
            inv.insert(AEItemKey.of(LiliBlade.stack(helper.getLevel().registryAccess())), 1, Actionable.MODULATE, null);
        }
        inv.persist();
        return cell;
    }

    /** 每把 10%:元件 2 把 + 快捷栏 1 把 ⇒ 3 × 10% × 基础伤害。 */
    static void poolIsTenPercentPerBlade(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack held = LiliBlade.stack(helper.getLevel().registryAccess());
        float base = InventoryDamageTransferLogic.baseDamage(held);
        var inv = player.getInventory();
        inv.setItem(0, held);
        inv.selected = 0;
        inv.setItem(1, cellWith(helper, 2));
        inv.setItem(2, LiliBlade.stack(helper.getLevel().registryAccess()));

        InventoryDamageTransferLogic.Pool pool = InventoryDamageTransferLogic.computePool(held, player);
        double expect = 3.0D * base * InventoryDamageTransfer.RATIO;
        helper.assertTrue(pool.fromCells() == 2 && pool.fromHotbar() == 1,
                "构成应为 元件 2 + 快捷栏 1,实际 " + pool.fromCells() + " / " + pool.fromHotbar());
        helper.assertTrue(Math.abs(pool.bonus() - expect) < 1.0e-6D,
                "加伤应为 3×10%×基础 = " + expect + ",实际 " + pool.bonus()
                        + "(比例常量 = " + InventoryDamageTransfer.RATIO + ")");
        helper.succeed();
    }

    /** 元件内优先 + 合计截断 8:元件 8 把 + 快捷栏 3 把 ⇒ 只算 8,且全部来自元件。 */
    static void poolCountsCellFirstAndCapsAtEight(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack held = LiliBlade.stack(helper.getLevel().registryAccess());
        var inv = player.getInventory();
        inv.setItem(0, held);
        inv.selected = 0;
        inv.setItem(1, cellWith(helper, 8));
        for (int i = 2; i <= 4; i++) {
            inv.setItem(i, LiliBlade.stack(helper.getLevel().registryAccess()));
        }
        InventoryDamageTransferLogic.Pool pool = InventoryDamageTransferLogic.computePool(held, player);
        helper.assertTrue(pool.total() == 8, "参与刀数应截断到 8,实际 " + pool.total());
        helper.assertTrue(pool.fromCells() == 8 && pool.fromHotbar() == 0,
                "元件内应优先占满(8/0),实际 " + pool.fromCells() + "/" + pool.fromHotbar());
        helper.succeed();
    }

    /** 元件放在主背包(非快捷栏)⇒ 完全不计入。 */
    static void cellOutsideHotbarDoesNotCount(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack held = LiliBlade.stack(helper.getLevel().registryAccess());
        var inv = player.getInventory();
        inv.setItem(0, held);
        inv.selected = 0;
        inv.setItem(9, cellWith(helper, 3));      // 主背包第一格
        InventoryDamageTransferLogic.Pool pool = InventoryDamageTransferLogic.computePool(held, player);
        helper.assertTrue(pool.total() == 0 && pool.bonus() == 0.0D,
                "主背包里的元件不该计入,实际 total=" + pool.total() + " bonus=" + pool.bonus());
        helper.succeed();
    }

    /** 手持那把刀不计入自己(即使元件里放着一把相同的刀)。 */
    static void heldBladeExcludesItself(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack held = LiliBlade.stack(helper.getLevel().registryAccess());
        var inv = player.getInventory();
        inv.setItem(0, held);
        inv.selected = 0;
        InventoryDamageTransferLogic.Pool pool = InventoryDamageTransferLogic.computePool(held, player);
        helper.assertTrue(pool.total() == 0, "只有手持那把刀时应为 0,实际 " + pool.total());
        helper.succeed();
    }
}