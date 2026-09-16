package com.applied.slash;

import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeDataComponents;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * 拔刀剑身份判定与规范化。
 *
 * <p>判定必须双保险:SlashBlade 的物品标签 {@code slashblade:swords} 只列了 5 把基础刀
 * (slashblade / bamboo / silverbamboo / white / wood);具名刀与附属刀(SJAP、雫刀等)
 * 用的是同一个物品 + 不同刀身数据,所以还要叠加 {@link ItemSlashBlade} 的类判定
 * (附属刀如 TofuSlashBladeItem 都继承它)。
 *
 * <p>规范化({@link #stripVolatileState}):刀的运行时状态组件 {@code BLADE_RUNTIME_STATE}
 * 会随战斗过程变化,若不剥离,同一把刀在不同运行时态下会算成两个不同的存储键,
 * 导致类型数虚增、每键载荷变大。持久化的刀身数据({@code BLADE_STATE_DATA}:刀铭/耀魂/
 * 附魔/耐久/SA 等)全部保留。
 */
public final class SlashBladeBlades {
    /** SlashBlade 本体的物品标签,数据包可扩展。 */
    public static final TagKey<Item> SWORDS_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.parse(AppliedSlash.SLASHBLADE_MODID + ":swords"));

    private SlashBladeBlades() {
    }

    /** 该物品栈是否为拔刀剑。 */
    public static boolean isSlashBlade(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(SWORDS_TAG)) {
            return true;
        }
        return isSlashBladeItem(stack.getItem());
    }

    /** 该物品是否为拔刀剑物品(不构造 ItemStack,适合用作热路径判定)。 */
    public static boolean isSlashBladeItem(Item item) {
        if (item == null) {
            return false;
        }
        return ModList.get().isLoaded(AppliedSlash.SLASHBLADE_MODID) && isItemSlashBlade(item);
    }

    /**
     * 该物品栈是否带"易变组件"(运行时状态)。**便宜检查**:用于在热路径上先判断"规范化到底会不会改变键",
     * 避免每次未命中的查找都做一次 {@code stack.copy()}(插入/取出/优先存储判定都会走到)。
     */
    public static boolean hasVolatileState(ItemStack stack) {
        if (stack.isEmpty() || !AppliedSlashConfig.stripRuntimeState()) {
            return false;
        }
        if (!ModList.get().isLoaded(AppliedSlash.SLASHBLADE_MODID)) {
            return false;
        }
        return hasRuntimeState(stack);
    }

    /** 剥离纯运行时状态组件,使同一把刀的身份稳定(持久化刀身数据不动)。受配置开关控制。 */
    public static void stripVolatileState(ItemStack stack) {
        if (stack.isEmpty() || !AppliedSlashConfig.stripRuntimeState()) {
            return;
        }
        if (ModList.get().isLoaded(AppliedSlash.SLASHBLADE_MODID)) {
            removeRuntimeState(stack);
        }
    }

    /**
     * 单独成方法:引用 {@link ItemSlashBlade} 只会在此方法首次执行时触发类加载,
     * 调用点已被 {@code ModList.isLoaded} 门卫,SlashBlade 缺席时不会抛 NoClassDefFoundError。
     */
    private static boolean isItemSlashBlade(Item item) {
        return item instanceof ItemSlashBlade;
    }

    /** 同上:对 SlashBlade 组件的引用只在这个被门卫保护的独立方法里发生。 */
    private static void removeRuntimeState(ItemStack stack) {
        stack.remove(SlashBladeDataComponents.BLADE_RUNTIME_STATE.get());
    }

    /** 同上:只在这个被门卫保护的独立方法里引用 SlashBlade 组件。 */
    private static boolean hasRuntimeState(ItemStack stack) {
        return stack.has(SlashBladeDataComponents.BLADE_RUNTIME_STATE.get());
    }
}
