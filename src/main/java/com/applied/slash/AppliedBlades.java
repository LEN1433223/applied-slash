package com.applied.slash;

import java.util.List;
import java.util.Optional;

import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 本模组的**数据包刀集合**(与重锋/丛雨丸同一套做法:物品是重锋原生 {@code slashblade:slashblade},
 * 定义放 {@code data/applied_slash/slashblade/named_blades/<id>.json},模型复用 sange.obj,
 * 贴图用各自的 UV 图集)。
 *
 * <p>四把刀共享同一个 SE({@code applied_slash:inventory_transfer}「爱与羁绊」),
 * 刀光基础色各不相同 ⇒ 动态彩色刀光的**色板按刀切换**(见 {@code LiliRainbowColor},按基础色查表)。
 *
 * <p><b>类加载边界</b>:本类引用重锋的 {@link SlashBladeDefinition},所有入口都先过
 * {@link AppliedSlash#isSlashBladeLoaded()} 门卫;重锋缺席时本类不会被类加载。
 */
public final class AppliedBlades {
    /** 莉莉(粉黛 · 粉紫刀光)。 */
    public static final String LILI = "lili";
    /** 枫(秋枫 · 橙金刀光,无红)。 */
    public static final String KAEDE = "kaede";
    /** 栞(暖纸 · 桃金刀光)。 */
    public static final String SHIORI = "shiori";
    /** 星奈(星空 · 银紫刀光)。 */
    public static final String SEINA = "seina";

    /** 创造栏顺序。 */
    public static final List<String> ALL = List.of(LILI, KAEDE, SHIORI, SEINA);

    private AppliedBlades() {
    }

    /** 刀身定义的 id:{@code applied_slash:<name>}。 */
    public static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(AppliedSlash.MODID, name);
    }

    /** 取一把全新的刀;定义缺席(数据包没加载)时返回空栈,绝不抛异常。 */
    public static ItemStack stack(HolderLookup.Provider registries, String name) {
        if (registries == null || !AppliedSlash.isSlashBladeLoaded()) {
            return ItemStack.EMPTY;
        }
        return definition(registries, name)
                .map(definition -> definition.getBlade(registries))
                .orElse(ItemStack.EMPTY);
    }

    /** 刀身定义(缺席 → 空 Optional)。 */
    public static Optional<SlashBladeDefinition> definition(HolderLookup.Provider registries, String name) {
        if (registries == null || !AppliedSlash.isSlashBladeLoaded()) {
            return Optional.empty();
        }
        ResourceKey<SlashBladeDefinition> key =
                ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, id(name));
        return registries.lookup(SlashBladeDefinition.REGISTRY_KEY)
                .flatMap(lookup -> lookup.get(key))
                .map(holder -> holder.value());
    }
}