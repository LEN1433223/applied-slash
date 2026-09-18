package com.applied.slash.se;

import com.applied.slash.AppliedSlash;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的拔刀剑 SE 注册(重锋的 {@code SpecialEffect.REGISTRY_KEY} 注册表)。
 *
 * <p><b>类加载约定</b>:本类与 {@link InventoryDamageTransfer} / {@link InventoryDamageTransferLogic}
 * 都直接引用重锋类型 ⇒ 只在确认重锋存在时调用 {@link #register(IEventBus)}
 * (见 {@code AppliedSlash} 构造函数里的门卫),否则这些类根本不会被加载。
 */
public final class LiliSpecialEffects {
    /** SE 注册表(重锋暴露的 ResourceKey)。 */
    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECTS =
            DeferredRegister.create(SpecialEffect.REGISTRY_KEY, AppliedSlash.MODID);

    /** 莉莉的 SE:背包内其它拔刀剑 15% 伤害转加到自身。 */
    public static final DeferredHolder<SpecialEffect, InventoryDamageTransfer> INVENTORY_TRANSFER =
            SPECIAL_EFFECTS.register("inventory_transfer", InventoryDamageTransfer::new);

    private LiliSpecialEffects() {
    }

    /** 注册 SE 本体 + 两条事件监听(只在重锋存在时调用)。 */
    public static void register(IEventBus modEventBus) {
        SPECIAL_EFFECTS.register(modEventBus);
        NeoForge.EVENT_BUS.register(InventoryDamageTransferLogic.class);
        // 动态彩色刀光(粉紫 12 色,每次攻击推进一档)
        com.applied.slash.rainbow.LiliRainbowColor.register();
        AppliedSlash.LOGGER.info("已注册拔刀剑特殊效果 SE:{} (每把其它刀 {}%)",
                InventoryDamageTransferLogic.SE_ID, (int) (InventoryDamageTransfer.RATIO * 100.0F));
    }
}
