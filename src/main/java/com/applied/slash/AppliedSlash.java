package com.applied.slash;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.applied.slash.command.AppliedSlashCommand;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Applied Slash —— 专门存储拔刀剑的 AE2 ME 存储元件。
 *
 * <p>AE2 与 SlashBlade 都按软依赖处理:前置缺席时本模组照常加载,
 * 只是不注册元件 / 元件识别不到任何刀(见 {@link #isAe2Loaded()} 与 {@link SlashBladeBlades})。
 */
@Mod(AppliedSlash.MODID)
public class AppliedSlash {
    /** 本模组 modid,必须与 neoforge.mods.toml 及 @Mod 注解一致。 */
    public static final String MODID = "applied_slash";
    /** AE2 的 modid(现代线是 ae2,老线的 appliedenergistics2 不适用)。 */
    public static final String AE2_MODID = "ae2";
    /** SlashBlade: Resharpened 的 modid。 */
    public static final String SLASHBLADE_MODID = "slashblade";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /**
     * 本模组的创造模式标签页。只在 AE2 存在时注册 —— 没有 AE2 就没有元件可展示,
     * 留一个空标签页只会让人困惑。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> AppliedSlashAe2.SLASH_BLADE_CELL.get().getDefaultInstance())
                    // 存储元件放本模组标签页;**不**塞进 AE2 自己的标签页,减少与 AE2 更新的耦合(方案 §5.1)。
                    .displayItems((parameters, output) -> {
                        output.accept(AppliedSlashAe2.SLASH_BLADE_CELL.get());
                        // 与拔刀剑元件并列的第二个元件(不可堆叠物品存储元件)
                        output.accept(AppliedSlashAe2.UNSTACKABLE_ITEM_CELL.get());
                        // 5 把充能拔刀剑(出厂即带默认刀身数据 + 满能量)+ 专用充能方块。
                        //
                        // 关于类加载:这个 lambda 只有在创造界面构建时才执行,那时 SlashBlade 与 AE2
                        // 两类前置的类才会被加载 —— 而 displayItems 现在挂在 isAe2Loaded() 分支里注册,
                        // 且两个前置都已是 required,所以是安全的。**但不要**因此以为可以在 lambda 里
                        // 随便引用前置类:一旦前置回到 optional,这里的加载时机就是唯一的防线。
                        for (com.applied.slash.charged.ChargedBladeItem blade
                                : com.applied.slash.charged.ChargedBladeItems.all()) {
                            output.accept(com.applied.slash.charged.ChargedBladeFactory.fresh(blade));
                        }
                        output.accept(com.applied.slash.charged.block.BladeChargerRegistry.BLADE_CHARGER_ITEM.get());
                    })
                    .build());

    public AppliedSlash(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // 组件与 AE2 无关,始终注册(元件被注册时才用得上)
        AppliedSlashComponents.COMPONENTS.register(modEventBus);

        // 可调项:类型上限与"是否剥离运行时组件"(配置加载/重载时刷新缓存值)
        modContainer.registerConfig(ModConfig.Type.COMMON, AppliedSlashConfig.SPEC);
        // 用方法引用而不是 lambda:lambda 没有泛型签名,NeoForge 推断不出事件类型(见 AppliedSlashConfig#refresh)
        modEventBus.addListener(AppliedSlashConfig::refresh);

        // 注意:对 AE2 类的任何引用都要留在 AppliedSlashAe2 里,并在这里做门卫,
        // 保证 AE2 缺席时那些类根本不会被类加载。
        if (isAe2Loaded()) {
            AppliedSlashAe2.register(modEventBus);
            CREATIVE_MODE_TABS.register(modEventBus);
        } else {
            LOGGER.warn("未检测到 Applied Energistics 2 (modid \"{}\"),SlashBlade存储元件不会被注册。", AE2_MODID);
        }

        // 5 把充能拔刀剑:引用 ItemSlashBlade 的类只能在这里的门卫之后注册(与 AE2 同一个约定)。
        // 改 required 之后本分支必然为真,但保留结构以免破坏「前置缺席也不 NoClassDefFoundError」。
        if (isSlashBladeLoaded()) {
            com.applied.slash.charged.ChargedBladeItems.register(modEventBus);
            // 游戏总线:第二保证(UpdateAttackEvent 压伤害)+ 断刀双保险 + actionbar 节流。
            // 方法签名里出现 SlashBladeEvent ⇒ 注册那一刻会加载它,所以必须在这道门卫里。
            com.applied.slash.charged.ChargedBladeEvents.register();
        } else {
            LOGGER.warn("未检测到 SlashBlade (modid \"{}\"),存储元件将识别不到任何拔刀剑。", SLASHBLADE_MODID);
        }

        NeoForge.EVENT_BUS.register(this);
    }

    public static boolean isAe2Loaded() {
        return ModList.get().isLoaded(AE2_MODID);
    }

    public static boolean isSlashBladeLoaded() {
        return ModList.get().isLoaded(SLASHBLADE_MODID);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (isAe2Loaded()) {
            // 升级卡白名单与驱动器模型只能在注册期结束后接线
            event.enqueueWork(AppliedSlashAe2::registerIntegration);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 测试用命令(/appliedslash testcell / inspect),需要 AE2 在场才有意义;权限等级 2 由命令自身限制
        //
        // 生产环境判断:成品 jar 里排除了 com/applied/slash/dev/**(见 build.gradle 末尾的 jar 排除),
        // 而 AppliedSlashCommand 的 testcell / inspect 子命令引用了 dev.BladeTestFactory / dev.BladeInspector。
        // Java 只在**真正执行到**某条指令时才解析它引用的类,所以"排除类 + 生产环境永不进入这个分支"是安全的;
        // 反之,若生产环境仍然注册该命令,执行子命令时就会抛 NoClassDefFoundError。
        // 用 FML 自带的加载期标志 FMLEnvironment.production:开发运行与生产运行取值不同,不要替换成别的东西。
        // NEEDS_JAVAP: net.neoforged.fml.loading.FMLEnvironment#production
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            if (isAe2Loaded()) {
                AppliedSlashCommand.register(event.getDispatcher());
            }
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Applied Slash 已加载(ae2={}, slashblade={})", isAe2Loaded(), isSlashBladeLoaded());
        // 开发期自检(-Dappliedslash.selftest=true)引用了 dev.SlashBladeCellSelfTest,而 dev/** 不进成品 jar。
        // 与上面的命令注册同理:只在开发运行进入该分支。Java 的类解析发生在指令真正执行时,
        // 因此生产环境既不加载 dev 类、也不会因它缺席而 NoClassDefFoundError;
        // 若把这段放到生产判断之外,生产环境一旦启动服务端就会直接炸 NoClassDefFoundError。
        // NEEDS_JAVAP: net.neoforged.fml.loading.FMLEnvironment#production
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            if (isAe2Loaded() && com.applied.slash.dev.SlashBladeCellSelfTest.enabled()) {
                com.applied.slash.dev.SlashBladeCellSelfTest.run(event.getServer());
            }
        }
    }
}
