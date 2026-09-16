package com.applied.slash.charged;

import net.minecraft.world.item.ItemStack;

/**
 * 「没能量能不能用」的判定,<b>抽成纯函数</b>以便 GameTest 直接断言(不需要造真事件 / 真玩家)。
 *
 * <p>事实表 §9.1 修正了原方案的门禁设计:
 * <ul>
 *   <li>{@code AttackEntityEvent} 与 {@code onLeftClickEntity} 在 {@code Player.attack} 的
 *       全类扫描里<b>扫不到触发点</b> ⇒ 不能作保证(本类因此不提供「取消攻击事件」的判定);</li>
 *   <li>主保证是**原生 sealed**(能量归零置封印,由重锋自己拦),第二保证是
 *       {@code SlashBladeEvent.UpdateAttackEvent} 里把伤害压到 0;</li>
 *   <li>本类的 {@link #shouldZeroDamage} 就是第二保证的判定入口。</li>
 * </ul>
 *
 * <p>本类**只读不写**:所有能量写入都只发生在 {@link ChargedBladeEnergy#set} 的那几个调用点上,
 * 其中唯一的「扣费」写点是 {@link ChargedBladeItem#hurtEnemy}。
 */
public final class ChargedBladeGate {
    private ChargedBladeGate() {
    }

    /** 该刀此刻是否可用(能量 ≥ 每次消耗)。 */
    public static boolean canUse(ItemStack stack) {
        return ChargedBladeEnergy.canUse(stack);
    }

    /**
     * 第二保证:是否应当在 {@code SlashBladeEvent.UpdateAttackEvent} 里把伤害压到 0
     * (即「没能量」)。
     *
     * <p>为什么这条能成立:该事件是重锋在 {@code ItemSlashBlade.getDefaultAttributeModifiers}
     * 内部 {@code NeoForge.EVENT_BUS.post(...)} 之后**采用其返回值**作为
     * ATTACK_DAMAGE 修饰的(实证偏移 186–235)⇒ 我们改它 = 直接改这一击的伤害。
     */
    public static boolean shouldZeroDamage(ItemStack stack) {
        return !canUse(stack);
    }

    /** {@link #shouldZeroDamage} 的同义入口:该刀此刻是否被能量门禁挡住(等价于 {@code !canUse})。 */
    public static boolean isBlocked(ItemStack stack) {
        return shouldZeroDamage(stack);
    }
}
