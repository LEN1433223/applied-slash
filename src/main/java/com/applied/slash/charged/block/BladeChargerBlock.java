package com.applied.slash.charged.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 「拔刀剑充能器」方块本体。
 *
 * <p>负责四件事:属性(硬度 3.5) / 右键打开菜单 / 给方块实体提供 ticker / 破坏时不要吞刀。
 * 网格与充能逻辑全部在 {@link BladeChargerBlockEntity}。
 *
 * <p>方块外观走资源链:{@code assets/applied_slash/blockstates/blade_charger.json} +
 * {@code models/block/blade_charger.json}({@code cube_all})+ 一张 16×16 贴图。
 * 文件名必须与 {@code registerItem("blade_charger")} 完全一致,否则
 * {@code verifyResources} 第一条就会点名。
 *
 * <h2>为什么实现 {@code EntityBlock}</h2>
 * 方块要每 tick 结算充能,就必须给出 {@code getTicker} 与 {@code newBlockEntity} ——
 * 这是原版「带方块实体的方块」的标准形状(箱子/熔炉都这么写)。
 * <b>注意</b>:事实表 §2 把 {@code getTicker} 记在 {@code Block} 名下,但 host 自己的 dump
 * ({@code build/facts/blockbehaviour.txt} 里那份 {@code Block} 全量成员表)**没有**这个方法 ——
 * 它是 {@code EntityBlock} 的方法。两种解释下本文件都能编译(父类有则覆盖父类,
 * 只有一个来源则是实现接口),见下面的 NEEDS_HOST_CHECK。
 */
public class BladeChargerBlock extends Block implements EntityBlock {

    public BladeChargerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * 创建方块实体(原版 {@code EntityBlock} 的标准入口)。
     *
     * <p>用两参构造器,类型由它去注册表里取(见 {@code BladeChargerBlockEntity(BlockPos, BlockState)})。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BladeChargerBlockEntity(pos, state);
    }

    /**
     * 服务端每 tick 一次:把缓冲里的 AE 换成刀上的能量。
     *
     * <p>客户端返回 {@code null} = 不 tick(界面数据全部走 {@code ContainerData} 同步)。
     *
     * <p>// NEEDS_HOST_CHECK: {@code BlockEntityTicker#tick(Level, BlockPos, BlockState, T)} 的精确
     * 参数形状没有列进事实表(只列了 {@code Block#getTicker} 的返回类型),这里按 1.21.1 原版约定写。
     * 若编译不过,需要 host 补一次 {@code javap net.minecraft.world.level.block.entity.BlockEntityTicker}。
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof BladeChargerBlockEntity charger) {
                BladeChargerBlockEntity.serverTick(tickLevel, tickPos, tickState, charger);
            }
        };
    }

    /**
     * 空手右键 = 打开界面。
     *
     * <p>用 {@code useWithoutItem}(签名已实证:protected,声明在
     * {@code net.minecraft.world.level.block.state.BlockBehaviour},事实表 §2)而不是
     * {@code useItemOn}:手上拿着任何东西都不该影响交互。
     *
     * <p>打开走 {@code player.openMenu(provider, extraData)}:菜单要靠方块坐标才能同时
     * 在客户端找到那个方块实体,坐标经 {@code FriendlyByteBuf} 写过去,客户端由
     * {@code IMenuTypeExtension} 的工厂 {@code data.readBlockPos()} 读出来(两边必须成对)。
     *
     * <p>// NEEDS_HOST_CHECK: 事实表 §1 只列了 {@code Player#openMenu(MenuProvider)};
     * 这里的双参重载是 NeoForge 的额外数据版本(与 {@code IMenuTypeExtension} 配套)。
     * 若编译不过(该重载不存在),退路是:服务端只写一个占位(give 不了坐标)——
     * 那会让客户端拿不到方块实体,界面上就只剩空槽位,因此**不建议**退回单参版本,
     * 而应让 host 补一次 {@code javap} 确认该重载(或改用 {@code openMenu(provider)} +
     * 客户端用玩家脚下方块反查,后者语义脆弱)。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            // 真正的打开由服务端下发(客户端这一次只是给玩家反馈,不会自己建菜单)
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BladeChargerBlockEntity) {
            player.openMenu(new ChargerMenuProvider(pos), buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * 玩家挖掘时把槽里的刀掉出去(否则吞刀)。
     *
     * <p><b>为什么挂这里而不是 {@code BlockEntity#setRemoved()}</b>:{@code setRemoved} 在
     * 区块卸载时同样会被调用 —— 挂在那里等于「玩家走远一点,充能器就把刀吐一地」。
     *
     * <p>已知边界(与 PLAN 一致):爆破 / 活塞等**非玩家**破坏路径不会走这里,槽里的刀会丢。
     * 方块本身是普通方块,原版也没有「被炸时掉落方块实体内容」的通用机制。
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BladeChargerBlockEntity charger) {
            charger.dropContents();
        }
        return result;
    }

    /**
     * 菜单提供者:把方块坐标经 {@code FriendlyByteBuf} 送到客户端。
     *
     * <p>只在服务端被构造({@code createMenu} 也由服务端调用);客户端那一侧由
     * {@link BladeChargerRegistry#BLADE_CHARGER_MENU} 的工厂直接 {@code new BladeChargerMenu(...)}。
     */
    private record ChargerMenuProvider(BlockPos pos) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return Component.translatable("container.applied_slash." + BladeChargerRegistry.BLADE_CHARGER_ID);
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new BladeChargerMenu(containerId, playerInventory, pos);
        }
    }
}
