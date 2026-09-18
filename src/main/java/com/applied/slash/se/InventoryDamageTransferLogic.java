package com.applied.slash.se;

import java.util.Optional;

import com.applied.slash.AppliedSlash;
import com.applied.slash.AppliedSlashComponents;
import com.applied.slash.portable.SlashCellAccess;

import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * 「莉莉」SE 的实际逻辑:把手持元件内 + 快捷栏里的拔刀剑各 **10%** 的基础伤害加到当前这把刀上。
 *
 * <p>两条链路(签名均来自 javap 实证):
 * <ol>
 *   <li>{@link SlashBladeEvent.UpdateEvent} —— 重锋每刻对刀调用,带 level/entity/slot/是否选中;
 *       我们在这里**算出加成并写进本模组的物品组件**(仅在数值变化时写,避免每刻刷同步包)。</li>
 *   <li>{@link ItemAttributeModifierEvent} —— NeoForge 在查询物品属性时触发;
 *       我们按组件值往 {@link Attributes#ATTACK_DAMAGE} 上加一条 MAINHAND 修正
 *       (重锋的刀伤也正是走这个属性:ItemSlashBlade#getDefaultAttributeModifiers 实测如此)。</li>
 * </ol>
 *
 * <p>「每把刀的伤害」取 {@code ISlashBladeState.getBaseAttackModifier()},即面板基础伤害(莉莉为 14.13)。
 */
public final class InventoryDamageTransferLogic {
    /** 本 SE 的注册 id。 */
    public static final ResourceLocation SE_ID =
            ResourceLocation.fromNamespaceAndPath(AppliedSlash.MODID, "inventory_transfer");
    /** 属性修正的固定 id(同一 id 覆盖,不会叠加)。 */
    /** 重锋连击/攻击伤害属性的修正 id(AttackManager/AttackHelper 读的就是这个属性)。 */
    public static final ResourceLocation SLASHBLADE_DAMAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(AppliedSlash.MODID, "inventory_transfer_sb_damage");
    /** 原版攻击伤害属性的修正 id。 */
    public static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(AppliedSlash.MODID, "inventory_transfer_bonus");
    /** 快捷栏格数(PlayerInventory 的 0..8;副手不计,需要的话改这里)。 */
    private static final int HOTBAR_SIZE = 9;
    /**
     * 每个玩家的"上次算过的状态"缓存:[签名, 持有槽位, 加成位模式]。
     * 命中时直接返回 ⇒ 常态下一 tick 只做 9 次物品引用 + 一次哈希,不读任何刀状态、不写组件。
     */
    private static final java.util.Map<java.util.UUID, long[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 写组件的最小变化量:小于它就不写,避免每刻同步。 */
    private static final double EPSILON = 0.01D;

    private InventoryDamageTransferLogic() {
    }

    /**
     * 纯计算(可单测):背包里**除自己所在槽位**以外,每把拔刀剑各取 {@link InventoryDamageTransfer#RATIO} 基础伤害。
     *
     * @param ownSlot 当前这把刀所在槽位(该槽跳过);同一份物品对象也跳过
     */
    public static double computeBonus(ItemStack blade, int ownSlot, Container inventory) {
        double bonus = 0.0D;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (i == ownSlot) {
                continue;
            }
            ItemStack other = inventory.getItem(i);
            if (other.isEmpty() || other == blade) {
                continue;
            }
            Float dmg = baseDamage(other);
            if (dmg != null) {
                bonus += dmg.doubleValue() * InventoryDamageTransfer.RATIO;
            }
        }
        return bonus;
    }

    /** 是拔刀剑则返回其基础伤害,否则 null。 */
    public static Float baseDamage(ItemStack stack) {
        Optional<ISlashBladeState> state = BladeStateAccess.of(stack);
        return state.map(ISlashBladeState::getBaseAttackModifier).orElse(null);
    }

    /** 这把刀是否带着我们的 SE 且满足等级条件。 */
    public static boolean hasSe(ItemStack blade, ISlashBladeState state) {
        return state.hasSpecialEffect(SE_ID) && SpecialEffect.isEffective(SE_ID, state.getRefine());
    }

    /**
     * 每刻为**玩家手持/副手**的莉莉刷新加成。
     *
     * <p><b>为什么用玩家 tick 而不是重锋的 {@code SlashBladeEvent.UpdateEvent}</b>:
     * 那条事件依赖重锋在什么时机、向哪个总线投递,上一版就是栽在这里(进游戏完全不生效,
     * 而手动调用同一个方法在 GameTest 里却是通的)。玩家 tick 是 NeoForge 官方事件,
     * 触发时机与总线都有保证,且**加成本来就只在手持时才有意义**(属性修正挂在 MAINHAND 上)。
     */
    /**
     * 每刻刷新**手持**的莉莉加成。
     *
     * <p><b>性能设计</b>(比"每刻盲扫背包"省得多):
     * <ol>
     *   <li>只看**快捷栏 9 格**(0..8),不看 41 格整背包 —— 用户口径也是快捷栏;</li>
     *   <li>先算一个**签名**(9 格的 物品 + 数量 + 是否带我们的组件),与上次相同就**直接返回** ——
     *       常态下不调用任何状态读取(真正贵的 {@code BladeStateAccess.of} 与组件写入都被跳过);</li>
     *   <li>持有的刀不带本 SE 时,只做一次"清组件"就返回(组件在就清,不在什么都不做)。</li>
     * </ol>
     * 需要重算的时机:快捷栏内容变了 / 持有槽位换了 / 签名未命中(例如首次、换维度后)。
     */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            cacheRemove(player);
            return;
        }
        ISlashBladeState state = BladeStateAccess.of(held).orElse(null);
        if (state == null || !hasSe(held, state)) {
            cacheRemove(player);
            clearBonus(held);
            return;
        }
        var inv = player.getInventory();
        int heldSlot = inv.selected;
        long signature = hotbarSignature(inv);
        long[] cached = CACHE.get(player.getUUID());
        if (cached != null && cached[0] == signature && (int) cached[1] == heldSlot) {
            return;                                   // 快捷栏没变、也没换手 ⇒ 无事可做
        }
        // 手持元件内优先 + 快捷栏补足,合计 ≤ 8 把,每把 10%
        Pool pool = computePool(held, player);
        setBonus(held, pool.bonus());
        CACHE.put(player.getUUID(), new long[] {signature, heldSlot, Double.doubleToLongBits(pool.bonus())});
    }

    /** 玩家退出时清掉缓存,避免长期驻留。 */
    @SubscribeEvent
    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        CACHE.remove(event.getEntity().getUUID());
    }

    private static void cacheRemove(Player player) {
        CACHE.remove(player.getUUID());
    }

    /** 快捷栏签名:9 格的物品/数量/是否带我们的组件;元件还要把**内容**合进来(任一变化都代表要重算)。 */
    private static long hotbarSignature(net.minecraft.world.entity.player.Inventory inv) {
        long h = 1125899906842597L;                  // 任意非零种子
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            if (SlashCellAccess.isSlashCell(s)) {
                // 元件内容变化也要触发重算:每把刀的唯一标记合进签名
                // (标记是随机 UUID ⇒ 插入/取出/换序都会改变它;比逐字段比较 ItemStack 便宜得多)
                for (ItemStack entry : SlashCellAccess.bladesIn(s)) {
                    java.util.UUID id = entry.get(AppliedSlashComponents.PORTABLE_ENTRY_ID.get());
                    h = h * 31L + (id == null ? 0L : id.getMostSignificantBits() ^ id.getLeastSignificantBits());
                }
            }
            long v = s.isEmpty() ? 0L
                    : (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).hashCode() * 31L
                       + s.getCount() * 7L
                       + (s.has(AppliedSlashComponents.LILI_DAMAGE_BONUS.get()) ? 1L : 0L));
            h = h * 31L + v;
        }
        return h;
    }

    /** 只扫**快捷栏**的加成计算(9 格)。 */
    public static double computeHotbarBonus(ItemStack blade, int ownSlot, Container hotbar) {
        double bonus = 0.0D;
        int n = Math.min(HOTBAR_SIZE, hotbar.getContainerSize());
        for (int i = 0; i < n; i++) {
            if (i == ownSlot) {
                continue;                            // 除了自己
            }
            ItemStack other = hotbar.getItem(i);
            if (other.isEmpty() || other == blade) {
                continue;
            }
            Float dmg = baseDamage(other);
            if (dmg != null) {
                bonus += dmg.doubleValue() * InventoryDamageTransfer.RATIO;
            }
        }
        return bonus;
    }



    /** 物品属性重算:把组件里的加成作为 ATTACK_DAMAGE 修正加进去。 */
    @SubscribeEvent
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Double bonus = stack.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        if (bonus == null || bonus <= 0.0D) {
            return;
        }
        // ①原版攻击伤害(普通左键/走原版路子的部分)
        event.addModifier(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
        // ②**重锋自己的伤害属性** —— 连击(A/B)的伤害由 AttackManager/AttackHelper 读它来算
        //   (实测该属性只在重锋内部被引用,加在原版属性上对连击无效,所以两条都要加)
        event.addModifier(mods.flammpfeil.slashblade.registry.ModAttributes.SLASHBLADE_DAMAGE,
                new AttributeModifier(SLASHBLADE_DAMAGE_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }

    /**
     * 工具提示用的面板伤害文本(工具提示与测试共用同一份实现)。
     *
     * <p>口径:面板伤害 = 刀的基础伤害({@code getBaseAttackModifier},莉莉为 14.13)
     * + 本 SE 当前算出的加成;加成没算出来时(比如没拿在手上)就只显示基础伤害。
     */
    public static String panelDamageText(ItemStack blade) {
        float base = BladeStateAccess.of(blade).map(ISlashBladeState::getBaseAttackModifier).orElse(0.0F);
        Double bonus = blade.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        double total = base + (bonus == null ? 0.0D : bonus);
        return String.format(java.util.Locale.ROOT, "现在的面板伤害为 %.2f", total);
    }

    /** 鼠标悬停时追加一行动态面板伤害(只在带我们 SE 的刀上)。 */
    @SubscribeEvent
    public static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !BladeStateAccess.of(stack).isPresent()) {
            return;
        }
        ISlashBladeState state = BladeStateAccess.of(stack).orElseThrow();
        if (!hasSe(stack, state)) {
            return;
        }
        // 插到"SE 描述那一行"的紧下一行(描述由重锋用 se.<ns>.<path>.desc 渲染)。
        // 找不到那行(语言文件缺失等)就退化为追加到末尾,不会丢信息。
        var line = net.minecraft.network.chat.Component.literal(panelDamageText(stack, event.getEntity()))
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        String descText = net.minecraft.network.chat.Component
                .translatable("se." + SE_ID.getNamespace() + "." + SE_ID.getPath() + ".desc").getString();
        var tip = event.getToolTip();
        int insertAt = -1;
        for (int i = 0; i < tip.size(); i++) {
            if (tip.get(i).getString().contains(descText)) {
                insertAt = i + 1;
                break;
            }
        }
        if (insertAt >= 0 && insertAt <= tip.size()) {
            tip.add(insertAt, line);
        } else {
            tip.add(line);
        }
    }
    /** 加伤池的构成(tooltip 明细用)。 */
    public record Pool(double bonus, int fromCells, int fromHotbar) {
        /** 参与计算的刀数(≤ 8)。 */
        public int total() {
            return fromCells + fromHotbar;
        }
    }

    /**
     * 计算加伤池:**元件内优先,再用快捷栏补足,合计不超过 8 把**,每把 10%。
     *
     * <p>两条口径(用户定):
     * <ul>
     *   <li>元件必须位于**快捷栏(0..8)**才计入;主背包里不算;</li>
     *   <li>被手持的那把刀按**物品同一性**排除(不计入自己)。</li>
     * </ul>
     */
    public static Pool computePool(ItemStack held, Player player) {
        var inv = player.getInventory();
        java.util.List<ItemStack> picked = new java.util.ArrayList<>();
        // ① 元件内优先
        for (int i = 0; i < HOTBAR_SIZE && picked.size() < SlashCellAccess.CAPACITY; i++) {
            ItemStack cell = inv.getItem(i);
            if (!SlashCellAccess.isSlashCell(cell)) {
                continue;
            }
            for (ItemStack blade : SlashCellAccess.bladesIn(cell)) {
                if (picked.size() >= SlashCellAccess.CAPACITY) {
                    break;
                }
                if (blade.isEmpty() || blade == held) {
                    continue;
                }
                if (baseDamage(blade) != null) {
                    picked.add(blade);
                }
            }
        }
        int fromCells = picked.size();
        // ② 再用快捷栏补足
        int fromHotbar = 0;
        for (int i = 0; i < HOTBAR_SIZE && picked.size() < SlashCellAccess.CAPACITY; i++) {
            if (i == inv.selected) {
                continue;                       // 除了自己所在槽位
            }
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s == held || SlashCellAccess.isSlashCell(s)) {
                continue;                       // 元件已在 ① 处理
            }
            if (baseDamage(s) == null) {
                continue;
            }
            picked.add(s);
            fromHotbar++;
        }
        double bonus = 0.0D;
        for (ItemStack s : picked) {
            Float d = baseDamage(s);
            if (d != null) {
                bonus += d.doubleValue() * InventoryDamageTransfer.RATIO;
            }
        }
        return new Pool(bonus, fromCells, fromHotbar);
    }

    /** 面板伤害文本(带池构成;玩家不可用时退化为只显示数字)。 */
    public static String panelDamageText(ItemStack blade, Player player) {
        String base = panelDamageText(blade);
        if (player == null) {
            return base;
        }
        Pool pool = computePool(blade, player);
        return String.format(java.util.Locale.ROOT, "%s(元件 %d 把 + 快捷栏 %d 把 = %d/%d)",
                base, pool.fromCells(), pool.fromHotbar(), pool.total(), SlashCellAccess.CAPACITY);
    }
    private static void setBonus(ItemStack blade, double bonus) {
        if (bonus <= 0.0D) {
            clearBonus(blade);
            return;
        }
        Double current = blade.get(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        if (current != null && Math.abs(current - bonus) < EPSILON) {
            return;
        }
        blade.set(AppliedSlashComponents.LILI_DAMAGE_BONUS.get(), bonus);
    }

    private static void clearBonus(ItemStack blade) {
        if (blade.has(AppliedSlashComponents.LILI_DAMAGE_BONUS.get())) {
            blade.remove(AppliedSlashComponents.LILI_DAMAGE_BONUS.get());
        }
    }
}
