package com.applied.slash.dev;

import java.util.ArrayList;
import java.util.List;

import com.applied.slash.AppliedSlash;
import com.applied.slash.SlashBladeCellItem;
import com.applied.slash.LiliBlade;

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
    /** 打开截图台。 */
    private static final String PROPERTY = "appliedslash.uiShot";
    /**
     * <b>额外</b>开关:截图后自动关闭客户端。默认 <b>不开</b>。
     *
     * <h2>为什么要拆成两个开关(踩过的坑,写下来免得再犯)</h2>
     * 原来"截图 + 关客户端"是同一个开关。而 gradle 每次跑 {@code runClient} 都会重新生成
     * {@code build/moddev/clientRunVmArgs.txt},<b>而 IntelliJ 的 Client 运行配置正是读这个文件启动的</b>
     * (见 {@code .idea/workspace.xml} 里的 {@code VM_PARAMETERS=@...clientRunVmArgs.txt})。
     * 于是我用 {@code -PuiShot} 跑了一次诊断,{@code -Dappliedslash.uiShot=true} 就被留在了那个文件里,
     * 之后玩家从 IDE 启动 —— 标题界面被涂黑画满图标,然后客户端在 24 帧后自己退出。
     * 表现就是"游戏一开就跳出个东西,然后一直进不去游戏"。
     *
     * <p>现在:{@link #PROPERTY} 单独存在时<b>只画一帧、截一张图、然后自我禁用</b>,绝不关客户端;
     * 要自动关(开发期跑批量截图用)必须额外加 {@code -Dappliedslash.uiShotQuit=true}。
     */
    private static final String QUIT_PROPERTY = "appliedslash.uiShotQuit";
    private static final int SAMPLES_PER_ROW = 4;
    /**
     * 格子步距。
     *
     * <p>48 而不是 20:诊断"图标有没有画在自己的格子里"时,20 px 步距会让相邻格子互相侵入,
     * 量出来的包围盒全是邻居的像素(第一次跑就是这个结果)。48 让每个格子四周留出 14 px 空档,
     * 这样"跑位"一旦发生,超出部分与格子边界的关系一目了然。
     */
    private static final int CELL = 48;
    private static final int GRAB_AT_FRAME = 6;
    private static final int QUIT_AT_FRAME = 24;

    private static int frames;
    private static boolean grabbed;
    /** 截过一次就关掉自己 —— 诊断台不该在标题界面上一直刷。 */
    private static boolean done;

    private UiScreenshot() {
    }

    public static boolean enabled() {
        return !done && Boolean.getBoolean(PROPERTY);
    }

    /** 由客户端事件在每帧界面渲染之后调用。 */
    public static void onRender(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        List<ItemStack> samples = samples();
        if (samples.isEmpty()) {
            AppliedSlash.LOGGER.warn("[UISHOT] 没有可画的东西(物品没注册?)");
            done = true;
            return;
        }

        int columns = Math.min(SAMPLES_PER_ROW, samples.size());
        // 全屏铺一层纯黑:格的边界与"跑位"的像素都要能在截图里量出来,标题界面的背景太花
        graphics.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight(), 0xFF000000);

        // 把每一格的实际坐标写进日志:截图是缩放过的,靠反推缩放比去量偏移容易算错
        // (前面就量错过两次)。这里直接把"格子的 GUI 坐标 + 画面缩放"打出来,量图时有基准。
        int screenW = minecraft.getWindow().getWidth();
        int screenH = minecraft.getWindow().getHeight();
        int guiW = minecraft.getWindow().getGuiScaledWidth();
        int guiH = minecraft.getWindow().getGuiScaledHeight();
        AppliedSlash.LOGGER.info("[UISHOT] 画面 {}×{} / GUI {}×{} → 缩放 {}×{}",
                screenW, screenH, guiW, guiH,
                (double) screenW / guiW, (double) screenH / guiH);

        for (int index = 0; index < samples.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = 12 + column * CELL;
            int y = 12 + row * CELL;
            // 深灰底衬 20×20(比格子小,四周留出空档,便于量偏移)
            graphics.fill(x - 2, y - 2, x + 18, y + 18, 0xFF404040);
            graphics.renderItem(samples.get(index), x, y);
            AppliedSlash.LOGGER.info("[UISHOT] #{} {} GUI格=({},{}) 底衬=({},{})-({},{}) 内容应为 ({},{})-({},{})",
                    index, describe(List.of(samples.get(index))), x, y,
                    x - 2, y - 2, x + 18, y + 18, x, y, x + 16, y + 16);
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
        // 只关自己的开关,绝不动客户端 —— 见 QUIT_PROPERTY 的说明(曾经因为"截图即退出"把玩家挡在门外)。
        if (grabbed) {
            done = true;
            AppliedSlash.LOGGER.info("[UISHOT] 截图完成,截图台自我禁用(客户端继续运行;"
                    + "要它截完自动退出请加 -D{}=true)", QUIT_PROPERTY);
        }
        if (frames >= QUIT_AT_FRAME && Boolean.getBoolean(QUIT_PROPERTY)) {
            AppliedSlash.LOGGER.info("[UISHOT] 诊断完成,按 -D{}=true 关闭客户端", QUIT_PROPERTY);
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

        // 本模组的拔刀剑「莉莉」:走的是重锋的原生渲染管线(SlashBladeTEISR)。
        // 它在 GUI 里画的是 3D 模型,尺寸与位置都由重锋自己的 display 决定 —— 跑位时靠这张图量。
        // 数据包注册表要从当前维度取(客户端也有同步过来的那份)。
        var minecraft = Minecraft.getInstance();
        ItemStack lili = minecraft.level == null
                ? ItemStack.EMPTY
                : LiliBlade.stack(minecraft.level.registryAccess());
        if (!lili.isEmpty()) {
            samples.add(lili);
        }

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
