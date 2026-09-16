package com.applied.slash.dev;

import java.util.ArrayList;
import java.util.List;

import com.applied.slash.AppliedSlash;
import com.applied.slash.SlashBladeCellItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 开发期诊断:把物品图标**用真实的 GUI 渲染路径**画到屏幕上,然后自动截屏并退出客户端。
 *
 * <p>为什么需要它:"贴图/图标看起来不对"是屏幕上的事,而模型转储只能证明"烘焙结果正确",
 * 证明不了"画出来的样子正确"。这台渲染台把两件事钉在一起 ——
 * 用的是 {@link GuiGraphics#renderItem} 这条和创造栏格子完全相同的代码路径,
 * 画完立刻用 {@link Screenshot#grab} 抓真实帧缓冲,不依赖任何人工操作。
 *
 * <p>每次都带对照组:原版物品(钻石剑/石头)证明"渲染管线本身正常";
 * 若 AE2 在场,还会并排画一个 AE2 自己的 1k 存储元件做同族对照。
 * 这样左起第一格空白就只能是"我们的模型/贴图有问题",而不是"整屏都坏了"。
 *
 * <p>开关:{@code -Dappliedslash.uiShot=true}(gradle 侧 {@code -PuiShot})。
 */
public final class UiScreenshot {
    private static final String PROPERTY = "appliedslash.uiShot";
    private static final int SAMPLES_PER_ROW = 8;
    private static final int CELL = 20;
    private static final int GRAB_AT_FRAME = 6;
    private static final int QUIT_AT_FRAME = 24;

    private static int frames;
    private static boolean grabbed;

    private UiScreenshot() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** 由客户端事件在每帧界面渲染之后调用。 */
    public static void onRender(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        List<ItemStack> samples = samples();
        if (samples.isEmpty()) {
            AppliedSlash.LOGGER.warn("[UISHOT] 没有可画的东西(物品没注册?)");
            return;
        }

        int columns = Math.min(SAMPLES_PER_ROW, samples.size());
        for (int index = 0; index < samples.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = 12 + column * CELL;
            int y = 12 + row * CELL;
            // 深灰底衬:透明像素和白色像素都能看清
            graphics.fill(x - 2, y - 2, x + 18, y + 18, 0xFF404040);
            graphics.renderItem(samples.get(index), x, y);
        }

        // 第二行:把我们的元件与 AE2 的元件都放大 4 倍再画一次。
        // 如果物品只是"画得太小"而不是"根本没画",放大后就会现形。
        // 注意必须每帧都画:截图抓的是当前帧缓冲,而本帧的 GUI 绘制要到帧末才提交。
        if (samples.size() >= 6) {
            graphics.pose().pushPose();
            graphics.pose().scale(4.0F, 4.0F, 1.0F);
            graphics.fill(2, 2, 22, 22, 0xFF404040);
            graphics.fill(22, 2, 42, 22, 0xFF404040);
            graphics.renderItem(samples.get(2), 4, 4);
            graphics.renderItem(samples.get(5), 24, 4);
            graphics.pose().popPose();
        }

        frames++;
        if (!grabbed && frames >= GRAB_AT_FRAME) {
            grabbed = true;
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
                    component -> AppliedSlash.LOGGER.info("[UISHOT] 截图结果: {}", component.getString()));
            AppliedSlash.LOGGER.info("[UISHOT] 已请求截图,共画了 {} 个物品: {}", samples.size(), describe(samples));
        }
        if (frames >= QUIT_AT_FRAME) {
            AppliedSlash.LOGGER.info("[UISHOT] 诊断完成,关闭客户端");
            minecraft.stop();
        }
    }

    private static List<ItemStack> samples() {
        List<ItemStack> samples = new ArrayList<>();

        // 对照组:原版物品,证明渲染管线本身没问题
        addItem(samples, "minecraft:diamond_sword");
        addItem(samples, "minecraft:stone");

        // 主角:空盘 / 有内容 / 类型满 —— 三种状态各画一次(状态只影响 LED 那 6 个面)
        Item cell = BuiltInRegistries.ITEM.get(ResourceLocation.parse(AppliedSlash.MODID + ":slash_blade_cell"));
        if (cell != Items.AIR) {
            samples.add(cell.getDefaultInstance());

            ItemStack filled = cell.getDefaultInstance();
            SlashBladeCellItem.writeSummary(filled, 137, 5_000L, 5_000, false);
            samples.add(filled);

            ItemStack full = cell.getDefaultInstance();
            SlashBladeCellItem.writeSummary(full, 5_000, 9_999L, 5_000, false);
            samples.add(full);
        }

        // 同族对照:AE2 自己的 1k 元件(同样用 layer0+layer1 的生成模型)
        addItem(samples, "ae2:item_storage_cell_1k");

        return samples;
    }

    private static void addItem(List<ItemStack> samples, String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item != Items.AIR) {
            samples.add(item.getDefaultInstance());
        }
    }

    private static String describe(List<ItemStack> samples) {
        StringBuilder builder = new StringBuilder();
        for (ItemStack stack : samples) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return builder.toString();
    }
}
