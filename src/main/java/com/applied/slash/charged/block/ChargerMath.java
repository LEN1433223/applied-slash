package com.applied.slash.charged.block;

/**
 * 充能方块的**纯函数**(没有任何 MC / AE2 类型 ⇒ GameTest 可以直接断言,不需要真世界)。
 *
 * <p>每 tick 的语义(PLAN §6.3):按 {@code (max - energy)} 精确取电,不预留内部缓存 ——
 * 避免「多取了 AE 却没地方放」。不足一点就不充(不制造半点余数语义)。
 */
public final class ChargerMath {
    /** 槽里没刀(或不是拔刀剑):不耗电。 */
    public static final int STATE_IDLE = 0;
    /** 正在充能。 */
    public static final int STATE_CHARGING = 1;
    /** 已满:不耗电。 */
    public static final int STATE_FULL = 2;
    /** 网络缺电(实取 AE 不足一点):不充、不扣。 */
    public static final int STATE_NO_AE = 3;
    /**
     * 正在烧燃料自产 KAE(虞美人 → 缓冲)。
     *
     * <p>只在「网格没电 **且** 本机缓冲凑不齐一点」时才可能出现 —— 有电可用的那几种情形
     * 一律记 {@link #STATE_CHARGING}。界面把它与 CHARGING 同色显示,因为它同样是「正在工作」。
     */
    public static final int STATE_GENERATING = 4;

    private ChargerMath() {
    }

    /**
     * 一次充电结算的结果。
     *
     * @param newEnergy  结算后的刀能量
     * @param aeConsumed 本次真正从网络取走的 AE(≤ 请求量)
     * @param state      {@link #STATE_IDLE} / {@link #STATE_CHARGING} / {@link #STATE_FULL} / {@link #STATE_NO_AE}
     */
    public record ChargeTick(int newEnergy, long aeConsumed, int state) {
    }

    /**
     * 一次充电结算(纯函数)。
     *
     * <p>定稿算法(PLAN §6.3):
     * <pre>
     * if (energy &gt;= max)                     return (energy, 0, FULL);
     * int  needPoints = min(perTick, max - energy);
     * long needAe     = (long) needPoints * aePerPoint;
     * if (availableAe &lt; aePerPoint)          return (energy, 0, NO_AE);
     * long takeAe     = min(needAe, availableAe);
     * int  points     = (int) min(needPoints, takeAe / aePerPoint);
     * return (energy + points, (long) points * aePerPoint, CHARGING);
     * </pre>
     *
     * <p>三条刻意保留的性质:
     * <ol>
     *   <li><b>不超发</b>:请求量按 {@code max - energy} 精确算,绝不出现「多取了 AE 却没地方放」;</li>
     *   <li><b>半点不充</b>:可用 AE 连一点都凑不齐时直接 NO_AE,不制造半点的余数语义
     *       (否则会把不足一点的 AE 白扣掉);</li>
     *   <li><b>不越界</b>:{@code points ≤ needPoints},所以 {@code newEnergy ≤ max}。</li>
     * </ol>
     *
     * <p>槽为空 / 不是拔刀剑时由调用方判 {@link #STATE_IDLE},不进入本函数。
     * {@code aePerPoint} 理论上有配置下限 1,这里仍做防御性 clamp(避免除零)。
     */
    public static ChargeTick chargeTick(int energy, int max, long availableAe, int aePerPoint, int perTick) {
        if (max <= 0 || energy >= max) {
            return new ChargeTick(energy, 0L, STATE_FULL);
        }
        int perPoint = Math.max(1, aePerPoint);
        int needPoints = Math.min(Math.max(0, perTick), max - energy);
        if (needPoints <= 0) {
            return new ChargeTick(energy, 0L, STATE_CHARGING);
        }
        long needAe = (long) needPoints * perPoint;
        if (availableAe < perPoint) {
            return new ChargeTick(energy, 0L, STATE_NO_AE);
        }
        long takeAe = Math.min(needAe, availableAe);
        int points = (int) Math.min(needPoints, takeAe / perPoint);
        if (points <= 0) {
            return new ChargeTick(energy, 0L, STATE_NO_AE);
        }
        return new ChargeTick(energy + points, (long) points * perPoint, STATE_CHARGING);
    }

    /**
     * 把 {@code (energy, max)} 折算成 0..13 的能量条宽度(界面自绘用,与物品能量条同口径:
     * {@code Item#getBarWidth} 也调用本方法)。
     */
    public static int barWidth(int energy, int max) {
        if (max <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(energy, max));
        return (int) Math.round(clamped / (double) max * 13.0);
    }
}
