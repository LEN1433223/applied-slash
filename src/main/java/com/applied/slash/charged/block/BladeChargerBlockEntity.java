package com.applied.slash.charged.block;

import java.util.EnumSet;
import java.util.Set;

import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.charged.ChargedBladeEnergy;
import com.applied.slash.charged.ChargedBladeFactory;
import com.applied.slash.charged.ChargedBladeItem;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.AEBaseBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 充能方块的方块实体:1 个刀槽 + 一块 AE 缓冲 + 网格节点 + {@code ContainerData}。
 *
 * <h2>为什么是这个基类与这条路线(事实表 §9.2 的设计修正)</h2>
 * {@code AENetworkInvBlockEntity} / {@code AENetworkBlockEntity} 的成员枚举在取证时
 * <b>javap 无输出</b> ⇒ 不再作为依赖。改用已实证的组合:
 * <ul>
 *   <li>基类 {@code appeng.blockentity.AEBaseBlockEntity}(构造器
 *       {@code (BlockEntityType<?>, BlockPos, BlockState)} 已实证);</li>
 *   <li>自行实现 {@code appeng.api.networking.IInWorldGridNodeHost}
 *       (唯一抽象方法 {@code IGridNode getGridNode(Direction)});</li>
 *   <li>{@code GridHelper.createManagedNode(owner, listener)} 建受管节点,
 *       再用 {@code create / setFlags / setExposedOnSides / setIdlePowerUsage} 配置;</li>
 *   <li>{@code addService(IAEPowerStorage.class, this)} 把本 BE 的能量缓冲挂到网络上 ——
 *       这条仍保留(缓冲是**第二来源**,也给外界一个可见的能量存储)。</li>
 * </ul>
 *
 * <h2>取电路径(第一来源 = 主动从网格抽取)</h2>
 * 早期实现只从自己的缓冲 {@link #storedAe} 取电,依赖「AE2 的能量服务会主动往
 * {@code addService} 注册的缓冲注入富余能量」这一<b>未实证</b>行为;若不成立,接上 ME 网络也永远充不上电。
 * 现在改为 AE2 机器的标准路径:每 tick 先向网格能量服务要电
 * ({@code IGrid#getEnergyService()} → {@code IEnergySource#extractAEPower(double, Actionable, PowerMultiplier)},
 * 都是已实证签名),**网格给不出电时**才回落到本机缓冲。结算全程记账平衡:实取量里没兑成整点的部分退回缓冲。
 *
 * <h2>为什么槽位直接实现 {@link Container} 而不是引入 AE2 的 InternalInventory</h2>
 * N9(内部库存/菜单底座)未闭合,而 {@code Container} 是原版接口、语义最小:
 * 一个刀槽 = 一个 {@code Container}(大小 1)。菜单那边的 {@code Slot} 直接指向本对象,
 * 槽内容由原版菜单机制自动同步(服务端 {@code broadcastChanges} → 客户端),不需要额外的同步代码。
 *
 * <h2>类加载隔离</h2>
 * 本类引用 {@code appeng.*},只能被 {@link BladeChargerRegistry} 引用,而后者只在
 * {@code AppliedSlashAe2.register} 里被调用 —— AE2 缺席时本类根本不会被加载。
 */
public class BladeChargerBlockEntity extends AEBaseBlockEntity
        implements IInWorldGridNodeHost, IAEPowerStorage, ContainerData, Container {

    /** 刀槽的下标(菜单里槽 0 = 刀槽)。 */
    public static final int SLOT_BLADE = 0;

    /** {@link ContainerData} 暴露给菜单的 4 个 int:能量 / 上限 / 状态 / 每点 AE 成本。 */
    public static final int DATA_ENERGY = 0;
    public static final int DATA_MAX = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_AE_PER_POINT = 3;
    private static final int DATA_COUNT = 4;

    /** 本机缓冲上限(配置项里没有这一项;200 kAE 足够吃下默认档的一批注入)。 */
    private static final double MAX_STORED_AE = 200_000.0;
    /** 单次注入里允许的最小量:小于它就不必走 clamp 逻辑。 */
    private static final double MIN_MEANINGFUL_AE = 1.0e-4;

    /** 持久化键(写在 NeoForge 的 {@code getPersistentData()} 容器里,见 {@link #saveAdditional})。 */
    private static final String TAG_STORED_AE = "applied_slash_charger_ae";
    private static final String TAG_BLADE = "applied_slash_charger_blade";

    /** 网格节点(AE2 侧);{@link #initializeNode()} 里创建。 */
    private IManagedGridNode mainNode;
    /** {@link #getGridNode(Direction)} 的返回值;由 {@link #initializeNode()} 填。 */
    private IGridNode gridNode;
    /**
     * 节点是否**确实创建过**({@code mainNode.create(...)} 成功返回之后才置 true)。
     *
     * <p>为什么不能只用 {@code mainNode != null}:那一行在 {@code create(...)} <b>之前</b>就赋值了,
     * 若 {@code create(...)} 抛异常,{@code mainNode} 非空但内部没有真节点 ——
     * 那时候调 {@code destroy()} 是未定义行为(区块卸载路径可能 NPE)。
     * 本标志只由 {@link #initializeNode()} 置位、由 {@link #setRemoved()} 消费。
     */
    private boolean nodeCreated;

    /** 本机 AE 缓冲(AE2 的能量服务会自动向这里注入,我们放电给刀)。 */
    private double storedAe;
    /** 本机缓冲上限(见 {@link #MAX_STORED_AE})。 */
    private final double maxStoredAe = MAX_STORED_AE;

    /** 刀槽(空栈 = 没刀)。 */
    private ItemStack blade = ItemStack.EMPTY;
    /** 上一 tick 的结算状态(0..3,见 {@link ChargerMath})。 */
    private int lastState = ChargerMath.STATE_IDLE;
    /** 是否已经从持久化容器里读过一次(惰性读,见 {@link #restorePersistentState()})。 */
    private boolean restored;

    public BladeChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    /**
     * 两参构造器:供 {@code BlockEntityType.Builder.of(BladeChargerBlockEntity::new, block)} 使用。
     *
     * <p>该 Builder 的工厂形状是 {@code (BlockPos, BlockState) -> T}(没有 type 参数),
     * 所以必须额外提供这一条路径,否则构造器引用无处可绑(与类型 = 三参构造器同一条路线)。
     *
     * <p>类型从已有的注册里取({@link BladeChargerRegistry#BLADE_CHARGER_BE})。这是安全的:
     * 本构造器只在方块实体<b>真正被创建时</b>执行,那时 DeferredHolder 早已在
     * mod 构造期注册完毕并填充;而注册 lambda 本身只做方法引用绑定,不会构造方块实体。
     */
    public BladeChargerBlockEntity(BlockPos pos, BlockState blockState) {
        this(BladeChargerRegistry.BLADE_CHARGER_BE.get(), pos, blockState);
    }

    // ------------------------------------------------------------------
    // 网格节点
    // ------------------------------------------------------------------

    /**
     * 建立并配置网格节点(**幂等**:已经建过就直接返回)。
     *
     * <p>调用时机:{@link #serverTick} 的第一行,所以只有服务端、且方块实体已经在世界里
     * ({@code level != null})时才会真正建节点 —— 这正是 {@code IManagedGridNode.create} 的前提。
     * 不在构造函数里建:那时 {@code level}/{@code worldPosition} 还不完整。
     *
     * <p>// NEEDS_HOST_CHECK: 骨架里给的另一条路是 {@code GridHelper.onFirstTick(this, this::initializeNode)}
     * (通常挂在 {@code AEBaseBlockEntity#onReady()} 里)。这里**刻意不用它**:一是
     * {@code onReady()} 是否真的会被 AE2 在服务端调用没有实证,二是 {@code onFirstTick} 重复调用时
     * 的行为(会不会重复执行回调)也没实证。用「serverTick 里判一次 null」的行为是确定的,
     * 且 {@code create()} 只会被调用一次。
     */
    private void initializeNode() {
        if (mainNode != null || level == null || level.isClientSide) {
            return;
        }
        mainNode = GridHelper.createManagedNode(this, new IGridNodeListener<BladeChargerBlockEntity>() {
            /** AE2 只管一件事:节点状态变了要存档(事实表 §4:这是唯一的抽象方法)。 */
            @Override
            public void onSaveChanges(BladeChargerBlockEntity nodeOwner, IGridNode node) {
                nodeOwner.setChanged();
            }
        });
        // 占 1 个频道(与 AE2 用电机器一致;节点标志走 GridFlags,PLAN §6.2 的决策)
        mainNode.setFlags(GridFlags.REQUIRE_CHANNEL);
        Set<Direction> exposedSides = EnumSet.allOf(Direction.class);
        mainNode.setExposedOnSides(exposedSides);
        // 空闲耗电交给节点自己托管(节点在线即耗),我们不去改缓冲
        mainNode.setIdlePowerUsage(AppliedSlashConfig.chargedBladeIdleDrain());
        // 事实表 §8.3 的答案:这样 AE2 的能量服务才会往我们的缓冲注入
        mainNode.addService(IAEPowerStorage.class, this);
        mainNode.create(level, worldPosition);
        gridNode = mainNode.getNode();
        // 到这里节点才算真的建好:setRemoved() 的 destroy() 只认这个标志(见字段上的说明)
        nodeCreated = true;
    }

    /**
     * 事实表 §9.2 已实证:这是 {@code IInWorldGridNodeHost} 唯一的抽象方法。
     *
     * <p>节点还没建好(首 tick 之前)时返回 null —— 那正是「这里还没有节点」的标准表达。
     */
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode == null ? null : mainNode.getNode();
    }

    /**
     * 方块实体被移除(挖掘 / 区块卸载 / 世界关闭)时,把受管网格节点从网格里摘掉。
     *
     * <p><b>顺序与保护</b>:先 {@code super.setRemoved()}(基类自己的清理),
     * 再在**节点确实创建过**时调 {@code mainNode.destroy()}(已实证签名)。
     * 判定用的是显式的 {@link #nodeCreated} 标志而不是 {@code mainNode != null} ——
     * 后者在 {@code create(...)} 之前就已赋值,拿它当「节点存在」会在
     * {@code create(...)} 失败过的实例上调用 {@code destroy()},区块卸载路径可能 NPE。
     *
     * <p><b>刻意不做的事</b>:不掉刀。区块卸载同样走 {@code setRemoved()},
     * 在那里掉刀等于「走远一点就掉一地刀」;掉刀只由 {@link #dropContents()}(玩家挖掘)负责。
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mainNode != null && nodeCreated) {
            nodeCreated = false;
            mainNode.destroy();
        }
    }

    /**
     * AE2 的 {@code AEBaseBlockEntity} 惯例方法:这个方块实体对应的物品。
     *
     * <p>这里<b>刻意不写 {@code @Override}</b>:该基类是否有这个方法、是否抽象,
     * 取证未覆盖。不写注解时两种情况都能编译(存在则实现/覆盖,不存在则是无害的普通方法)。
     * <br>// NEEDS_HOST_CHECK: {@code AEBaseBlockEntity} 的完整方法表没有取证
     * ({@code javap -p appeng.blockentity.AEBaseBlockEntity} 即可补齐);补齐后应决定这两个方法
     * 是否加 {@code @Override}、以及基类是否要求实现别的抽象方法。
     */
    public Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    /**
     * 方块实体的显示名(界面标题/调试用)。
     *
     * <p>同 {@link #getItemFromBlockEntity()}:<b>不写 {@code @Override}</b>,
     * 因为 {@code AEBaseBlockEntity} 是否实现了 {@code Nameable.getName()} 未取证 ——
     * 写了注解而基类没有该方法就是编译不过;不写注解则两种情况都能编译。
     */
    public Component getName() {
        return Component.translatable("block.applied_slash." + BladeChargerRegistry.BLADE_CHARGER_ID);
    }

    // ------------------------------------------------------------------
    // IAEPowerStorage —— 事实表 §5/§8.3:注册成节点服务后 AE2 会自动注入
    // ------------------------------------------------------------------

    /**
     * 往本机缓冲注入 AE(AE2 的能量服务会调用)。
     *
     * <p>按 {@code amt} 与剩余空间 clamp,返回**实际接受的量**;
     * {@code Actionable.SIMULATE} 只计算不写入(这是 AE2 的标准探测语义)。
     */
    @Override
    public double injectAEPower(double amt, Actionable mode) {
        if (!(amt > MIN_MEANINGFUL_AE) || mode == null) {
            return 0;
        }
        double space = maxStoredAe - storedAe;
        if (space <= 0) {
            return 0;
        }
        double accepted = Math.min(amt, space);
        if (mode == Actionable.MODULATE) {
            storedAe += accepted;
            setChanged();
        }
        return accepted;
    }

    /**
     * 从本机缓冲抽取 AE。
     *
     * <p>必须实现:{@code IAEPowerStorage extends IEnergySource},而
     * {@code IEnergySource#extractAEPower(double, Actionable, PowerMultiplier)} 是抽象方法 ——
     * 只实现 {@code injectAEPower} / {@code getAEMaxPower} / {@code getAECurrentPower} /
     * {@code isAEPublicPowerStorage} / {@code getPowerFlow} 会让本类成为抽象类。
     *
     * <p><b>语义(刻意收紧,不给网络供能)</b>:
     * <ul>
     *   <li>{@code amount <= 0} ⇒ 返回 0;</li>
     *   <li>只从<b>本方块自己的缓冲</b>({@link #storedAe})里取,绝不去动 ME 网络里其它机器的能量
     *       (本方块是纯消费者:能量的进由 AE2 的能量服务注入,出只用于给自己的刀充能);</li>
     *   <li>可取量 = {@code min(需求, 当前缓冲)};{@code Actionable.SIMULATE} 只计算不修改,
     *       {@code Actionable.MODULATE} 才真正扣减缓冲并标脏;</li>
     *   <li>返回<b>实际取出量</b>。</li>
     * </ul>
     *
     * <p><b>门禁与 {@link #getPowerFlow()} 统一</b>:用 {@code AccessRestriction#isAllowExtraction()}
     * 判定,而不是写 {@code == READ_WRITE} 这类等值比较(事实表 §4 的明确要求)。
     * 当前 {@code getPowerFlow()} 返回 {@code WRITE}(只允许写入),所以外部<b>抽不走</b>本缓冲的能量;
     * 给刀充能走的是本类内部的 {@link #serverTick},不经过这个方法。
     */
    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        if (amount <= 0) {
            return 0;
        }
        // 只进不出:getPowerFlow() 不允许抽取时,外部一律抽不走(语义在方法自身守住,不依赖调用者守规矩)
        if (!getPowerFlow().isAllowExtraction()) {
            return 0;
        }
        double extracted = Math.min(amount, storedAe);
        if (extracted <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            storedAe -= extracted;
            setChanged();
        }
        return extracted;
    }

    @Override
    public double getAEMaxPower() {
        return maxStoredAe;
    }

    @Override
    public double getAECurrentPower() {
        return storedAe;
    }

    /**
     * 本缓冲是否作为「公共能量存储」暴露给网络。
     *
     * <p>保持 true:缓冲是**兜底来源**,而它要被填上只能靠 AE2 的能量服务注入或外部写入,
     * 所以这条必须敞开。注意:现在取电的第一来源是 {@link #serverTick} 里对网格能量服务的主动抽取,
     * <b>不再依赖**这条注入行为成立**;即使 AE2 从不往这里注入,只要能上网就充得上电。
     */
    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    /**
     * 访问权限:**纯消费者 ⇒ 只允许写入(网络可以充进来,不能抽走)**。
     *
     * <p>判定一律走判定方法:{@link #extractAEPower} 用 {@code getPowerFlow().isAllowExtraction()},
     * <b>不写</b> {@code getPowerFlow() == AccessRestriction.READ_WRITE} 这类等值比较
     * (`isAllowExtraction()` 已实证存在,语义就是「允许被抽取」)。
     *
     * <p>// NEEDS_HOST_CHECK: 常量的语义方向(WRITE = 允许写入本存储)按 AE2 惯例解释,
     * 但 host 这轮只实证了 {@code AccessRestriction.isAllowExtraction()/isAllowInsertion()} 两个判定方法
     * 存在,没实证四个常量的含义。本文件的做法是:**判定一律用判定方法**,
     * 所以即使常量方向被换错,也不会出现「代码读起来是只进不出、实际被抽干」这种隐性错误 ——
     * 只会表现为本方法返回值与预期不符,在 GameTest {@code chargerChargeMath} 里会直接断言出来。
     * 若 host 实证 WRITE 不允许注入,改成 {@code READ_WRITE} 即可(仅此一行 + 相应注释)。
     *
     * <p>// NEEDS_HOST_CHECK: 本方法返回 WRITE(不许抽取)是否会影响 AE2 能量服务把本缓冲当作
     * **可抽取的电源**聚合进 {@code IEnergyService},未实证。若 AE2 忽略该门禁,{@link #drawFromGrid}
     * 可能抽到自己的缓冲(记账仍然平衡,不会凭空产生 AE,只是「网格优先」退化为「缓冲优先」);
     * 若要彻底排除,可给 {@code extractAEPower} 再加一条「调用来源不是本 BE」的判定。
     */
    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.WRITE;
    }

    // ------------------------------------------------------------------
    // 每 tick 的充能结算(纯函数在 ChargerMath 里)
    // ------------------------------------------------------------------

    /**
     * 服务端每 tick 一次:把 AE 换成刀上的能量。**来源有两级,网格优先**。
     *
     * <p>接线方式:{@link BladeChargerBlock#getTicker} 返回的 ticker 每 tick 调它一次
     * (方块实现了 {@code EntityBlock})。
     *
     * <p>流程(PLAN §6.3 定稿 + 本轮取电路径修正):
     * <ol>
     *   <li>惰性恢复存档内容 + 惰性建节点(都需要 {@code level != null},所以放在这里而不是构造函数);</li>
     *   <li>槽里没刀 / 不是充能刀 ⇒ IDLE,不耗电;</li>
     *   <li>刀自愈一次(能量组件缺席视作满,与背包里的刀同一条规则);</li>
     *   <li>算需求:{@code needPoints = min(perTick, max - energy)};满 / 需求为 0 ⇒ 只更新状态行并返回
     *       (与 {@link ChargerMath#chargeTick} 的前两段同口径:FULL 与「perTick ≤ 0 时的 CHARGING」);</li>
     *   <li><b>第一来源 · 网格</b>:{@link #drawFromGrid} 主动向网格能量服务抽取
     *       ({@code IGrid#getEnergyService()} → {@code IEnergySource#extractAEPower}),
     *       拿到 {@code granted > 0} 点就直接涨刀能量并 <b>本 tick 结束</b>;</li>
     *   <li><b>第二来源 · 本机缓冲</b>:网格缺席 / 网格没电 / 网格只给出 0 点 ⇒
     *       沿用 {@link ChargerMath#chargeTick} 从 {@link #storedAe} 结算(语义一字未改:
     *       不足一点判 NO_AE、一点 AE 都不扣);</li>
     *   <li>状态行语义不变:IDLE / FULL / NO_AE / CHARGING,从网格拿到电一律记 CHARGING。</li>
     * </ol>
     *
     * <p><b>为什么不能只靠本机缓冲</b>:旧实现假定「AE2 的能量服务会主动往
     * {@code addService(IAEPowerStorage.class, this)} 的缓冲注入富余能量」,
     * 这一点从未被实证 —— 若不成立,接上 ME 网络也永远充不上电,「必须用 AE2 供电」的需求就落空。
     * 现在走 AE2 机器的标准路径(主动抽取),缓冲退化为兜底,两种行为下都能充上电。
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BladeChargerBlockEntity be) {
        if (be == null || level == null || level.isClientSide) {
            return;
        }
        be.restorePersistentState();
        be.initializeNode();

        ItemStack blade = be.blade;
        if (blade.isEmpty() || !(blade.getItem() instanceof ChargedBladeItem)) {
            be.lastState = ChargerMath.STATE_IDLE;
            return;
        }
        ChargedBladeFactory.ensureInitialized(blade);

        int energy = ChargedBladeEnergy.get(blade);
        int max = ChargedBladeEnergy.max(blade);
        int aePerPoint = Math.max(1, AppliedSlashConfig.chargedBladeAePerPoint());
        int perTick = AppliedSlashConfig.chargedBladeChargePerTick();

        // 需求为零(满 / 上限非法 / perTick ≤ 0):不取电,只把状态行写成与旧口径一致的值
        if (max <= 0 || energy >= max) {
            be.lastState = ChargerMath.STATE_FULL;
            return;
        }
        int needPoints = Math.min(Math.max(0, perTick), max - energy);
        if (needPoints <= 0) {
            be.lastState = ChargerMath.STATE_CHARGING;
            return;
        }

        // 第一来源:网格。抽到电就直接充,不再碰本机缓冲(避免同一 tick 双份记账)
        int fromGrid = be.drawFromGrid(needPoints, aePerPoint);
        if (fromGrid > 0) {
            ChargedBladeEnergy.set(blade, energy + fromGrid);
            be.setChanged();
            be.lastState = ChargerMath.STATE_CHARGING;
            return;
        }

        // 第二来源:本机缓冲兜底(ChargerMath 的既有语义原样保留,GameTest 覆盖不变;
        // aePerPoint 已在上面 clamp 到 ≥1,与 chargeTick 内部那次 clamp 等价)
        ChargerMath.ChargeTick tick = ChargerMath.chargeTick(energy, max, (long) be.storedAe, aePerPoint, perTick);
        if (tick.state() == ChargerMath.STATE_CHARGING) {
            be.storedAe = Math.max(0, be.storedAe - tick.aeConsumed());
            ChargedBladeEnergy.set(blade, tick.newEnergy());
            be.setChanged();
        }
        be.lastState = tick.state();
    }

    /**
     * 从网格的能量服务里**主动**抽取 AE(第一来源),返回这一 tick 真正能兑换给刀的**点数**。
     *
     * <p>返回 0 表示「这一 tick 网格这条路没给电」(没节点 / 节点没就绪 / 没网格 / 网络无电 /
     * 凑不够一点)——调用方据此回落到本机缓冲,不会卡死。
     *
     * <p><b>结算记账(不许凭空生成、不许销毁 AE)</b>:
     * <ol>
     *   <li>{@code SIMULATE} 只探测可用量,<b>不动账</b>;据此算 {@code points}(只取需要且拿得出的点数);</li>
     *   <li>{@code MODULATE} 的返回值 {@code got} 才是**真正从网络扣走**的 AE(AE2 的能量存储是 double,
     *       实取可能少于请求 —— 例如网络被别的机器同时抽走);</li>
     *   <li>{@code granted = min(points, floor(got / perPoint))} 是给刀的点数;</li>
     *   <li>{@code leftover = got - granted * perPoint} 是实取量里没兑成整点的部分,
     *       **原量(含小数)退回本机缓冲**并 clamp 到 {@link #maxStoredAe}。</li>
     * </ol>
     * 展开即 {@code got == granted * perPoint + leftover}:网络少掉的 AE 全部落在「刀涨的点数 × 每点成本」
     * 或「缓冲里多出来的 AE」上,两边相加恒等于被扣掉的总量。
     */
    private int drawFromGrid(int needPoints, int perPoint) {
        IManagedGridNode node = mainNode;
        if (node == null || !nodeCreated || !node.isReady()) {
            return 0;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return 0;
        }
        IEnergyService energyService = grid.getEnergyService();
        if (energyService == null) {
            return 0;
        }

        long needAe = (long) needPoints * perPoint;
        double available = energyService.extractAEPower(needAe, Actionable.SIMULATE, PowerMultiplier.ONE);
        if (!(available > 0)) {
            // 网络无电(或节点没上线):返回 0,让调用方走本机缓冲兜底
            return 0;
        }
        int points = (int) Math.min(needPoints, (long) available / perPoint);
        if (points <= 0) {
            return 0;
        }

        long want = (long) points * perPoint;
        double got = energyService.extractAEPower(want, Actionable.MODULATE, PowerMultiplier.ONE);
        if (!(got > 0)) {
            // 竞态:SIMULATE 说够、MODULATE 却一点没扣到(当 tick 被别的机器抽干)⇒ 走兜底
            return 0;
        }
        int granted = (int) Math.min(points, (long) got / perPoint);

        // 没兑成整点的实取量退回缓冲 —— 这是「不许销毁 AE」的关键一步
        double leftover = got - (double) granted * perPoint;
        if (leftover > 0) {
            // // NEEDS_HOST_CHECK: 缓冲已满时这一小段(< perPoint)无处可去会被丢弃。
            // 触发条件苛刻(缓冲正好顶满 + MODULATE 只给到零头);若 host 认为必须严格零损耗,
            // 可改为「按缓冲剩余空间反推本次最多请求多少 AE」再抽取。
            storedAe = Math.min(maxStoredAe, storedAe + leftover);
            setChanged();
        }
        return granted;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    /**
     * 存档:缓冲 AE + 槽里的刀。
     *
     * <p><b>为什么写进 {@code getPersistentData()} 而不是直接写 {@code tag}</b>:
     * 读路径无法自己实现 —— {@code AEBaseBlockEntity#loadAdditional} 是 <b>final</b>(事实表 §4),
     * 我们不能覆写它。NeoForge 给 {@code BlockEntity} 加的 {@code getPersistentData()} 容器
     * 则是由基类在 {@code saveAdditional}/{@code loadAdditional} 里**对称**读写的
     * (见本项目的 NeoForge 补丁:存档时写 {@code "NeoForgeData"},读取时再填回),
     * 所以只要在调 {@code super.saveAdditional} <b>之前</b>把数据写进这个容器,两边就自动闭环。
     *
     * <p>// NEEDS_HOST_CHECK: 上述闭环还依赖「AE2 的 {@code AEBaseBlockEntity.saveAdditional}
     * 与 final 的 {@code loadAdditional} 都会转发到 {@code BlockEntity}` 的实现」这一条未被取证的假设。
     * 若实测「放刀 → 存盘 → 重进世界」后槽里是空的,说明其中一侧没有转发,需要 host 用
     * {@code javap -p appeng.blockentity.AEBaseBlockEntity} 找一个可覆写的读钩子(这属于新增信息,
     * 本阶段没有任何已实证的替代路径,所以宁可先按官方持久化容器实现,并把风险显式记在这里)。
     */
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        writePersistentState(registries);
        super.saveAdditional(tag, registries);
    }

    /**
     * 惰性读取持久化容器(第一次 tick 时调用,那时 {@code level} 与 registry access 都可用)。
     *
     * <p>只读一次:之后 blade/storedAe 就是权威值(区块卸载会重新构造本 BE,标志自然重置)。
     */
    private void restorePersistentState() {
        if (restored || level == null) {
            return;
        }
        restored = true;
        CompoundTag data = getPersistentData();
        if (data.isEmpty()) {
            return;
        }
        storedAe = clampStoredAe(data.getDouble(TAG_STORED_AE));
        AEItemKey key = AEItemKey.fromTag(level.registryAccess(), data.getCompound(TAG_BLADE));
        if (key != null) {
            // getReadOnlyStack() 返回的是只读视图,必须 copy() 之后再持有点
            this.blade = key.getReadOnlyStack().copy();
        }
    }

    private void writePersistentState(HolderLookup.Provider registries) {
        CompoundTag data = getPersistentData();
        data.putDouble(TAG_STORED_AE, storedAe);
        Tag bladeTag = new CompoundTag();
        if (!blade.isEmpty()) {
            AEItemKey key = AEItemKey.of(blade);
            if (key != null) {
                bladeTag = key.toTag(registries);
            }
        }
        // 空槽也写(写成空复合标签):否则「拿走刀 → 存盘」会留下上一把刀的旧标签,
        // 下次读档会把已经被取走的刀变回来(复制漏洞)。
        data.put(TAG_BLADE, bladeTag);
    }

    private static double clampStoredAe(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_STORED_AE, value));
    }

    // ------------------------------------------------------------------
    // 刀槽(Container:菜单里的 Slot 直接指向本对象)
    // ------------------------------------------------------------------

    public ItemStack getBlade() {
        return blade;
    }

    /** 写入刀槽内容(空栈表示取出)。 */
    public void setBlade(ItemStack stack) {
        this.blade = stack == null ? ItemStack.EMPTY : stack;
        setChanged();
    }

    public int getLastState() {
        return lastState;
    }

    public int getStoredAe() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, storedAe));
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return blade.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == SLOT_BLADE ? blade : ItemStack.EMPTY;
    }

    /** 取走刀(只支持整个取走:刀不可堆叠,没有「取一半」的语义)。 */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != SLOT_BLADE || blade.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = blade.copy();
        taken.setCount(Math.min(Math.max(1, amount), taken.getCount()));
        setBlade(ItemStack.EMPTY);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != SLOT_BLADE) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = blade;
        this.blade = ItemStack.EMPTY;
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == SLOT_BLADE) {
            setBlade(stack);
        }
    }

    /** 槽位只收本模组的充能刀,且永远只有 1 件。 */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SLOT_BLADE && !stack.isEmpty() && stack.getItem() instanceof ChargedBladeItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** 槽位的有效性由菜单统一判定({@code BladeChargerMenu#stillValid}),这里恒 true。 */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        setBlade(ItemStack.EMPTY);
    }

    /**
     * 把槽里的刀掉出去(否则吞刀)。
     *
     * <p><b>调用点只有一个</b>:{@link BladeChargerBlock#playerWillDestroy} —— 玩家挖掘。
     * {@code setRemoved()} 里<b>只销毁网格节点、不掉刀</b>:区块卸载同样会走 setRemoved,
     * 在那里掉刀等于「走远一点就掉一地刀」(见 {@link #setRemoved()})。
     */
    public void dropContents() {
        if (blade.isEmpty() || level == null || level.isClientSide) {
            return;
        }
        ItemStack dropped = blade;
        setBlade(ItemStack.EMPTY);
        Block.popResource(level, worldPosition, dropped);
    }

    // ------------------------------------------------------------------
    // ContainerData(给菜单同步用)
    // ------------------------------------------------------------------

    @Override
    public int getCount() {
        return DATA_COUNT;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case DATA_ENERGY -> getStoredAe();
            case DATA_MAX -> (int) Math.min(Integer.MAX_VALUE, maxStoredAe);
            case DATA_STATE -> lastState;
            case DATA_AE_PER_POINT -> AppliedSlashConfig.chargedBladeAePerPoint();
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        // 全部由服务端单向推送;客户端不写
    }
}
