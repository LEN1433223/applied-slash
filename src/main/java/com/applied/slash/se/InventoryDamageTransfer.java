package com.applied.slash.se;

import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

/**
 * 莉莉的特殊效果(SE):把**背包里其它拔刀剑**的一部分伤害加到自己身上。
 *
 * <p>比例见 {@link #RATIO}(每把 15%)。等级门槛 0(拿到即生效);
 * {@code copiable/removable} 为 true,与重锋本体 SE 的常规做法一致。
 *
 * <p>本类只在重锋存在时被类加载(注册走 {@link LiliSpecialEffects#register})。
 */
public class InventoryDamageTransfer extends SpecialEffect {
    /**
     * 每把刀的加伤比例:**10%**。
     *
     * <p>口径(用户 2026-08 定):手持元件内的刀与快捷栏里的刀**同价**,各 10%;
     * 两处共享上限 8 把,元件内优先(见 {@code InventoryDamageTransferLogic#computePool})。
     */
    public static final float RATIO = 0.10F;

    public InventoryDamageTransfer() {
        super(0, true, true);
    }
}
