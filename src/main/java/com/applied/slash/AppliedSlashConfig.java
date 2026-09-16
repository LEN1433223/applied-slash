package com.applied.slash;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的可调项。
 *
 * <p>{@code maxTypes} 之所以必须可调:下面的数字是**实测**出来的(见 PLAN v2 §2.3 与自检输出)——
 * AE2 每 tick 会重建全网库存缓存,成本与网络里可见的键总数成正比且超线性:
 *
 * <pre>
 *   1,000 键 → 0.074 ms/tick      5,000 键 → 0.751 ms/tick
 *   2,000 键 → 0.245 ms/tick     10,000 键 → 1.790 ms/tick
 *                                20,000 键 → 5.535 ms/tick
 * </pre>
 *
 * 这段成本发生在 AE2 自己的 {@code KeyCounter} 里,不在本模组,我们无法消除,只能控制"网络里有多少把刀"。
 * 所以默认给一个安全值(5000),需要囤上万把的服主可以自行上调到 20000。
 */
public final class AppliedSlashConfig {
    private static final int DEFAULT_MAX_TYPES = 5_000;
    /** 「不可堆叠物品存储元件」的默认类型上限(独立于拔刀剑元件的 5000,理由见下面的注释)。 */
    private static final int DEFAULT_UNSTACKABLE_MAX_TYPES = 2_000;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_TYPES_VALUE = BUILDER
            .comment("单个元件可容纳的不同拔刀剑数量上限。",
                    "实测每 tick 全网库存重建成本:2000 类型 ≈ 0.25 ms,5000 ≈ 0.75 ms,10000 ≈ 1.8 ms,20000 ≈ 5.5 ms。",
                    "只要网络里有终端/存储监视器/等级发射器在线,该成本每 tick 都会发生,请按服务器规模取舍。")
            .defineInRange("maxTypes", DEFAULT_MAX_TYPES, 64, BladeCellSpec.MAX_TYPES);

    /**
     * 「不可堆叠物品存储元件」(applied_slash:unstackable_item_cell)的类型上限。
     *
     * <p>与 {@link #MAX_TYPES_VALUE} <b>彼此独立</b>:两个元件是不同的物品、不同的配置项,
     * 谁也不会改动另一个的容量。
     *
     * <p>默认 2000 而不是 5000 的原因是不可堆叠物品的**类型密度**远高于拔刀剑:
     * 工具/盔甲/附魔书/药水几乎每一件都带独立组件(耐久、附魔、自定义名称),一格常常就是一个类型,
     * 而 2000 已经是 31 块 AE2 原生 1k 单元(63 类型/块)的总和。成本曲线(实测,见 PLAN §2):
     * <pre>
     *   2,000 键 → ≈0.25 ms/tick    5,000 键 → ≈0.75 ms/tick    20,000 键 → ≈5.5 ms/tick
     * </pre>
     * 该成本发生在 AE2 自己的 {@code KeyCounter} 里(每 tick 重建全网库存缓存),不在本模组,只能靠上限控制。
     */
    public static final ModConfigSpec.IntValue UNSTACKABLE_MAX_TYPES_VALUE = BUILDER
            .comment("单个「不可堆叠物品存储元件」可容纳的不同物品数量上限(不同组件即不同类型)。",
                    "实测每 tick 全网库存重建成本:2000 类型 ≈ 0.25 ms,5000 ≈ 0.75 ms,10000 ≈ 1.8 ms,20000 ≈ 5.5 ms。",
                    "只要网络里有终端/存储监视器/等级发射器在线,该成本每 tick 都会发生,请按服务器规模取舍。",
                    "与拔刀剑元件的 maxTypes 互不影响;绝对上限同为 BladeCellSpec.MAX_TYPES(20000)。")
            .defineInRange("unstackableMaxTypes", DEFAULT_UNSTACKABLE_MAX_TYPES, 64, BladeCellSpec.MAX_TYPES);

    public static final ModConfigSpec.BooleanValue STRIP_RUNTIME_STATE_VALUE = BUILDER
            .comment("入库时剥离拔刀剑的运行时状态组件(blade runtime state)。",
                    "开启:同一把刀在不同战斗状态下算同一个存储键 —— 类型数不虚增、每个键更小(推荐)。",
                    "关闭:组件全保留,但同一把刀可能占据多个类型,且每键载荷更大。")
            .define("stripRuntimeState", true);

    public static final ModConfigSpec.BooleanValue ACCEPT_BEYOND_MAX_TYPES_VALUE = BUILDER
            .comment("类型数达到 maxTypes 之后,是否仍然接收新的拔刀剑。",
                    "false(默认):拒收新类型 —— 网络会把刀存到别的盘,或让它留在原处;每 tick 的重建成本被 maxTypes 钉死。",
                    "true:一律收下,拔刀剑从此不会再进其它盘;但类型数不再有上限。",
                    "     每 tick 全网库存重建成本随类型数线性上升(实测 20000 类型 ≈ 5.5 ms),请按服务器规模权衡。",
                    "补充:AE2 的插入第一遍扫描只问 isPreferredStorageFor(早于任何优先级排序),",
                    "     所以本盘对拔刀剑的抢占与其它盘的全局优先级无关,这个开关是唯一会让刀跑到别处的原因。")
            .define("acceptBeyondMaxTypes", false);

    // ==================================================================
    // 充能拔刀剑 / 充能方块(PLAN-CHARGED-BLADES §4.4,共 11 项)
    // ------------------------------------------------------------------
    // 与上面的存储项共用**同一个 SPEC 与同一个 refresh**:单一配置文件 = 单一加载/重载路径,
    // 刻意不做第二个 SPEC(否则会出现「两套默认值 + 两条 reload 路径」的维护陷阱)。
    // ==================================================================

    /** 每次命中消耗的能量(5 把刀共用)。 */
    public static final ModConfigSpec.IntValue CHARGED_BLADE_ATTACK_COST_VALUE = BUILDER
            .comment("充能拔刀剑每次**命中**消耗的能量(只有真正命中才扣,扣费唯一入口是 hurtEnemy)。")
            .defineInRange("chargedBladeAttackCost", 1, 1, 100);

    /** 5 把刀各自的能量上限。上限是**每把独立**的,因为它们决定量化档宽(max/levels)。 */
    public static final ModConfigSpec.IntValue CHARGED_BLADE_MAX_PULSE_VALUE = BUILDER
            .comment("充能刀·脉冲(applied_slash:charged_blade_pulse)的能量上限。")
            .defineInRange("chargedBladeMaxPulse", 200, 1, 1_000_000);

    public static final ModConfigSpec.IntValue CHARGED_BLADE_MAX_RESONANCE_VALUE = BUILDER
            .comment("充能刀·谐振(applied_slash:charged_blade_resonance)的能量上限。")
            .defineInRange("chargedBladeMaxResonance", 400, 1, 1_000_000);

    public static final ModConfigSpec.IntValue CHARGED_BLADE_MAX_SURGE_VALUE = BUILDER
            .comment("充能刀·涌流(applied_slash:charged_blade_surge)的能量上限。")
            .defineInRange("chargedBladeMaxSurge", 800, 1, 1_000_000);

    public static final ModConfigSpec.IntValue CHARGED_BLADE_MAX_OVERCHARGE_VALUE = BUILDER
            .comment("充能刀·超荷(applied_slash:charged_blade_overcharge)的能量上限。")
            .defineInRange("chargedBladeMaxOvercharge", 1600, 1, 1_000_000);

    public static final ModConfigSpec.IntValue CHARGED_BLADE_MAX_SINGULARITY_VALUE = BUILDER
            .comment("充能刀·奇点(applied_slash:charged_blade_singularity)的能量上限。")
            .defineInRange("chargedBladeMaxSingularity", 3200, 1, 1_000_000);

    /** 充能方块每 tick 往刀里充的点数。 */
    public static final ModConfigSpec.IntValue CHARGED_BLADE_CHARGE_PER_TICK_VALUE = BUILDER
            .comment("拔刀剑充能器每 tick 给刀充的能量点数。")
            .defineInRange("chargedBladeChargePerTick", 1, 1, 1000);

    /** 每点能量的 AE 成本 ⇒ 满充 3200 点 = 320 kAE;1 点/t 时约 160 秒。 */
    public static final ModConfigSpec.IntValue CHARGED_BLADE_AE_PER_POINT_VALUE = BUILDER
            .comment("每 1 点刀能量需要的 AE 数量。满充奇点(3200 点)在默认 100 下 ≈ 320 kAE。")
            .defineInRange("chargedBladeAePerPoint", 100, 1, 1_000_000);

    /** 充能方块自身的空闲耗电(交给网格节点的 setIdlePowerUsage,节点在线即耗)。 */
    public static final ModConfigSpec.DoubleValue CHARGED_BLADE_IDLE_DRAIN_VALUE = BUILDER
            .comment("拔刀剑充能器的空闲耗电(AE/t),交给 AE2 网格节点托管(节点在线即耗)。")
            .defineInRange("chargedBladeIdleDrain", 1.0, 0.0, 1000.0);

    /** 量化档数:入库时把能量向下取整到 max/levels 的整数倍。 */
    public static final ModConfigSpec.IntValue CHARGED_BLADE_QUANTIZE_LEVELS_VALUE = BUILDER
            .comment("充能刀入库时的量化档数:能量被向下取整到 max/levels 的整数倍。",
                    "量化是**必需的**:能量组件会进入 AE2 的存储键,不量化则每个能量值一个类型。",
                    "默认 10 ⇒ 每把刀最多 11 个取值(0, p, 2p … 9p, max),损失上界 = 档宽 - 1(≤ 上限的 10%)。",
                    "嫌损失大可调到 40(损失 ≤ 2.5%,代价是每把刀最多 41 个键)。")
            .defineInRange("chargedBladeQuantizeLevels", 10, 2, 100);

    /** 满能量取精确值:保证「满能量的刀入库取出仍是满的」(最常见的场景无损)。 */
    public static final ModConfigSpec.BooleanValue CHARGED_BLADE_QUANTIZE_KEEP_FULL_VALUE = BUILDER
            .comment("满能量的刀量化时取精确值(不向下取整),保证「满能量入库→取出仍满」。")
            .define("chargedBladeQuantizeKeepFull", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile int maxTypes = DEFAULT_MAX_TYPES;
    private static volatile int unstackableMaxTypes = DEFAULT_UNSTACKABLE_MAX_TYPES;
    private static volatile boolean stripRuntimeState = true;
    private static volatile boolean acceptBeyondMaxTypes = false;

    // ---- 充能刀 / 充能方块(11 项,见上面的定义)----
    private static volatile int chargedBladeAttackCost = 1;
    private static volatile int chargedBladeMaxPulse = 200;
    private static volatile int chargedBladeMaxResonance = 400;
    private static volatile int chargedBladeMaxSurge = 800;
    private static volatile int chargedBladeMaxOvercharge = 1600;
    private static volatile int chargedBladeMaxSingularity = 3200;
    private static volatile int chargedBladeChargePerTick = 1;
    private static volatile int chargedBladeAePerPoint = 100;
    private static volatile double chargedBladeIdleDrain = 1.0;
    private static volatile int chargedBladeQuantizeLevels = 10;
    private static volatile boolean chargedBladeQuantizeKeepFull = true;

    private AppliedSlashConfig() {
    }

    public static int maxTypes() {
        return maxTypes;
    }

    /** 「不可堆叠物品存储元件」的类型上限(见 {@link #UNSTACKABLE_MAX_TYPES_VALUE});与 {@link #maxTypes()} 无关。 */
    public static int unstackableMaxTypes() {
        return unstackableMaxTypes;
    }

    public static boolean stripRuntimeState() {
        return stripRuntimeState;
    }

    /** 类型数超限后是否继续收下(见 {@link #ACCEPT_BEYOND_MAX_TYPES_VALUE})。 */
    public static boolean acceptBeyondMaxTypes() {
        return acceptBeyondMaxTypes;
    }

    // ------------------------------------------------------------------
    // 充能刀 / 充能方块的访问器(11 项)
    // ------------------------------------------------------------------

    /** 每次命中消耗的能量(≥1)。 */
    public static int chargedBladeAttackCost() {
        return chargedBladeAttackCost;
    }

    /** 充能刀·脉冲的上限。 */
    public static int chargedBladeMaxPulse() {
        return chargedBladeMaxPulse;
    }

    /** 充能刀·谐振的上限。 */
    public static int chargedBladeMaxResonance() {
        return chargedBladeMaxResonance;
    }

    /** 充能刀·涌流的上限。 */
    public static int chargedBladeMaxSurge() {
        return chargedBladeMaxSurge;
    }

    /** 充能刀·超荷的上限。 */
    public static int chargedBladeMaxOvercharge() {
        return chargedBladeMaxOvercharge;
    }

    /** 充能刀·奇点的上限。 */
    public static int chargedBladeMaxSingularity() {
        return chargedBladeMaxSingularity;
    }

    /** 充能方块每 tick 充入的点数。 */
    public static int chargedBladeChargePerTick() {
        return chargedBladeChargePerTick;
    }

    /** 每点刀能量的 AE 成本。 */
    public static int chargedBladeAePerPoint() {
        return chargedBladeAePerPoint;
    }

    /** 充能方块的节点空闲耗电(AE/t)。 */
    public static double chargedBladeIdleDrain() {
        return chargedBladeIdleDrain;
    }

    /** 量化档数(≥2)。 */
    public static int chargedBladeQuantizeLevels() {
        return chargedBladeQuantizeLevels;
    }

    /** 满能量是否取精确值。 */
    public static boolean chargedBladeQuantizeKeepFull() {
        return chargedBladeQuantizeKeepFull;
    }

    /**
     * 配置加载/重载时刷新缓存值。
     *
     * <p><b>注意参数类型不能省</b>:NeoForge 的事件总线靠泛型参数推断事件类型,而 <b>lambda 不携带泛型签名</b>,
     * 写 {@code addListener(e -> refresh())} 会让类型推断落到 {@code Object} 上(监听器要么漏掉 ModConfigEvent,
     * 要么被每个事件都调用一次)。这里改成"参数类型明确的方法 + 方法引用",推断才是确定的。
     */
    public static void refresh(net.neoforged.fml.event.config.ModConfigEvent event) {
        try {
            maxTypes = MAX_TYPES_VALUE.get();
            unstackableMaxTypes = UNSTACKABLE_MAX_TYPES_VALUE.get();
            stripRuntimeState = STRIP_RUNTIME_STATE_VALUE.get();
            acceptBeyondMaxTypes = ACCEPT_BEYOND_MAX_TYPES_VALUE.get();
            // 充能刀 / 充能方块(11 项)
            chargedBladeAttackCost = CHARGED_BLADE_ATTACK_COST_VALUE.get();
            chargedBladeMaxPulse = CHARGED_BLADE_MAX_PULSE_VALUE.get();
            chargedBladeMaxResonance = CHARGED_BLADE_MAX_RESONANCE_VALUE.get();
            chargedBladeMaxSurge = CHARGED_BLADE_MAX_SURGE_VALUE.get();
            chargedBladeMaxOvercharge = CHARGED_BLADE_MAX_OVERCHARGE_VALUE.get();
            chargedBladeMaxSingularity = CHARGED_BLADE_MAX_SINGULARITY_VALUE.get();
            chargedBladeChargePerTick = CHARGED_BLADE_CHARGE_PER_TICK_VALUE.get();
            chargedBladeAePerPoint = CHARGED_BLADE_AE_PER_POINT_VALUE.get();
            chargedBladeIdleDrain = CHARGED_BLADE_IDLE_DRAIN_VALUE.get();
            chargedBladeQuantizeLevels = CHARGED_BLADE_QUANTIZE_LEVELS_VALUE.get();
            chargedBladeQuantizeKeepFull = CHARGED_BLADE_QUANTIZE_KEEP_FULL_VALUE.get();
        } catch (IllegalStateException configNotLoaded) {
            // 配置还没加载:沿用默认值
        }
    }
}
