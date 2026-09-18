package com.applied.slash.charged.client;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * 充能器界面的**样式读取**(AE2 屏样式文档的一个只读子集)。
 *
 * <h2>这个类解决什么问题</h2>
 * 需求是「界面与 AE2 风格保持一致」。最彻底的做法是用 AE2 的 {@code AEBaseScreen} +
 * {@code StyleManager.loadStyleDoc} —— 但那要求菜单是 {@code appeng.menu.AEBaseMenu}
 * (类型参数 {@code AEBaseScreen<T extends AEBaseMenu>} 硬绑,且 {@code AEBaseScreen}
 * 的槽位摆放直接调 {@code AEBaseMenu#getSlots(SlotSemantic)})。
 * 本模组的充能器菜单继承原版 {@code AbstractContainerMenu}(槽内容同步走原版机制),
 * 所以不能直接复用那两个类。
 *
 * <h2>改成什么做法</h2>
 * 不复用 AE2 的<b>类</b>,而是复用它的<b>资源</b>:直接读
 * {@code assets/applied_slash/screens/blade_charger.json}(它被写成 AE2 屏样式文档的形状,
 * 并通过 {@code "includes"} 引用 AE2 自己的 {@code ae2:screens/common/palette.json}),
 * 只取两样东西:
 * <ul>
 *   <li>{@code slots} —— 每个语义槽的 {@code left/top}(布局的唯一来源;改布局只改 json);</li>
 *   <li>{@code palette} —— AE2 的 8 个语义色。色值来自 AE2 自己的资源文件,
 *       所以 AE2 换主题(或资源包覆盖)时这里跟着变,而不是我在代码里抄一份会过期的色值。</li>
 * </ul>
 * 面板边框仍然调 AE2 的 {@link appeng.client.gui.style.BackgroundGenerator#draw}
 * (public static,AE2 自己 {@code drawBG} 用的同一个 9 宫格生成器)。
 *
 * <h2>解析规则</h2>
 * 逐条对应 AE2 {@code StyleManager} 的行为,刻意保持一致:
 * <ul>
 *   <li>{@code includes} 按 <b>文档所在目录</b> 解析相对路径;</li>
 *   <li>不写命名空间的资源 id 用 {@code ae2} 作默认命名空间(AE2 的 {@code loadMergedJsonTree}
 *       就是把它自己的 {@code AppEng.MOD_ID} 当默认值);</li>
 *   <li>后加载的层<b>覆盖</b>先加载的层(AE2 的 {@code mergeObjectKeys})。</li>
 * </ul>
 *
 * <p>本类只在客户端加载(调用点是 {@link BladeChargerScreen})。
 */
public final class ChargerScreenStyle {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 本模组的样式文档(与 {@code BladeChargerScreen.STYLE_DOC} 对应,但这里是资源 id 形式)。 */
    private static final ResourceLocation STYLE_ID =
            ResourceLocation.fromNamespaceAndPath("applied_slash", "screens/blade_charger.json");

    /** 缺省命名空间:与 AE2 一致 —— 它把不带命名空间的 id 都当自己的资源。 */
    private static final String DEFAULT_NAMESPACE = "ae2";

    /** 语义槽的左上角坐标(界面相对,单位像素)。 */
    public record SlotPos(int x, int y) {
    }

    /** 解析结果:槽位 + 调色板。两者都可能为空(资源包异常),调用方一律要有兜底。 */
    public record Data(Map<String, SlotPos> slots, Map<String, Integer> palette) {
        static Data empty() {
            return new Data(Map.of(), Map.of());
        }
    }

    /**
     * 全局缓存:一次会话只读一次 json(读取发生在客户端线程,故用 volatile + 局部构建)。
     *
     * <p><b>刻意没有资源重载钩子</b>:曾试过注册 {@code PreparableReloadListener} 在重载时清缓存,
     * 结果客户端卡死在加载界面(线程转储与启动日志对照见 {@code AppliedSlashClient} 里那段说明)。
     * 代价是「开着客户端切资源包」时本界面可能仍用旧色值,重启客户端即恢复。
     */
    private static volatile Data cache;

    private ChargerScreenStyle() {
    }

    /** 取(并缓存)解析结果;任何失败都退化为空数据,绝不抛给界面。 */
    public static Data get() {
        Data local = cache;
        if (local == null) {
            local = load();
            cache = local;
        }
        return local;
    }

    private static Data load() {
        try {
            JsonObject merged = loadMerged(STYLE_ID, "screens/");
            Map<String, SlotPos> slots = parseSlots(merged);
            Map<String, Integer> palette = parsePalette(merged);
            return new Data(slots, palette);
        } catch (Exception e) {
            LOGGER.warn("[Applied Slash] 充能器界面样式 {} 读取失败:{}", STYLE_ID, e.toString());
            return Data.empty();
        }
    }

    // ------------------------------------------------------------------
    // includes 链
    // ------------------------------------------------------------------

    /**
     * 递归加载 {@code includes} 链并合并。
     *
     * @param id      要加载的文档
     * @param baseDir 该文档内相对 include 的基准目录(如 {@code "screens/"}),末尾带斜杠
     */
    private static JsonObject loadMerged(ResourceLocation id, String baseDir) throws Exception {
        JsonObject root = readJson(id);
        JsonObject merged = new JsonObject();
        JsonElement includes = root.get("includes");
        if (includes != null && includes.isJsonArray()) {
            for (JsonElement entry : includes.getAsJsonArray()) {
                String raw = entry.getAsString();
                ResourceLocation includeId = resolve(raw, baseDir);
                String includeDir = directoryOf(includeId.getPath());
                mergeInto(merged, loadMerged(includeId, includeDir));
            }
        }
        mergeInto(merged, root);
        return merged;
    }

    /** 与 AE2 同口径:相对 id 按当前文档所在目录展开,不带命名空间时用 {@code ae2}。 */
    private static ResourceLocation resolve(String raw, String baseDir) {
        String namespace = DEFAULT_NAMESPACE;
        String path = raw;
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            namespace = raw.substring(0, colon);
            path = raw.substring(colon + 1);
        } else {
            path = baseDir + path;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** {@code "screens/x.json"} → {@code "screens/"};没有目录时返回空串。 */
    private static String directoryOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    /**
     * 把 {@code layer} 合进 {@code target}:同名对象**递归**合并,其余键直接覆盖。
     *
     * <p>递归是必需的:AE2 的 {@code palette.json} 与我们的文档都会有 {@code palette} 对象,
     * 浅合并会把先加载的那 8 个语义色整块丢掉。
     */
    private static void mergeInto(JsonObject target, JsonObject layer) {
        for (Map.Entry<String, JsonElement> entry : layer.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            JsonElement existing = target.get(key);
            if (existing != null && existing.isJsonObject() && value.isJsonObject()) {
                mergeInto(existing.getAsJsonObject(), value.getAsJsonObject());
            } else {
                target.add(key, value);
            }
        }
    }

    private static JsonObject readJson(ResourceLocation id) throws Exception {
        Resource resourceOpt = Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow(() -> new java.io.FileNotFoundException(id.toString()));
        try (BufferedReader reader = resourceOpt.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    // ------------------------------------------------------------------
    // 取值
    // ------------------------------------------------------------------

    private static Map<String, SlotPos> parseSlots(JsonObject merged) {
        Map<String, SlotPos> slots = new HashMap<>();
        JsonElement slotsEl = merged.get("slots");
        if (slotsEl == null || !slotsEl.isJsonObject()) {
            return slots;
        }
        for (Map.Entry<String, JsonElement> entry : slotsEl.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject pos = entry.getValue().getAsJsonObject();
            Integer left = asInt(pos.get("left"));
            Integer top = asInt(pos.get("top"));
            if (left != null && top != null) {
                slots.put(entry.getKey(), new SlotPos(left, top));
            }
        }
        return slots;
    }

    /**
     * 解析调色板。
     *
     * <p>色值格式沿用 AE2 的 {@code Color.parse}:{@code #RRGGBB} 或 {@code #AARRGGBB}
     * ——注意 AE2 的四段写法是 <b>ARGB</b>(它自己的 {@code toString} 也按 ARGB 输出)。
     */
    private static Map<String, Integer> parsePalette(JsonObject merged) {
        Map<String, Integer> palette = new HashMap<>();
        JsonElement paletteEl = merged.get("palette");
        if (paletteEl == null || !paletteEl.isJsonObject()) {
            return palette;
        }
        for (Map.Entry<String, JsonElement> entry : paletteEl.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            Integer argb = parseColor(entry.getValue().getAsString());
            if (argb != null) {
                palette.put(entry.getKey(), argb);
            }
        }
        return palette;
    }

    private static Integer parseColor(String raw) {
        if (raw == null || !raw.startsWith("#")) {
            return null;
        }
        String hex = raw.substring(1);
        try {
            // 三段 = RGB(不透明),四段 = ARGB(与 AE2 的 Color.parse 一致)
            return switch (hex.length()) {
                case 6 -> (int) (0xFF000000L | Long.parseLong(hex, 16));
                case 8 -> (int) Long.parseLong(hex, 16);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer asInt(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
