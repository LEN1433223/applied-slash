package com.applied.slash.cell;

import com.applied.slash.AppliedSlash;
import com.applied.slash.SlashBladeBlades;
import com.applied.slash.charged.BladeEnergy;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * AE2 键层面的拔刀剑身份。
 *
 * <p>三条要点:
 * <ol>
 *   <li>判定走"标签 + 物品类"双保险,且**不构造 ItemStack**({@link AEItemKey#getItem()} 与
 *       {@link AEItemKey#isTagged} 都是零拷贝),保证插入热路径尽量便宜;</li>
 *   <li>规范化:入库前剥离刀的运行时状态组件,让同一把刀在不同运行时态下是同一个键
 *       (否则类型数会虚增、每键载荷变大);</li>
 *   <li><b>规范化还要量化能量</b>(带能量的刀,例如充能器充过的任意拔刀剑):能量组件会进入键,
 *       不量化则每个能量值一个类型。
 *       注意这里必须<b>成对</b>修改 {@link #mayNormalize} 与 {@link #normalize} —— 见下面的注释。</li>
 * </ol>
 */
public final class BladeIdentity {
    private BladeIdentity() {
    }

    public static boolean isBlade(AEKey key) {
        return key instanceof AEItemKey itemKey && isBlade(itemKey);
    }

    public static boolean isBlade(AEItemKey key) {
        if (key.isTagged(SlashBladeBlades.SWORDS_TAG)) {
            return true;
        }
        return SlashBladeBlades.isSlashBladeItem(key.getItem());
    }

    /**
     * 该键是否<b>可能</b>被规范化成另一个键(带运行时状态组件,<b>或</b>带未量化的能量)。
     *
     * <p>便宜检查,热路径用它避开无谓的 {@code stack.copy()};返回 false 时 {@link #normalize}
     * 必然原样返回。
     *
     * <p><b>为什么这里是关键点(而不只是 {@code normalize})</b>:
     * {@code SlashBladeCellInventory#insert} 的写法是
     * {@code if (existing <= 0 && BladeIdentity.mayNormalize(itemKey)) target = BladeIdentity.normalize(itemKey);}
     * —— 一旦本方法返回 false,{@code normalize} <b>根本不会被调用</b>,
     * 原始精确能量键会被原样收下 ⇒ 每个能量值一个类型,量化形同虚设。
     *
     * <p><b>与 {@code stripRuntimeState} 的关系</b>:能量维度**独立**于那个开关,
     * 绝不能挂在它下面 —— 否则关掉「剥离运行时状态」会连带关掉量化,静默制造类型爆炸。
     */
    public static boolean mayNormalize(AEItemKey key) {
        return SlashBladeBlades.hasVolatileState(key.getReadOnlyStack()) || needsEnergyQuantizing(key);
    }

    /**
     * 返回规范化后的键;若本来就不需要改动,原样返回入参(不产生额外分配)。
     *
     * <p>两步规范化:
     * <ol>
     *   <li>剥离纯运行时状态组件(受 {@code stripRuntimeState} 开关控制);</li>
     *   <li>把能量量化到档位**并一并写死 sealed 位** —— 不写死 sealed 的话,
     *       「能量同为档位值但 sealed 不同」的两把刀仍会变成两个键。</li>
     * </ol>
     *
     * <p>代价:仅在"该键尚未存在于刀库"时才会走到拷贝 + 重建键,重复存入同一把刀走快路径。
     */
    public static AEItemKey normalize(AEItemKey key) {
        ItemStack stack = key.getReadOnlyStack();
        if (stack.isEmpty()) {
            return key;
        }
        ItemStack copy = stack.copy();
        SlashBladeBlades.stripVolatileState(copy);
        // 新增调用点:能量 → 档位 + sealed 同步(必须与 mayNormalize 成对存在,见 mayNormalize 的注释)
        BladeEnergy.quantizeInPlace(copy);
        if (ItemStack.isSameItemSameComponents(copy, stack)) {
            return key;
        }
        return AEItemKey.of(copy);
    }

    /**
     * 该键是否带「未量化的能量」。
     *
     * <p>这是 {@link #mayNormalize} 的能量维度入口,与 {@code SlashBladeBlades.hasVolatileState}
     * 同构:能量判定要读重锋的刀身状态({@code BladeStateAccess}/{@code ItemSlashBlade} 一侧),
     * 会触发那些类加载 —— 所以必须像
     * {@code SlashBladeBlades.isSlashBladeItem} 那样,把它放进**被
     * {@code ModList.isLoaded("slashblade")} 包住的独立方法**里,
     * 否则会破坏「SlashBlade 缺席也不 NoClassDefFoundError」这条既有性质。
     */
    private static boolean needsEnergyQuantizing(AEItemKey key) {
        if (!ModList.get().isLoaded(AppliedSlash.SLASHBLADE_MODID)) {
            return false;
        }
        return needsEnergyQuantizingGuarded(key.getReadOnlyStack());
    }

    /** 同上:对重锋类的引用只在这个被门卫保护的独立方法里发生。 */
    private static boolean needsEnergyQuantizingGuarded(ItemStack stack) {
        return BladeEnergy.needsQuantizing(stack);
    }
}
