package com.applied.slash;

import java.util.UUID;

import com.mojang.serialization.Codec;

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

    /**
     * 充能拔刀剑的**能量**(整型)。
     *
     * <p><b>为什么必须是本模组自己的组件</b>(事实表 §8.2):
     * 重锋的 {@code BladeStateData} 是 <b>record、字段固定</b>,加不了字段;而
     * {@code BLADE_RUNTIME_STATE} 会被本模组的存储元件剥离 ⇒ 只能自己开一张组件。
     *
     * <p>它是<b>普通持久化组件</b>:
     * <ul>
     *   <li>随物品整栈往返({@code AEItemKey.toTag/fromTag})⇒ 「存进元件再取出,能量保留」天然成立;</li>
     *   <li>不参与 {@code stripVolatileState}(那里只删 {@code BLADE_RUNTIME_STATE});</li>
     *   <li><b>会进入 AE2 的存储键</b> ⇒ 每个能量值都是一个新类型 ⇒ 必须有量化兜底
     *       (见 {@code charged/ChargedBladeEnergy} 与 {@code cell/BladeIdentity} 的两个调用点)。</li>
     * </ul>
     *
     * <p>命名:PLAN §4.1 定的是 {@code applied_slash:blade_energy}(与常量名 BLADE_ENERGY 同源);
     * 事实表 §8.2 举例写作 {@code blade_charge}。此处取 PLAN 的 {@code blade_energy};
     * 若要改名,只需改这一处字符串(C 侧的辅助方法名不受影响)。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BLADE_ENERGY =
            COMPONENTS.registerComponentType("blade_energy",
                    builder -> builder
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    private AppliedSlashComponents() {
    }
}
