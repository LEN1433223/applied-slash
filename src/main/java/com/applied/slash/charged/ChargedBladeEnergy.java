package com.applied.slash.charged;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.AppliedSlashConfig;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.world.item.ItemStack;

/**
 * 充能刀的**能量读写 API**(全部纯函数,便于 GameTest 直接断言)。
 *
 * <p>能量存在<b>本模组自己的持久化物品组件</b> {@code applied_slash:blade_energy}(Int)上 ——
 * 不是世界侧表、不是重锋的 {@code BladeStateData}(那是 record,字段固定,加不了字段,
 * 见事实表 §8.2)。组件随物品整栈往返,所以「存进元件再取出」天然保留能量。
 *
 * <p><b>为什么它会进 AE2 的存储键</b>:普通物品组件会写进 {@code AEItemKey} 的键载荷
 * ⇒ 每个能量值都是一个新类型。缓解手段是入库**量化**(见 {@link #quantize} 与
 * {@code cell/BladeIdentity} 的两个调用点)。
 *
 * <h2>前置模组的引用边界(类加载隔离)</h2>
 * 本类里的每个 {@link BladeStateAccess} 调用点都在 {@code instanceof ChargedBladeItem} 判定<b>之后</b>
 * ({@link #set} 与 {@link #quantizeInPlace})。这条不变式保证:只有当栈确定是本模组充能刀时,
 * 才会去解析重锋的类 —— 而那一刻 {@code ItemSlashBlade} 必然已被成功加载。
 * 调用 {@link #get}/{@link #max}/{@link #needsQuantizing} 的外部路径里,
 * {@code cell/BladeIdentity#needsEnergyQuantizing} 还有一层 {@code ModList.isLoaded("slashblade")} 门卫。
 */
public final class ChargedBladeEnergy {
    private ChargedBladeEnergy() {
    }

    /**
     * 读取能量。<b>组件缺席 → 0</b>。
     *
     * <p>注意区分:「组件缺席 → 0」是**读取**语义,而 {@link ChargedBladeFactory#ensureInitialized}
     * 里的「缺席 = 满」是**初始化**语义(否则 {@code /give} 出来的刀一上手就是废刀)。
     */
    public static int get(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return stack.getOrDefault(AppliedSlashComponents.BLADE_ENERGY.get(), 0);
    }

    /**
     * 上限:由 {@link ChargedBladeItem#maxEnergy()}(读配置)决定;不是充能刀 → 0。
     *
     * <p>返回 0 的所有调用点都必须按「无上限 = 不可用」处理({@link #set} 会因此拒绝写入)。
     */
    public static int max(ItemStack stack) {
        if (stack.getItem() instanceof ChargedBladeItem blade) {
            return Math.max(0, blade.maxEnergy());
        }
        return 0;
    }

    /**
     * 写入能量:clamp 到 {@code [0, max]},**并同步 sealed 位**
     * ({@code energy == 0 ⇔ sealed == true},事实表 §9.1 的主保证)。
     *
     * <p>sealed 必须在这里一并写死,否则「能量同为档位值但 sealed 不同」的两把刀会变成两个存储键。
     *
     * <p>非充能刀直接返回,什么都不写(避免把 0 能量写进别人家物品,也避免碰重锋的类)。
     */
    public static void set(ItemStack stack, int energy) {
        if (!(stack.getItem() instanceof ChargedBladeItem blade)) {
            return;
        }
        int clamped = Math.max(0, Math.min(energy, Math.max(0, blade.maxEnergy())));
        stack.set(AppliedSlashComponents.BLADE_ENERGY.get(), clamped);
        // sealed 是能量的只读投影(PLAN §5.1):归零即封印,充上即解封。
        // 这里对 BladeStateAccess 的引用只会在「已确认是本模组充能刀」之后执行(见类注释的边界)。
        BladeStateAccess.of(stack).ifPresent(state -> state.setSealed(clamped <= 0));
    }

    /** 本次攻击消耗(配置项 {@code chargedBladeAttackCost},默认 1)。 */
    public static int costPerAttack() {
        return Math.max(1, AppliedSlashConfig.chargedBladeAttackCost());
    }

    /**
     * 是否还能使用:能量 ≥ 每次攻击消耗。
     *
     * <p>这是**四层门禁里的 L1/L2 共用的判定入口**(分别由 {@link ChargedBladeItem#use} 与
     * {@link ChargedBladeGate#shouldZeroDamage} 调用)。
     */
    public static boolean canUse(ItemStack stack) {
        return get(stack) >= costPerAttack();
    }

    /**
     * 命中一次的实际扣费(纯数据操作,<b>不判端</b>)。
     *
     * <p>「只在服务端扣」这条约束由唯一调用点 {@link ChargedBladeItem#hurtEnemy} 的
     * {@code instanceof ServerLevel} 分支承担 —— 拆成这个方法是为了让它可被 GameTest 直接断言
     * (不需要造真玩家/真世界),而不是为了让别处也能调用。
     *
     * @return 扣费后的能量(能量不足时原样返回,不会扣成负数、不会置 sealed)
     */
    public static int spendOnHit(ItemStack stack) {
        int cost = costPerAttack();
        int energy = get(stack);
        if (energy < cost) {
            return energy;
        }
        set(stack, energy - cost);
        return Math.max(0, energy - cost);
    }

    /**
     * 量化档位宽度:{@code max(1, max / levels)}。
     *
     * <p>5 把刀默认上限 200/400/800/1600/3200 都能被 10 整除 ⇒ 档宽 20/40/80/160/320。
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
     * <p>算法由 PLAN §7.1 定稿,**不得改成"无损"**(损失上界 = 档宽 - 1,由用户拍板接受):
     * <pre>
     * if (max &lt;= 0) return 0;
     * if (energy &lt;= 0) return 0;
     * if (keepFull &amp;&amp; energy &gt;= max) return max;
     * int p = max(1, max / levels);
     * int b = min(levels - 1, (energy - 1) / p);
     * return b * p;
     * </pre>
     * 举一个具体例子(涌流,上限 800,档宽 80):{@code quantize(137, 800, 10, true) == 80}
     * —— 137 落在 {80, 160) 区间,向下取到 80。**注意** PLAN §10.2 的手工验收表里写作「变成 130」,
     * 那是笔误(130 不是 80 的整数倍);算法以 §7.1 为准。
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
     * 该刀是否<b>需要</b>量化(组件存在、是充能刀、且当前值不是档位值)。
     *
     * <p>这是控制键膨胀的开关:{@code BladeIdentity.mayNormalize} 返回 false 时
     * {@code insert} 根本不会调用 {@code normalize}(见 {@code SlashBladeCellInventory} 第 110 行),
     * 原始精确能量键会被原样收下 ⇒ 每个能量值一个类型,量化形同虚设。
     *
     * <p><b>独立于 {@code stripRuntimeState}</b>:绝不能挂在该开关之下,
     * 否则关掉「剥离运行时状态」会连带关掉量化,静默制造类型爆炸。
     *
     * <p>调用方必须已通过 {@code ModList.isLoaded("slashblade")} 门卫 —— 本方法里的
     * {@code instanceof ChargedBladeItem} 会触发 {@code ItemSlashBlade} 的类加载。
     */
    public static boolean needsQuantizing(ItemStack stack) {
        if (!(stack.getItem() instanceof ChargedBladeItem blade)) {
            return false;
        }
        if (!has(stack)) {
            // 组件缺席:读取语义是 0,但那是「尚未初始化」而不是「需要量化」——
            // 真正的初始化交给 ChargedBladeFactory.ensureInitialized(缺席 = 满)。
            return false;
        }
        int energy = get(stack);
        return energy != quantize(energy, blade.maxEnergy(), quantizeLevels(), quantizeKeepFull());
    }

    /**
     * 入库规范化时用:能量 → 档位值 + 按档位写死 sealed 位。
     *
     * <p>调用点是 {@code cell/BladeIdentity#normalize}(必须与 {@code mayNormalize} 成对修改)。
     *
     * <p><b>即使能量已经是档位值,也要把 sealed 强行对齐一次</b>:否则「能量同为 80 但 sealed
     * 一真一假」的两把刀仍然会被算成两个存储键,量化的收益会被吃掉一半。
     */
    public static void quantizeInPlace(ItemStack stack) {
        if (!(stack.getItem() instanceof ChargedBladeItem blade)) {
            return;
        }
        if (!has(stack)) {
            return;
        }
        int max = blade.maxEnergy();
        int current = get(stack);
        int quantized = quantize(current, max, quantizeLevels(), quantizeKeepFull());
        if (quantized != current) {
            set(stack, quantized);
            return;
        }
        // 值已是档位值:只补 sealed 投影(不写组件,避免无谓的组件变更通知)
        BladeStateAccess.of(stack).ifPresent(state -> state.setSealed(quantized <= 0));
    }

    /** 当前量化档数(配置项 {@code chargedBladeQuantizeLevels},默认 10)。 */
    public static int quantizeLevels() {
        return Math.max(2, AppliedSlashConfig.chargedBladeQuantizeLevels());
    }

    /** 满能量是否取精确值(配置项 {@code chargedBladeQuantizeKeepFull},默认 true)。 */
    public static boolean quantizeKeepFull() {
        return AppliedSlashConfig.chargedBladeQuantizeKeepFull();
    }

    /** 组件是否存在于该物品栈上(供 tooltip / 诊断使用;不构造 ItemStack)。 */
    public static boolean has(ItemStack stack) {
        return !stack.isEmpty() && stack.has(AppliedSlashComponents.BLADE_ENERGY.get());
    }
}
