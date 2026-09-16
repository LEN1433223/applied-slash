package com.applied.slash.dev;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 开发期补丁(需显式开启):消除 <b>GuideME</b> 在"首次客户端资源重载"时抛的 NPE,
 * 从而避免客户端卡在加载界面。
 *
 * <h2>问题机制(全部由字节码实证)</h2>
 * <ol>
 *   <li>GuideME 的兜底字段只在 {@code GuideOnStartup.runDatapackReload()} 里被赋值,而那个方法是
 *       挂在 {@code FMLClientSetupEvent} 之后经 {@code Minecraft.execute} 排队执行的;</li>
 *   <li>与此同时,首次资源重载已经并行跑起来,并执行 GuideME 注册的客户端重载监听器
 *       {@code GuideReloadListener.apply} → {@code MutableGuide.buildNavigation()} →
 *       {@code NavigationUtil.createNavigationIcon()};</li>
 *   <li>此刻客户端还没有世界,于是 {@code Platform.getClientRegistryAccess()} 走兜底分支:
 *       {@code Objects.requireNonNull(fallbackClientRegistryAccess)} → <b>NPE</b>(兜底还没赋值);</li>
 *   <li>首次重载因此失败 → 原版 {@code clearResourcePacksOnError} 清空资源包并做一次恢复重载
 *       ({@code reloadResourcePacks(true, ...)}) → <b>第二次重载再次 NPE</b>(兜底依旧没赋值)
 *       → 走恢复失败分支 {@code abortResourcePackRecovery()} 之后,加载遮罩始终没被撤掉;</li>
 *   <li>表现:主循环正常空转(Render thread 停在 {@code runTick → limitDisplayFPS}),
 *       没有崩溃报告,但永远停在加载界面。</li>
 * </ol>
 *
 * <h2>本补丁做什么</h2>
 * 在客户端 mod 构造期,把 GuideME 那个 {@code public static} 兜底字段填上一个非 null 的
 * {@link RegistryAccess}(由原版内置注册表构造,足够解析图标物品)。这样第 3 步不会 NPE,
 * 首次重载正常完成,加载遮罩被撤掉,客户端进入标题界面。
 *
 * <p><b>定位</b>:这是给上游 GuideME 报 bug 之外的临时手段,默认<b>关闭</b>;
 * 开启方式见 {@link #PROPERTY}。GuideME 修好后应删除本类。
 */
public final class GuideMeStartupPatch {
    /** 开关:{@code -Dappliedslash.guidemePatch=true}(gradle 侧 {@code -PguidemePatch})。 */
    public static final String PROPERTY = "appliedslash.guidemePatch";

    private static final String PLATFORM_CLASS = "guideme.internal.util.Platform";
    private static final String FALLBACK_FIELD = "fallbackClientRegistryAccess";

    private GuideMeStartupPatch() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** @return 结果描述,便于在日志里一眼看出补丁有没有生效 */
    public static String apply() {
        try {
            Class<?> platform = Class.forName(PLATFORM_CLASS);
            var field = platform.getField(FALLBACK_FIELD);
            if (field.get(null) != null) {
                return "GuideME 兜底 RegistryAccess 已有值,未改动";
            }
            field.set(null, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            return "已注入兜底 RegistryAccess(由 BuiltInRegistries 构造)";
        } catch (ClassNotFoundException noGuideMe) {
            return "未安装 GuideME,跳过";
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return "注入失败(" + failure.getClass().getSimpleName() + ": " + failure.getMessage() + ")";
        }
    }
}
