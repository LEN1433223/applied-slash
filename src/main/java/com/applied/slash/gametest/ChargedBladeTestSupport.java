package com.applied.slash.gametest;

import java.util.List;

import com.applied.slash.AppliedSlashAe2;
import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.charged.ChargedBladeEnergy;
import com.applied.slash.charged.ChargedBladeFactory;
import com.applied.slash.charged.ChargedBladeGate;
import com.applied.slash.charged.ChargedBladeItem;
import com.applied.slash.charged.ChargedBladeItems;
import com.applied.slash.charged.ChargedBladeSpec;
import com.applied.slash.charged.block.BladeChargerBlockEntity;
import com.applied.slash.charged.block.BladeChargerRegistry;
import com.applied.slash.charged.block.ChargerMath;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 充能刀 / 充能方块 GameTest 的实际实现。**刻意放在非 {@code @GameTestHolder} 类里**
 * (理由见 {@link ChargedBladeGameTests} 与既有的 {@link GameTestSupport})。
 *
 * <p><b>C 阶段状态</b>:8 条全部是真实断言 —— 每一条都能真的红。
 * 写断言的原则是「钉住已定稿的契约」而不是「钉住当前实现」:
 * <ul>
 *   <li>量化相关的期望值用 {@link ChargedBladeEnergy#quantize} 现算(配置改了测试仍然成立),
 *       只在配置为默认值时再额外断言文档里写死的数字(137 → 80);</li>
 *   <li>与重锋交互的部分只断言**纯数据后果**(能量、sealed、刀身字段),
 *       不赌未取证的返回值语义(N23/N24);</li>
 *   <li>每处失败信息都带上实测值,便于 D/host 直接判断是「实现错」还是「前提不成立」。</li>
 * </ul>
 */
final class ChargedBladeTestSupport {
    /** 默认配置下的量化档数(用于「文档里写死的那几个数字」的额外断言)。 */
    private static final int DOCUMENTED_QUANTIZE_LEVELS = 10;

    private ChargedBladeTestSupport() {
    }

    /**
     * 0 能量:不能使用、把伤害压到 0、并处于封印态。
     *
     * <p>刻意不调 {@code item.use(...)}:那需要一个真玩家/真 level(N29 未闭合),
     * 而「能不能用」与「有没有伤害」全部落在能量与 sealed 这两个可断言的量上。
     */
    static void chargedBladeWithoutEnergyCannotBeUsed(GameTestHelper helper) {
        List<ChargedBladeItem> blades = requireFiveBlades(helper);
        for (ChargedBladeItem blade : blades) {
            ItemStack stack = ChargedBladeFactory.fresh(blade);
            assertTrue(helper, ChargedBladeEnergy.get(stack) == blade.maxEnergy(),
                    "出厂栈应是满能量(上限 " + blade.maxEnergy() + "),实际 " + ChargedBladeEnergy.get(stack));
            assertTrue(helper, ChargedBladeEnergy.canUse(stack), "满能量刀应可用:" + blade.bladeId());

            ChargedBladeEnergy.set(stack, 0);
            assertTrue(helper, ChargedBladeEnergy.get(stack) == 0,
                    "写入 0 之后读到的却是 " + ChargedBladeEnergy.get(stack));
            assertFalse(helper, ChargedBladeEnergy.canUse(stack), "0 能量仍被判为可用:" + blade.bladeId());
            assertFalse(helper, ChargedBladeGate.canUse(stack),
                    "ChargedBladeGate.canUse 与 ChargedBladeEnergy.canUse 不一致:" + blade.bladeId());
            assertTrue(helper, ChargedBladeGate.shouldZeroDamage(stack),
                    "0 能量时 shouldZeroDamage 应为 true:" + blade.bladeId());
            assertTrue(helper, ChargedBladeGate.isBlocked(stack),
                    "isBlocked 应与 shouldZeroDamage 一致:" + blade.bladeId());
            // 主保证:sealed 是能量的只读投影(能量归零 ⇒ 封印)
            assertTrue(helper, isSealed(stack),
                    "能量归零后 sealed 应为 true(否则重锋侧不会拦):" + blade.bladeId());
        }
        helper.succeed();
    }

    /**
     * 充能后可再次使用;同时钉住写入的 clamp 语义。
     */
    static void chargedBladeAfterChargeCanBeUsed(GameTestHelper helper) {
        int cost = ChargedBladeEnergy.costPerAttack();
        for (ChargedBladeItem blade : requireFiveBlades(helper)) {
            ItemStack stack = ChargedBladeFactory.fresh(blade);
            int max = blade.maxEnergy();

            ChargedBladeEnergy.set(stack, 0);
            assertFalse(helper, ChargedBladeEnergy.canUse(stack), "清零后应不可用:" + blade.bladeId());

            // 刚好够一次
            ChargedBladeEnergy.set(stack, cost);
            assertTrue(helper, ChargedBladeEnergy.get(stack) == cost,
                    "写入 " + cost + " 后读到 " + ChargedBladeEnergy.get(stack));
            assertTrue(helper, ChargedBladeEnergy.canUse(stack),
                    "能量 = 消耗(" + cost + ")时应可用:" + blade.bladeId());
            assertFalse(helper, ChargedBladeGate.shouldZeroDamage(stack),
                    "能量 = 消耗时不应把伤害压到 0:" + blade.bladeId());
            assertFalse(helper, isSealed(stack), "有能量时不应是封印态:" + blade.bladeId());

            // 越界写入必须被 clamp 到 [0, max]
            ChargedBladeEnergy.set(stack, max + 1000);
            assertTrue(helper, ChargedBladeEnergy.get(stack) == max,
                    "超上限写入应被 clamp 到 " + max + ",实际 " + ChargedBladeEnergy.get(stack));
            ChargedBladeEnergy.set(stack, -50);
            assertTrue(helper, ChargedBladeEnergy.get(stack) == 0,
                    "负值写入应被 clamp 到 0,实际 " + ChargedBladeEnergy.get(stack));
            assertTrue(helper, isSealed(stack), "clamp 到 0 之后应回到封印态:" + blade.bladeId());
        }
        helper.succeed();
    }

    /**
     * 只有真正命中才扣费,且不会扣成负数;归零即封印。
     *
     * <p>两个层次都测:
     * <ol>
     *   <li><b>真实调用</b>{@code hurtEnemy(stack, target, attacker)}(攻击者是服务端的生物,
     *       它的 {@code level()} 就是测试用的 ServerLevel ⇒ 会走扣费分支);</li>
     *   <li>纯数据边界(能量不足时不写数据、不为负)。</li>
     * </ol>
     */
    static void energySpentOnlyOnRealHit(GameTestHelper helper) {
        ChargedBladeItem blade = requireBlade(helper, ChargedBladeSpec.PULSE);
        ItemStack stack = ChargedBladeFactory.fresh(blade);
        int cost = ChargedBladeEnergy.costPerAttack();

        // 攻击者/目标都用测试世界的僵尸:只需要 attacker.level() 是 ServerLevel,不需要进场
        Zombie attacker = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        Zombie target = new Zombie(EntityType.ZOMBIE, helper.getLevel());

        int before = ChargedBladeEnergy.get(stack);
        blade.hurtEnemy(stack, target, attacker);
        int after = ChargedBladeEnergy.get(stack);
        assertTrue(helper, after == before - cost,
                "命中一次应扣 " + cost + " 点(期望 " + (before - cost) + "),实际 " + after);

        // 打到刚好 0:不再为负,且 sealed 置位
        ChargedBladeEnergy.set(stack, cost);
        blade.hurtEnemy(stack, target, attacker);
        assertTrue(helper, ChargedBladeEnergy.get(stack) == 0,
                "扣到 0 时应正好为 0,实际 " + ChargedBladeEnergy.get(stack));
        assertTrue(helper, isSealed(stack), "能量归零应把 sealed 置上");

        blade.hurtEnemy(stack, target, attacker);
        assertTrue(helper, ChargedBladeEnergy.get(stack) == 0,
                "0 能量再命中不得扣成负数,实际 " + ChargedBladeEnergy.get(stack));

        // 纯函数边界:能量不足时 spendOnHit 原样返回
        assertTrue(helper, ChargedBladeEnergy.spendOnHit(stack) == 0,
                "能量不足时 spendOnHit 应返回 0(=不扣)");

        // 非服务端不扣费(双端隔离:扣费只在 ServerLevel 分支里发生)。
        // 客户端的 level 在 GameTest 里拿不到,所以这里只断言「拿不到 level 的攻击者不会崩」——
        // 用测试世界的 level 构造一个「没有 level 的」场景做不到,故留一条注释说明覆盖边界。
        // NEEDS_HOST_CHECK: 「客户端不扣费」这一条只能靠真机验收(专用服务器 + 客户端)覆盖。
        helper.succeed();
    }

    /**
     * 量化后能量仍能随刀进出存储元件,并且经得起真正的 NBT 往返。
     *
     * <p>三层:
     * <ol>
     *   <li><b>满能量</b>入库:默认 {@code keepFull} 下应原样保留;</li>
     *   <li><b>137(涌流,上限 800)</b>入库:向下取整到档位,损失 ≤ 档宽 - 1
     *       (默认配置下正好是 80 —— 注意 PLAN §10.2 手工验收表里写的「130」是笔误,
     *       算法以 §7.1 为准);</li>
     *   <li>{@code AEItemKey.toTag/fromTag} 往返:刀库落盘走的就是这条路
     *       (见 {@code vault/BladeVaultShard}),能量组件必须随之保留。</li>
     * </ol>
     */
    static void energySurvivesStorageRoundTrip(GameTestHelper helper) {
        ChargedBladeItem blade = requireBlade(helper, ChargedBladeSpec.SURGE);
        int max = blade.maxEnergy();
        int levels = ChargedBladeEnergy.quantizeLevels();
        boolean keepFull = ChargedBladeEnergy.quantizeKeepFull();
        IActionSource source = IActionSource.empty();

        // (1) 满能量:期望值由 quantize 现算(默认 keepFull=true ⇒ 就是 max)
        StorageCell fullCell = requireInventory(helper, newCell());
        ItemStack full = ChargedBladeFactory.fresh(blade);
        ChargedBladeEnergy.set(full, max);
        assertTrue(helper, fullCell.insert(AEItemKey.of(full), 1, Actionable.MODULATE, source) == 1L,
                "满能量刀入库失败");
        int storedFull = storedEnergy(helper, fullCell, "满能量");
        int expectedFull = ChargedBladeEnergy.quantize(max, max, levels, keepFull);
        assertTrue(helper, storedFull == expectedFull,
                "满能量(" + max + ")入库后库里应读到 " + expectedFull + ",实际 " + storedFull);
        if (keepFull) {
            assertTrue(helper, storedFull == max,
                    "keepFull=true 时满能量的刀取出必须仍满,实际 " + storedFull);
        }

        // (2) 137 → 档位
        StorageCell partialCell = requireInventory(helper, newCell());
        ItemStack partial = ChargedBladeFactory.fresh(blade);
        ChargedBladeEnergy.set(partial, 137);
        assertTrue(helper, ChargedBladeEnergy.needsQuantizing(partial),
                "137(上限 " + max + ")不是档位值,应被判为需要量化");
        assertTrue(helper, partialCell.insert(AEItemKey.of(partial), 1, Actionable.MODULATE, source) == 1L,
                "137 能量的刀入库失败");
        int storedPartial = storedEnergy(helper, partialCell, "137");
        int expectedPartial = ChargedBladeEnergy.quantize(137, max, levels, keepFull);
        assertTrue(helper, storedPartial == expectedPartial,
                "137 入库后库里应读到量化值 " + expectedPartial + ",实际 " + storedPartial);
        assertTrue(helper, storedPartial <= 137, "量化只允许向下取整(不得凭空增加能量)");
        assertTrue(helper, 137 - storedPartial <= ChargedBladeEnergy.perBucket(max, levels) - 1,
                "量化损失不得超过档宽 - 1(档宽 " + ChargedBladeEnergy.perBucket(max, levels)
                        + ",实际损失 " + (137 - storedPartial) + ")");
        if (levels == DOCUMENTED_QUANTIZE_LEVELS && keepFull) {
            assertTrue(helper, storedPartial == 80,
                    "默认 10 档下 137(上限 800)应量化到 80,实际 " + storedPartial);
        }

        // (3) 真正的持久化往返(AEItemKey 的 NBT 标签 = 刀库落盘所用的形式)
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack source2 = ChargedBladeFactory.fresh(blade);
        ChargedBladeEnergy.set(source2, 137);
        AEItemKey key = AEItemKey.of(source2);
        assertTrue(helper, key != null, "充能刀构造不出 AEItemKey(栈为空?)");
        CompoundTag tag = new CompoundTag();
        tag.put("blade", key.toTag(registries));
        AEItemKey restored = AEItemKey.fromTag(registries, tag.getCompound("blade"));
        assertTrue(helper, restored != null, "NBT 往返后的键为 null(标签写错?)");
        ItemStack restoredStack = restored.getReadOnlyStack();
        assertTrue(helper, restoredStack.getItem() == blade,
                "NBT 往返后物品变了:" + restoredStack.getItem());
        assertTrue(helper, ChargedBladeEnergy.get(restoredStack) == 137,
                "NBT 往返后能量应保留 137,实际 " + ChargedBladeEnergy.get(restoredStack));
        helper.succeed();
    }

    /**
     * 同档能量合并成一个键、异档分裂成两个键。
     *
     * <p>期望值全部由 {@link ChargedBladeEnergy#quantize} 现算 ⇒ 改配置不会把这条件误报成失败;
     * 默认配置下额外断言文档里写死的那组数字(131/139 → 同档,139/161 → 异档)。
     */
    static void sameBucketMergesToOneKey(GameTestHelper helper) {
        ChargedBladeItem blade = requireBlade(helper, ChargedBladeSpec.SURGE);
        int max = blade.maxEnergy();
        int levels = ChargedBladeEnergy.quantizeLevels();
        boolean keepFull = ChargedBladeEnergy.quantizeKeepFull();
        IActionSource source = IActionSource.empty();

        int low = 131;
        int mid = 139;
        int high = 161;
        int qLow = ChargedBladeEnergy.quantize(low, max, levels, keepFull);
        int qMid = ChargedBladeEnergy.quantize(mid, max, levels, keepFull);
        int qHigh = ChargedBladeEnergy.quantize(high, max, levels, keepFull);

        // 同档:两把刀必须合成 1 个键、总数 2
        ItemStack sameCell = newCell();
        StorageCell sameInventory = requireInventory(helper, sameCell);
        assertTrue(helper, sameInventory.insert(AEItemKey.of(energized(blade, low)), 1, Actionable.MODULATE, source) == 1L,
                "入库 " + low + " 失败");
        assertTrue(helper, sameInventory.insert(AEItemKey.of(energized(blade, mid)), 1, Actionable.MODULATE, source) == 1L,
                "入库 " + mid + " 失败");
        int expectedSameTypes = qLow == qMid ? 1 : 2;
        assertTrue(helper, summaryTypes(sameCell) == expectedSameTypes,
                low + "(" + qLow + ")与 " + mid + "(" + qMid + ")应算 " + expectedSameTypes
                        + " 个类型,实际 " + summaryTypes(sameCell));
        assertTrue(helper, summaryCount(sameCell) == 2L,
                "两把刀应在同一个键上累加到 2,实际 " + summaryCount(sameCell));
        assertTrue(helper, visibleKeys(sameInventory) == expectedSameTypes,
                "网络可见键数应为 " + expectedSameTypes + ",实际 " + visibleKeys(sameInventory));

        // 异档:必须分裂成 2 个键
        ItemStack diffCell = newCell();
        StorageCell diffInventory = requireInventory(helper, diffCell);
        assertTrue(helper, diffInventory.insert(AEItemKey.of(energized(blade, mid)), 1, Actionable.MODULATE, source) == 1L,
                "入库 " + mid + " 失败");
        assertTrue(helper, diffInventory.insert(AEItemKey.of(energized(blade, high)), 1, Actionable.MODULATE, source) == 1L,
                "入库 " + high + " 失败");
        int expectedDiffTypes = qMid == qHigh ? 1 : 2;
        assertTrue(helper, summaryTypes(diffCell) == expectedDiffTypes,
                mid + "(" + qMid + ")与 " + high + "(" + qHigh + ")应算 " + expectedDiffTypes
                        + " 个类型,实际 " + summaryTypes(diffCell));
        assertTrue(helper, summaryCount(diffCell) == 2L, "总件数应为 2,实际 " + summaryCount(diffCell));

        if (levels == DOCUMENTED_QUANTIZE_LEVELS && keepFull) {
            assertTrue(helper, qLow == 80 && qMid == 80 && qHigh == 160,
                    "默认 10 档下 131/139 应取到 80、161 应取到 160,实际 " + qLow + "/" + qMid + "/" + qHigh);
        }
        helper.succeed();
    }

    /**
     * 门禁纯函数:0 能量 ⇒ 压伤害;≥ 消耗 ⇒ 不压;两者永远互斥。
     *
     * <p>刻意只断言纯函数:{@code AttackEntityEvent} 在 {@code Player.attack} 里扫不到触发点
     * (事实表 §9.1),不做需要真事件的断言。
     */
    static void chargedBladeGateBlocksAttack(GameTestHelper helper) {
        int cost = ChargedBladeEnergy.costPerAttack();
        for (ChargedBladeItem blade : requireFiveBlades(helper)) {
            ItemStack stack = ChargedBladeFactory.fresh(blade);

            ChargedBladeEnergy.set(stack, 0);
            assertTrue(helper, ChargedBladeGate.shouldZeroDamage(stack),
                    "0 能量时 shouldZeroDamage 应为 true:" + blade.bladeId());
            assertFalse(helper, ChargedBladeGate.canUse(stack), "0 能量时 canUse 应为 false:" + blade.bladeId());

            ChargedBladeEnergy.set(stack, cost);
            assertFalse(helper, ChargedBladeGate.shouldZeroDamage(stack),
                    "能量 = 消耗时 shouldZeroDamage 应为 false:" + blade.bladeId());
            assertTrue(helper, ChargedBladeGate.canUse(stack), "能量 = 消耗时 canUse 应为 true:" + blade.bladeId());

            assertFalse(helper, ChargedBladeGate.canUse(stack) && ChargedBladeGate.shouldZeroDamage(stack),
                    "canUse 与 shouldZeroDamage 同时为真:门禁自相矛盾(" + blade.bladeId() + ")");
        }
        helper.succeed();
    }

    /**
     * 任何时刻都不允许存在「缺刀身数据 + 走耐久路径」的刀。
     *
     * <p>为什么这些断言必须成立:
     * ① {@code damageItem} 恒返回 0 是本模组刀永不 broken 的唯一保证 —— 一旦它返回正数,
     * {@code getDefaultAttributeModifiers} 会走 {@code -0.5 - b} 分支,伤害 13.14 立刻被破坏;
     * ② {@code setDamage}/{@code damageItem} 内部都有 {@code BladeStateAccess.of(stack).orElseThrow()},
     * 没有刀身数据的刀走到这里就是 {@code NoSuchElementException} 崩服;
     * ③ 13.14 的公式要求 {@code baseAttackModifier=13.14F ∧ refine=0 ∧ 不 broken}
     * (事实表 §8.1),所以这三项各自都要钉住。
     */
    static void chargedBladeNeverLacksBladeState(GameTestHelper helper) {
        List<ChargedBladeItem> blades = requireFiveBlades(helper);
        for (ChargedBladeItem blade : blades) {
            ItemStack bare = new ItemStack(blade);
            assertFalse(helper, bare.isEmpty(), "充能刀物品栈为空:" + blade.bladeId());
            int damage = blade.<LivingEntity>damageItem(bare, 1, null, item -> {
            });
            assertTrue(helper, damage == 0,
                    "damageItem 必须恒返回 0(否则 broken ⇒ 13.14 被破坏、且踩 orElseThrow),实际 "
                            + damage + "(" + blade.bladeId() + ")");

            // 出厂栈的刀身字段
            ItemStack fresh = ChargedBladeFactory.fresh(blade);
            assertTrue(helper, BladeStateAccess.of(fresh).isPresent(),
                    "出厂栈缺刀身数据(一旦走耐久路径就是 orElseThrow 崩服):" + blade.bladeId());
            assertTrue(helper, baseAttackModifier(fresh) == ChargedBladeSpec.BASE_ATTACK_MODIFIER,
                    "baseAttackModifier 应为 " + ChargedBladeSpec.BASE_ATTACK_MODIFIER + "(= 面板 13.14),实际 "
                            + baseAttackModifier(fresh) + "(" + blade.bladeId() + ")");
            assertTrue(helper, refine(fresh) == 0,
                    "refine 必须为 0,否则 13.14 的公式不成立,实际 " + refine(fresh) + "(" + blade.bladeId() + ")");
            assertFalse(helper, isBroken(fresh),
                    "出厂栈不应是断刀(broken ⇒ 伤害变负):" + blade.bladeId());
            assertFalse(helper, isBewitched(fresh),
                    "不应有初始附魔(defaultBewitched 必须为 false):" + blade.bladeId());
            assertFalse(helper, isDestructable(fresh),
                    "destructable 应为 false(避免耐久语义混入):" + blade.bladeId());

            // 自愈:裸栈(无任何组件)经过 ensureInitialized 之后必须有刀身数据 + 满能量
            ChargedBladeFactory.ensureInitialized(bare);
            assertTrue(helper, BladeStateAccess.of(bare).isPresent(),
                    "自愈后仍缺刀身数据(N14/N26 未闭合时可能出现,host 请据此定位):" + blade.bladeId());
            assertTrue(helper, ChargedBladeEnergy.get(bare) == blade.maxEnergy(),
                    "自愈后能量应为满(缺席 = 满),实际 " + ChargedBladeEnergy.get(bare) + "(" + blade.bladeId() + ")");
            // 越界值必须**绕过 set 的 clamp** 直接写进组件,否则测不出 ensureInitialized 自己的 clamp
            bare.set(AppliedSlashComponents.BLADE_ENERGY.get(), blade.maxEnergy() + 5000);
            ChargedBladeFactory.ensureInitialized(bare);
            assertTrue(helper, ChargedBladeEnergy.get(bare) == blade.maxEnergy(),
                    "自愈应把越界能量 clamp 回上限 " + blade.maxEnergy() + ",实际 "
                            + ChargedBladeEnergy.get(bare) + "(" + blade.bladeId() + ")");
        }
        helper.succeed();
    }

    /**
     * 充能数学 + 充能方块的注入 / 抽取 / 每 tick 结算。
     *
     * <p>前半是纯函数边界(不依赖世界);后半直接构造一个方块实体调用
     * {@code injectAEPower}/{@code extractAEPower}/{@code serverTick} ——
     * 这些方法不需要网格(节点在 {@code level == null} 时会主动跳过初始化,见实现里的守卫)。
     */
    static void chargerChargeMath(GameTestHelper helper) {
        // ---- (1) 纯函数边界 ----
        ChargerMath.ChargeTick full = ChargerMath.chargeTick(800, 800, 1_000_000L, 100, 1);
        assertTrue(helper, full.newEnergy() == 800, "满能量时不应改变能量,实际 " + full.newEnergy());
        assertTrue(helper, full.aeConsumed() == 0L, "满能量时不应耗电,实际 " + full.aeConsumed());
        assertTrue(helper, full.state() == ChargerMath.STATE_FULL,
                "满能量时状态应为 FULL,实际 " + full.state());

        ChargerMath.ChargeTick poor = ChargerMath.chargeTick(0, 800, 50L, 100, 1);
        assertTrue(helper, poor.aeConsumed() == 0L, "AE 不足一点时不应扣电,实际 " + poor.aeConsumed());
        assertTrue(helper, poor.newEnergy() == 0, "AE 不足一点时不应充能,实际 " + poor.newEnergy());
        assertTrue(helper, poor.state() == ChargerMath.STATE_NO_AE,
                "AE 不足一点时状态应为 NO_AE,实际 " + poor.state());

        ChargerMath.ChargeTick top = ChargerMath.chargeTick(750, 800, 1_000_000L, 100, 50);
        assertTrue(helper, top.newEnergy() == 800, "750 充一 tick 后应为 800,实际 " + top.newEnergy());
        assertTrue(helper, top.aeConsumed() == 50L * 100L,
                "应恰好消耗 50 点 × 100 AE = 5000,实际 " + top.aeConsumed());
        assertTrue(helper, top.state() == ChargerMath.STATE_CHARGING,
                "充能中状态应为 CHARGING,实际 " + top.state());
        ChargerMath.ChargeTick again = ChargerMath.chargeTick(top.newEnergy(), 800, 1_000_000L, 100, 1);
        assertTrue(helper, again.aeConsumed() == 0L, "充满之后再算不应耗电,实际 " + again.aeConsumed());

        // 不满一点 + 只够部分点数
        assertTrue(helper, ChargerMath.chargeTick(0, 800, 99L, 100, 1).state() == ChargerMath.STATE_NO_AE,
                "可用 AE 差一点点时也应判 NO_AE");
        ChargerMath.ChargeTick partial = ChargerMath.chargeTick(0, 800, 250L, 100, 3);
        assertTrue(helper, partial.newEnergy() == 2,
                "可用 AE 只够 2 点时应只充 2 点,实际 " + partial.newEnergy());
        assertTrue(helper, partial.aeConsumed() == 200L,
                "只充 2 点时应只扣 200 AE,实际 " + partial.aeConsumed());

        // 能量条宽度(与物品 getBarWidth 同口径)
        assertTrue(helper, ChargerMath.barWidth(0, 800) == 0, "0 能量条宽应为 0");
        assertTrue(helper, ChargerMath.barWidth(800, 800) == 13, "满能量条宽应为 13");
        assertTrue(helper, ChargerMath.barWidth(400, 800) == 7,
                "半能量条宽应为 round(6.5)=7,实际 " + ChargerMath.barWidth(400, 800));
        assertTrue(helper, ChargerMath.barWidth(100, 0) == 0, "上限为 0 时条宽应为 0(不得除零)");

        // ---- (2) 注入 / 抽取 ----
        BlockState chargerState = BladeChargerRegistry.BLADE_CHARGER.get().defaultBlockState();
        BladeChargerBlockEntity charger = newCharger(chargerState);
        double injected = charger.injectAEPower(50_000.0, Actionable.SIMULATE);
        assertTrue(helper, injected == 50_000.0, "SIMULATE 应返回可接受量 50000,实际 " + injected);
        assertTrue(helper, charger.getAECurrentPower() == 0.0,
                "SIMULATE 不得改动缓冲,实际 " + charger.getAECurrentPower());

        double accepted = charger.injectAEPower(50_000.0, Actionable.MODULATE);
        assertTrue(helper, accepted == 50_000.0, "MODULATE 应接受 50000,实际 " + accepted);
        assertTrue(helper, charger.getAECurrentPower() == 50_000.0,
                "缓冲应为 50000,实际 " + charger.getAECurrentPower());

        double space = charger.getAEMaxPower() - charger.getAECurrentPower();
        double overflow = charger.injectAEPower(1.0e9, Actionable.MODULATE);
        assertTrue(helper, overflow == space,
                "超量注入应被 clamp 到剩余空间 " + space + ",实际 " + overflow);
        assertTrue(helper, charger.getAECurrentPower() == charger.getAEMaxPower(),
                "clamp 之后缓冲应正好满,实际 " + charger.getAECurrentPower());

        // 纯消费者:getPowerFlow 不允许抽取 ⇒ 外部抽不走,缓冲不变
        assertFalse(helper, charger.getPowerFlow().isAllowExtraction(),
                "本方块是纯消费者,getPowerFlow 不应允许抽取,实际 " + charger.getPowerFlow());
        double beforeExtract = charger.getAECurrentPower();
        // 第三个参数(multiplier)传 null:本实现不使用它,而事实表只给了参数类型、没给可用常量
        assertTrue(helper, charger.extractAEPower(1000.0, Actionable.MODULATE, null) == 0.0,
                "不允许抽取时 extractAEPower 必须返回 0");
        assertTrue(helper, charger.getAECurrentPower() == beforeExtract,
                "抽取被拒后缓冲不得变化,实际 " + charger.getAECurrentPower());

        // ---- (3) 每 tick 结算 ----
        int aePerPoint = AppliedSlashConfig.chargedBladeAePerPoint();
        int perTick = AppliedSlashConfig.chargedBladeChargePerTick();
        ChargedBladeItem blade = requireBlade(helper, ChargedBladeSpec.SURGE);
        int max = blade.maxEnergy();
        int missing = 3;
        ItemStack stack = ChargedBladeFactory.fresh(blade);
        ChargedBladeEnergy.set(stack, max - missing);
        charger.setBlade(stack);
        double aeBefore = charger.getAECurrentPower();

        BladeChargerBlockEntity.serverTick(helper.getLevel(), BlockPos.ZERO, chargerState, charger);
        int gained = Math.min(perTick, missing);
        assertTrue(helper, ChargedBladeEnergy.get(charger.getBlade()) == max - missing + gained,
                "一 tick 应充 " + gained + " 点(期望 " + (max - missing + gained) + "),实际 "
                        + ChargedBladeEnergy.get(charger.getBlade()));
        assertTrue(helper, charger.getAECurrentPower() == aeBefore - (double) gained * aePerPoint,
                "应扣掉 " + gained + " × " + aePerPoint + " AE(期望 " + (aeBefore - (double) gained * aePerPoint)
                        + "),实际 " + charger.getAECurrentPower());
        assertTrue(helper, charger.getLastState() == ChargerMath.STATE_CHARGING,
                "结算状态应为 CHARGING,实际 " + charger.getLastState());

        // 空槽 ⇒ IDLE 且不耗电
        charger.setBlade(ItemStack.EMPTY);
        double aeIdle = charger.getAECurrentPower();
        BladeChargerBlockEntity.serverTick(helper.getLevel(), BlockPos.ZERO, chargerState, charger);
        assertTrue(helper, charger.getLastState() == ChargerMath.STATE_IDLE,
                "空槽时状态应为 IDLE,实际 " + charger.getLastState());
        assertTrue(helper, charger.getAECurrentPower() == aeIdle,
                "空槽时不应耗电,实际 " + charger.getAECurrentPower());

        assertTrue(helper, ChargedBladeEnergy.quantizeLevels() >= 2, "量化档数应 ≥ 2");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 断言与构造辅助
    // ------------------------------------------------------------------

    /** 5 把刀必须全部注册上(任何一条测试都拿它当前提)。 */
    private static List<ChargedBladeItem> requireFiveBlades(GameTestHelper helper) {
        List<ChargedBladeItem> blades = ChargedBladeItems.all();
        assertTrue(helper, blades.size() == 5, "充能刀应注册 5 把,实际 " + blades.size());
        return blades;
    }

    /** 按 spec 取那一把刀(找不到就直接 fail)。 */
    private static ChargedBladeItem requireBlade(GameTestHelper helper, ChargedBladeSpec spec) {
        for (ChargedBladeItem blade : ChargedBladeItems.all()) {
            if (blade.spec().equals(spec)) {
                return blade;
            }
        }
        helper.fail("找不到充能刀:" + spec.id() + "(前置缺席或注册漏了?)");
        return null;
    }

    /** 一把指定能量的出厂刀(先满能量出厂,再写目标能量 —— 保证刀身数据齐全)。 */
    private static ItemStack energized(ChargedBladeItem blade, int energy) {
        ItemStack stack = ChargedBladeFactory.fresh(blade);
        ChargedBladeEnergy.set(stack, energy);
        return stack;
    }

    private static ItemStack newCell() {
        return new ItemStack(AppliedSlashAe2.SLASH_BLADE_CELL.get());
    }

    private static StorageCell requireInventory(GameTestHelper helper, ItemStack cell) {
        StorageCell inventory = StorageCells.getCellInventory(cell, null);
        if (inventory == null) {
            helper.fail("StorageCells.getCellInventory 返回 null:元件没有被 ICellHandler 接管");
        }
        return inventory;
    }

    private static BladeChargerBlockEntity newCharger(BlockState state) {
        return new BladeChargerBlockEntity(BladeChargerRegistry.BLADE_CHARGER_BE.get(), BlockPos.ZERO, state);
    }

    /** 读出元件里**那把刀**的能量(要求元件里恰好只有一个物品键)。 */
    private static int storedEnergy(GameTestHelper helper, StorageCell inventory, String label) {
        KeyCounter visible = new KeyCounter();
        inventory.getAvailableStacks(visible);
        for (AEKey key : visible.keySet()) {
            if (key instanceof AEItemKey itemKey) {
                return storedEnergyOf(itemKey.getReadOnlyStack());
            }
        }
        helper.fail(label + ":元件里没有任何物品键");
        return -1;
    }

    private static int storedEnergyOf(ItemStack stack) {
        return ChargedBladeEnergy.get(stack);
    }

    private static int visibleKeys(StorageCell inventory) {
        KeyCounter visible = new KeyCounter();
        inventory.getAvailableStacks(visible);
        return visible.size();
    }

    private static int summaryTypes(ItemStack cell) {
        return com.applied.slash.SlashBladeCellItem.summaryTypes(cell);
    }

    private static long summaryCount(ItemStack cell) {
        return com.applied.slash.SlashBladeCellItem.summaryCount(cell);
    }

    private static boolean isSealed(ItemStack stack) {
        return BladeStateAccess.of(stack).map(state -> state.isSealed()).orElse(false);
    }

    private static boolean isBroken(ItemStack stack) {
        return BladeStateAccess.of(stack).map(state -> state.isBroken()).orElse(false);
    }

    private static boolean isBewitched(ItemStack stack) {
        return BladeStateAccess.of(stack).map(state -> state.isDefaultBewitched()).orElse(false);
    }

    private static boolean isDestructable(ItemStack stack) {
        return BladeStateAccess.of(stack).map(state -> state.isDestructable()).orElse(true);
    }

    private static int refine(ItemStack stack) {
        return BladeStateAccess.of(stack).map(state -> state.getRefine()).orElse(-1);
    }

    private static float baseAttackModifier(ItemStack stack) {
        return BladeStateAccess.of(stack).map(state -> state.getBaseAttackModifier()).orElse(Float.NaN);
    }

    private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static void assertFalse(GameTestHelper helper, boolean condition, String message) {
        helper.assertFalse(condition, message);
    }
}
