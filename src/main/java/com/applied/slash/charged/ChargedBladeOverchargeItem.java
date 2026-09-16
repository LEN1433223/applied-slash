package com.applied.slash.charged;

import com.applied.slash.AppliedSlashConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/** 充能刀·超荷(charged_blade_overcharge,默认上限 1600)。刀身外观 = 重锋的 sange(见 {@link ChargedBladeSpec})。 */
public final class ChargedBladeOverchargeItem extends ChargedBladeItem {

    public ChargedBladeOverchargeItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);
    }

    @Override
    public String bladeId() {
        return ChargedBladeSpec.OVERCHARGE.fullId();
    }

    @Override
    public String nameKey() {
        return ChargedBladeSpec.OVERCHARGE.nameKey();
    }

    @Override
    public int maxEnergy() {
        return AppliedSlashConfig.chargedBladeMaxOvercharge();
    }

    @Override
    public ChargedBladeSpec spec() {
        return ChargedBladeSpec.OVERCHARGE;
    }
}
