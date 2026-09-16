package com.applied.slash;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * 客户端接线。本类不会在专用服务端加载,可以安全引用客户端类。
 *
 * <p>{@code @EventBusSubscriber} 不指定 bus:NeoForge 会按方法参数的
 * {@code IModBusEvent} 归属自动分流到 mod bus / game bus,不需要手写 bus。
 */
@Mod(value = AppliedSlash.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AppliedSlash.MODID, value = Dist.CLIENT)
public class AppliedSlashClient {
    /** 看门狗开关:{@code -Dappliedslash.clientDiag=true}(gradle 侧 {@code -PclientDiag})。 */
    private static final String DIAG_PROPERTY = "appliedslash.clientDiag";
    private static final String DUMP_FILE = "applied_slash-client-dump.txt";
    private static final String SCREEN_LOG_FILE = "applied_slash-client-screens.txt";
    private static final long DIAG_DELAY_MS = 20_000L;

    private static volatile boolean titleScreenSeen;

    static {
        // 必须尽早执行:GuideME 的兜底字段要在首次资源重载的监听器跑之前填好(详见补丁类注释)
        //
        // 生产环境判断:成品 jar 里没有 com/applied/slash/dev/**(build.gradle 末尾的 jar 排除),
        // 而本类在生产环境的客户端**会被加载**(@Mod/@EventBusSubscriber 会被注解扫描拾取),
        // 所以这里的 dev 调用点必须包在"生产环境永不进入"的分支里 —— Java 只在真正执行到调用指令时
        // 才解析 dev 类,不进入分支就不会 NoClassDefFoundError;少了这层判断则生产客户端一启动就炸。
        // NEEDS_JAVAP: net.neoforged.fml.loading.FMLEnvironment#production
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            if (com.applied.slash.dev.GuideMeStartupPatch.enabled()) {
                AppliedSlash.LOGGER.info("[DIAG] GuideME 启动补丁: {}",
                        com.applied.slash.dev.GuideMeStartupPatch.apply());
            }
        }
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AppliedSlash.LOGGER.info("Applied Slash 客户端已就绪,玩家 {}", Minecraft.getInstance().getUser().getName());
        if (Boolean.getBoolean(DIAG_PROPERTY)) {
            Thread watchdog = new Thread(AppliedSlashClient::watchdog, "applied-slash-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
        }
    }

    /**
     * 屏幕切换观测。
     *
     * <p>标题界面出现即证明"客户端资源加载完成、没有被卡在加载界面";这一行是关键判据
     * (开发客户端慢/有第三方 NPE 时很容易被误判为卡死)。
     *
     * <p>{@code -PclientDiag} 打开时会**记录每一次屏幕切换**到 {@value #SCREEN_LOG_FILE}:
     * 只认 TitleScreen 是个盲点 —— 万一客户端停在了别的界面(错误屏、GenericMessageScreen 等),
     * 只盯 TitleScreen 会误判成"什么都没发生"。
     */
    @SubscribeEvent
    static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            titleScreenSeen = true;
            AppliedSlash.LOGGER.info("[CLIENT] 已进入标题界面 —— 客户端加载完成,不是卡死");
            // 此时资源重载(含模型烘焙)已完成,可以问客户端要"它真正烤出来的物品模型"这个地面真值
            // dev.ItemModelDump 不在成品 jar 里 → 只在开发环境进入(理由见本类 static 块的说明)。
            // NEEDS_JAVAP: net.neoforged.fml.loading.FMLEnvironment#production
            if (!net.neoforged.fml.loading.FMLEnvironment.production) {
                if (com.applied.slash.dev.ItemModelDump.enabled()) {
                    com.applied.slash.dev.ItemModelDump.dumpOnce(
                            net.minecraft.resources.ResourceLocation.parse(AppliedSlash.MODID + ":slash_blade_cell"));
                }
            }
        }
        // 创造栏的内容要到界面打开时才构建 —— 想在日志里看到"物品到底进没进标签页",就得在这里抓
        // dev.ItemModelDump 不在成品 jar 里 → 只在开发环境进入(理由见本类 static 块的说明)。
        // NEEDS_JAVAP: net.neoforged.fml.loading.FMLEnvironment#production
        if (!net.neoforged.fml.loading.FMLEnvironment.production
                && com.applied.slash.dev.ItemModelDump.enabled()
                && event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            com.applied.slash.dev.ItemModelDump.dumpCreativeTabs(
                    net.minecraft.resources.ResourceLocation.parse(AppliedSlash.MODID + ":slash_blade_cell"));
        }
        if (Boolean.getBoolean(DIAG_PROPERTY)) {
            recordScreen(event.getNewScreen());
        }
    }

    private static void recordScreen(Object screen) {
        String line = java.time.LocalTime.now() + "  " + screen;
        AppliedSlash.LOGGER.info("[DIAG] 屏幕切换 -> {}", screen);
        try {
            java.nio.file.Files.writeString(Path.of(SCREEN_LOG_FILE).toAbsolutePath(), line + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException failure) {
            AppliedSlash.LOGGER.warn("[DIAG] 写屏幕记录失败: {}", failure.getMessage());
        }
    }

    /**
     * 纯 Java 线程转储看门狗:60 秒内没进标题界面就把所有线程栈写到运行目录下的
     * {@value #DUMP_FILE}(卡死时能直接看出卡在哪个线程的哪一帧,不依赖 jcmd/jstack)。
     */
    private static void watchdog() {
        try {
            Thread.sleep(DIAG_DELAY_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        }
        if (titleScreenSeen) {
            AppliedSlash.LOGGER.info("[DIAG] 已进入标题界面,客户端没有卡死,无需转储");
            return;
        }
        Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
        Path target = Path.of(DUMP_FILE).toAbsolutePath();
        try (Writer writer = Files.newBufferedWriter(target)) {
            Minecraft minecraft = Minecraft.getInstance();
            writer.write("Applied Slash 客户端看门狗转储 —— 启动后 " + (DIAG_DELAY_MS / 1000) + " 秒仍未出现标题界面\n");
            writer.write("overlay=" + minecraft.getOverlay() + "\n");
            writer.write("screen=" + minecraft.screen + "\n");
            writer.write("level=" + minecraft.level + "\n");
            writer.write("running=" + minecraft.isRunning() + "\n");
            writer.write("pendingReload=" + describePendingReload(minecraft) + "\n");
            writer.write("线程总数: " + stacks.size() + "\n\n");
            for (Map.Entry<Thread, StackTraceElement[]> entry : stacks.entrySet()) {
                Thread thread = entry.getKey();
                writer.write("\"" + thread.getName() + "\" state=" + thread.getState()
                        + " daemon=" + thread.isDaemon() + "\n");
                for (StackTraceElement frame : entry.getValue()) {
                    writer.write("    at " + frame + "\n");
                }
                writer.write("\n");
            }
        } catch (IOException failure) {
            AppliedSlash.LOGGER.error("[DIAG] 写线程转储失败", failure);
            return;
        }
        AppliedSlash.LOGGER.error("[DIAG] 客户端 60 秒未出现标题界面,线程转储已写入 {}", target);
    }

    /**
     * 图标渲染台:{@code -PuiShot} 时在标题界面上用真实 GUI 物品渲染路径画出几个样品并自动截屏。
     * 这是唯一能证明"画出来的样子"的路径 —— 模型转储只能证明"烘焙结果"。
     */
    @SubscribeEvent
    static void onScreenRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        // dev.UiScreenshot 不在成品 jar 里 → 只在开发环境进入(理由见本类 static 块的说明)。
        // NEEDS_JAVAP: net.neoforged.fml.loading.FMLEnvironment#production
        if (!net.neoforged.fml.loading.FMLEnvironment.production
                && com.applied.slash.dev.UiScreenshot.enabled()
                && event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) {
            com.applied.slash.dev.UiScreenshot.onRender(event.getGuiGraphics());
        }
    }

    @SubscribeEvent
    static void registerItemColors(RegisterColorHandlersEvent.Item event) {        if (AppliedSlash.isAe2Loaded()) {
            AppliedSlashAe2Client.registerItemColors(event);
        }
    }

    /**
     * 充能方块的界面注册(照 {@link #registerItemColors} 的转发模式:客户端入口只做转发,
     * 具体类留在 charged 包里,并且被 AE2 门卫包住)。
     *
     * <p>NEEDS_JAVAP: net.neoforged.neoforge.client.event.RegisterMenuScreensEvent#register(MenuType, ScreenConstructor)
     * 在 1.21.1 的签名(N30)
     */
    @SubscribeEvent
    static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        if (AppliedSlash.isAe2Loaded()) {
            com.applied.slash.charged.client.BladeChargerScreen.register(event);
        }
    }

    /**
     * 反射读 {@code Minecraft.pendingReload} 的状态 —— 它是"资源重载完成的未来",
     * 加载遮罩只有在它正常完成后才会被撤掉。卡死时这个字段的取值就是判据。
     */
    private static String describePendingReload(Minecraft minecraft) {
        try {
            java.lang.reflect.Field field = Minecraft.class.getDeclaredField("pendingReload");
            field.setAccessible(true);
            Object value = field.get(minecraft);
            if (value instanceof java.util.concurrent.CompletableFuture<?> future) {
                return "done=" + future.isDone() + " completedExceptionally=" + future.isCompletedExceptionally()
                        + " cancelled=" + future.isCancelled();
            }
            return String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return "无法读取(" + failure.getClass().getSimpleName() + ")";
        }
    }
}
