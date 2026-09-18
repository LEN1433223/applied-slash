package com.applied.slash.rainbow;

import java.util.concurrent.atomic.AtomicInteger;

import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.se.InventoryDamageTransferLogic;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 莉莉的**动态彩色刀光**:每次攻击把刀的效果颜色推进到粉紫色板的下一档。
 *
 * <p>原理(字节码实证):刀光实体 {@code EntitySlashEffect} 只有一个同步颜色字段 COLOR,
 * 颜色来自刀状态的 {@code getColorCode()/setColorCode(int)}(default 方法,内部转
 * {@code get/setEffectColor});重锋本体除次元斩渲染器外没有色相轮转逻辑
 * ⇒ 单次斩击只能一个颜色,但**颜色可以逐次变化**,这就是"动态彩色/彩虹"的做法。
 *
 * <p>三点工程约定:
 * <ol>
 *   <li><b>只按攻击推进</b>(打到实体或右键使用)—— 不做每刻写状态,避免刷物品同步包;</li>
 *   <li><b>可还原</b>:第一次改色前把刀原本的颜色码记进本模组组件 {@code lili_original_color},
 *       {@link #restore(ItemStack)} 能原样写回;</li>
 *   <li>只对**带本模组 SE 的刀**(= 莉莉)生效,玩家手里拿着别的刀不受影响。</li>
 * </ol>
 */
public final class LiliRainbowColor {
    /** 粉紫色板 12 档:色相 350°→260°→350° 的三角波(循环处平滑,不跳变)。 */
    public static final int[] PALETTE = {
            0xF5768B, 0xF576AB, 0xF576CA, 0xF576EA, 0xE076F5, 0xC076F5,
            0xA076F5, 0xC076F5, 0xE076F5, 0xF576EA, 0xF576CA, 0xF576AB};

    /** 枫:橙金 12 档(用户指定**橙色系,不要红**)。 */
    private static final int[] MAPLE_PALETTE = {
            0xFF8C1A, 0xFF9C2E, 0xFFAC42, 0xFFBC56, 0xFFCC6A, 0xFFDC7E,
            0xFFEC92, 0xFFDC7E, 0xFFCC6A, 0xFFBC56, 0xFFAC42, 0xFF9C2E};

    /** 栞:暖桃金 12 档(柔和,配她的安静气质)。 */
    private static final int[] SHIORI_PALETTE = {
            0xF0B49C, 0xF2BCA4, 0xF4C4AC, 0xF6CCB4, 0xF8D4BC, 0xFADCC4,
            0xFCE4CC, 0xFADCC4, 0xF8D4BC, 0xF6CCB4, 0xF4C4AC, 0xF2BCA4};

    /** 星奈:银白 → 淡紫 → 星光蓝 12 档。 */
    private static final int[] SEINA_PALETTE = {
            0xE8E4FF, 0xDCD4FF, 0xD0C4FF, 0xC4B4FF, 0xB8A4FF, 0xAC94FF,
            0xA084FF, 0xAC94FF, 0xB8A4FF, 0xC4B4FF, 0xD0C4FF, 0xDCD4FF};

    /**
     * 基础色 → 色板。
     *
     * <p>基础色就是各刀定义里的 {@code render.summon_sword_color}
     * (见 {@code data/applied_slash/slashblade/named_blades/*.json})——
     * 用它当键,不需要读取重锋的刀身 id,也就不引入任何新 API。
     */
    private static final java.util.Map<Integer, int[]> BY_BASE_COLOR = java.util.Map.of(
            0xF5768B, PALETTE,           // 莉莉(粉黛 · 粉紫)
            0xFF8C1A, MAPLE_PALETTE,     // 枫(秋枫 · 橙金,无红)
            0xF0B49C, SHIORI_PALETTE,    // 栞(暖纸 · 桃金)
            0xB98CF5, SEINA_PALETTE);    // 星奈(星空 · 银紫)

    /** 取色板:认不出的基础色退回粉紫(莉莉那套),保证任何刀都有彩色可用。 */
    public static int[] paletteFor(int baseColor) {
        return BY_BASE_COLOR.getOrDefault(baseColor & 0xFFFFFF, PALETTE);
    }
    /** 总开关(测试用它开关并验证还原)。 */
    public static volatile boolean ENABLED = true;

    /** 全局色板指针(k 不落盘:颜色码本身会写进刀状态)。 */
    private static final AtomicInteger NEXT = new AtomicInteger();

    private LiliRainbowColor() {
    }

    /** 注册事件(只在重锋存在时由 LiliSpecialEffects 调用)。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(LiliRainbowColor.class);
    }

    /** 打到实体 ⇒ 换一档。 */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            advance(player.getMainHandItem());
        }
    }

    /** 右键使用(含释放剑技)⇒ 换一档。 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        advance(event.getItemStack());
    }

    /**
     * 把刀的效果颜色推进一档。
     *
     * @return 是否真的改了(不是带 SE 的刀就返回 false)
     */
    public static boolean advance(ItemStack blade) {
        if (!ENABLED || blade == null || blade.isEmpty()) {
            return false;
        }
        ISlashBladeState state = BladeStateAccess.of(blade).orElse(null);
        if (state == null || !InventoryDamageTransferLogic.hasSe(blade, state)) {
            return false;
        }
        remember(blade, state);
        // 按刀的**基础色**选色板:原色在被我们改色前记在组件里,没有记录就用当前值
        Integer remembered = blade.get(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get());
        int base = (remembered != null ? remembered : state.getColorCode()) & 0xFFFFFF;
        int[] palette = paletteFor(base);
        state.setColorCode(palette[Math.floorMod(NEXT.getAndIncrement(), palette.length)]);
        return true;
    }

    /** 把颜色还原成我们第一次改之前的值(并清掉存档组件)。 */
    public static boolean restore(ItemStack blade) {
        if (blade == null || blade.isEmpty()) {
            return false;
        }
        Integer original = blade.get(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get());
        if (original == null) {
            return false;
        }
        BladeStateAccess.of(blade).ifPresent(state -> state.setColorCode(original));
        blade.remove(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get());
        return true;
    }

    private static void remember(ItemStack blade, ISlashBladeState state) {
        if (!blade.has(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get())) {
            blade.set(AppliedSlashComponents.LILI_ORIGINAL_COLOR.get(), state.getColorCode());
        }
    }
}