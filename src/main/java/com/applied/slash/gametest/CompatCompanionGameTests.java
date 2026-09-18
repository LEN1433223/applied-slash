package com.applied.slash.gametest;

import com.applied.slash.AppliedSlash;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 兼容性测试用的第三方模组"真的可用吗"的机器断言。
 *
 * <p><b>为什么需要它</b>:模组出现在启动日志的 Mod List 里,只说明 jar 被 FML 发现了;
 * 如果它的注册表内容(jar 里的物品/实体)没进去,游戏里照样"看不见、拿不到"。
 * 这两条断言把"到底加载没加载"从"人眼找"变成"跑一次就知道"。
 *
 * <p><b>缺席即跳过</b>:用 {@link ModList#isLoaded(String)} 先判断,没装就直接 succeed ——
 * 所以它既可以作为兼容测试跑,也不会在干净环境下变成噪音。
 *
 * <p>与 {@link SlashBladeCellGameTests} 同一约定:holder 类的方法签名**只能有
 * {@code GameTestHelper} 一个参数**,不引用任何第三方模组的类(全靠字符串 id 查注册表),
 * 因此不存在"没装前置就 NoClassDefFoundError"的风险。
 */
@GameTestHolder(AppliedSlash.MODID)
@PrefixGameTestTemplate(false)
public final class CompatCompanionGameTests {
    private static final String TEMPLATE = "empty";
    private static final int TIMEOUT_TICKS = 200;

    /** 试验假人(DuMmmMmMy,MmmMmmMmmMmm)的 modid。 */
    private static final String DUMMY_MODID = "dummmmmmy";

    private CompatCompanionGameTests() {
    }

    /**
     * 试验假人模组在场时:它的**物品与实体都必须真的注册进注册表**。
     *
     * <p>实测要点(读 jar 得到):物品与实体同 id {@code dummmmmmy:target_dummy},
     * 中文名「试验假人」,配方 = 干草块 + 盔甲架。这里查注册表而不是查 ModList,
     * 因为"jar 被发现"和"内容可用"是两件事。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void companionDummyContentIsRegistered(GameTestHelper helper) {
        if (!ModList.get().isLoaded(DUMMY_MODID)) {
            AppliedSlash.LOGGER.info("[COMPAT] 未安装 {},跳过试验假人内容断言", DUMMY_MODID);
            helper.succeed();
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DUMMY_MODID, "target_dummy");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id),
                "试验假人模组已加载,但物品 " + id + " 没注册进 ITEM 注册表");
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id),
                "试验假人模组已加载,但实体 " + id + " 没注册进 ENTITY_TYPE 注册表");
        // 配方也确认一下:没有配方的话玩家"合成不出来",同样会被当成"没加载"
        Object recipe = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(DUMMY_MODID, "dummy_crafting")).orElse(null);
        helper.assertTrue(recipe != null,
                "试验假人的配方 dummmmmmy:dummy_crafting 没加载(玩家将合成不出来)");
        helper.succeed();
    }

    /**
     * 其余兼容测试模组的加载状态汇总成一条日志。
     *
     * <p>刻意**不做成败断言**(它们只是别人的模组,缺席不算我们的失败),只把状态打到日志里,
     * 方便一眼看出"这次运行到底带了哪些模组"。
     */
    @GameTest(template = TEMPLATE, timeoutTicks = TIMEOUT_TICKS)
    public static void companionModsReportOnly(GameTestHelper helper) {
        String[] ids = {"dummmmmmy", "moonlight", "jei", "mezz_config", "codecui", "imblocker"};
        StringBuilder sb = new StringBuilder("[COMPAT] 兼容测试模组状态: ");
        for (String id : ids) {
            sb.append(id).append('=').append(ModList.get().isLoaded(id) ? "在" : "无").append(' ');
        }
        AppliedSlash.LOGGER.info(sb.toString());
        // 注册表本身必须可用(这条是我们自己的断言,永远成立才有意义)
        helper.assertTrue(BuiltInRegistries.REGISTRY.containsKey(Registries.ITEM.location()),
                "物品注册表不可用 —— 环境本身有问题");
        helper.succeed();
    }
}
