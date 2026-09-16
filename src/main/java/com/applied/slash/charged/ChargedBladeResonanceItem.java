package com.applied.slash.charged;

import com.applied.slash.AppliedSlashConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/** 充能刀·谐振(charged_blade_resonance,默认上限 400)。 */
public final class ChargedBladeResonanceItem extends ChargedBladeItem {

    public ChargedBladeResonanceItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);
    }

    @Override
    public String bladeId() {
        return ChargedBladeSpec.RESONANCE.fullId();
    }

    @Override
    public String nameKey() {
        return ChargedBladeSpec.RESONANCE.nameKey();
    }

    @Override
    public int maxEnergy() {
        return AppliedSlashConfig.chargedBladeMaxResonance();
    }

    @Override
    public ChargedBladeSpec spec() {
        return ChargedBladeSpec.RESONANCE;
    }
}
