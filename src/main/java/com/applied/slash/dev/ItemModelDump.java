package com.applied.slash.dev;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.applied.slash.AppliedSlash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 开发期诊断:把**游戏真正烘焙出来的物品模型**打印到日志。
 *
 * <p>为什么需要它:贴图"看起来不对"时,只看资源文件无法区分下面这几种完全不同的故障 ——
 * 模型没烤出来(退化成 missing)、被烤成了方块模型、某层贴图没进图集(退化成 missingno)、
 * tint 索引错层(整件物品被状态色刷成一块纯色)、贴图像素本身不对。
 * 这些只有问客户端自己的 {@code ItemRenderer}/{@code ModelManager} 才能得到地面真值。
 *
 * <p>开关:{@code -Dappliedslash.modelDump=true}(gradle 侧 {@code -PmodelDump})。
 * 进入标题界面时(资源重载已完成)打印一次。
 */
public final class ItemModelDump {
    private static final String PROPERTY = "appliedslash.modelDump";

    private static boolean done;

    private ItemModelDump() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** 幂等:只有第一次调用会真的打印。 */
    public static void dumpOnce(ResourceLocation itemId) {
        if (done) {
            return;
        }
        done = true;
        // 并排转储三个模型:我们的 / AE2 能正常显示的同类元件 / 原版物品(已知一定显示正常)。
        // 差异就在这三份输出之间,比单独看一份自己猜要可靠。
        List<ResourceLocation> targets = List.of(
                itemId,
                ResourceLocation.parse("ae2:item_storage_cell_1k"),
                ResourceLocation.parse("minecraft:diamond_sword"));
        for (ResourceLocation target : targets) {
            try {
                AppliedSlash.LOGGER.info("[MODELDUMP] ==================== {} ====================", target);
                dump(target);
            } catch (RuntimeException | LinkageError failure) {
                AppliedSlash.LOGGER.error("[MODELDUMP] 转储 " + target + " 失败", failure);
            }
        }
    }

    /**
     * 转储创造栏内容 —— "标签页点开是空网格"和"物品图标画不出来"是两种完全不同的故障,
     * 这一条用来区分:前者说明物品没进标签页,后者说明标签页里有物品但画不出来。
     * 需要在创造栏打开时调用(标签页内容那时才构建好)。
     */
    public static void dumpCreativeTabs(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        for (var entry : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            net.minecraft.world.item.CreativeModeTab tab = entry.getValue();
            java.util.Collection<ItemStack> items;
            try {
                items = tab.getDisplayItems();
            } catch (RuntimeException | LinkageError failure) {
                AppliedSlash.LOGGER.warn("[MODELDUMP] 创造栏 {} 取内容失败: {}",
                        entry.getKey().location(), failure.toString());
                continue;
            }
            boolean contains = items.stream().anyMatch(stack -> stack.getItem() == item);
            if (!contains && !tab.getDisplayName().getString().contains("拔刀剑")) {
                continue;
            }
            AppliedSlash.LOGGER.info("[MODELDUMP] 创造栏 {} \"{}\" 物品数={} 含本物品={} 图标={} 前5项={}",
                    entry.getKey().location(),
                    tab.getDisplayName().getString(),
                    items.size(),
                    contains,
                    tab.getIconItem().getItem(),
                    items.stream().limit(5).map(stack -> String.valueOf(stack.getItem())).toList());
        }
    }

    private static void dump(ResourceLocation itemId) {
        Minecraft minecraft = Minecraft.getInstance();
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) {
            AppliedSlash.LOGGER.warn("[MODELDUMP] 物品注册表里没有 {}", itemId);
            return;
        }

        BakedModel missing = minecraft.getModelManager().getMissingModel();
        ItemStack stack = item.getDefaultInstance();

        // 物品栏/手持走的就是这个入口
        BakedModel model = minecraft.getItemRenderer().getModel(stack, null, null, 0);
        AppliedSlash.LOGGER.info("[MODELDUMP] {} -> {} | missing={} gui3d={} blockLight={} customRenderer={} ao={}",
                itemId,
                model.getClass().getName(),
                model == missing,
                model.isGui3d(),
                model.usesBlockLight(),
                model.isCustomRenderer(),
                model.useAmbientOcclusion());
        AppliedSlash.LOGGER.info("[MODELDUMP] particle 层 -> {}", describe(model.getParticleIcon()));

        // 物品栏模型变体单独查一次(创造栏/物品栏按 inventory 变体解析)
        try {
            BakedModel inventory = minecraft.getModelManager().getModel(ModelResourceLocation.inventory(itemId));
            AppliedSlash.LOGGER.info("[MODELDUMP] inventory 变体 -> {} | missing={}",
                    inventory.getClass().getName(), inventory == missing);
        } catch (RuntimeException failure) {
            AppliedSlash.LOGGER.warn("[MODELDUMP] inventory 变体查询失败: {}", failure.toString());
        }

        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(42L));
        Map<Integer, Integer> byTint = new HashMap<>();
        AppliedSlash.LOGGER.info("[MODELDUMP] 无朝向面 = {} 个", quads.size());
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            byTint.merge(quad.getTintIndex(), 1, Integer::sum);
            TextureAtlasSprite sprite = quad.getSprite();
            AppliedSlash.LOGGER.info("[MODELDUMP]   #{} tint={} dir={} sprite={} 图集占位[{},{},{},{}] 顶点 {}",
                    i, quad.getTintIndex(), quad.getDirection(), describe(sprite),
                    fmt(sprite.getU0()), fmt(sprite.getU1()), fmt(sprite.getV0()), fmt(sprite.getV1()),
                    vertices(quad));
        }
        AppliedSlash.LOGGER.info("[MODELDUMP] 面按 tint 索引分布 = {}", byTint);

        // 显示变换:GUI 里那 16x16 的外观全靠 display.gui 这条变换把模型单位换算成像素。
        // 它一旦缺失/为零,物品就会画成一个像素点或者画到屏幕外 —— 表现正是"槽位是空的"。
        var transforms = model.getTransforms();
        var gui = transforms.gui;
        AppliedSlash.LOGGER.info("[MODELDUMP] display.gui: translation={} rotation={} scale={} 是默认无变换={}",
                gui.translation, gui.rotation, gui.scale,
                gui == net.minecraft.client.renderer.block.model.ItemTransform.NO_TRANSFORM);

        // 每一层的贴图在图集里到底是什么像素 —— 这能区分"我们的贴图"和"退化成 missingno"
        quads.stream().map(BakedQuad::getSprite).distinct().forEach(ItemModelDump::dumpSpritePixels);

        // 再用软件把模型画一遍,写成 PNG —— 不看屏幕也能知道游戏会画出什么
        try {
            rasterize(model, stack, itemId);
        } catch (RuntimeException | LinkageError | java.io.IOException failure) {
            AppliedSlash.LOGGER.warn("[MODELDUMP] 软件重绘失败: {}", failure.toString());
        }
    }

    /**
     * 软件重绘:用烘好的四边形(顶点位置 + UV)去采样图集里该贴图的原图,逐层合成成 16x16。
     *
     * <p>这条路子不需要显卡,也不是"贴图看起来应该长这样"的推测 —— 它用的就是客户端烘出来的
     * 几何与 UV、以及客户端图集里的像素。物品图标若真的画不出来(UV 退化、采样到全透明像素、
     * 层被盖住),这张图就会跟着一起空/纯色,一眼就能定案。
     *
     * <p>生成的物品四边形都是轴对齐平行四边形,所以用"原点 + 两条边"解一次 2x2 线性方程组,
     * 就能把像素中心映射回 UV。
     */
    private static void rasterize(BakedModel model, ItemStack stack, ResourceLocation itemId) throws java.io.IOException {
        final int size = 16;
        int[] canvas = new int[size * size];
        int drawn = 0;

        for (BakedQuad quad : model.getQuads(null, null, RandomSource.create(42L))) {
            int[] data = quad.getVertices();
            if (data.length % 4 != 0) {
                continue;
            }
            final int stride = data.length / 4;
            double[] vx = new double[4];
            double[] vy = new double[4];
            double[] vu = new double[4];
            double[] vv = new double[4];
            for (int i = 0; i < 4; i++) {
                vx[i] = Float.intBitsToFloat(data[i * stride]);
                vy[i] = Float.intBitsToFloat(data[i * stride + 1]);
                vu[i] = Float.intBitsToFloat(data[i * stride + 4]);
                vv[i] = Float.intBitsToFloat(data[i * stride + 5]);
            }

            double ax = vx[1] - vx[0];
            double ay = vy[1] - vy[0];
            double bx = vx[3] - vx[0];
            double by = vy[3] - vy[0];
            double determinant = ax * by - ay * bx;
            if (Math.abs(determinant) < 1.0E-9) {
                AppliedSlash.LOGGER.warn("[MODELDUMP] 遇到退化四边形(面积 0),UV 无法映射: dir={} sprite={}",
                        quad.getDirection(), describe(quad.getSprite()));
                continue;
            }

            TextureAtlasSprite sprite = quad.getSprite();
            var image = sprite.contents().getOriginalImage();
            double su0 = sprite.getU0();
            double su1 = sprite.getU1();
            double sv0 = sprite.getV0();
            double sv1 = sprite.getV1();
            if (Math.abs(su1 - su0) < 1.0E-9 || Math.abs(sv1 - sv0) < 1.0E-9) {
                AppliedSlash.LOGGER.warn("[MODELDUMP] 图集里该贴图占位为 0(u0={} u1={} v0={} v1={}): {}",
                        su0, su1, sv0, sv1, describe(sprite));
                continue;
            }

            int tint = tintFor(stack, quad.getTintIndex());
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    // 生成的物品四边形坐标在 [0,16] 模型单位;模型 y 向上,图片 y 向下
                    double mx = px + 0.5;
                    double my = size - (py + 0.5);
                    double dx = mx - vx[0];
                    double dy = my - vy[0];
                    double a = (dx * by - dy * bx) / determinant;
                    double b = (ax * dy - ay * dx) / determinant;
                    if (a < 0.0 || a > 1.0 || b < 0.0 || b > 1.0) {
                        continue;
                    }
                    double u = vu[0] + a * (vu[1] - vu[0]) + b * (vu[3] - vu[0]);
                    double v = vv[0] + a * (vv[1] - vv[0]) + b * (vv[3] - vv[0]);
                    int tx = clamp((int) Math.floor((u - su0) / (su1 - su0) * image.getWidth()), image.getWidth());
                    int ty = clamp((int) Math.floor((v - sv0) / (sv1 - sv0) * image.getHeight()), image.getHeight());
                    int argb = toArgb(image.getPixelRGBA(tx, ty));
                    if (((argb >>> 24) & 0xFF) == 0) {
                        continue;
                    }
                    canvas[py * size + px] = over(canvas[py * size + px], multiply(argb, tint));
                    drawn++;
                }
            }
        }

        var out = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                out.setRGB(px, py, canvas[py * size + px]);
            }
        }
        // 放大 8 倍,方便肉眼看细节;透明处补棋盘格,避免被误读成"白色贴图"
        var scaled = new java.awt.image.BufferedImage(size * 8, size * 8, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int argb = canvas[py * size + px];
                if (((argb >>> 24) & 0xFF) == 0) {
                    argb = ((((px >> 1) + (py >> 1)) & 1) == 0) ? 0xFF3A3A3A : 0xFF5A5A5A;
                }
                for (int dy = 0; dy < 8; dy++) {
                    for (int dx = 0; dx < 8; dx++) {
                        scaled.setRGB(px * 8 + dx, py * 8 + dy, argb);
                    }
                }
            }
        }
        java.nio.file.Path target = java.nio.file.Path
                .of("applied_slash-modeldump-" + itemId.getPath().replace('/', '_') + "-x8.png")
                .toAbsolutePath();
        javax.imageio.ImageIO.write(scaled, "png", target.toFile());
        AppliedSlash.LOGGER.info("[MODELDUMP] 软件重绘完成: 采样命中 {} 次,已写出 {}", drawn, target);
    }

    /** 用与客户端染色回调等价的逻辑取 tint(空盘 = CellState.EMPTY 的灰)。 */
    private static int tintFor(ItemStack stack, int tintIndex) {
        if (tintIndex != 1) {
            return 0xFFFFFF;
        }
        return 0x9E9E9E;
    }

    /** NativeImage.getPixelRGBA 是 ABGR 排布(alpha 在高字节),转成常规 ARGB。 */
    private static int toArgb(int raw) {
        int a = (raw >>> 24) & 0xFF;
        int b = (raw >>> 16) & 0xFF;
        int g = (raw >>> 8) & 0xFF;
        int r = raw & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int multiply(int argb, int tint) {
        int a = (argb >>> 24) & 0xFF;
        int r = (((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255;
        int g = (((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF)) / 255;
        int b = ((argb & 0xFF) * (tint & 0xFF)) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 常规 alpha 合成(上层压下层)。 */
    private static int over(int below, int above) {
        int aa = (above >>> 24) & 0xFF;
        if (aa == 255) {
            return above;
        }
        if (aa == 0) {
            return below;
        }
        double alpha = aa / 255.0;
        int ba = (below >>> 24) & 0xFF;
        int outA = (int) Math.round(alpha * 255 + ba * (1.0 - alpha));
        int r = blend((below >> 16) & 0xFF, (above >> 16) & 0xFF, alpha);
        int g = blend((below >> 8) & 0xFF, (above >> 8) & 0xFF, alpha);
        int b = blend(below & 0xFF, above & 0xFF, alpha);
        return (outA << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int below, int above, double alpha) {
        return (int) Math.round(above * alpha + below * (1.0 - alpha));
    }

    private static int clamp(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static void dumpSpritePixels(TextureAtlasSprite sprite) {
        if (sprite == null) {
            AppliedSlash.LOGGER.warn("[MODELDUMP] 面引用了 null sprite");
            return;
        }
        SpriteContents contents = sprite.contents();
        Map<Integer, Integer> histogram = new HashMap<>();
        int opaque = 0;
        try {
            var image = contents.getOriginalImage();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getPixelRGBA(x, y);
                    if (((argb >>> 24) & 0xFF) == 0) {
                        continue;
                    }
                    opaque++;
                    histogram.merge(argb & 0xFFFFFF, 1, Integer::sum);
                }
            }
            String top = histogram.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .map(entry -> String.format("#%06X x%d", entry.getKey(), entry.getValue()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(无)");
            AppliedSlash.LOGGER.info("[MODELDUMP] 图集内像素 {} {}x{} 不透明={} 主色: {}",
                    describe(sprite), contents.width(), contents.height(), opaque, top);
        } catch (RuntimeException | LinkageError failure) {
            AppliedSlash.LOGGER.warn("[MODELDUMP] 读取图集像素失败({}): {}", describe(sprite), failure.toString());
        }
    }

    private static String describe(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return "null";
        }
        return sprite.contents().name() + " @" + sprite.atlasLocation();
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    /** 顶点位置与 UV —— 用来判断模型到底占了多大一块地方、UV 有没有退化。 */
    private static String vertices(net.minecraft.client.renderer.block.model.BakedQuad quad) {
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(data[i * stride]);
            float y = Float.intBitsToFloat(data[i * stride + 1]);
            float z = Float.intBitsToFloat(data[i * stride + 2]);
            float u = Float.intBitsToFloat(data[i * stride + 4]);
            float v = Float.intBitsToFloat(data[i * stride + 5]);
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format(java.util.Locale.ROOT, "(%.3f,%.3f,%.3f u=%.4f v=%.4f)", x, y, z, u, v));
        }
        return builder.toString();
    }
}
