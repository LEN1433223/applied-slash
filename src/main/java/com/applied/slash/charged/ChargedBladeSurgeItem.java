package com.applied.slash.charged;

import com.applied.slash.AppliedSlashConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/** 充能刀·涌流(charged_blade_surge,默认上限 800)。 */
public final class ChargedBladeSurgeItem extends ChargedBladeItem {

    public ChargedBladeSurgeItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);
    }

    @Override
    public String bladeId() {
        return ChargedBladeSpec.SURGE.fullId();
    }

    @Override
    public String nameKey() {
        return ChargedBladeSpec.SURGE.nameKey();
    }

    @Override
    public int maxEnergy() {
        return AppliedSlashConfig.chargedBladeMaxSurge();
    }

    @Override
    public ChargedBladeSpec spec() {
        return ChargedBladeSpec.SURGE;
    }
}
