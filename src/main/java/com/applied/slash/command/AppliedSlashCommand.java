package com.applied.slash.command;

import com.applied.slash.BladeCellSpec;
import com.applied.slash.SlashBladeCellItem;
import com.applied.slash.UnstackableItemCellItem;
import com.applied.slash.dev.BladeInspector;
import com.applied.slash.dev.BladeTestFactory;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 测试用命令(需要权限等级 2,即管理员):
 *
 * <pre>
 *   /appliedslash testcell &lt;数量&gt; [玩家]     生成一个已装好 N 把刀的元件(真机压测用)
 *   /appliedslash inspect                    诊断手上元件的"看得见却取不出"
 * </pre>
 *
 * 生成一个已经装了指定数量不同拔刀剑的存储元件,并交给玩家 —— 用于真机压测
 * (驱动器插拔、终端浏览、存储总线反复存取)。内容必须在目标存档里创建,原因见
 * {@link BladeTestFactory} 的说明。
 *
 * <p>{@code inspect} 读取<b>玩家主手</b>的元件并按固定顺序打印诊断行(每行带 {@code [INSPECT]}
 * 前缀):vault_id → 世界侧刀库 → 网络视图条目数 → 第一个键(能否还原成物品栈)→
 * SIMULATE 取出探测(随后 MODULATE 真取一次并原样放回,净零改动)。实现全在
 * {@link BladeInspector} 里,本类只负责权限、取玩家主手、翻译与打印。
 *
 * <p>本类刻意不引用任何 AE2 类:AE2 相关代码全部在 {@link BladeTestFactory} 与
 * {@link BladeInspector} 里,而它们只在命令真正被执行(即 AE2 在场)时才会被类加载。
 */
public final class AppliedSlashCommand {
    private AppliedSlashCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("appliedslash")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("testcell")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, BladeCellSpec.MAX_TYPES))
                                .executes(context -> fill(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count"), null))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> fill(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("inspect")
                        .executes(context -> inspect(context.getSource()))));
    }

    private static int fill(CommandSourceStack source, int count, ServerPlayer target) {
        ServerPlayer player = target != null ? target : source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.applied_slash.testcell.need_player"));
            return 0;
        }

        BladeTestFactory.FillResult result = BladeTestFactory.createFilledCell(count);
        if (result.cell().isEmpty() || result.types() == 0) {
            source.sendFailure(Component.translatable("command.applied_slash.testcell.failed"));
            return 0;
        }
        if (!player.getInventory().add(result.cell())) {
            player.drop(result.cell(), false);
        }
        source.sendSuccess(() -> Component.translatable("command.applied_slash.testcell.done",
                result.types(), result.total(), String.format("%.1f", result.millis())), true);
        return 1;
    }

    /**
     * {@code /appliedslash inspect}:诊断"元件里的刀看得见却取不出"。
     *
     * <p>读玩家主手物品;不是本模组元件就明确提示并返回;是元件就把 {@link BladeInspector}
     * 产出的每一行按 {@code command.applied_slash.inspect.*} 翻译后打印 —— <b>先打印再返回</b>,
     * 整段包在 try/catch 里,任何异常只打印摘要,命令绝不崩。
     */
    private static int inspect(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.applied_slash.inspect.need_player"));
            return 0;
        }

        ItemStack held = player.getMainHandItem(); // NEEDS_JAVAP: net.minecraft.world.entity.LivingEntity#getMainHandItem
        if (held.isEmpty()) {
            source.sendFailure(inspectLine("hand_empty"));
            return 0;
        }
        if (!(held.getItem() instanceof SlashBladeCellItem) && !(held.getItem() instanceof UnstackableItemCellItem)) {
            source.sendFailure(inspectLine("not_cell", held.getHoverName()));
            return 0;
        }

        try {
            BladeInspector.Probe probe = BladeInspector.inspect(held);
            for (BladeInspector.Line line : probe.lines()) {
                source.sendSuccess(() -> inspectLine(line.key(), line.args()), false);
            }
            if (probe.error() != null) {
                source.sendSuccess(() -> inspectLine("error", probe.error()), false);
            }
        } catch (Throwable t) {
            String summary = BladeInspector.summarize(t);
            source.sendSuccess(() -> inspectLine("error", summary), false);
        }
        return 1;
    }

    /** 统一前缀 {@code [INSPECT]} 的一行输出(前缀写在代码里,翻译文本缺失时也能一眼认出诊断行)。 */
    private static Component inspectLine(String keySuffix, Object... args) {
        return Component.literal("[INSPECT] ")
                .append(Component.translatable("command.applied_slash.inspect." + keySuffix, args));
    }
}
