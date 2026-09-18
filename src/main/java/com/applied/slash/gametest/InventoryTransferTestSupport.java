package com.applied.slash.gametest;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.LiliBlade;
import com.applied.slash.se.InventoryDamageTransfer;
import com.applied.slash.se.InventoryDamageTransferLogic;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 莉莉 SE「背包伤害转移」的测试实现。
 *
 * <p>放在 support 类里的原因与其它测试一致:holder 类的方法签名只能有 {@link GameTestHelper},
 * 第三方类型(重锋的 SE 类)只能出现在 support 里,保证前置缺席时不会在扫描期炸类加载。
 *
 * <p>可测性来自设计:{@link InventoryDamageTransferLogic#computeBonus} 是**纯函数**
 * (只依赖一个 {@link net.minecraft.world.Container}),所以不需要真玩家、不需要进世界就能验算术。
 */
final class InventoryTransferTestSupport {
    private InventoryTransferTestSupport() {
    }

    /** SE 真的注册进了重锋的 SE 注册表(按 id 查得到且生效)。 */
    static void seIsRegistered(GameTestHelper helper) {
        helper.assertTrue(SpecialEffect.isEffective(InventoryDamageTransferLogic.SE_ID, 0),
                "SE 没注册成功:" + InventoryDamageTransferLogic.SE_ID);
        helper.succeed();
    }

    /** 背包里两把其它刀 ⇒ 加成 = 2 × 15% × 各自基础伤害。 */
    static void bonusSumsOtherBlades(GameTestHelper helper) {
        ItemStack own = LiliBlade.stack(helper.getLevel().registryAccess());
        ItemStack other1 = LiliBlade.stack(helper.getLevel().registryAccess());
        ItemStack other2 = LiliBlade.stack(helper.getLevel().registryAccess());
        helper.assertTrue(!own.isEmpty(), "莉莉没加载,无法验证 SE 算术");
        SimpleContainer inv = new SimpleContainer(3);
        inv.setItem(0, own);
        inv.setItem(1, other1);
        inv.setItem(2, other2);
        double one = InventoryDamageTransferLogic.baseDamage(own) * InventoryDamageTransfer.RATIO;
        double got = InventoryDamageTransferLogic.computeBonus(own, 0, inv);
        helper.assertTrue(Math.abs(got - 2.0D * one) < 1.0e-6D,
                "加成应为 2 × 15% × 基础伤害 = " + (2.0D * one) + ",实际 " + got);
        helper.succeed();
    }

    /** 非拔刀剑不算;自己所在槽位也不算。 */
    static void bonusSkipsSelfAndNonBlades(GameTestHelper helper) {
        ItemStack own = LiliBlade.stack(helper.getLevel().registryAccess());
        ItemStack other = LiliBlade.stack(helper.getLevel().registryAccess());
        SimpleContainer inv = new SimpleContainer(4);
        inv.setItem(0, own);
        inv.setItem(1, new ItemStack(Items.STONE));
        inv.setItem(2, other);
        inv.setItem(3, new ItemStack(Items.DIAMOND));
        double one = InventoryDamageTransferLogic.baseDamage(own) * InventoryDamageTransfer.RATIO;
        double got = InventoryDamageTransferLogic.computeBonus(own, 0, inv);
        helper.assertTrue(Math.abs(got - one) < 1.0e-6D,
                "石头/钻石不该计入,应只有一把其它刀 = " + one + ",实际 " + got);
        double gotSelf = InventoryDamageTransferLogic.computeBonus(other, 2, inv);
        helper.assertTrue(Math.abs(gotSelf - one) < 1.0e-6D,
                "站在第 2 槽那把刀的视角,只该算到第 0 槽那把 = " + one + ",实际 " + gotSelf);
        helper.succeed();
    }

    /**
     * **端到端**:拿一个 mock 玩家,把莉莉放进快捷栏并选中,背包里再放两把其它刀;
     * 直接调用 SE 的更新钩子(与重锋运行期同一条路径),然后检查
     * ①组件被写上;②**属性查询里真的出现了我们的修正**(这一步就是"伤害到底生没生效"的判据)。
     */
    static void bonusReachesAttribute(GameTestHelper helper) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var level = helper.getLevel();
        ItemStack lili = LiliBlade.stack(level.registryAccess());
        helper.assertTrue(!lili.isEmpty(), "莉莉没加载");
        var state = mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess.of(lili).orElseThrow();
        float base = state.getBaseAttackModifier();
        helper.assertTrue(base > 0.0F, "基础伤害应大于 0,实际 " + base);

        player.getInventory().setItem(0, lili);
        player.getInventory().setItem(1, LiliBlade.stack(level.registryAccess()));
        player.getInventory().setItem(2, LiliBlade.stack(level.registryAccess()));
        player.getInventory().selected = 0;

        // **走真正的总线**:投递 NeoForge 的玩家 tick 事件,验证"注册 -> 触发 -> 写组件"整条链
        // (上一版就是漏了这一步,只手动调方法,所以进游戏不生效而测试却是通的。)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));

        Double bonus = lili.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        double expect = 2.0D * base * InventoryDamageTransfer.RATIO;
        helper.assertTrue(bonus != null && Math.abs(bonus - expect) < 0.01D,
                "组件应为 " + expect + ",实际 " + bonus);

        // 属性侧:查询主手属性,必须能找到我们 id 的那条修正
        var modsList = lili.getAttributeModifiers();   // NeoForge 1.21:无参,返回全部槽位的修正
        double ours = modsList.modifiers().stream()
                .filter(e -> e.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
                .filter(e -> e.modifier().id().equals(InventoryDamageTransferLogic.MODIFIER_ID))
                .mapToDouble(e -> e.modifier().amount())
                .sum();
        helper.assertTrue(Math.abs(ours - expect) < 0.01D,
                "原版攻击属性上应出现 id=" + InventoryDamageTransferLogic.MODIFIER_ID + " 的修正 " + expect
                        + ",实际 " + ours);

        // 连击侧:重锋自定义属性 slashblade:slashblade_damage 上也要有我们的修正
        // (AttackManager/AttackHelper 读的是它;只加原版属性时连击伤害不变 —— 实测踩过)
        var sbAttr = mods.flammpfeil.slashblade.registry.ModAttributes.SLASHBLADE_DAMAGE;
        double oursSb = modsList.modifiers().stream()
                .filter(e -> e.attribute().is(sbAttr))
                .filter(e -> e.modifier().id().equals(InventoryDamageTransferLogic.SLASHBLADE_DAMAGE_MODIFIER_ID))
                .mapToDouble(e -> e.modifier().amount())
                .sum();
        helper.assertTrue(Math.abs(oursSb - expect) < 0.01D,
                "重锋连击属性上应出现 id=" + InventoryDamageTransferLogic.SLASHBLADE_DAMAGE_MODIFIER_ID
                        + " 的修正 " + expect + ",实际 " + oursSb + "(0 = 连击不会吃到加成)");

        // 文本:面板伤害 = 基础 + 加成
        String text = InventoryDamageTransferLogic.panelDamageText(lili);
        helper.assertTrue(text.startsWith("现在的面板伤害为 ") && text.contains(String.format(java.util.Locale.ROOT, "%.2f", base + expect)),
                "面板伤害文本不对:" + text);
        helper.succeed();
    }
    /** 只算**快捷栏**(0..8):主背包(第 9 格及以后)里的刀不算。 */
    static void bonusOnlyCountsHotbar(GameTestHelper helper) {
        ItemStack own = LiliBlade.stack(helper.getLevel().registryAccess());
        ItemStack inHotbar = LiliBlade.stack(helper.getLevel().registryAccess());
        ItemStack inMain = LiliBlade.stack(helper.getLevel().registryAccess());
        SimpleContainer inv = new SimpleContainer(12);
        inv.setItem(0, own);          // 手持(排除)
        inv.setItem(1, inHotbar);     // 快捷栏 ⇒ 计入
        inv.setItem(9, inMain);       // 主背包 ⇒ 不计入
        double one = InventoryDamageTransferLogic.baseDamage(own) * InventoryDamageTransfer.RATIO;
        double got = InventoryDamageTransferLogic.computeHotbarBonus(own, 0, inv);
        helper.assertTrue(Math.abs(got - one) < 1.0e-6D,
                "只该算快捷栏那一把 = " + one + ",实际 " + got + "(把主背包那把也算进去说明范围没改)");
        helper.succeed();
    }
    /** 组件往返:写进去/读出来/移除。 */
    static void bonusComponentRoundTrip(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STONE);
        helper.assertTrue(stack.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get()) == null,
                "默认应为空");
        stack.set(AppliedSlashComponents.LILI_DAMAGE_BONUS.get(), 3.5D);
        Double got = stack.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        helper.assertTrue(got != null && Math.abs(got - 3.5D) < 1.0e-9D, "组件往返失败:" + got);
        stack.remove(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        helper.assertTrue(stack.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get()) == null, "移除失败");
        helper.succeed();
    }
}