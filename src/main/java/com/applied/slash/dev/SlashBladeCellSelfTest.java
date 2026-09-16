package com.applied.slash.dev;

import com.applied.slash.AppliedSlash;
import com.applied.slash.AppliedSlashAe2;
import com.applied.slash.AppliedSlashConfig;
import com.applied.slash.BladeCellSpec;
import com.applied.slash.SlashBladeCellItem;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 开发期自检(仅在 {@code -Dappliedslash.selftest=true} 时运行,由 gradle {@code -PselfTest} 打开)。
 *
 * <p>它不是单元测试框架里的测试,而是**在真实服务端里跑一遍存储路径**的体检:直接把 PLAN v2 的
 * R1/R2/R3 硬指标量出来(拒收语义、类型上限、插入吞吐、每 tick 遍历成本、元件物品体积),
 * 跑完自动关服,便于无人值守执行。
 */
public final class SlashBladeCellSelfTest {
    public static final String PROPERTY = "appliedslash.selftest";

    private SlashBladeCellSelfTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void run(MinecraftServer server) {
        try {
            execute(server);
        } catch (Throwable t) {
            AppliedSlash.LOGGER.error("[SELFTEST] 自检异常中止", t);
        } finally {
            AppliedSlash.LOGGER.info("[SELFTEST] 结束,关闭服务器");
            server.halt(false);
        }
    }

    private static void execute(MinecraftServer server) {
        Item cellItem = AppliedSlashAe2.SLASH_BLADE_CELL.get();
        ItemStack cell = new ItemStack(cellItem);

        // (0) AE2 是否认这个物品是存储单元(驱动器槽位放行条件就是它)
        log("isCellHandled", StorageCells.isCellHandled(cell), "true");

        StorageCell inv = StorageCells.getCellInventory(cell, null);
        log("cellInventory", inv == null ? "null" : inv.getClass().getSimpleName(), "SlashBladeCellInventory");
        if (inv == null) {
            return;
        }

        IActionSource source = IActionSource.empty();

        // (1) 非拔刀剑一律拒收,并且不参与"优先存储"抢夺
        long diamond = inv.insert(AEItemKey.of(new ItemStack(Items.DIAMOND)), 64, Actionable.MODULATE, source);
        log("非拔刀剑插入", diamond, "0");
        log("非刀 isPreferredStorageFor", inv.isPreferredStorageFor(AEItemKey.of(new ItemStack(Items.DIAMOND)), source),
                "false");

        Item bladeItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("slashblade:slashblade"));
        if (bladeItem == null || bladeItem == Items.AIR) {
            log("slashblade:slashblade", "未找到", "存在");
            return;
        }
        ItemStack blade = new ItemStack(bladeItem);

        // (2) 拔刀剑入库 + 重复入库不增类型 + 优先存储声明(R1 的机制)
        log("拔刀剑插入", inv.insert(AEItemKey.of(blade), 1, Actionable.MODULATE, source), "1");
        log("刀 isPreferredStorageFor", inv.isPreferredStorageFor(AEItemKey.of(blade), source), "true");
        inv.insert(AEItemKey.of(blade), 3, Actionable.MODULATE, source);
        log("同刀再插 3 把后 类型数", SlashBladeCellItem.summaryTypes(cell), "1");
        log("同刀再插 3 把后 总数", SlashBladeCellItem.summaryCount(cell), "4");

        // (3) 规范化:带运行时状态组件的"同一把刀"必须仍算同一个键
        try {
            ItemStack withRuntime = blade.copy();
            BladeStateAccess.ensureRuntimeComponent(withRuntime);
            boolean differs = !ItemStack.isSameItemSameComponents(withRuntime, blade);
            inv.insert(AEItemKey.of(withRuntime), 1, Actionable.MODULATE, source);
            log("带运行时组件的同刀 组件确实不同", differs, "true");
            log("带运行时组件的同刀插入后 类型数", SlashBladeCellItem.summaryTypes(cell), "1(规范化生效)");
        } catch (Throwable t) {
            log("运行时组件规范化", "异常 " + t.getClass().getSimpleName(), "无异常");
        }

        // (4) 容量 + 插入吞吐:填到配置的类型上限
        final int limit = AppliedSlashConfig.maxTypes();
        final int fill = limit - SlashBladeCellItem.summaryTypes(cell);
        int accepted = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < fill; i++) {
            ItemStack named = blade.copy();
            named.set(DataComponents.CUSTOM_NAME, Component.literal("selftest-" + i));
            if (inv.insert(AEItemKey.of(named), 1, Actionable.MODULATE, source) == 1) {
                accepted++;
            }
        }
        long insertNanos = System.nanoTime() - t0;
        log("配置的类型上限", limit, "见 applied_slash-common.toml");
        log("批量插入成功数", accepted, String.valueOf(fill));
        log("插入平均耗时(µs/次)", String.format("%.2f", insertNanos / 1000.0 / Math.max(1, fill)), "< 50");
        log("当前类型数", SlashBladeCellItem.summaryTypes(cell), String.valueOf(limit));

        // (5) 超限拒收(不淘汰已有内容)
        ItemStack overflow = blade.copy();
        overflow.set(DataComponents.CUSTOM_NAME, Component.literal("selftest-overflow"));
        log("超限插入", inv.insert(AEItemKey.of(overflow), 1, Actionable.MODULATE, source), "0");
        log("超限后 类型数", SlashBladeCellItem.summaryTypes(cell), String.valueOf(limit));

        // (5b) 重新挂载一致性(回归测试):同一元件重新解析刀库后,计数/上限必须仍与磁盘内容一致。
        //      这里曾经出过 P1:计数只做增量维护,重新解析后归零 → 状态灯谎报 EMPTY、类型上限失效。
        StorageCell reopened = StorageCells.getCellInventory(cell, null);
        if (reopened == null) {
            log("重新解析库存", "null", "非 null");
        } else {
            KeyCounter reopenedCounter = new KeyCounter();
            reopened.getAvailableStacks(reopenedCounter);
            log("重新解析后 可见键数", reopenedCounter.size(), String.valueOf(limit));
            log("重新解析后 状态", reopened.getStatus(), "非 EMPTY");
            ItemStack reloadExtra = blade.copy();
            reloadExtra.set(DataComponents.CUSTOM_NAME, Component.literal("reload-check"));
            log("重新解析后 超限仍拒收", reopened.insert(AEItemKey.of(reloadExtra), 1, Actionable.MODULATE, source),
                    "0");
            log("重新解析后 计数与摘要一致",
                    reopenedCounter.size() == SlashBladeCellItem.summaryTypes(cell), "true");
        }

        // (6) 每 tick 遍历成本(AE2 会每 tick 调 getAvailableStacks 重建全网缓存)
        //     拆开量,区分"我们慢"还是"AE2 的 KeyCounter.add 慢"——这决定了优化方向。
        KeyCounter counter = new KeyCounter();
        final int warmup = 10;
        final int iterations = 100;
        for (int i = 0; i < warmup; i++) {
            counter.clear();
            inv.getAvailableStacks(counter);
        }
        long best = Long.MAX_VALUE;
        long worst = 0;
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            counter.clear();
            long t = System.nanoTime();
            inv.getAvailableStacks(counter);
            long elapsed = System.nanoTime() - t;
            total += elapsed;
            worst = Math.max(worst, elapsed);
            best = Math.min(best, elapsed);
        }
        log("getAvailableStacks 条数", counter.size(), String.valueOf(limit));
        log("getAvailableStacks 平均(ms)", String.format("%.3f", total / 1_000_000.0 / iterations), "< 1.0");
        log("getAvailableStacks 最优(ms)", String.format("%.3f", best / 1_000_000.0), "< 1.0");
        log("getAvailableStacks 最差(ms)", String.format("%.3f", worst / 1_000_000.0), "< 1.0");

        // (6a) 只测 AE2 那一侧:把同一批键塞进另一个 KeyCounter。
        //      键从 reference.keySet() 取(公开 API),不依赖任何内部结构。
        KeyCounter reference = new KeyCounter();
        inv.getAvailableStacks(reference);
        KeyCounter manual = new KeyCounter();
        for (int i = 0; i < warmup; i++) {
            manual.clear();
            refill(reference, manual);
        }
        long manualTotal = 0;
        long manualBest = Long.MAX_VALUE;
        for (int i = 0; i < iterations; i++) {
            manual.clear();
            long t = System.nanoTime();
            refill(reference, manual);
            long elapsed = System.nanoTime() - t;
            manualTotal += elapsed;
            manualBest = Math.min(manualBest, elapsed);
        }
        log("仅 KeyCounter.add 侧 平均(ms)", String.format("%.3f", manualTotal / 1_000_000.0 / iterations), "越小越好");
        log("仅 KeyCounter.add 侧 最优(ms)", String.format("%.3f", manualBest / 1_000_000.0), "越小越好");

        // (6b) counter.clear() 的成本
        long t3 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            counter.clear();
        }
        log("KeyCounter.clear 平均(ms)", String.format("%.3f", (System.nanoTime() - t3) / 1_000_000.0 / iterations),
                "越小越好");

        // (7) 元件物品自身体积:必须与存量无关
        CompoundTag cellTag = (CompoundTag) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, cell).getOrThrow();
        log("元件物品序列化体积(字节)", cellTag.sizeInBytes(), "< 2048 且不随存量增长");
        log("摘要组件 类型/总数", SlashBladeCellItem.summaryTypes(cell) + "/" + SlashBladeCellItem.summaryCount(cell),
                limit + "/" + (4 + fill + 1));

        // (8) 客户端同步体积:ME 终端会把可见键逐个写包,这一项决定"打开终端会不会卡客户端"
        net.minecraft.core.RegistryAccess registries = serverRegistryAccess();
        if (registries != null) {
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
            net.minecraft.network.RegistryFriendlyByteBuf friendly =
                    new net.minecraft.network.RegistryFriendlyByteBuf(buf, registries);
            int keyCount = 0;
            KeyCounter snapshot = new KeyCounter();
            inv.getAvailableStacks(snapshot);
            for (var key : snapshot.keySet()) {
                key.writeToPacket(friendly);
                keyCount++;
            }
            log("同步 " + keyCount + " 个键的字节数", friendly.readableBytes(),
                    "越小越好(本条为窄载荷刀,真实刀身数据更重)");
        }

        // (8b) 世界存档成本:分片落盘时每个刀要序列化一次(只在存档周期发生,不是每次存取)
        if (registries != null) {
            KeyCounter snapshot = new KeyCounter();
            inv.getAvailableStacks(snapshot);
            long tSave = System.nanoTime();
            int serialized = 0;
            for (var key : snapshot.keySet()) {
                if (key instanceof AEItemKey itemKey) {
                    itemKey.toTag(registries);
                    serialized++;
                }
            }
            double saveMs = (System.nanoTime() - tSave) / 1_000_000.0;
            log("序列化 " + serialized + " 把刀(存档期成本,ms)", String.format("%.3f", saveMs),
                    "只在世界存档时发生;分片后单次只重写脏分片");
        }

        // (8c) 分片落盘:改一把刀只重写它所在的那一块分片,而不是整个盘
        if (registries != null) {
            java.util.UUID vaultId = cell.get(com.applied.slash.AppliedSlashComponents.VAULT_ID.get());
            double[] shardCost = com.applied.slash.vault.BladeVaultStore.debugSaveShards(vaultId, registries);
            log("分片落盘 单块最大(ms)", String.format("%.3f", shardCost[0]),
                    "约为合计的 1/" + com.applied.slash.vault.VaultContents.BUCKETS);
            log("分片落盘 合计(ms)", String.format("%.3f", shardCost[1]), "越小越好");
        }

        // (9) 成本曲线:类型数 → 每 tick 重建成本(AE2 每 tick 都会调 getAvailableStacks)
        int[] targets = {20_000, 10_000, 5_000, 2_000, 1_000, 200};
        for (int target : targets) {
            if (target > limit) {
                continue;
            }
            trimTo(inv, cell, target, source);
            double ms = bestGetAvailableStacks(inv, counter);
            log("类型数 " + SlashBladeCellItem.summaryTypes(cell) + " 时 getAvailableStacks 最优(ms)",
                    String.format("%.3f", ms), "越小越好");
        }

        // (10) 测试元件工厂(真机压测入口):产出的元件必须真的装进互不相同的刀
        BladeTestFactory.FillResult sample = BladeTestFactory.createFilledCell(50);
        log("工厂产出 50 把 → 类型/总数", sample.types() + "/" + sample.total(), "50/50");
        log("工厂产出耗时(ms)", String.format("%.2f", sample.millis()), "越小越好");

        // (11) 命令注册与可执行性(下面这行应打印"必须指定玩家"的失败,而不是抛异常)
        log("命令 /appliedslash 已注册",
                server.getCommands().getDispatcher().getRoot().getChild("appliedslash") != null, "true");
        AppliedSlash.LOGGER.info("[SELFTEST] 下面应打印\"必须指定玩家\"的失败信息(证明命令可执行且不崩):");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "appliedslash testcell 100");
    }

    /** 把刀库修剪到指定类型数(用真实的 take 路径,顺带验证取出的正确性)。 */
    private static void trimTo(StorageCell inv, ItemStack cell, int target, IActionSource source) {
        if (SlashBladeCellItem.summaryTypes(cell) <= target) {
            return;
        }
        KeyCounter snapshot = new KeyCounter();
        inv.getAvailableStacks(snapshot);
        for (var key : snapshot.keySet()) {
            if (SlashBladeCellItem.summaryTypes(cell) <= target) {
                break;
            }
            inv.extract(key, Long.MAX_VALUE, Actionable.MODULATE, source);
        }
    }

    private static double bestGetAvailableStacks(StorageCell inv, KeyCounter counter) {
        for (int i = 0; i < 3; i++) {
            counter.clear();
            inv.getAvailableStacks(counter);
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 20; i++) {
            counter.clear();
            long t = System.nanoTime();
            inv.getAvailableStacks(counter);
            best = Math.min(best, System.nanoTime() - t);
        }
        return best / 1_000_000.0;
    }

    private static net.minecraft.core.RegistryAccess serverRegistryAccess() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    private static void log(String label, Object actual, String expected) {
        AppliedSlash.LOGGER.info("[SELFTEST] {} = {}  (期望 {})", label, actual, expected);
    }

    private static void refill(KeyCounter from, KeyCounter to) {
        for (var key : from.keySet()) {
            to.add(key, from.get(key));
        }
    }
}
