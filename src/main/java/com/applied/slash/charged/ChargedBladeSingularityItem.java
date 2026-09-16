package com.applied.slash.charged;

import com.applied.slash.AppliedSlashConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/** 充能刀·奇点(charged_blade_singularity,默认上限 3200)。刀身外观 = 重锋的 yamato(见 {@link ChargedBladeSpec})。 */
public final class ChargedBladeSingularityItem extends ChargedBladeItem {

    public ChargedBladeSingularityItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);
    }

    @Override
    public String bladeId() {
        return ChargedBladeSpec.SINGULARITY.fullId();
    }

    @Override
    public String nameKey() {
        return ChargedBladeSpec.SINGULARITY.nameKey();
    }

    @Override
    public int maxEnergy() {
        return AppliedSlashConfig.chargedBladeMaxSingularity();
    }

    @Override
    public ChargedBladeSpec spec() {
        return ChargedBladeSpec.SINGULARITY;
    }
}
