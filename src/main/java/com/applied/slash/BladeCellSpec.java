package com.applied.slash;

/**
 * SlashBlade存储元件的容量与结构参数(不引用任何 AE2/Minecraft 类,便于被物品 tooltip 与库存共用)。
 *
 * <p>为什么容量按"类型"而不是"字节":拔刀剑几乎每把都独一无二(刀身数据不同即不同存储键),
 * 所以真正的瓶颈是能容纳多少个不同的键,而不是物品堆叠数。AE2 原生单元的 63 类型上限
 * 在本模组不适用 —— 我们自研库存,不受该钳制(见 PLAN v2 §2.2)。
 */
public final class BladeCellSpec {
    /** 绝对上限(实测 20000 类型时全网每 tick 重建约 5.5 ms);实际生效值见 {@link AppliedSlashConfig#maxTypes()}。 */
    public static final int MAX_TYPES = 20_000;
    /** 同一种刀(刀身数据完全一致)的最大堆叠数。 */
    public static final long MAX_AMOUNT_PER_TYPE = 1_000_000L;
    /** 空闲耗电(AE/t)。 */
    public static final double IDLE_DRAIN = 1.5;
    /** 世界侧刀库的分片数:分片让一次存档只重写"变脏的那一片",避免每次保存重写全部数据。 */
    public static final int VAULT_SHARDS = 16;

    private BladeCellSpec() {
    }
}
