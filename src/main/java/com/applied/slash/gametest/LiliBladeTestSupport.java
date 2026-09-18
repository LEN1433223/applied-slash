package com.applied.slash.gametest;

import java.util.EnumSet;

import com.applied.slash.LiliBlade;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * {@link LiliBladeGameTests} 的实现(非 holder 类,不会被 GameTest 扫描)。
 *
 * <p>所有方法签名只收 {@link GameTestHelper} —— 保持"holder 不引用第三方类型"的约定。
 */
final class LiliBladeTestSupport {
    /** 莉莉的期望伤害(baseAttackModifier;refine=0 ⇒ 面板即该值)。 */
    private static final float EXPECTED_ATTACK = 14.13F;
    /** 复用的丛雨丸资产路径。 */
    static final ResourceLocation MODEL = ResourceLocation.parse("slashblade:model/named/sange/sange.obj");
    static final ResourceLocation TEXTURE = ResourceLocation.parse("applied_slash:models/lili/uv1.png");

    private LiliBladeTestSupport() {
    }

    static void liliDefinitionIsLoaded(GameTestHelper helper) {
        ItemStack lili = liliStack(helper);
        helper.assertFalse(lili.isEmpty(), "莉莉的数据包定义没有加载:applied_slash:lili 查不到(检查 data/applied_slash/slashblade/named_blades/lili.json)");
        helper.assertTrue(lili.getItem() instanceof mods.flammpfeil.slashblade.item.ItemSlashBlade,
                "莉莉的物品应当是重锋原生的 slashblade:slashblade,实际: " + lili.getItem());
        helper.assertTrue(state(helper, lili).isPresent(), "莉莉没有刀身状态组件(BladeStateAccess 为空)");
        helper.succeed();
    }

    static void liliHasExactDamage(GameTestHelper helper) {
        ItemStack lili = liliStack(helper);
        ISlashBladeState state = requireState(helper, lili);
        float base = state.getBaseAttackModifier();
        helper.assertTrue(Math.abs(base - EXPECTED_ATTACK) < 1.0e-4F,
                "莉莉的 baseAttackModifier 应为 " + EXPECTED_ATTACK + ",实际 " + base);
        helper.assertTrue(state.getRefine() == 0,
                "莉莉的 refine 应为 0(refine ≠ 0 会让面板伤害偏离 baseAttackModifier),实际 " + state.getRefine());
        helper.succeed();
    }

    static void liliHasNoEnchantments(GameTestHelper helper) {
        ItemStack lili = liliStack(helper);
        ItemEnchantments ench = lili.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments stored = lili.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        helper.assertTrue(ench.isEmpty(), "莉莉不该带附魔,实际有 " + ench.size() + " 条");
        helper.assertTrue(stored.isEmpty(), "莉莉不该带附魔书条目(stored_enchantments),实际有 " + stored.size() + " 条");
        helper.assertFalse(lili.isEnchanted(), "莉莉不该被判定为已附魔(isEnchanted)");
        // sword_type = ["bewitched"] ⇒ 莉莉是**妖刀**,defaultBewitched = true。
        // 注意取舍:重锋把"妖刀"和"附魔光效旗标"绑在一起 ⇒ 要妖刀就会带光效。
        // 附魔本身仍然为零(上面三条断言保证),这里断言的正是"妖刀旗标已生效"。
        helper.assertTrue(requireState(helper, lili).isDefaultBewitched(),
                "莉莉现在是妖刀(sword_type=[bewitched]),defaultBewitched 应为 true");
        helper.succeed();
    }

    static void liliHasDriveHorizontalSa(GameTestHelper helper) {
        ItemStack lili = liliStack(helper);
        ISlashBladeState state = requireState(helper, lili);
        // SA:莉莉挂的是重锋的「次元斩」= slashblade:judgement_cut(语言键 slash_art.slashblade.judgement_cut 实测)
        ResourceLocation saKey = state.getSlashArtsKey();
        helper.assertTrue(saKey != null && saKey.equals(ResourceLocation.parse("slashblade:drive_horizontal")),
                "莉莉的 SA 应为 slashblade:drive_horizontal(幻影刃),实际 " + saKey);
        // SE:莉莉现在**恰好**带一个 SE —— 我们自己的「背包伤害转移」。
        // 断言语义随之更新:不再要求为空,而是要求"只有这一个、且 id 正确"
        // (多一个/少一个都要报错,避免以后误加)。
        var expectedSe = java.util.Set.of(
                ResourceLocation.fromNamespaceAndPath(com.applied.slash.AppliedSlash.MODID, "inventory_transfer"));
        var actualSe = java.util.Set.copyOf(state.getSpecialEffects());
        helper.assertTrue(actualSe.equals(expectedSe),
                "莉莉的 SE 应恰好是 " + expectedSe + ",实际 " + actualSe);
        // 刀身类型:莉莉是**妖刀**(bewitched,白狐同款);broken / sealed / noscabbard 仍然不许
        EnumSet<SwordType> types = SwordType.from(lili);
        for (SwordType forbidden : new SwordType[] {SwordType.BROKEN, SwordType.SEALED, SwordType.NOSCABBARD}) {
            helper.assertFalse(types.contains(forbidden),
                    "莉莉的 sword_type 不该包含 " + forbidden + ",实际 " + types);
        }
        helper.succeed();
    }

    static void liliUsesReusedMurasameAssets(GameTestHelper helper) {
        ItemStack lili = liliStack(helper);
        ISlashBladeState state = requireState(helper, lili);
        helper.assertTrue(state.getModel().orElse(null) != null && state.getModel().get().equals(MODEL),
                "莉莉的模型应指向 " + MODEL + ",实际 " + state.getModel().orElse(null));
        helper.assertTrue(state.getTexture().orElse(null) != null && state.getTexture().get().equals(TEXTURE),
                "莉莉的贴图应指向 " + TEXTURE + ",实际 " + state.getTexture().orElse(null));
        helper.succeed();
    }

    // ------------------------------------------------------------------

    private static ItemStack liliStack(GameTestHelper helper) {
        return LiliBlade.stack(helper.getLevel().registryAccess());
    }

    private static java.util.Optional<ISlashBladeState> state(GameTestHelper helper, ItemStack stack) {
        if (stack.isEmpty()) {
            return java.util.Optional.empty();
        }
        return BladeStateAccess.of(stack);
    }

    private static ISlashBladeState requireState(GameTestHelper helper, ItemStack stack) {
        helper.assertFalse(stack.isEmpty(), "莉莉的刀身定义没有加载,后续断言无法进行");
        java.util.Optional<ISlashBladeState> state = state(helper, stack);
        helper.assertTrue(state.isPresent(), "莉莉没有刀身状态组件");
        return state.orElseThrow();
    }
}
