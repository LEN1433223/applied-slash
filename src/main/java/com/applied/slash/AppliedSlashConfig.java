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
    // 刀能量 / 充能方块
    // ------------------------------------------------------------------
    // 与上面的存储项共用**同一个 SPEC 与同一个 refresh**:单一配置文件 = 单一加载/重载路径,
    // 刻意不做第二个 SPEC(否则会出现「两套默认值 + 两条 reload 路径」的维护陷阱)。
    //
    // 5 把自设充能刀已删除(见 THIRD-PARTY-NOTICES.md 与 README):
    // 能量上限从「每把刀一条」收敛为**单条** chargerMaxEnergy,任何拔刀剑(含数据包刀「莉莉」)
    // 都用同一条上限;攻击消耗/封印相关的键随之删除(没有消费者了)。
    // ==================================================================

    /** 任意拔刀剑的能量上限(量化档宽 = 本项 / levels)。 */
    public static final ModConfigSpec.IntValue CHARGER_MAX_ENERGY_VALUE = BUILDER
            .comment("拔刀剑充能器给一把刀充到的能量上限(所有拔刀剑共用同一条上限)。")
            .defineInRange("chargerMaxEnergy", 800, 1, 1_000_000);

    /** 充能方块每 tick 往刀里充的点数。 */
    public static final ModConfigSpec.IntValue CHARGER_CHARGE_PER_TICK_VALUE = BUILDER
            .comment("拔刀剑充能器每 tick 给刀充的能量点数。")
            .defineInRange("chargerChargePerTick", 1, 1, 1000);

    /** 每点能量的 AE 成本。 */
    public static final ModConfigSpec.IntValue CHARGER_AE_PER_POINT_VALUE = BUILDER
            .comment("每 1 点刀能量需要的 AE 数量。满充满默认真空 800 点 = 80 kAE。")
            .defineInRange("chargerAePerPoint", 100, 1, 1_000_000);

    /** 充能方块自身的空闲耗电(交给网格节点的 setIdlePowerUsage,节点在线即耗)。 */
    public static final ModConfigSpec.DoubleValue CHARGER_IDLE_DRAIN_VALUE = BUILDER
            .comment("拔刀剑充能器的空闲耗电(AE/t),交给 AE2 网格节点托管(节点在线即耗)。")
            .defineInRange("chargerIdleDrain", 1.0, 0.0, 1000.0);

    /** 量化档数:入库时把能量向下取整到 max/levels 的整数倍。 */
    public static final ModConfigSpec.IntValue BLADE_ENERGY_QUANTIZE_LEVELS_VALUE = BUILDER
            .comment("带能量的刀入库时的量化档数:能量被向下取整到 max/levels 的整数倍。",
                    "量化是**必需的**:能量组件会进入 AE2 的存储键,不量化则每个能量值一个类型。",
                    "默认 10 ⇒ 每把刀最多 11 个取值(0, p, 2p … 9p, max),损失上界 = 档宽 - 1(≤ 上限的 10%)。",
                    "嫌损失大可调到 40(损失 ≤ 2.5%,代价是每把刀最多 41 个键)。")
            .defineInRange("bladeEnergyQuantizeLevels", 10, 2, 100);

    /** 满能量取精确值:保证「满能量的刀入库取出仍是满的」(最常见的场景无损)。 */
    public static final ModConfigSpec.BooleanValue BLADE_ENERGY_QUANTIZE_KEEP_FULL_VALUE = BUILDER
            .comment("满能量的刀量化时取精确值(不向下取整),保证「满能量入库→取出仍满」。")
            .define("bladeEnergyQuantizeKeepFull", true);

    // ==================================================================
    // 充能器的自发发电:烧虞美人产 KAE
    // ------------------------------------------------------------------
    // 这一组让充能器**不依赖 ME 网络**也能工作:燃料槽里的虞美人按进度缓慢烧掉,
    // 产出的 KAE 进本机缓冲(就是界面右下那条缓冲条),再由此给刀充能。
    // 它只是「第三级来源」——网格给得出电时一滴油都不烧(见 serverTick 的顺序)。
    // ==================================================================

    /** 一朵虞美人产出的 KAE(AE)总量。 */
    public static final ModConfigSpec.IntValue CHARGER_POPPY_AE_VALUE = BUILDER
            .comment("烧掉 1 朵虞美人产出的能量(AE)。",
                    "默认 5000:满充默认上限(800 点 × 100 AE/点 = 80 kAE)需要 16 朵虞美人。",
                    "与 chargerPoppyBurnTicks 一起决定产电速率:AE/刻 = 本项 ÷ 燃烧刻数。")
            .defineInRange("chargerPoppyAe", 5_000, 1, 100_000_000);

    /** 一朵虞美人的燃烧时长(刻)。 */
    public static final ModConfigSpec.IntValue CHARGER_POPPY_BURN_TICKS_VALUE = BUILDER
            .comment("烧掉 1 朵虞美人所需的刻数(20 刻 = 1 秒)。",
                    "默认 200(=10 秒/朵),即产电 25 AE/刻;而充电开销是",
                    "chargerChargePerTick × chargerAePerPoint = 1 × 100 = 100 AE/刻,",
                    "所以单朵供不上——缓冲会缓慢下降,想只靠虞美人就要一次放一把(16 朵 = 80 kAE)。",
                    "把本项调到 50 即 100 AE/刻,可与默认充电速率 1:1 打平。")
            .defineInRange("chargerPoppyBurnTicks", 200, 1, 72_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile int maxTypes = DEFAULT_MAX_TYPES;
    private static volatile int unstackableMaxTypes = DEFAULT_UNSTACKABLE_MAX_TYPES;
    private static volatile boolean stripRuntimeState = true;
    private static volatile boolean acceptBeyondMaxTypes = false;

    // ---- 刀能量 / 充能方块(8 项,见上面的定义)----
    private static volatile int chargerMaxEnergy = 800;
    private static volatile int chargerChargePerTick = 1;
    private static volatile int chargerAePerPoint = 100;
    private static volatile double chargerIdleDrain = 1.0;
    private static volatile int bladeEnergyQuantizeLevels = 10;
    private static volatile boolean bladeEnergyQuantizeKeepFull = true;
    // ---- 自发发电:烧虞美人产 KAE ----
    private static volatile int chargerPoppyAe = 5_000;
    private static volatile int chargerPoppyBurnTicks = 200;

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
    // 刀能量 / 充能方块的访问器(8 项)
    // ------------------------------------------------------------------

    /** 任意拔刀剑的能量上限(≥1)。 */
    public static int chargerMaxEnergy() {
        return chargerMaxEnergy;
    }

    /** 充能方块每 tick 充入的点数。 */
    public static int chargerChargePerTick() {
        return chargerChargePerTick;
    }

    /** 每点刀能量的 AE 成本。 */
    public static int chargerAePerPoint() {
        return chargerAePerPoint;
    }

    /** 充能方块的节点空闲耗电(AE/t)。 */
    public static double chargerIdleDrain() {
        return chargerIdleDrain;
    }

    /** 量化档数(≥2)。 */
    public static int bladeEnergyQuantizeLevels() {
        return bladeEnergyQuantizeLevels;
    }

    /** 满能量是否取精确值。 */
    public static boolean bladeEnergyQuantizeKeepFull() {
        return bladeEnergyQuantizeKeepFull;
    }

    /** 烧掉 1 朵虞美人产出的 AE(见 {@link #CHARGER_POPPY_AE_VALUE})。 */
    public static int chargerPoppyAe() {
        return chargerPoppyAe;
    }

    /** 烧掉 1 朵虞美人所需的刻数(见 {@link #CHARGER_POPPY_BURN_TICKS_VALUE})。 */
    public static int chargerPoppyBurnTicks() {
        return chargerPoppyBurnTicks;
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
            // 刀能量 / 充能方块(8 项)
            chargerMaxEnergy = CHARGER_MAX_ENERGY_VALUE.get();
            chargerChargePerTick = CHARGER_CHARGE_PER_TICK_VALUE.get();
            chargerAePerPoint = CHARGER_AE_PER_POINT_VALUE.get();
            chargerIdleDrain = CHARGER_IDLE_DRAIN_VALUE.get();
            bladeEnergyQuantizeLevels = BLADE_ENERGY_QUANTIZE_LEVELS_VALUE.get();
            bladeEnergyQuantizeKeepFull = BLADE_ENERGY_QUANTIZE_KEEP_FULL_VALUE.get();
            chargerPoppyAe = CHARGER_POPPY_AE_VALUE.get();
            chargerPoppyBurnTicks = CHARGER_POPPY_BURN_TICKS_VALUE.get();
        } catch (IllegalStateException configNotLoaded) {
            // 配置还没加载:沿用默认值
        }
    }
}
