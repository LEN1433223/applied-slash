package com.applied.slash;

import com.applied.slash.cell.SlashBladeCellHandler;
import com.applied.slash.cell.UnstackableCellHandler;
import com.applied.slash.charged.block.BladeChargerRegistry;
import com.applied.slash.portable.PortableSlashCellHandler;
import com.applied.slash.portable.PortableSlashCellItem;

import appeng.api.client.StorageCellModels;
import appeng.api.storage.StorageCells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * AE2 侧接线。所有对 {@code appeng.*} 的引用都集中在本类,
 * 只有在确认 AE2 已加载后才会被类加载 —— 否则前置缺席时会直接 NoClassDefFoundError。
 */
public final class AppliedSlashAe2 {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedSlash.MODID);

    public static final DeferredItem<SlashBladeCellItem> SLASH_BLADE_CELL = ITEMS.registerItem(
            "slash_blade_cell",
            SlashBladeCellItem::new,
            new Item.Properties().stacksTo(1));

    /**
     * 「不可堆叠物品存储元件」——与拔刀剑元件**并列**的第二个元件(用户选择"另写一份平行实现,
     * 不要重构现有元件")。接受范围见 {@link com.applied.slash.cell.UnstackableIdentity}:
     * 不可堆叠 + 非拔刀剑 + 非存储元件类物品;其库存实现绝不剥离任何组件。
     */
    public static final DeferredItem<UnstackableItemCellItem> UNSTACKABLE_ITEM_CELL = ITEMS.registerItem(
            "unstackable_item_cell",
            UnstackableItemCellItem::new,
            new Item.Properties().stacksTo(1));

    /**
     * 手持「Slash 元件」:AE2 便携元件(需充电),容量 8 把拔刀剑,同 NBT 不合并。
     * 界面直接复用 AE2 的便携元件菜单类型(见 {@link PortableSlashCellItem})。
     */
    public static final DeferredItem<PortableSlashCellItem> PORTABLE_SLASH_CELL = ITEMS.registerItem(
            "portable_slash_cell",
            PortableSlashCellItem::new,
            new Item.Properties().stacksTo(1));

    private AppliedSlashAe2() {
    }

    /** 注册物品;必须在 mod 构造期调用。 */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        // 充能方块的四个注册器(方块 / 方块物品 / 方块实体类型 / 菜单类型)。
        // 它引用了 appeng.*,所以必须留在这个 AE2 门卫之内 —— 保住「AE2 缺席也不 NoClassDefFoundError」。
        BladeChargerRegistry.register(modBus);
    }

    /**
     * 注册期结束之后才能执行的接线。在 {@code FMLCommonSetupEvent} 中调用。
     *
     * <p>关于升级卡:AE2 的物品存储单元可装 fuzzy / inverter / equal distribution / void 各 1 张。
     * 本模组**刻意不注册任何升级卡白名单** —— 我们的库存没有分区语义,装了 fuzzy/inverter 也不会有
     * 效果,与其提供"装了没反应"的假功能,不如先不支持(见 {@link SlashBladeCellItem} 的说明)。
     */
    public static void registerIntegration() {
        // 接上自研库存(必须在 AE2 自带 BasicCellHandler 之后的判定里仍然命中,见 SlashBladeCellHandler)
        StorageCells.addCellHandler(SlashBladeCellHandler.INSTANCE);
        // 第二个元件同理:两个 handler 的 isCell 互斥(各自认自己的物品类),注册顺序对它们无影响
        StorageCells.addCellHandler(UnstackableCellHandler.INSTANCE);
        // 第三个:手持便携元件(8 格拔刀剑)。它的 isCell 只看自己的物品类,与上面两个互斥。
        StorageCells.addCellHandler(PortableSlashCellHandler.INSTANCE);

        // 让 ME 驱动器 / 机箱里渲染出元件本体(未注册时回退为 ae2:block/drive/drive_cell)
        StorageCellModels.registerModel(SLASH_BLADE_CELL.get(),
                ResourceLocation.parse(AppliedSlash.MODID + ":block/slash_blade_cell"));
        // 不可堆叠物品元件**暂时复用**拔刀剑元件的驱动器单元模型:驱动器里只采样一张 16x16 贴图上
        // 的 6x2 面板,复用现有模型可以少维护一套 block 模型与贴图;后续若要独立外观,
        // 再写一个 applied_slash:block/unstackable_item_cell 模型并在这一行换掉路径即可。
        StorageCellModels.registerModel(UNSTACKABLE_ITEM_CELL.get(),
                ResourceLocation.parse(AppliedSlash.MODID + ":block/slash_blade_cell"));
    }
}
