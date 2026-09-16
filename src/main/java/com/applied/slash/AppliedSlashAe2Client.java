package com.applied.slash;

import appeng.api.storage.cells.CellState;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * AE2 侧客户端接线(元件贴图染色)。同样只在 AE2 在场时才会被类加载。
 *
 * <p>染色逻辑与 AE2 自己的 {@code BasicStorageCell.getColor} 一致:tint 索引 1 是 LED 层,
 * 返回状态色;其余图层返回白色(即使用贴图原色)。
 *
 * <p>与 AE2 的差别:AE2 在客户端也去查单元库存拿状态,我们改成读**物品上的摘要组件** ——
 * 摘要随物品同步到客户端,所以 LED 颜色在客户端同样准确,且不需要在客户端访问世界侧刀库。
 */
public final class AppliedSlashAe2Client {
    private static final int LED_TINT_INDEX = 1;

    /**
     * 未染色层必须返回**不透明**白色(alpha=FF)。
     *
     * <p>这里踩过一个隐蔽的坑,记录在案:MC 1.21.1 的
     * {@code ItemRenderer.renderQuadList} 是把 tint 值**按 4 个通道**拆开写进顶点的
     * (字节码里是 {@code FastColor$ARGB32.alpha(tint)} → {@code putBulkData(..., alpha, ...)}),
     * 也就是说 **tint 的 alpha 就是顶点的 alpha**。
     * 如果这里写成 {@code 0xFFFFFF}(alpha=00),物品的每一个顶点都会变成全透明 ——
     * 模型烘焙、图集、UV 全部正确,屏幕上却什么都看不到,而且日志里不会有任何报错。
     */
    private static final int NO_TINT = 0xFFFFFFFF;

    private AppliedSlashAe2Client() {
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(AppliedSlashAe2Client::getColor, AppliedSlashAe2.SLASH_BLADE_CELL.get());
    }

    private static int getColor(ItemStack stack, int tintIndex) {
        if (tintIndex != LED_TINT_INDEX) {
            return NO_TINT;
        }
        int types = SlashBladeCellItem.summaryTypes(stack);
        long count = SlashBladeCellItem.summaryCount(stack);
        final CellState state;
        if (count <= 0) {
            state = CellState.EMPTY;
        } else if (types >= SlashBladeCellItem.summaryMax(stack)) {
            state = CellState.TYPES_FULL;
        } else {
            state = CellState.NOT_EMPTY;
        }
        // 同上:AE2 的 CellState 颜色不带 alpha,必须自己补成不透明,否则 LED 层同样会消失
        return 0xFF000000 | (state.getStateColor() & 0xFFFFFF);
    }
}
