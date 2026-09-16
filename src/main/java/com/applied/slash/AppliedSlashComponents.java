package com.applied.slash;

import java.util.UUID;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的物品组件。
 *
 * <p>设计要点:元件物品上**只**保存"指向世界侧刀库的 UUID"与一份极小的摘要,
 * 不保存刀身数据本身 —— 这是性能需求的核心(见 PLAN v2 §3)。摘要组件是为了让
 * 客户端 tooltip 能显示真实用量(客户端读不到服务端的刀库)。
 */
public final class AppliedSlashComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AppliedSlash.MODID);

    /** 指向世界侧刀库存档的标识;首次插入时分配。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> VAULT_ID =
            COMPONENTS.registerComponentType("vault_id",
                    builder -> builder
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC));

    /**
     * 用量摘要,键:{@code types}(int)、{@code count}(long)、{@code missing}(boolean,载荷丢失标记)。
     * 只用于 tooltip 与直观诊断,不参与存储逻辑。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> VAULT_SUMMARY =
            COMPONENTS.registerComponentType("vault_summary",
                    builder -> builder
                            .persistent(CompoundTag.CODEC)
                            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    private AppliedSlashComponents() {
    }
}
