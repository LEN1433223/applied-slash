package com.applied.slash.charged;

import com.applied.slash.AppliedSlashConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/**
 * 充能刀·脉冲(charged_blade_pulse,默认上限 200)。
 *
 * <p>5 把刀是**互相独立的类**而不是「1 个类 5 个实例」:每把将来可能独立覆写 SA / 外观 / 上限调整,
 * 与项目现有「平行实现、显式类」的风格一致(见 {@code cell/UnstackableIdentity} 的注释)。
 */
public final class ChargedBladePulseItem extends ChargedBladeItem {

    public ChargedBladePulseItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);
    }

    @Override
    public String bladeId() {
        return ChargedBladeSpec.PULSE.fullId();
    }

    @Override
    public String nameKey() {
        return ChargedBladeSpec.PULSE.nameKey();
    }

    @Override
    public int maxEnergy() {
        return AppliedSlashConfig.chargedBladeMaxPulse();
    }

    @Override
    public ChargedBladeSpec spec() {
        return ChargedBladeSpec.PULSE;
    }
}
