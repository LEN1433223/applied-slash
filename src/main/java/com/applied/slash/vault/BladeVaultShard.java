package com.applied.slash.vault;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 刀库的一块分片(按"刀"再切,不只是按元件切)。
 *
 * <p><b>为什么必须按刀再分片</b>:单个元件可能有上万把刀。若一个元件的全部刀放在同一个
 * {@link SavedData} 里,世界自动存档时就要一次性序列化全部刀(实测 5000 把窄载荷刀 ≈ 86 ms,
 * 2 万把 ≈ 345 ms),主线程被直接堵住。把每个元件的条目散到 {@code VAULT_SHARDS} 块分片后,
 * 改一把刀只标脏它所在的那一块,存档时只重写那 ~1/16。
 *
 * <p>格式:{@code { cells: [ { id:<uuid>, entries:[ { key:<AEItemKey 标签>, count:<long> } ] } ] }}
 * —— 每块分片只保存属于自己桶的条目。
 */
public final class BladeVaultShard extends SavedData {
    private static final String TAG_CELLS = "cells";
    private static final String TAG_ID = "id";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_KEY = "key";
    private static final String TAG_COUNT = "count";

    private final Map<UUID, Object2LongMap<AEItemKey>> cells = new HashMap<>();

    /** 取该元件在本分片里的条目表(没有就建)。 */
    public Object2LongMap<AEItemKey> bucket(UUID cellId) {
        return cells.computeIfAbsent(cellId, id -> new Object2LongOpenHashMap<>());
    }

    public static BladeVaultShard load(CompoundTag tag, HolderLookup.Provider registries) {
        BladeVaultShard shard = new BladeVaultShard();
        ListTag cellList = tag.getList(TAG_CELLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            CompoundTag cellTag = cellList.getCompound(i);
            if (!cellTag.contains(TAG_ID)) {
                continue;
            }
            UUID id = cellTag.getUUID(TAG_ID);
            if (id == null) {
                continue;
            }
            Object2LongMap<AEItemKey> bucket = shard.bucket(id);
            ListTag entryList = cellTag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
            for (int j = 0; j < entryList.size(); j++) {
                CompoundTag entryTag = entryList.getCompound(j);
                if (!entryTag.contains(TAG_KEY)) {
                    continue;
                }
                // 单条损坏只丢这一条,不让整盘数据报废
                AEItemKey key = AEItemKey.fromTag(registries, entryTag.getCompound(TAG_KEY));
                if (key == null) {
                    continue;
                }
                long amount = entryTag.getLong(TAG_COUNT);
                if (amount > 0) {
                    bucket.put(key, bucket.getLong(key) + amount);
                }
            }
        }
        shard.setDirty(false);
        return shard;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag cellList = new ListTag();
        for (Map.Entry<UUID, Object2LongMap<AEItemKey>> cell : cells.entrySet()) {
            Object2LongMap<AEItemKey> bucket = cell.getValue();
            if (bucket.isEmpty()) {
                continue;
            }
            CompoundTag cellTag = new CompoundTag();
            cellTag.putUUID(TAG_ID, cell.getKey());

            ListTag entryList = new ListTag();
            var iterator = bucket.object2LongEntrySet().iterator();
            while (iterator.hasNext()) {
                Object2LongMap.Entry<AEItemKey> entry = iterator.next();
                CompoundTag entryTag = new CompoundTag();
                entryTag.put(TAG_KEY, entry.getKey().toTag(registries));
                entryTag.putLong(TAG_COUNT, entry.getLongValue());
                entryList.addTag(entryList.size(), entryTag);
            }
            cellTag.put(TAG_ENTRIES, entryList);
            cellList.addTag(cellList.size(), cellTag);
        }
        tag.put(TAG_CELLS, cellList);
        return tag;
    }
}
