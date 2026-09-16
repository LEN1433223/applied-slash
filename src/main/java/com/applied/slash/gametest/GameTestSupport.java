package com.applied.slash.gametest;

import java.util.UUID;

import com.applied.slash.AppliedSlashAe2;
import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.BladeCellSpec;
import com.applied.slash.UnstackableItemCellItem;
import com.applied.slash.cell.BladeIdentity;
import com.applied.slash.dev.BladeTestFactory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * GameTest 的实际实现。**刻意放在非 {@code @GameTestHolder} 类里**:方法签名含 AE2 类型,
 * 若放在 holder 类里会让"没装 AE2 的开发客户端"在启动扫描阶段
 * ({@code GameTestHooks.addGameTestMethods} → {@code getDeclaredMethods()})抛
 * {@code NoClassDefFoundError} 直接崩溃。详见 {@link SlashBladeCellGameTests} 的说明。
 */
final class GameTestSupport {
    private GameTestSupport() {
    }

    static void rejectsNonBlades(GameTestHelper helper) {
        ItemStack cell = newCell();
        helper.assertTrue(StorageCells.isCellHandled(cell), "驱动器不接受本元件:ICellHandler 未接管");
        StorageCell inventory = requireInventory(helper, cell);

        ItemStack diamond = new ItemStack(Items.DIAMOND);
        long inserted = inventory.insert(AEItemKey.of(diamond), 64, Actionable.MODULATE, IActionSource.empty());
        helper.assertTrue(inserted == 0L, "非拔刀剑被收下了(期望 0,实际 " + inserted + ")");
        helper.assertFalse(inventory.isPreferredStorageFor(AEItemKey.of(diamond), IActionSource.empty()),
                "非拔刀剑不应声明优先存储");
        helper.assertTrue(summaryCount(cell) == 0L, "拒收之后不应留下任何内容");
        helper.succeed();
    }

    static void acceptsBladesAndClaimsPriority(GameTestHelper helper) {
        ItemStack cell = newCell();
        StorageCell inventory = requireInventory(helper, cell);
        ItemStack blade = requireBlade(helper);
        IActionSource source = IActionSource.empty();

        long first = inventory.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, source);
        helper.assertTrue(first == 1L, "拔刀剑未被收下(期望 1,实际 " + first + ")");
        helper.assertTrue(inventory.isPreferredStorageFor(AEItemKey.of(blade), source),
                "拔刀剑应声明优先存储(否则不会走 NetworkStorage 第一遍扫描)");

        long more = inventory.insert(AEItemKey.of(blade), 3, Actionable.MODULATE, source);
        helper.assertTrue(more == 3L, "同刀追加失败(期望 3,实际 " + more + ")");
        helper.assertTrue(summaryTypes(cell) == 1, "同一种刀算成了多个类型:" + summaryTypes(cell));
        helper.assertTrue(summaryCount(cell) == 4L, "总数不对:" + summaryCount(cell));

        KeyCounter visible = new KeyCounter();
        inventory.getAvailableStacks(visible);
        helper.assertTrue(visible.size() == 1, "网络可见键数应为 1,实际 " + visible.size());

        long extracted = inventory.extract(AEItemKey.of(blade), 2, Actionable.MODULATE, source);
        helper.assertTrue(extracted == 2L, "取出应为 2,实际 " + extracted);
        helper.succeed();
    }

    static void normalizesRuntimeState(GameTestHelper helper) {
        ItemStack cell = newCell();
        StorageCell inventory = requireInventory(helper, cell);
        ItemStack blade = requireBlade(helper);
        IActionSource source = IActionSource.empty();

        inventory.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, source);

        ItemStack withRuntime = blade.copy();
        BladeStateAccess.ensureRuntimeComponent(withRuntime);
        helper.assertFalse(ItemStack.isSameItemSameComponents(withRuntime, blade),
                "运行时组件没有被加上,本测试的前提不成立");

        inventory.insert(AEItemKey.of(withRuntime), 1, Actionable.MODULATE, source);
        helper.assertTrue(summaryTypes(cell) == 1,
                "带运行时状态的同一把刀被算成了新类型(规范化失效),类型数=" + summaryTypes(cell));
        helper.assertTrue(summaryCount(cell) == 2L, "数量应累加到同一键上,实际 " + summaryCount(cell));
        helper.succeed();
    }

    /**
     * SIMULATE 取出探测回归(本测试补的正是"看得见却取不出"的盲区)。
     *
     * <p>AE2 的取出链路**第一步永远是 {@code extract(..., Actionable.SIMULATE, ...)}**;
     * 这一步返回 0,它就判定"网络里没有这件东西",于是根本不调用第二步 ——
     * 表现为**终端里看得见这把刀,点了却毫无反应**。
     *
     * <p>触发条件:库里的键是规范化形态(入库时剥离了 {@code BLADE_RUNTIME_STATE}),
     * 而发起方送来的键带着运行时状态(玩家手里那把用过的刀、样板、导出总线都是这种形态)。
     * 因此"入库之后,必须能用<b>带运行时状态的原始键</b> SIMULATE 取出"是本元件的一条硬性契约。
     */
    static void simulateExtractAcceptsRuntimeKey(GameTestHelper helper) {
        ItemStack cell = newCell();
        StorageCell inventory = requireInventory(helper, cell);
        IActionSource source = IActionSource.empty();

        ItemStack blade = requireBlade(helper);
        ItemStack withRuntime = blade.copy();
        BladeStateAccess.ensureRuntimeComponent(withRuntime);
        helper.assertFalse(ItemStack.isSameItemSameComponents(withRuntime, blade),
                "运行时组件没有被加上,本测试的前提不成立");

        AEItemKey rawKey = AEItemKey.of(withRuntime);
        helper.assertTrue(rawKey != null, "带运行时状态的刀构造不出 AEItemKey(栈为空?)");

        long inserted = inventory.insert(rawKey, 1, Actionable.MODULATE, source);
        helper.assertTrue(inserted == 1L, "带运行时状态的刀入库失败(期望 1,实际 " + inserted + ")");
        helper.assertTrue(summaryTypes(cell) == 1, "入库后类型数应为 1,实际 " + summaryTypes(cell));
        helper.assertTrue(summaryCount(cell) == 1L, "入库后总数应为 1,实际 " + summaryCount(cell));

        // (1) 关键断言:用"带运行时状态的原始键"做 SIMULATE 必须 > 0
        long simulate = inventory.extract(rawKey, 1, Actionable.SIMULATE, source);
        helper.assertTrue(simulate > 0,
                "带运行时状态的原始键做 SIMULATE 返回 " + simulate
                        + "(期望 > 0):AE2 取出第一步就会放弃,终端点了没反应");
        helper.assertTrue(summaryCount(cell) == 1L, "SIMULATE 探测不得改动库存,实际总数 " + summaryCount(cell));

        // (2) 反向形态:规范化后的键做 SIMULATE 同样必须 > 0
        //     (关掉 stripRuntimeState 时 normalize 原样返回,此时上面那条已经覆盖,故用 if 包住)
        AEItemKey normalizedKey = BladeIdentity.normalize(rawKey);
        if (!normalizedKey.equals(rawKey)) {
            long normalizedSimulate = inventory.extract(normalizedKey, 1, Actionable.SIMULATE, source);
            helper.assertTrue(normalizedSimulate > 0,
                    "规范化后的键做 SIMULATE 返回 " + normalizedSimulate + "(期望 > 0)");
        }

        // (3) 真取:同一原始键 MODULATE 必须拿到 1 件
        long extracted = inventory.extract(rawKey, 1, Actionable.MODULATE, source);
        helper.assertTrue(extracted == 1L, "带运行时状态的原始键真取应为 1,实际 " + extracted);
        helper.assertTrue(summaryCount(cell) == 0L, "真取之后总数应为 0,实际 " + summaryCount(cell));
        helper.succeed();
    }

    static void enforcesLimitAndSurvivesReload(GameTestHelper helper) {
        ItemStack cell = newCell();
        StorageCell inventory = requireInventory(helper, cell);
        ItemStack blade = requireBlade(helper);
        IActionSource source = IActionSource.empty();

        // 先放一把素刀:后面用它验证"带运行时状态的同一把刀"在类型满时仍能入库
        helper.assertTrue(inventory.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, source) == 1L,
                "素刀入库失败");

        final int limit = AppliedSlashConfig.maxTypes();
        int accepted = 0;
        for (int i = 0; i < limit - 1; i++) {
            if (inventory.insert(AEItemKey.of(namedBlade(blade, "gametest-" + i)), 1, Actionable.MODULATE, source) == 1L) {
                accepted++;
            }
        }
        helper.assertTrue(accepted == limit - 1, "可容纳的类型数不足:期望 " + (limit - 1) + ",实际 " + accepted);
        helper.assertTrue(summaryTypes(cell) == limit,
                "类型数应为上限 " + limit + ",实际 " + summaryTypes(cell));

        long overflow = inventory.insert(AEItemKey.of(namedBlade(blade, "gametest-overflow")), 1,
                Actionable.MODULATE, source);
        helper.assertTrue(overflow == 0L, "超过类型上限仍然收下了(期望 0,实际 " + overflow + ")");

        // 类型已满 + 带运行时状态的同一把刀:必须仍被判为"优先存储"并能入库(规范化后命中已有键),
        // 否则同一把刀会被别的盘分走 —— 这是修复前的语义裂缝。
        ItemStack volatileBlade = blade.copy();
        BladeStateAccess.ensureRuntimeComponent(volatileBlade);
        helper.assertTrue(inventory.isPreferredStorageFor(AEItemKey.of(volatileBlade), source),
                "类型满时,带运行时状态的同刀应仍声明优先存储");
        long volatileInsert = inventory.insert(AEItemKey.of(volatileBlade), 1, Actionable.MODULATE, source);
        helper.assertTrue(volatileInsert == 1L,
                "类型满时,带运行时状态的同刀应仍能入库(期望 1,实际 " + volatileInsert + ")");
        helper.assertTrue(summaryTypes(cell) == limit, "不该新增类型,实际 " + summaryTypes(cell));

        StorageCell reopened = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(reopened != null, "重新解析刀库返回 null");
        KeyCounter visible = new KeyCounter();
        reopened.getAvailableStacks(visible);
        helper.assertTrue(visible.size() == limit,
                "重新解析后可见键数应为 " + limit + ",实际 " + visible.size() + "(计数未按磁盘内容重算?)");
        helper.assertFalse(reopened.getStatus() == CellState.EMPTY, "重新解析后谎报空盘(计数未按磁盘内容重算)");
        long reopenedOverflow = reopened.insert(AEItemKey.of(namedBlade(blade, "gametest-overflow-2")), 1,
                Actionable.MODULATE, source);
        helper.assertTrue(reopenedOverflow == 0L, "重新解析后类型上限失效(期望 0,实际 " + reopenedOverflow + ")");
        helper.succeed();
    }

    /**
     * 重登(重新解析刀库)之后,旧存档里的刀必须仍然<b>取得出来</b> —— 回归"看得见却取不出"。
     *
     * <p>为什么单列这一条:{@code VaultContents} 过去只按 {@code hashCode} 定位桶,而分片是从 NBT
     * <b>重新解析</b>出来的 —— 同一个逻辑键的新实例算出的哈希可能与落盘时那个不同,条目落在 A 桶、
     * 查询却去 B 桶 → {@code amountOf} 为 0 → {@code extract} 的 SIMULATE 返回 0 → AE2 判定
     * "网络里没有这件东西" → 终端里点了毫无反应。而 {@code getAvailableStacks} 本来就是扫全部桶的,
     * 于是"看得见"与"取不出"同时成立。
     *
     * <p>重载手法与 {@link #enforcesLimitAndSurvivesReload} 完全一致:
     * {@code StorageCells.getCellInventory(元件, null)}(真实取用路径上的"重新解析")。
     * 断言用的是<b>重载后列出来的那个键本身</b> —— 终端里点它用的就是这个键。
     */
    static void extractsReloadedContents(GameTestHelper helper) {
        ItemStack cell = newCell();
        StorageCell inventory = requireInventory(helper, cell);
        ItemStack blade = requireBlade(helper);
        IActionSource source = IActionSource.empty();

        // 存 8 把互不相同的刀:组件不同 = 键不同 = 哈希散在不同桶里
        int stored = 0;
        for (int i = 0; i < 8; i++) {
            if (inventory.insert(AEItemKey.of(namedBlade(blade, "gametest-reload-" + i)), 1,
                    Actionable.MODULATE, source) == 1L) {
                stored++;
            }
        }
        helper.assertTrue(stored == 8, "入库失败:期望 8,实际 " + stored);

        // 模拟一次持久化往返:重新解析元件(与 enforcesLimitAndSurvivesReload 同一手法)
        StorageCell reopened = StorageCells.getCellInventory(cell, null);
        helper.assertTrue(reopened != null, "重新解析刀库返回 null");

        KeyCounter visible = new KeyCounter();
        reopened.getAvailableStacks(visible);
        helper.assertTrue(visible.size() == 8, "重载后可见键数应为 8,实际 " + visible.size());
        helper.assertFalse(reopened.getStatus() == CellState.EMPTY, "重载后谎报空盘");

        AEKey firstKey = null;
        for (var key : visible.keySet()) {
            firstKey = key;
            break;
        }
        helper.assertTrue(firstKey != null, "重载后可见键为空");

        long simulate = reopened.extract(firstKey, 1, Actionable.SIMULATE, source);
        helper.assertTrue(simulate > 0, "重载后对列出的键做 SIMULATE 返回 " + simulate
                + "(期望 > 0):AE2 取出第一步就会放弃,终端点了没反应");
        helper.assertTrue(summaryCount(cell) == 8L, "SIMULATE 探测不得改动库存,实际总数 " + summaryCount(cell));

        long extracted = reopened.extract(firstKey, 1, Actionable.MODULATE, source);
        helper.assertTrue(extracted == 1L, "重载后真取应为 1,实际 " + extracted);
        helper.assertTrue(summaryCount(cell) == 7L, "真取 1 件后总数应为 7,实际 " + summaryCount(cell));
        helper.succeed();
    }

    /**
     * 内容相同但<b>不同实例</b>的键也必须取得到 —— "哈希不可信"的最小复现。
     *
     * <p>入库用 {@code AEItemKey.of(stack)},取出用 {@code AEItemKey.of(stack.copy())}:两个对象、
     * 同样内容。AE2 的键哈希建立在 MC 的组件补丁上,同一逻辑内容在不同实例上算出的哈希<b>可能不同</b>,
     * 一旦查找只认哈希桶,就会出现"入库正常、取出探测返回 0;但条目仍在(getAvailableStacks 扫全桶列得出来)"
     * —— 正是线上那个 bug 的形状。
     *
     * <p>取 1 件而不是全取:顺带盯住写路径 —— 它必须在<b>真正持有条目的那个桶</b>里扣减,
     * 而不是在哈希桶里另建一份(那会让类型数虚增、两份内容各自为政)。
     */
    static void equivalentKeyInstanceExtractsStoredEntry(GameTestHelper helper) {
        ItemStack cell = newCell();
        StorageCell inventory = requireInventory(helper, cell);
        ItemStack blade = requireBlade(helper);
        IActionSource source = IActionSource.empty();

        AEItemKey storedKey = AEItemKey.of(blade);
        AEItemKey queryKey = AEItemKey.of(blade.copy());
        helper.assertTrue(storedKey != null && queryKey != null, "素刀构造不出 AEItemKey(栈为空?)");
        helper.assertTrue(storedKey != queryKey,
                "前提不成立:AEItemKey.of 复用了同一个实例,本测试需要两个不同对象");
        helper.assertTrue(storedKey.equals(queryKey), "前提不成立:内容相同的两个键却不 equals");

        long inserted = inventory.insert(storedKey, 2, Actionable.MODULATE, source);
        helper.assertTrue(inserted == 2L, "入库失败(期望 2,实际 " + inserted + ")");
        helper.assertTrue(summaryTypes(cell) == 1, "同刀应只算 1 个类型,实际 " + summaryTypes(cell));

        // 两个实例的哈希是否一致,写进失败信息:不一致即是"哈希不可信"的直接证据
        boolean sameHash = storedKey.hashCode() == queryKey.hashCode();

        long simulate = inventory.extract(queryKey, 1, Actionable.SIMULATE, source);
        helper.assertTrue(simulate > 0, "内容相同但不同实例的键做 SIMULATE 返回 " + simulate
                + "(期望 > 0;两键 hashCode 是否一致 = " + sameHash + ")");

        long extracted = inventory.extract(queryKey, 1, Actionable.MODULATE, source);
        helper.assertTrue(extracted == 1L, "真取应为 1,实际 " + extracted
                + "(两键 hashCode 是否一致 = " + sameHash + ")");
        helper.assertTrue(summaryTypes(cell) == 1,
                "取出后类型数应仍为 1(写路径不得在别的桶里另建一份),实际 " + summaryTypes(cell));
        helper.assertTrue(summaryCount(cell) == 1L, "取出 1 件后总数应为 1,实际 " + summaryCount(cell));
        helper.succeed();
    }

    static void survivesMissingPayload(GameTestHelper helper) {
        ItemStack orphan = newCell();
        orphan.set(AppliedSlashComponents.VAULT_ID.get(), UUID.randomUUID());
        StorageCell inventory = requireInventory(helper, orphan);

        helper.assertTrue(inventory.getStatus() == CellState.EMPTY,
                "查不到载荷时应表现为空盘,实际 " + inventory.getStatus());
        long inserted = inventory.insert(AEItemKey.of(requireBlade(helper)), 1, Actionable.MODULATE,
                IActionSource.empty());
        helper.assertTrue(inserted == 1L, "空盘应能正常入库(期望 1,实际 " + inserted + ")");
        helper.succeed();
    }

    static void testCellFactoryProducesDistinctBlades(GameTestHelper helper) {
        final int count = 200;
        ItemStack plainBlade = requireBlade(helper);
        helper.assertFalse(ItemStack.isSameItemSameComponents(BladeTestFactory.syntheticBlade(0), plainBlade),
                "合成刀与素刀组件相同:刀身数据没写进去,压测载荷会偏小");

        BladeTestFactory.FillResult result = BladeTestFactory.createFilledCell(count);
        helper.assertFalse(result.cell().isEmpty(), "工厂没有产出元件(缺少拔刀剑?)");
        helper.assertTrue(result.accepted() == count, "接受数应为 " + count + ",实际 " + result.accepted());
        helper.assertTrue(result.types() == count,
                "类型数应为 " + count + ",实际 " + result.types() + "(合成刀之间不够不同)");
        helper.assertTrue(summaryTypes(result.cell()) == count, "元件摘要没写入类型数");
        helper.assertTrue(summaryCount(result.cell()) == count, "元件摘要没写入总数");

        StorageCell reopened = StorageCells.getCellInventory(result.cell(), null);
        helper.assertTrue(reopened != null, "重新解析工厂产出的元件返回 null");
        KeyCounter visible = new KeyCounter();
        reopened.getAvailableStacks(visible);
        helper.assertTrue(visible.size() == count, "网络可见键数应为 " + count + ",实际 " + visible.size());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 以下三组是「不可堆叠物品存储元件」的断言(与拔刀剑元件的七条并列,互不影响)
    // ------------------------------------------------------------------

    /**
     * 新元件:不可堆叠物品收下、可堆叠物品拒收,且**不做任何规范化**(组件即身份)。
     *
     * <p>最后一条断言是本元件与拔刀剑元件最本质的区别:拔刀剑元件把"同刀的两态"归并成一个键,
     * 而这里带自定义名称的镐与素镐必须是两个类型 —— 一旦有人往这条路径里塞进规范化,本测试立刻失败。
     */
    static void unstackableCellAcceptsUnstackablesOnly(GameTestHelper helper) {
        ItemStack cell = newUnstackableCell();
        helper.assertTrue(StorageCells.isCellHandled(cell), "驱动器不接受不可堆叠物品元件:ICellHandler 未接管");
        StorageCell inventory = requireInventory(helper, cell);
        IActionSource source = IActionSource.empty();

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.assertTrue(pickaxe.getMaxStackSize() == 1, "前提不成立:diamond_pickaxe 应不可堆叠");
        long inserted = inventory.insert(AEItemKey.of(pickaxe), 1, Actionable.MODULATE, source);
        helper.assertTrue(inserted == 1L, "不可堆叠物品被拒收(期望 1,实际 " + inserted + ")");
        helper.assertTrue(inventory.isPreferredStorageFor(AEItemKey.of(pickaxe), source),
                "不可堆叠物品应无条件声明优先存储(否则不会走 NetworkStorage 第一遍扫描)");

        ItemStack named = pickaxe.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("gametest-pickaxe"));
        long namedInserted = inventory.insert(AEItemKey.of(named), 1, Actionable.MODULATE, source);
        helper.assertTrue(namedInserted == 1L, "带自定义名称的镐被拒收(期望 1,实际 " + namedInserted + ")");
        helper.assertTrue(unstackableTypes(cell) == 2,
                "不同组件的同类物品应算不同类型(本元件绝不规范化),实际类型数 " + unstackableTypes(cell));

        ItemStack diamond = new ItemStack(Items.DIAMOND);
        helper.assertTrue(diamond.getMaxStackSize() > 1, "前提不成立:diamond 应可堆叠");
        long rejected = inventory.insert(AEItemKey.of(diamond), 64, Actionable.MODULATE, source);
        helper.assertTrue(rejected == 0L, "可堆叠物品被收下了(期望 0,实际 " + rejected + ")");
        helper.assertFalse(inventory.isPreferredStorageFor(AEItemKey.of(diamond), source),
                "可堆叠物品不应声明优先存储");
        helper.assertTrue(unstackableCount(cell) == 2L, "总件数应为 2,实际 " + unstackableCount(cell));
        helper.succeed();
    }

    /**
     * 新元件必须拒收拔刀剑。
     *
     * <p>拔刀剑本身就是不可堆叠的,所以它一定会走到"不可堆叠"这一条 —— 本测试证明的是
     * 紧随其后的"排除拔刀剑"那一句真的生效(否则刀会被新元件抢走,而新元件不剥离刀身运行时状态)。
     */
    static void unstackableCellRejectsSlashBlades(GameTestHelper helper) {
        ItemStack cell = newUnstackableCell();
        StorageCell inventory = requireInventory(helper, cell);
        ItemStack blade = requireBlade(helper);
        helper.assertTrue(blade.getMaxStackSize() == 1, "前提不成立:拔刀剑应不可堆叠");

        long inserted = inventory.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, IActionSource.empty());
        helper.assertTrue(inserted == 0L, "拔刀剑被不可堆叠物品元件收下了(期望 0,实际 " + inserted + ")");
        helper.assertFalse(inventory.isPreferredStorageFor(AEItemKey.of(blade), IActionSource.empty()),
                "拔刀剑不应在不可堆叠物品元件上声明优先存储");
        helper.assertTrue(unstackableCount(cell) == 0L, "拒收之后不应留下任何内容");
        helper.succeed();
    }

    /**
     * 新元件必须拒收"存储元件类物品"(至少本模组自己的两个元件),否则元件会被吞进储存盘;
     * 同时确认这条排除规则没有误伤普通不可堆叠物品。
     */
    static void unstackableCellRejectsCells(GameTestHelper helper) {
        ItemStack cell = newUnstackableCell();
        StorageCell inventory = requireInventory(helper, cell);
        IActionSource source = IActionSource.empty();

        ItemStack bladeCell = newCell();
        helper.assertTrue(bladeCell.getMaxStackSize() == 1, "前提不成立:本模组元件应不可堆叠");
        long bladeCellInserted = inventory.insert(AEItemKey.of(bladeCell), 1, Actionable.MODULATE, source);
        helper.assertTrue(bladeCellInserted == 0L,
                "拔刀剑元件被吞进不可堆叠物品元件(期望 0,实际 " + bladeCellInserted + ")");

        long selfInserted = inventory.insert(AEItemKey.of(newUnstackableCell()), 1, Actionable.MODULATE, source);
        helper.assertTrue(selfInserted == 0L, "元件自己吞自己(期望 0,实际 " + selfInserted + ")");

        // 排除规则只针对"元件类",不该误伤普通的不可堆叠物品
        helper.assertFalse(StorageCells.isCellHandled(new ItemStack(Items.DIAMOND_PICKAXE)),
                "钻石镐被 AE2 当成存储元件:排除规则会误伤");
        helper.succeed();
    }

    private static ItemStack newCell() {
        return new ItemStack(AppliedSlashAe2.SLASH_BLADE_CELL.get());
    }

    private static ItemStack newUnstackableCell() {
        return new ItemStack(AppliedSlashAe2.UNSTACKABLE_ITEM_CELL.get());
    }

    private static StorageCell requireInventory(GameTestHelper helper, ItemStack cell) {
        StorageCell inventory = StorageCells.getCellInventory(cell, null);
        if (inventory == null) {
            helper.fail("StorageCells.getCellInventory 返回 null:元件没有被 ICellHandler 接管");
        }
        return inventory;
    }

    private static ItemStack requireBlade(GameTestHelper helper) {
        Item bladeItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("slashblade:slashblade"));
        helper.assertTrue(bladeItem != null && bladeItem != Items.AIR, "缺少 slashblade:slashblade,无法运行本测试");
        return new ItemStack(bladeItem);
    }

    private static ItemStack namedBlade(ItemStack blade, String name) {
        ItemStack stack = blade.copy();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static int summaryTypes(ItemStack cell) {
        return com.applied.slash.SlashBladeCellItem.summaryTypes(cell);
    }

    private static long summaryCount(ItemStack cell) {
        return com.applied.slash.SlashBladeCellItem.summaryCount(cell);
    }

    /** 读的虽然是新元件自己的静态方法,摘要组件的键名与拔刀剑元件一致(见 UnstackableItemCellItem)。 */
    private static int unstackableTypes(ItemStack cell) {
        return UnstackableItemCellItem.summaryTypes(cell);
    }

    private static long unstackableCount(ItemStack cell) {
        return UnstackableItemCellItem.summaryCount(cell);
    }
}
