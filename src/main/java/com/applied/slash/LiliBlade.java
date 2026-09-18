package com.applied.slash;

import java.util.Optional;

import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 拔刀剑「莉莉」的便捷门面 —— 实际逻辑在 {@link AppliedBlades}(四把刀共用)。
 *
 * <p>保留本类是为了不让既有调用点与测试改动:莉莉的使用语义没变,只是"四把刀"被抽成了集合。
 * 莉莉现在是**数据包刀**:定义在 {@code data/applied_slash/slashblade/named_blades/lili.json},
 * 物品是重锋原生 {@code slashblade:slashblade},模型复用 sange.obj,贴图用我们自己的 UV 图集。
 */
public final class LiliBlade {
    /** 刀身定义的注册名:{@code applied_slash:lili}(= 数据包文件名 {@code lili.json})。 */
    public static final ResourceLocation ID = AppliedBlades.id(AppliedBlades.LILI);

    private LiliBlade() {
    }

    /** 取一把全新的莉莉。定义缺席时返回空栈,绝不抛异常。 */
    public static ItemStack stack(HolderLookup.Provider registries) {
        return AppliedBlades.stack(registries, AppliedBlades.LILI);
    }

    /** 莉莉的刀身定义(缺席 → 空 Optional)。 */
    public static Optional<SlashBladeDefinition> definition(HolderLookup.Provider registries) {
        return AppliedBlades.definition(registries, AppliedBlades.LILI);
    }
}