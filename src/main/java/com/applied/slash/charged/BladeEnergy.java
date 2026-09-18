package com.applied.slash.charged;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.SlashBladeBlades;

import net.minecraft.world.item.ItemStack;

/**
 * 拔刀剑的**能量读写 API**(全部纯函数,便于 GameTest 直接断言)。
 *
 * <p>能量存在<b>本模组自己的持久化物品组件</b> {@code applied_slash:blade_energy}(Int)上 ——
 * 不是世界侧表、不是重锋的 {@code BladeStateData}(那是 record,字段固定,加不了字段,
 * 见事实表 §8.2)。组件随物品整栈往返,所以「存进元件再取出」天然保留能量。
 *
 * <p>本类**与具体刀无关**:任何拔刀剑(数据包刀「莉莉」、重锋本体的刀、附属模组的刀)都可以带这份能量,
 * 唯一的写入方是充能方块({@code charged.block.BladeChargerBlockEntity})。
 * 5 把自设充能刀删除后,这里不再有 {@code instanceof ChargedBladeItem} 之类的判定,
 * 也不再联动重锋的 {@code sealed} 位(那是"能量耗尽就不能用"的旧语义,随充能刀一起移除)。
 *
 * <p><b>为什么它会进 AE2 的存储键</b>:普通物品组件会写进 {@code AEItemKey} 的键载荷
 * ⇒ 每个能量值都是一个新类型。缓解手段是入库**量化**(见 {@link #quantize} 与
 * {@code cell/BladeIdentity} 的两个调用点)。
 */
public final class BladeEnergy {
    private BladeEnergy() {
    }

    /**
     * 读取能量。<b>组件缺席 → 0</b>。
     */
    public static int get(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return stack.getOrDefault(AppliedSlashComponents.BLADE_ENERGY.get(), 0);
    }

    /**
     * 上限:所有拔刀剑共用配置项 {@code chargerMaxEnergy};不是拔刀剑 → 0。
     *
     * <p>返回 0 的所有调用点都必须按「无上限 = 不可写入」处理({@link #set} 会因此拒绝写入)。
     */
    public static int max(ItemStack stack) {
        if (!SlashBladeBlades.isSlashBlade(stack)) {
            return 0;
        }
        return Math.max(1, AppliedSlashConfig.chargerMaxEnergy());
    }

    /**
     * 写入能量:clamp 到 {@code [0, max]}。
     *
     * <p>非拔刀剑直接返回,什么都不写 —— 避免把能量写进别人家的物品。
     */
    public static void set(ItemStack stack, int energy) {
        int max = max(stack);
        if (max <= 0) {
            return;
        }
        stack.set(AppliedSlashComponents.BLADE_ENERGY.get(), Math.max(0, Math.min(energy, max)));
    }

    /**
     * 量化档位宽度:{@code max(1, max / levels)}。
     *
     * <p>默认上限 800 + 10 档 ⇒ 档宽 80。
     */
    public static int perBucket(int max, int levels) {
        if (max <= 0) {
            return 1;
        }
        return Math.max(1, max / Math.max(1, levels));
    }

    /**
     * 把能量量化到档位(**唯一实现点**,tooltip / 测试 / 入库规范化共用)。
     *
     * <p>档位集合 {@code {0, p, 2p, …, (levels-1)p} ∪ {max}}(≤ levels+1 个取值),
     * 一律**向下取整**(只减不增 ⇒ 不可能靠「入库再取出」刷能量);
     * {@code keepFull} 为真时满能量取精确值,保证「满能量的刀取出仍是满的」。
     *
     * <p>损失上界 = 档宽 - 1(由用户拍板接受):
     * <pre>
     * if (max &lt;= 0) return 0;
     * if (energy &lt;= 0) return 0;
     * if (keepFull &amp;&amp; energy &gt;= max) return max;
     * int p = max(1, max / levels);
     * int b = min(levels - 1, (energy - 1) / p);
     * return b * p;
     * </pre>
     * 例(默认上限 800,档宽 80):{@code quantize(137, 800, 10, true) == 80} —— 137 落在 {80, 160) 区间。
     */
    public static int quantize(int energy, int max, int levels, boolean keepFull) {
        if (max <= 0) {
            return 0;
        }
        if (energy <= 0) {
            return 0;
        }
        if (keepFull && energy >= max) {
            return max;
        }
        int levelCount = Math.max(1, levels);
        int width = perBucket(max, levelCount);
        int bucket = Math.min(levelCount - 1, (energy - 1) / width);
        return bucket * width;
    }

    /**
     * 该刀是否<b>需要</b>量化(是拔刀剑、组件存在、且当前值不是档位值)。
     *
     * <p>这是控制键膨胀的开关:{@code BladeIdentity.mayNormalize} 返回 false 时
     * {@code insert} 根本不会调用 {@code normalize}(见 {@code SlashBladeCellInventory} 第 110 行),
     * 原始精确能量键会被原样收下 ⇒ 每个能量值一个类型,量化形同虚设。
     *
     * <p><b>独立于 {@code stripRuntimeState}</b>:绝不能挂在该开关之下,
     * 否则关掉「剥离运行时状态」会连带关掉量化,静默制造类型爆炸。
     */
    public static boolean needsQuantizing(ItemStack stack) {
        int max = max(stack);
        if (max <= 0 || !has(stack)) {
            // 不是拔刀剑,或组件缺席:读取语义是 0,但那是「尚未充能」而不是「需要量化」。
            return false;
        }
        int energy = get(stack);
        return energy != quantize(energy, max, quantizeLevels(), quantizeKeepFull());
    }

    /**
     * 入库规范化时用:能量 → 档位值。
     *
     * <p>调用点是 {@code cell/BladeIdentity#normalize}(必须与 {@code mayNormalize} 成对修改)。
     */
    public static void quantizeInPlace(ItemStack stack) {
        int max = max(stack);
        if (max <= 0 || !has(stack)) {
            return;
        }
        int current = get(stack);
        int quantized = quantize(current, max, quantizeLevels(), quantizeKeepFull());
        if (quantized != current) {
            set(stack, quantized);
        }
    }

    /** 当前量化档数(配置项 {@code bladeEnergyQuantizeLevels},默认 10)。 */
    public static int quantizeLevels() {
        return Math.max(2, AppliedSlashConfig.bladeEnergyQuantizeLevels());
    }

    /** 满能量是否取精确值(配置项 {@code bladeEnergyQuantizeKeepFull},默认 true)。 */
    public static boolean quantizeKeepFull() {
        return AppliedSlashConfig.bladeEnergyQuantizeKeepFull();
    }

    /** 组件是否存在于该物品栈上(供 tooltip / 诊断使用)。 */
    public static boolean has(ItemStack stack) {
        return !stack.isEmpty() && stack.has(AppliedSlashComponents.BLADE_ENERGY.get());
    }
}
