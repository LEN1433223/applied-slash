package com.applied.slash.vault;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * 单个刀库(一个元件 UUID)的内存视图。
 *
 * <p>条目按"刀的哈希"散在 {@link BladeVaultShard} 的若干桶里,每个桶对应一块世界侧分片:
 * <ul>
 *   <li>读写仍是 O(1)(首选桶下标由 {@code AEKey.hashCode()} 直接算出,该哈希被 AE2 缓存在键里);</li>
 *   <li>改一把刀只标脏<b>它所在的那一块分片</b>,世界存档只重写 ~1/N 的数据(见 {@link #BUCKETS});</li>
 *   <li>{@link #forEach(KeyCounter)} 是 AE2 每 tick 调用的热路径,只做裸遍历、零 NBT 解析。</li>
 * </ul>
 *
 * <h2>桶只是「存档粒度」的优化,不是查找的唯一依据 —— 踩坑记录,勿回退</h2>
 *
 * <p><b>症状(已实测)</b>:存档之后重新进世界,以前存进去的刀在终端里<b>列得出来</b>,
 * 点它却<b>毫无反应(取不出)</b>;当次登录新存进去的刀一切正常。
 *
 * <p><b>根因</b>:落盘那一刻,条目被放进 {@code floorMod(key.hashCode(), BUCKETS)} 这个桶,
 * 而分片文件正是按桶切的 —— 条目在磁盘上就属于那一块分片。重新进世界后,键是
 * {@link BladeVaultShard#load} 从 NBT <b>重新解析出来的新实例</b>,而 AE2 的键哈希建立在 MC 的
 * 组件补丁上(补丁内部含有基于引用/身份的量),<b>同一个逻辑键在不同实例上算出的哈希可以不同</b>
 * —— 于是查询按新哈希走到另一个桶,那儿当然没有这条目:{@link #amountOf} 返回 0 →
 * {@code extract} 的 SIMULATE 返回 0 → AE2 判定"网络里没有这件东西",连第二步 MODULATE 都不会调
 * → 点击无反应。而 {@link #forEach(KeyCounter)} 本来就是<b>遍历全部桶</b>的,
 * 于是"看得见"和"取不出"同时成立。
 *
 * <p><b>修法</b>:把"桶"降级成纯优化 —— {@link #amountOf}/{@link #add}/{@link #remove} 一律
 * <b>先试哈希桶、未命中再扫其余各桶</b>(见 {@link #findBucket});写路径只改<b>真正持有该条目</b>
 * 的那个桶,并且只标脏那一块分片。{@link #recount()} 与 {@link #forEach(KeyCounter)} 不变(它们本来就是全桶遍历)。
 *
 * <p><b>为什么不靠"重登时把哈希重算一遍"解决</b>:
 * <ol>
 *   <li>不一致并不只发生在存档边界。同一次登录里,发起方送来的键(终端取件、样板、输入/输出总线、
 *       另一台机器上的同一把刀)本身就是新实例,"内容等价、哈希可能不同"随时会发生,
 *       只在读盘时重排一次挡不住这些路径;</li>
 *   <li>重排意味着把每个键重新哈希、重新搬桶:读盘会从"注册 16 张表"退化成 O(n) 全表重写,
 *       而且必然把所有分片标脏 —— 下一次存档要重写<b>整个</b>刀库,正好废掉分片存在的意义;</li>
 *   <li>查找的正确性不该建立在"我们无法控制、AE2 也没有承诺跨实例稳定"的哈希上。
 *       我们这侧唯一稳的做法是:哈希只用来选桶,查找保底能扫全桶。</li>
 * </ol>
 *
 * <p><b>代价与收益</b>:命中常见路径(哈希桶里就有)的成本与改动前完全一致 —— 一次哈希 + 一次
 * {@code getLong};累加路径因为要拿到旧值会多一次 {@code getLong}(单桶 O(1) 探测,十几纳秒)。
 * 只有"哈希对不上"的病理键才会退化:最多多扫 {@code BUCKETS - 1 = 15} 个桶,每个桶一次
 * {@code getLong}(空表 / 未命中表上都是 O(1) 探测),合计百纳秒量级 —— 用这点代价换"存进去的东西
 * 一定取得出来"。这条不妥协:存储模组最坏的故障模式就是"东西在、却取不出"。
 */
public final class VaultContents {
    /** 桶数 = 分片数。桶越多单次存档越轻,但文件越多。 */
    public static final int BUCKETS = com.applied.slash.BladeCellSpec.VAULT_SHARDS;

    private final BladeVaultShard[] shards;
    private final Object2LongMap<AEItemKey>[] buckets;

    private int typeCount;
    private long totalCount;

    @SuppressWarnings("unchecked")
    VaultContents(BladeVaultShard[] shards) {
        this.shards = shards;
        this.buckets = new Object2LongMap[shards.length];
    }

    /** 由 {@link BladeVaultStore} 在解析分片时逐桶注册。 */
    void registerBucket(int index, Object2LongMap<AEItemKey> bucket) {
        buckets[index] = bucket;
    }

    /** 首选桶下标。**只是优化**:它等于 {@code hashCode} 的桶,不等于条目唯一可能在的桶(见类注释)。 */
    private static int bucketOf(AEKey key) {
        return Math.floorMod(key.hashCode(), BUCKETS);
    }

    /**
     * 找出<b>真正持有</b>该键的桶下标;任何桶都没有正值条目时返回 {@code -1}。
     *
     * <p>先试哈希桶(常见情形,一次 {@code getLong} 就命中,成本与改动前相同),未命中再依次扫其余各桶。
     * 这样即使键的 {@code hashCode()} 跨实例不稳定,条目照样找得到 —— 见类注释里的踩坑记录。
     *
     * <p>判定一律用 {@code > 0}:桶里可能留着 {@code count <= 0} 的残留条目(损坏/历史存档),
     * 它们不算命中 —— 与 {@link #recount()} / {@link #forEach(KeyCounter)} 的 {@code amount > 0} 口径一致。
     */
    private int findBucket(AEItemKey key) {
        int primary = bucketOf(key);
        Object2LongMap<AEItemKey> bucket = buckets[primary];
        if (bucket != null && bucket.getLong(key) > 0) {
            return primary;
        }
        for (int i = 0; i < buckets.length; i++) {
            if (i == primary) {
                continue;
            }
            Object2LongMap<AEItemKey> other = buckets[i];
            if (other != null && other.getLong(key) > 0) {
                return i;
            }
        }
        return -1;
    }

    public long amountOf(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return 0L;
        }
        int index = findBucket(itemKey);
        return index < 0 ? 0L : buckets[index].getLong(itemKey);
    }

    public int typeCount() {
        return typeCount;
    }

    public long count() {
        return totalCount;
    }

    public boolean isEmpty() {
        return totalCount == 0;
    }

    public boolean hasRoomForNewType(int limit) {
        return typeCount < limit;
    }

    /**
     * 累加:命中已有条目就在<b>它所在的那个桶</b>里累加(不增类型数),否则在哈希桶里新建一个类型。
     *
     * <p>写路径必须"认桶":若累加到哈希桶而不是真正持有的桶,同一把刀会在两个桶里各有一份 ——
     * 类型数被重复计数,而取出只拿得到其中一份。
     */
    public void add(AEItemKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        int index = findBucket(key);
        if (index < 0) {
            // 新条目:落在哈希桶里(与改动前逐句等价,含"桶里有 <=0 残留条目"这一支)
            index = bucketOf(key);
            Object2LongMap<AEItemKey> bucket = buckets[index];
            long current = bucket.getLong(key);
            if (current <= 0) {
                typeCount++;
            }
            bucket.put(key, current + amount);
            totalCount += amount;
            shards[index].setDirty();
            return;
        }
        // 已有条目:findBucket 已保证"该桶里它的值 > 0",所以不增类型数
        Object2LongMap<AEItemKey> bucket = buckets[index];
        bucket.put(key, bucket.getLong(key) + amount);
        totalCount += amount;
        shards[index].setDirty();
    }

    /** 取出至多 amount,返回实际取出的数量。找不到条目(或桶里只有 {@code <=0} 的残留)返回 0。 */
    public long remove(AEItemKey key, long amount) {
        if (amount <= 0) {
            return 0L;
        }
        int index = findBucket(key);
        if (index < 0) {
            return 0L;
        }
        Object2LongMap<AEItemKey> bucket = buckets[index];
        long stored = bucket.getLong(key);
        if (stored <= 0) {
            // findBucket 已保证 > 0,这里只是防御性兜底
            return 0L;
        }
        long removed = Math.min(amount, stored);
        long left = stored - removed;
        if (left <= 0) {
            bucket.removeLong(key);
            typeCount--;
        } else {
            bucket.put(key, left);
        }
        totalCount -= removed;
        shards[index].setDirty();
        return removed;
    }

    /**
     * 由 {@link BladeVaultStore} 在注册完分片后调用,把**已经落盘**的条目计入计数。
     *
     * <p>必须做这一步:分片是从磁盘反序列化出来的,桶里可能已经有成千上万条,
     * 而 {@link #typeCount}/{@link #totalCount} 只由{@link #add}/{@link #remove} 增量维护
     * —— 不重算的话,重开世界后计数会从 0 开始,导致状态灯谎报"空"、类型上限失效、
     * tooltip 与摘要组件全为 0。
     */
    void recount() {
        typeCount = 0;
        totalCount = 0;
        for (Object2LongMap<AEItemKey> bucket : buckets) {
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            for (Object2LongMap.Entry<AEItemKey> entry : bucket.object2LongEntrySet()) {
                long amount = entry.getLongValue();
                if (amount > 0) {
                    typeCount++;
                    totalCount += amount;
                }
            }
        }
    }

    /** AE2 每 tick 重建网络库存时会调到:只遍历,不分配中间集合。 */
    public void forEach(KeyCounter out) {
        for (Object2LongMap<AEItemKey> bucket : buckets) {
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            ObjectIterator<Object2LongMap.Entry<AEItemKey>> it = bucket.object2LongEntrySet().iterator();
            while (it.hasNext()) {
                Object2LongMap.Entry<AEItemKey> entry = it.next();
                out.add(entry.getKey(), entry.getLongValue());
            }
        }
    }
}
