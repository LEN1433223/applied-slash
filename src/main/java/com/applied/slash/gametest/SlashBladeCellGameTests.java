package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 存储元件的 GameTest 入口。跑法:{@code gradlew runGameTestServer}(无显示环境亦可,跑完自动退出)。
 *
 * <p>模板:结构 SNBT 不是数据包资源,而是运行目录下的 {@code gameteststructures/empty.snbt}
 * (见 {@code StructureTemplateManager.loadFromTestStructures} → {@code loadFromSnbt(id, Paths.get("gameteststructures"))}),
 * 构建脚本会在跑测试前自动把它放进运行目录。
 *
 * <p>{@code @PrefixGameTestTemplate(false)}:NeoForge 默认会把模板名加上测试类名前缀
 * (不用它就会去找 {@code applied_slash:slashbladecellgametests.empty}),关掉后统一用 {@code empty}。
 *
 * <p><b>本类刻意不出现任何 AE2 类型</b>:NeoForge 在开发模式会在客户端初始化时扫描并
 * {@code Class.forName} 加载 {@code @GameTestHolder} 类,而 {@code getDeclaredMethods()}
 * 会解析该类**全部方法的签名** —— 一旦签名里含 AE2 类型,没装 AE2 的开发客户端就会
 * {@code NoClassDefFoundError} 崩在启动阶段(已实测)。因此所有实现都在
 * {@link GameTestSupport}(非 holder,不会被扫描)里,这里只留 {@code GameTestHelper} 入口。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class SlashBladeCellGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 400;

    private SlashBladeCellGameTests() {
    }

    /** 非拔刀剑:一律拒收,且不声明优先存储。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void rejectsNonBlades(GameTestHelper helper) {
        GameTestSupport.rejectsNonBlades(helper);
    }

    /** 拔刀剑:入库成功、声明优先存储、同刀重复只累加数量不增类型。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void acceptsBladesAndClaimsPriority(GameTestHelper helper) {
        GameTestSupport.acceptsBladesAndClaimsPriority(helper);
    }

    /** 运行时状态组件:同一把刀在不同战斗状态下必须是同一个存储键。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void normalizesRuntimeState(GameTestHelper helper) {
        GameTestSupport.normalizesRuntimeState(helper);
    }

    /**
     * SIMULATE 取出探测回归:库里存的是规范化键(剥离运行时状态),而发起方送来的键带运行时状态
     * —— AE2 取出第一步的 SIMULATE 必须 > 0,否则"终端里看得见、点了却毫无反应"。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void simulateExtractAcceptsRuntimeKey(GameTestHelper helper) {
        GameTestSupport.simulateExtractAcceptsRuntimeKey(helper);
    }

    /**
     * 类型上限 + 重新解析一致性(回归:计数曾只做增量维护,重新解析后归零 → 上限形同虚设)。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void enforcesLimitAndSurvivesReload(GameTestHelper helper) {
        GameTestSupport.enforcesLimitAndSurvivesReload(helper);
    }

    /** 刀库载荷缺失(跨存档复制/存档回滚):表现为空盘,不崩、可正常入库。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void survivesMissingPayload(GameTestHelper helper) {
        GameTestSupport.survivesMissingPayload(helper);
    }

    /**
     * 重登后仍可取:重新解析刀库之后,对列表里列出的第一个键做 SIMULATE 必须 &gt; 0、MODULATE 必须真取到
     * (回归"终端里看得见、点它却毫无反应" —— 根因是查找只认 hashCode 桶)。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void extractsReloadedContents(GameTestHelper helper) {
        GameTestSupport.extractsReloadedContents(helper);
    }

    /** 内容相同但不同实例的键也能取:入库用一个实例、取出用另一个实例,两条路径都必须成功。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void equivalentKeyInstanceExtractsStoredEntry(GameTestHelper helper) {
        GameTestSupport.equivalentKeyInstanceExtractsStoredEntry(helper);
    }

    /** 测试元件工厂({@code /appliedslash testcell} 用的那条路径):产出的刀必须互不相同且带刀身数据。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void testCellFactoryProducesDistinctBlades(GameTestHelper helper) {
        GameTestSupport.testCellFactoryProducesDistinctBlades(helper);
    }

    /** 不可堆叠物品元件:收下不可堆叠物品、拒收可堆叠物品,且组件即身份(绝不规范化)。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void unstackableCellAcceptsUnstackablesOnly(GameTestHelper helper) {
        GameTestSupport.unstackableCellAcceptsUnstackablesOnly(helper);
    }

    /** 不可堆叠物品元件:拔刀剑必须被拒收(它自己就是不可堆叠的,所以考的是"排除拔刀剑"那一句)。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void unstackableCellRejectsSlashBlades(GameTestHelper helper) {
        GameTestSupport.unstackableCellRejectsSlashBlades(helper);
    }

    /** 不可堆叠物品元件:拒收存储元件类物品(本模组两个元件),且不误伤普通不可堆叠物品。 */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void unstackableCellRejectsCells(GameTestHelper helper) {
        GameTestSupport.unstackableCellRejectsCells(helper);
    }
}
