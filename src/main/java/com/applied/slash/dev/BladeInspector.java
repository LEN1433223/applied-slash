package com.applied.slash.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.cell.BladeIdentity;
import com.applied.slash.vault.BladeVaultStore;
import com.applied.slash.vault.VaultContents;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 「元件看得见、取不出」的现场诊断器 —— 由 {@code /appliedslash inspect} 调用。
 *
 * <p><b>为什么单独一个类</b>:{@code command/AppliedSlashCommand} 刻意不引用任何 AE2 类型
 * (见该类的说明:AE2 相关类必须只在真正需要时被类加载)。这里承担全部 AE2 调用,
 * 对外只暴露 JDK 类型({@link Line} / {@link Probe}),因此命令类引用它不会牵出 AE2。
 *
 * <p><b>诊断什么</b>:
 * <ol>
 *   <li>元件的 {@code vault_id} 组件与世界侧刀库(查不到 = 载荷丢失);</li>
 *   <li>AE2 网络视图({@code getAvailableStacks})里的第一个键:它<b>能列出</b>不代表
 *       <b>能还原</b>成物品栈 —— 这里把键还原一次,打印 {@code isEmpty} / 物品 id / 组件数;</li>
 *   <li>对这个键做一次 <b>SIMULATE 取出探测</b>:AE2 的取出链路第一步永远是 SIMULATE,
 *       返回 0 它就认定"网络里没有这件东西",于是什么都不做 —— 这正对应"终端里点了没反应"。
 *       探测紧接着补一次 MODULATE 并<b>原样放回</b>,净改动为 0(两个数字应相等)。</li>
 * </ol>
 *
 * <p>所有输出都是"语言键后缀 + 参数",由命令侧加 {@code [INSPECT]} 前缀并翻译。
 * 整个探测包在 try/catch 里:任何异常只折成一行摘要,绝不向上抛。
 */
public final class BladeInspector {
    private BladeInspector() {
    }

    /** 一行输出:语言键后缀({@code command.applied_slash.inspect.<key>})+ 参数(只用 JDK 类型)。 */
    public record Line(String key, Object[] args) {
    }

    /** 探测结果:{@code lines} 按发生顺序排列,{@code error} 非 null 表示中途异常(前面的行仍然有效)。 */
    public record Probe(List<Line> lines, String error) {
    }

    /** 对一个存储元件做完整诊断。绝不抛异常。 */
    public static Probe inspect(ItemStack cellStack) {
        List<Line> lines = new ArrayList<>();
        try {
            // (1) 物品上的 vault_id:没有就是"从未入库过/元件是新做的"
            UUID vaultId = cellStack.get(AppliedSlashComponents.VAULT_ID.get());
            if (vaultId == null) {
                lines.add(line("vault_id_none"));
            } else {
                lines.add(line("vault_id_value", vaultId.toString()));
            }

            // (2) 世界侧刀库:null = 服务端不可用(客户端线程)或该 UUID 在本存档查不到(跨存档复制/回滚)
            VaultContents vault = BladeVaultStore.get(vaultId);
            if (vault == null) {
                lines.add(line("vault_missing"));
            } else {
                lines.add(line("vault_stats", vault.typeCount(), vault.count()));
            }

            // (3) 走真实取用路径:StorageCells.getCellInventory(元件, null)(写法同 BladeTestFactory)
            StorageCell inventory = StorageCells.getCellInventory(cellStack, null);
            if (inventory == null) {
                lines.add(line("inventory_null"));
                return new Probe(lines, null);
            }

            KeyCounter visible = new KeyCounter();
            inventory.getAvailableStacks(visible);
            lines.add(line("entries", visible.size()));
            if (visible.isEmpty()) {
                lines.add(line("entries_empty"));
                return new Probe(lines, null);
            }

            // 取"第一个键":KeyCounter#keySet 是本项目自检里已用过的形式(SlashBladeCellSelfTest)
            AEKey firstKey = null;
            for (var key : visible.keySet()) {
                firstKey = key;
                break;
            }
            if (firstKey == null) {
                lines.add(line("entries_empty"));
                return new Probe(lines, null);
            }

            boolean isItemKey = firstKey instanceof AEItemKey;
            lines.add(line("key_line", firstKey.toString(), firstKey.getClass().getName(), String.valueOf(isItemKey)));

            if (firstKey instanceof AEItemKey itemKey) {
                // 4. 键 → 物品栈:这一步用来判断"键能列出、但还原成物品是空栈"
                ItemStack restored = itemKey.toStack();
                lines.add(line("key_restore",
                        String.valueOf(restored.isEmpty()),
                        String.valueOf(BuiltInRegistries.ITEM.getKey(restored.getItem())),
                        restored.getComponents().size())); // NEEDS_JAVAP: net.minecraft.core.component.DataComponentMap#size

                // 4b. 与规范化形态比对:库里存的是剥离运行时状态的键,发起方手里的键通常带运行时状态
                AEItemKey normalized = BladeIdentity.normalize(itemKey);
                lines.add(line("key_normalize", normalized.toString(),
                        String.valueOf(normalized.equals(itemKey))));

                // 5. 取出探测:SIMULATE 先问,再 MODULATE 真取,并立刻原样放回(净零改动)
                IActionSource source = IActionSource.empty();
                long sim = inventory.extract(itemKey, 1, Actionable.SIMULATE, source);
                lines.add(line("extract_simulate", sim));
                if (sim > 0) {
                    long mod = inventory.extract(itemKey, 1, Actionable.MODULATE, source);
                    lines.add(line("extract_modulate", mod));
                    if (mod > 0) {
                        long back = inventory.insert(itemKey, mod, Actionable.MODULATE, source);
                        lines.add(line("extract_reinsert", back));
                    }
                } else {
                    lines.add(line("extract_rejected"));
                }
            } else {
                lines.add(line("key_not_item", firstKey.getClass().getName()));
            }

            return new Probe(lines, null);
        } catch (Throwable t) {
            // 诊断命令必须永远给出结论,不能把异常抛回命令调度器
            return new Probe(lines, summarize(t));
        }
    }

    private static Line line(String key, Object... args) {
        return new Line(key, args);
    }

    /** 异常摘要:{@code SimpleName: message}(消息为 null 时只留类名)。 */
    public static String summarize(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank()
                ? t.getClass().getName()
                : t.getClass().getName() + ": " + message;
    }
}
