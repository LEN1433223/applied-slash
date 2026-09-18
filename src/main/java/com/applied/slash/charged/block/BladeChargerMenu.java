package com.applied.slash.charged.block;

import com.applied.slash.SlashBladeBlades;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 充能方块的菜单:槽 0 = 刀槽,其余 = 玩家背包(3 行主背包 + 1 行快捷栏);
 * 另有 4 个 int 的 {@code ContainerData}
 * (能量 / 上限 / 状态 / 每点 AE 成本,见 {@link BladeChargerBlockEntity})。
 *
 * <p>界面本身**不用任何贴图**(纯自绘),原因不是偷懒:
 * {@code tools/resources.gradle} 会断言本模组的每个 PNG 都是 16×16,
 * 一张 176×166 的 GUI 背景会直接让 {@code verifyResources} 失败。自绘 {@code fill} 零资源、零风险。
 *
 * <p>槽位直接指向方块实体本身({@link BladeChargerBlockEntity} 实现了 {@code Container}),
 * 所以槽内容的同步完全走原版菜单机制({@code broadcastChanges} → 客户端),
 * 不需要自己写 {@code getUpdateTag}/{@code getUpdatePacket}。
 */
public class BladeChargerMenu extends AbstractContainerMenu {
    /** 界面里唯一的机器槽位下标。 */
    public static final int SLOT_BLADE = BladeChargerBlockEntity.SLOT_BLADE;

    /** 机器槽数量(只有刀槽)。 */
    private static final int MACHINE_SLOTS = 1;
    /** 玩家槽在菜单里的下标区间:[PLAYER_START, PLAYER_END)。 */
    private static final int PLAYER_START = MACHINE_SLOTS;
    private static final int PLAYER_END = PLAYER_START + 36;

    /** 布局(全部相对界面左上角;界面尺寸用 {@code AbstractContainerScreen} 的默认 176×166)。 */
    private static final int SLOT_BLADE_X = 26;
    private static final int SLOT_BLADE_Y = 35;
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_STEP = 18;

    /** 交互距离(原版方块界面的通用上限:8 格 ⇒ 平方 64)。 */
    private static final double MAX_INTERACTION_DISTANCE_SQR = 64.0;

    private final BlockPos pos;
    private final ContainerData data;
    /** 刀槽背后的容器(方块实体本身;方块实体找不到时退化为一个临时单格容器,避免 NPE)。 */
    private final Container bladeContainer;

    /**
     * 由 {@code IMenuTypeExtension} 的工厂在客户端/服务端两侧各自调用一次
     * (坐标经 {@code FriendlyByteBuf} 传来,两侧读的是同一条数据)。
     */
    public BladeChargerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(BladeChargerRegistry.BLADE_CHARGER_MENU.get(), containerId);
        this.pos = pos;
        BladeChargerBlockEntity charger = findCharger(playerInventory, pos);
        this.bladeContainer = charger != null ? charger : new SimpleContainer(MACHINE_SLOTS);
        this.data = charger != null
                ? (ContainerData) charger
                // 理论上到不了这里(菜单只由该方块打开);给一个等长的只读数据源,避免 NPE
                : new SimpleContainerData(4);

        this.addSlot(new BladeSlot(this.bladeContainer, SLOT_BLADE, SLOT_BLADE_X, SLOT_BLADE_Y));

        // 玩家主背包(3 行 × 9)
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * SLOT_STEP, PLAYER_INV_Y + row * SLOT_STEP));
            }
        }
        // 快捷栏(1 行 × 9)
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * SLOT_STEP, HOTBAR_Y));
        }

        // 4 个 int 的服务端 → 客户端推送(界面画能量条与状态行要用)
        this.addDataSlots(this.data);
    }

    /** 供界面读能量条与状态行。 */
    public ContainerData getData() {
        return data;
    }

    /** 菜单对应的方块坐标(界面与调试用)。 */
    public BlockPos getPos() {
        return pos;
    }

    /**
     * 距离 + 方块类型校验:玩家走远(> 8 格)或方块被换掉时关闭界面。
     *
     * <p>用「方块类型」而不是「方块实体类型」判断,客户端也能成立(方块状态一定是同步的)。
     */
    @Override
    public boolean stillValid(Player player) {
        if (pos == null) {
            return false;
        }
        if (!(player.level().getBlockState(pos).getBlock() instanceof BladeChargerBlock)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_INTERACTION_DISTANCE_SQR;
    }

    /**
     * shift 点击搬运:刀槽 ↔ 玩家背包。
     *
     * <p>刀不可堆叠,所以每一趟最多搬 1 件;从玩家背包往刀槽搬时,只有拔刀剑才放得进去
     * (由 {@link BladeSlot#mayPlace} 拦;这里用 {@code moveItemStackTo} 的返回值判断有没有搬成,
     * 搬不成时原样返回 EMPTY —— 与原版菜单一致的行为)。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == SLOT_BLADE) {
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(stack, SLOT_BLADE, SLOT_BLADE + 1, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** 客户端与服务端都要能找到同一个方块实体(客户端那份靠方块实体同步存在)。 */
    private static BladeChargerBlockEntity findCharger(Inventory playerInventory, BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return playerInventory.player.level().getBlockEntity(pos) instanceof BladeChargerBlockEntity charger
                ? charger
                : null;
    }

    /** 刀槽:只收**拔刀剑**(任意一把,含数据包刀「莉莉」),且只有 1 件。 */
    private static final class BladeSlot extends Slot {
        BladeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return SlashBladeBlades.isSlashBlade(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
