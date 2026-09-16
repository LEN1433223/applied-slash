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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile int maxTypes = DEFAULT_MAX_TYPES;
    private static volatile int unstackableMaxTypes = DEFAULT_UNSTACKABLE_MAX_TYPES;
    private static volatile boolean stripRuntimeState = true;
    private static volatile boolean acceptBeyondMaxTypes = false;

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
        } catch (IllegalStateException configNotLoaded) {
            // 配置还没加载:沿用默认值
        }
    }
}
