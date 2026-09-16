package com.applied.slash.charged.block;

import com.applied.slash.AppliedSlash;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 充能方块的四个注册器 + 一次性 {@link #register}(方块 / 方块物品 / 方块实体类型 / 菜单类型)。
 *
 * <p><b>只在本类及其下游({@code charged.block.*} / {@code charged.client.*})里引用 {@code appeng.*}</b>,
 * 调用点被 AE2 门卫保护(见 {@code AppliedSlashAe2.register})⇒ 前置缺席时这些类不会被类加载。
 *
 * <p>注册时机:在 {@code AppliedSlashAe2.register(IEventBus)} 末尾追加本类的 {@link #register}。
 */
public final class BladeChargerRegistry {
    /** 方块与方块物品的注册名 —— 必须与资源文件名完全一致,否则 verifyResources 第 1 条点名。 */
    public static final String BLADE_CHARGER_ID = "blade_charger";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AppliedSlash.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedSlash.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AppliedSlash.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AppliedSlash.MODID);

    /** 方块本体(硬度 3.5)。 */
    public static final DeferredHolder<Block, BladeChargerBlock> BLADE_CHARGER = BLOCKS.register(
            BLADE_CHARGER_ID,
            () -> new BladeChargerBlock(BlockBehaviour.Properties.of().strength(3.5F)));

    /**
     * 方块物品。
     *
     * <p><b>刻意用 {@code registerItem("blade_charger", ...)} 而不是 {@code registerSimpleBlockItem}</b>:
     * {@code tools/resources.gradle} 的正则只匹配 {@code registerItem("...")},
     * 走 simple 变体的话这个物品就<b>不会</b>被资源自检覆盖(模型缺了也不会报错)。
     * 同理,这里的 id 必须写成**字符串字面量**而不是 {@link #BLADE_CHARGER_ID} 常量 ——
     * 正则匹配的是源码字面量,用常量会让这条自检静默失效。
     */
    public static final DeferredItem<BlockItem> BLADE_CHARGER_ITEM = ITEMS.registerItem(
            "blade_charger",
            props -> new BlockItem(BLADE_CHARGER.get(), props),
            new Item.Properties());

    /**
     * 方块实体类型。
     *
     * <p>{@code build(null)} 是**合法**的:事实表 §2 实证
     * {@code BlockEntityType$Builder#build(com.mojang.datafixers.types.Type<?>)} 是**唯一**的重载
     * (没有无参版本)⇒ 传 null 就是唯一写法。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BladeChargerBlockEntity>> BLADE_CHARGER_BE =
            BLOCK_ENTITIES.register(BLADE_CHARGER_ID, () -> BlockEntityType.Builder
                    .of(BladeChargerBlockEntity::new, BLADE_CHARGER.get())
                    .build(null));

    /**
     * 菜单类型。用 NeoForge 的 {@code IMenuTypeExtension} 才能把方块坐标一起送到客户端
     * (菜单需要靠坐标找回那个方块实体)。
     *
     * <p>形状已由事实表 §3 实证:{@code IMenuTypeExtension#create(IContainerFactory<T>)} 是
     * static interface method,而 {@code IContainerFactory<T>#create(int, Inventory, RegistryFriendlyByteBuf)}
     * 正好就是下面这个 lambda 的形状 ⇒ 直接 {@code create(...)} 即可。
     *
     * <p><b>两侧必须成对</b>:这里的 {@code data.readBlockPos()} 读的是服务端打开界面时写进去的坐标
     * (见 {@code BladeChargerBlock#useWithoutItem} 的 {@code openMenu(provider, buf -> buf.writeBlockPos(pos))})。
     * 任何一侧少写/少读,客户端就会拿不到方块实体,表现为「界面里只有一个空刀槽」。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<BladeChargerMenu>> BLADE_CHARGER_MENU =
            MENUS.register(BLADE_CHARGER_ID, () -> IMenuTypeExtension.create(
                    (windowId, playerInventory, data) ->
                            new BladeChargerMenu(windowId, playerInventory, data.readBlockPos())));

    private BladeChargerRegistry() {
    }

    /** 注册全部四个注册器;必须在 mod 构造期、AE2 门卫内调用。 */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}
