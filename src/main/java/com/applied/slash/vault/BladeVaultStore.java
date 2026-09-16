package com.applied.slash.vault;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.applied.slash.BladeCellSpec;

import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 世界侧刀库的入口。
 *
 * <p>刀身数据**不放在元件的物品组件里**:一万把刀的完整 NBT 若写进物品栈,每次落盘都要重写
 * 数十 MB,而且物品栈本身还要参与同步。这里改成:元件物品只带 UUID,数据存世界的
 * {@link SavedData},并且<b>按刀哈希分桶</b>到多块分片 —— 改一把刀只重写它所在的那一块。
 *
 * <p>纯客户端({@code getCurrentServer() == null})取不到刀库,返回 {@code null} —— 调用方据此
 * 退化为"空 + 只读",不会在客户端伪造内容。
 */
public final class BladeVaultStore {
    private static final SavedData.Factory<BladeVaultShard> FACTORY =
            new SavedData.Factory<>(BladeVaultShard::new, BladeVaultShard::load);

    private static final String[] SHARD_NAMES;

    static {
        SHARD_NAMES = new String[BladeCellSpec.VAULT_SHARDS];
        for (int i = 0; i < SHARD_NAMES.length; i++) {
            SHARD_NAMES[i] = "applied_slash_blade_vault_" + i;
        }
    }

    private BladeVaultStore() {
    }

    /**
     * 已解析刀库的缓存:{@code StorageCells.getCellInventory} 的每个调用者(驱动器的每次重挂载、
     * 元件染色回调、各类 UI)都会走到 {@link #get(UUID)},而解析一次要 16 次分片查表 + 一次
     * {@code recount()}(O(n) 遍历全部条目)。不缓存的话,"装满 5000 把的盘每被解析一次就扫 5000 条"。
     *
     * <p>失效条件:服务器实例变化(重开世界/换存档)即整体清空,避免持有旧世界的数据。
     */
    private static final Map<UUID, VaultContents> CACHE = new HashMap<>();
    private static final int CACHE_LIMIT = 256;
    private static MinecraftServer cacheOwner;

    /** 取得该 UUID 的刀库内容;服务端不可用时返回 {@code null}。 */
    public static synchronized VaultContents get(UUID vaultId) {
        if (vaultId == null) {
            return null;
        }
        DimensionDataStorage storage = storage();
        if (storage == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != cacheOwner) {
            CACHE.clear();
            cacheOwner = server;
        }
        VaultContents cached = CACHE.get(vaultId);
        if (cached != null) {
            return cached;
        }

        BladeVaultShard[] shards = new BladeVaultShard[SHARD_NAMES.length];
        for (int i = 0; i < SHARD_NAMES.length; i++) {
            shards[i] = storage.computeIfAbsent(FACTORY, SHARD_NAMES[i]);
        }
        VaultContents contents = new VaultContents(shards);
        for (int i = 0; i < shards.length; i++) {
            contents.registerBucket(i, shards[i].bucket(vaultId));
        }
        // 桶里可能已有上次存档读回来的条目:计数必须按实际内容重算,否则重开世界后从 0 开始。
        contents.recount();

        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear(); // 只是丢外壳,数据仍在分片里;避免被"制造过上千个元件"撑爆
        }
        CACHE.put(vaultId, contents);
        return contents;
    }

    private static DimensionDataStorage storage() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // 单机环境下客户端渲染线程也能看到非 null 的 server:必须挡掉,
        // 否则会对活着的 DimensionDataStorage / HashMap 做跨线程读写(存档期可能 CME)。
        if (server == null || !server.isSameThread()) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        return overworld == null ? null : overworld.getDataStorage();
    }

    /**
     * 仅供自检/诊断:把该元件涉及的每一块分片各落盘一次,返回 {@code [单块最大耗时 ms, 合计耗时 ms]}。
     *
     * <p>正常运行只会重写被改动的那一块,所以真正该看的是<b>单块最大耗时</b>。
     */
    public static double[] debugSaveShards(UUID vaultId, HolderLookup.Provider registries) {
        DimensionDataStorage storage = storage();
        if (storage == null || vaultId == null) {
            return new double[] {0, 0};
        }
        double max = 0;
        double total = 0;
        for (String name : SHARD_NAMES) {
            BladeVaultShard shard = storage.computeIfAbsent(FACTORY, name);
            long t = System.nanoTime();
            shard.save(new CompoundTag(), registries);
            double ms = (System.nanoTime() - t) / 1_000_000.0;
            max = Math.max(max, ms);
            total += ms;
        }
        return new double[] {max, total};
    }
}
