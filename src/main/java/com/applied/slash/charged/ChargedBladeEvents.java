package com.applied.slash.charged;

import java.util.Map;
import java.util.WeakHashMap;

import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 充能刀的游戏总线事件接线。
 *
 * <p><b>本类只在 SlashBlade 门卫内被类加载</b>(调用点是
 * {@code AppliedSlash} 构造函数里 {@code isSlashBladeLoaded()} 分支内的方法引用)——
 * 方法签名里出现 {@code SlashBladeEvent} 就意味着注册那一刻会加载它。
 *
 * <h2>两条已实证的机制(事实表 §9.1),以及被否掉的那条</h2>
 * <ul>
 *   <li><b>主保证 sealed</b>:不是事件,是数据投影(能量 0 ⇔ sealed),
 *       由 {@link ChargedBladeEnergy#set} 写入;</li>
 *   <li><b>第二保证</b> = {@link #onUpdateAttack}:重锋自己会 post
 *       {@code SlashBladeEvent.UpdateAttackEvent} 并采用 {@code getNewDamage()}
 *       ⇒ 没能量时把伤害压到 0(比覆盖方法更干净);</li>
 *   <li><b>被否</b> = {@code AttackEntityEvent}:{@code Player.attack} 全类扫描<b>扫不到</b>
 *       它的触发,「没能量完全不能攻击」这条硬需求不能压在它身上。
 *       若日后要作为**可选补充**,必须先单独证明其触发路径。</li>
 * </ul>
 *
 * <h2>扣费不在本类</h2>
 * 扣费的唯一入口是 {@link ChargedBladeItem#hurtEnemy}(调用点已实证:{@code Player.attack}
 * 偏移 1177)。本类的事件处理**只读不写**({@code setNewDamage} 改的是本次事件携带的数值,
 * 不是物品数据),保证不会重复扣费。
 */
public final class ChargedBladeEvents {
    /** actionbar 提示节流窗口:1 秒(20 tick)。 */
    private static final int HINT_COOLDOWN_TICKS = 20;

    /** 能量耗尽的 actionbar 文案键(与 lang 文件一致,PLAN §9.3)。 */
    private static final String KEY_NO_ENERGY_HINT = "applied_slash.charged_blade.no_energy_hint";

    /** actionbar 提示节流:玩家 → 上次提示的游戏 tick。弱键,玩家下线自然回收。 */
    private static final Map<Player, Integer> LAST_HINT_TICK = new WeakHashMap<>();

    private ChargedBladeEvents() {
    }

    /** 在 mod 构造期、SlashBlade 门卫内调用。 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ChargedBladeEvents::onUpdateAttack);
        NeoForge.EVENT_BUS.addListener(ChargedBladeEvents::onBladeBreak);

        // 刻意不注册 AttackEntityEvent(事实表 §9.1:Player.attack 里扫不到触发点)。
        // 若日后实证其触发路径,可作为「可选补充」加在这里,但**不得**取代 sealed + UpdateAttackEvent:
        // NeoForge.EVENT_BUS.addListener(ChargedBladeEvents::onAttackEntity);
    }

    /**
     * 第二保证:没能量 ⇒ 这一击的 ATTACK_DAMAGE 修饰为 0。
     *
     * <p>取刀只能用 {@link SlashBladeEvent#getBlade()} —— 事实表 §5 明确{@code UpdateAttackEvent}
     * <b>没有</b> {@code getStack()}/{@code getState()}。
     *
     * <p>两道判定缺一不可:
     * <ol>
     *   <li>{@code isChargedBlade} —— 否则会误伤<b>别人家</b>的刀:它们的能量组件同样缺席 ⇒ 读到 0 ⇒
     *       会被判成「没能量」而伤害归零;</li>
     *   <li>{@code shouldZeroDamage} —— 本模组刀且能量 &lt; 消耗。</li>
     * </ol>
     *
     * <p>// NEEDS_HOST_CHECK: 本事件不携带玩家(实证构造器只有 {@code ItemStack, ISlashBladeState, double}),
     * 所以「能量耗尽」的 actionbar 提示<b>不在这里发</b>,而由带玩家的三条路径发
     * ({@link ChargedBladeItem#use} / {@link ChargedBladeItem#onLeftClickEntity} /
     * {@link ChargedBladeItem#hurtEnemy},都经 {@link #notifyOutOfEnergy} 并带 1 秒节流)。
     * 若 host 认为必须在压伤害的同时提示,需要另找一个能拿到攻击者的钩子(N23/N24 未闭合)。
     */
    public static void onUpdateAttack(SlashBladeEvent.UpdateAttackEvent event) {
        ItemStack blade = event.getBlade();
        if (!isChargedBlade(blade)) {
            return;
        }
        if (ChargedBladeGate.shouldZeroDamage(blade)) {
            event.setNewDamage(0);
        }
    }

    /**
     * 双保险:对本模组刀取消「断刀」。
     *
     * <p>即使别处调了 {@code setDamage},也不允许本模组刀进入 broken —— broken 会让
     * {@code getDefaultAttributeModifiers} 走 {@code -0.5 - b} 分支,13.14 立刻被破坏。
     * 主防线是 {@link ChargedBladeItem#damageItem} 恒返回 0。
     *
     * <p>{@code BreakEvent extends SlashBladeEvent implements ICancellableEvent}(事实表 §5)⇒
     * {@code setCanceled(true)} 可用;取刀同样走 {@code getBlade()}。
     */
    public static void onBladeBreak(SlashBladeEvent.BreakEvent event) {
        if (isChargedBlade(event.getBlade())) {
            event.setCanceled(true);
        }
    }

    /**
     * 能量耗尽的 actionbar 提示,按玩家 1 秒节流。
     *
     * <p>提示只在服务端发(客户端会收到 actionbar 包),这样 {@link #LAST_HINT_TICK} 的读写
     * 全部落在服务端主线程上(节流表是 {@code WeakHashMap},不保证并发安全)。
     */
    public static void notifyOutOfEnergy(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        int now = (int) player.level().getGameTime();
        Integer last = LAST_HINT_TICK.get(player);
        if (last != null && now - last < HINT_COOLDOWN_TICKS) {
            return;
        }
        LAST_HINT_TICK.put(player, now);
        player.displayClientMessage(Component.translatable(KEY_NO_ENERGY_HINT), true);
    }

    /** 供测试/诊断观察节流表大小(不影响行为)。 */
    public static int hintThrottleEntries() {
        return LAST_HINT_TICK.size();
    }

    /** 该物品栈是否属于本模组充能刀(门卫内可用;不构造新栈)。 */
    public static boolean isChargedBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ChargedBladeItem;
    }
}
