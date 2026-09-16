package com.applied.slash.charged;

import java.util.List;
import java.util.function.Consumer;

import com.applied.slash.charged.block.ChargerMath;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 5 把充能刀的抽象基类。**所有共性都在这里**,5 个子类只提供 id / 语言键 / 上限。
 *
 * <p>构造签名照抄事实表:{@code ItemSlashBlade(Tier, int, float, Item$Properties)} 已用
 * {@code javap} 实证存在(见 {@code build/asverify/javap-report.txt} 第 1743 行)。
 *
 * <h2>「没能量完全不能用」的四层(事实表 §9.1 的设计修正版)</h2>
 * <ul>
 *   <li><b>主保证 = 原生 sealed</b>:能量归零 ⇒ {@code sealed = true}(由
 *       {@link ChargedBladeEnergy#set} 同步),由重锋自己拦住使用;</li>
 *   <li><b>第二保证 = 原生伤害事件</b>{@code SlashBladeEvent.UpdateAttackEvent}:
 *       重锋在 {@code getDefaultAttributeModifiers} 内部 {@code post} 它并采用
 *       {@code getNewDamage()} —— 没能量时把伤害压到 0(见 {@link ChargedBladeEvents});</li>
 *   <li>L1 右键:{@link #use} 在能量不足时不调用 super;</li>
 *   <li><b>扣费唯一入口</b> = {@link #hurtEnemy}(只有真正命中才扣,且只在服务端改数据)。</li>
 * </ul>
 *
 * <p><b>刻意不使用</b> {@code AttackEntityEvent} 作保证:
 * {@code Player.attack} 的全类扫描里扫不到它的触发点(事实表 §9.1),
 * 依赖它等于把硬需求压在未证实的假设上。
 *
 * <h2>耐久 / broken(必须处理,否则 13.14 会自己崩掉)</h2>
 * {@link #damageItem} 恒返回 0 ⇒ 永不磨损、永不 broken。这不只是玩法选择:
 * ① broken 会让 {@code getDefaultAttributeModifiers} 走 {@code -0.5 - b} 分支
 * (伤害变负,13.14 被破坏);② {@code setDamage}/{@code damageItem} 内部都有
 * {@code BladeStateAccess.of(stack).orElseThrow()} —— 一把<b>没有刀身数据</b>的刀走到耐久路径
 * 就是 {@code NoSuchElementException} 崩服(事实表 §3.3 的硬约束)。
 */
public abstract class ChargedBladeItem extends ItemSlashBlade {

    // ------------------------------------------------------------------
    // 语言键:必须与 assets/applied_slash/lang/{zh_cn,en_us}.json 完全一致(PLAN §9.3)
    // ------------------------------------------------------------------
    private static final String KEY_TOOLTIP_ENERGY = "item.applied_slash.charged_blade.tooltip.energy";
    private static final String KEY_TOOLTIP_EMPTY = "item.applied_slash.charged_blade.tooltip.empty";
    private static final String KEY_TOOLTIP_QUANTIZE = "item.applied_slash.charged_blade.tooltip.quantize";

    /** 能量条满宽(原版约定:0..13,13 = 满)。 */
    private static final int BAR_MAX_WIDTH = 13;

    /** 能量比例上色阈值:≥ 50% 绿、≥ 20% 黄、其余红。 */
    private static final float BAR_COLOR_GREEN_AT = 0.5F;
    private static final float BAR_COLOR_YELLOW_AT = 0.2F;

    protected ChargedBladeItem(Tier tier, int attackDamageIn, float attackSpeedIn, Item.Properties properties) {
        super(tier, attackDamageIn, attackSpeedIn, properties);
    }

    /** 完整物品 id,例如 {@code applied_slash:charged_blade_pulse}。 */
    public abstract String bladeId();

    /** 语言键 = 刀身数据的 translationKey,例如 {@code item.applied_slash.charged_blade_pulse}。 */
    public abstract String nameKey();

    /** 该把刀的能量上限(读配置项,5 把各自独立)。 */
    public abstract int maxEnergy();

    /** 该把刀的静态说明(出厂默认数据、外观、默认上限都取自它)。 */
    public abstract ChargedBladeSpec spec();

    /** 本次使用所需的能量(配置项,5 把共用)。 */
    public int attackCost() {
        return ChargedBladeEnergy.costPerAttack();
    }

    // ------------------------------------------------------------------
    // L1:右键 / 使用。能量不足时不调用 super(不给 SA、不给连段)
    // ------------------------------------------------------------------
    /**
     * 能量 &lt; 消耗 ⇒ 不调用 super,返回 {@code InteractionResultHolder.fail(stack)}
     * + 服务端 actionbar 提示(1 秒节流)。
     *
     * <p>FAIL 是基类自己也在用的返回路径(实证:bytecode 偏移 2081–2105),安全。
     * 「不调用 super」是关键:重锋的 SA / 连段都挂在 super 的实现里,不进去就什么都不会发生。
     *
     * <p>顺带在门禁入口做一次自愈(PLAN §3.3):{@code /give} 出来的刀可能没有能量组件,
     * 那时「缺席 = 满」会让它立刻可用,而不是被当成废刀。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ChargedBladeFactory.ensureInitialized(stack);
        }
        if (!ChargedBladeEnergy.canUse(stack)) {
            if (!level.isClientSide) {
                ChargedBladeEvents.notifyOutOfEnergy(player);
            }
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    // ------------------------------------------------------------------
    // L3(可选补充,非保证):onLeftClickEntity
    // ------------------------------------------------------------------
    /**
     * <p><b>未证实</b>:{@code onLeftClickEntity} 的返回值语义(true = 取消攻击 还是 true = 已处理继续走
     * vanilla 伤害)在本项目没有实证,{@code Player.attack} 里也扫不到它的触发点(事实表 §9.1)。
     * 因此它<b>只作为可选补充</b>,不作为「没能量不能攻击」的保证 —— 保证是 sealed + UpdateAttackEvent。
     *
     * <p>实现口径(保守):
     * <ul>
     *   <li>能量不足 ⇒ <b>不调用 super</b>:重锋的 {@code progressCombo} 与 L_CLICK 行为都不会发生
     *       (连段不推进、不消耗下一次点击的窗口);</li>
     *   <li>返回值取 {@code false} = 重锋 {@code Item#onLeftClickEntity} 的默认值,意为「本次点击我不处理」。
     *       // NEEDS_HOST_CHECK: true/false 哪个表示「取消攻击」未取证(N23)。两种取值都不会造成
     *       「没能量却能打」:真正的伤害截断在 {@code SlashBladeEvent.UpdateAttackEvent}(L2)。
     *       若 host 后续实证 true = 取消,把这里的 {@code return false} 改成 {@code return true} 即可,
     *       其余代码不需要动;</li>
     *   <li>有能量 ⇒ 原样交给 super(保住重锋的连段 + L_CLICK 行为)。</li>
     * </ul>
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!ChargedBladeEnergy.canUse(stack)) {
            if (!player.level().isClientSide) {
                ChargedBladeEvents.notifyOutOfEnergy(player);
            }
            return false;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    // ------------------------------------------------------------------
    // 扣费唯一入口
    // ------------------------------------------------------------------
    /**
     * 命中扣费的**唯一**位置(事实表 §9.1:{@code Player.attack} 在偏移 1177 调用
     * {@code ItemStack.hurtEnemy(...)},紧随其后 1227 调 {@code postHurtEnemy} ⇒ 有调用点实证)。
     *
     * <p>不重复扣:其他三层门禁**只读不写**;不漏扣:命中路径唯一。
     * 「只有真正命中才扣」成立:目标处于无敌帧 / {@code hurt()} 返回 false 时 vanilla
     * 不会调用 {@code hurtEnemy}。
     *
     * <p>只在 {@code attacker.level() instanceof ServerLevel} 分支里写数据 ⇒ 双端都跑时也不会双扣;
     * 扣费走 {@link ChargedBladeEnergy#spendOnHit},归零时 {@code sealed} 由
     * {@link ChargedBladeEnergy#set} 一并置上(§5.1 的只读投影)。
     *
     * <p>// NEEDS_HOST_CHECK: {@code ItemStack#hurtEnemy} 的调用点已实证(偏移 1177),但
     * 「{@code getDefaultAttributeModifiers} 里的 ATTACK_DAMAGE 修饰是否会在能量变化后被重新结算」
     * 没有实证 —— 若 MC 只在**换装备时**收集一次物品属性修饰,L2 压伤害可能在「拿着满能量刀一路打到 0」
     * 的瞬间滞后若干 tick。验收清单第 4 条(0 能量打僵尸完全不掉血)正是要盯这一点。
     */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker != null && attacker.level() instanceof ServerLevel) {
            int before = ChargedBladeEnergy.get(stack);
            int after = ChargedBladeEnergy.spendOnHit(stack);
            if (after >= before && attacker instanceof Player player) {
                // 能量不足(理论上已被 sealed / L2 挡住):只提示,不动数据,更不会扣成负数
                ChargedBladeEvents.notifyOutOfEnergy(player);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    // ------------------------------------------------------------------
    // 耐久:恒不磨损(见类注释的三条理由)
    // ------------------------------------------------------------------
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<Item> onBroken) {
        // 永不耐久损耗:① 保住 13.14(broken 会让伤害变负);② 绕过 orElseThrow 崩服路径;③ 能量已是唯一资源
        return 0;
    }

    // ------------------------------------------------------------------
    // tooltip / 能量条
    // ------------------------------------------------------------------
    /**
     * 先调用 super(保留重锋的刀铭 / 耀魂 / 精炼 / SA / SE 行,实证偏移 2434–2454),再追加三行:
     * <ul>
     *   <li>{@code ...tooltip.energy}「能量 %s / %s」(灰);</li>
     *   <li>能量为 0 时追加 {@code ...tooltip.empty}「能量耗尽:无法使用」(红);</li>
     *   <li>常驻灰字 {@code ...tooltip.quantize},数字 = 档宽 - 1(入库最多丢多少点)。</li>
     * </ul>
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        int energy = ChargedBladeEnergy.get(stack);
        int max = ChargedBladeEnergy.max(stack);
        tooltip.add(Component.translatable(KEY_TOOLTIP_ENERGY, energy, max).withStyle(ChatFormatting.GRAY));
        if (energy <= 0) {
            tooltip.add(Component.translatable(KEY_TOOLTIP_EMPTY).withStyle(ChatFormatting.RED));
        }
        int perBucket = ChargedBladeEnergy.perBucket(max, ChargedBladeEnergy.quantizeLevels());
        tooltip.add(Component.translatable(KEY_TOOLTIP_QUANTIZE, Math.max(0, perBucket - 1))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 基类恒 false(实证偏移 2355–2358);必须覆写成 <b>true</b> 才能显示能量条。
     *
     * <p>与 {@link #getBarWidth} <b>同时上线</b>(事实表 §1):MC 在 {@code isBarVisible() == true}
     * 时会先画 13px 的黑色底槽,所以宽度计算必须已经实现,否则物品栏里会多出一条空槽。
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    /** {@code round(energy / max * 13)}(0..13,13 = 满);与界面能量条共用同一实现(见 {@link ChargerMath#barWidth})。 */
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(BAR_MAX_WIDTH, ChargerMath.barWidth(ChargedBladeEnergy.get(stack),
                ChargedBladeEnergy.max(stack)));
    }

    /**
     * 按能量比例上色:≥ 50% 绿、≥ 20% 黄、其余红。
     *
     * <p>返回 <b>int</b>(不是 {@code FastColor}/{@code ChatFormatting});原版约定是
     * {@code 0xRRGGBB} 形式(事实表 §1)。
     */
    @Override
    public int getBarColor(ItemStack stack) {
        int max = ChargedBladeEnergy.max(stack);
        if (max <= 0) {
            return 0xE04B4B;
        }
        float ratio = (float) ChargedBladeEnergy.get(stack) / (float) max;
        if (ratio >= BAR_COLOR_GREEN_AT) {
            return 0x3FE08A;
        }
        if (ratio >= BAR_COLOR_YELLOW_AT) {
            return 0xF0C24B;
        }
        return 0xE04B4B;
    }

    // ------------------------------------------------------------------
    // 自愈(三条创建路径:创造栏 / 配方与 /give / 任何持久化容器)
    // ------------------------------------------------------------------
    /** 合成台产出时自愈(命令 / 配方出来的刀也常经这条路补上刀身数据与能量)。 */
    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (!level.isClientSide) {
            ChargedBladeFactory.ensureInitialized(stack);
        }
        super.onCraftedBy(stack, level, player);
    }

    /**
     * 服务端每 tick 自愈一次 —— 这一条覆盖了「任何持久化容器里的刀」(含元件取出后的第一次 tick),
     * 是「绝不缺刀身数据」的兜底。
     *
     * <p>代价与基类自己的 {@code inventoryTick}(它同样每 tick 查一次刀身状态)同级:
     * 已经初始化过的刀只会走「读一个旗标就返回」的快路径。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            ChargedBladeFactory.ensureInitialized(stack);
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }
}
