package atopos.destiny2.common.gear

import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.stats.StatType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack

enum class DestinyArmorSlot(val displayName: String) {
    HELMET("头盔"),
    CHEST("胸甲"),
    LEGS("护腿"),
    BOOTS("靴子"),
    CLASS_ITEM("职业物品");

    companion object {
        fun from(stack: ItemStack): DestinyArmorSlot? = when (val item = stack.item) {
            is DestinyClassItem -> CLASS_ITEM
            is ArmorItem -> when (item.type) {
                ArmorItem.Type.HELMET -> HELMET
                ArmorItem.Type.CHESTPLATE -> CHEST
                ArmorItem.Type.LEGGINGS -> LEGS
                ArmorItem.Type.BOOTS -> BOOTS
                else -> null
            }
            else -> null
        }
    }
}

enum class ArmorModKind { STAT, SLOT }

/** Runtime keys are intentionally data-like: one server runtime can aggregate copies consistently. */
enum class ArmorModEffect {
    STAT,
    ORB_ON_WEAPON_KILL,
    AMMO_FINDER,
    AMMO_SCOUT,
    SUPER_FROM_GRENADE,
    SUPER_FROM_MELEE,
    SUPER_FROM_CLASS,
    SUPER_ORB_POWER,
    TARGETING,
    RELOAD,
    READY_SPEED,
    GRENADE_KICKSTART,
    MELEE_KICKSTART,
    ORB_ON_GRENADE_KILL,
    ORB_ON_MELEE_KILL,
    GRENADE_FROM_MELEE,
    CLASS_FROM_GRENADE,
    MELEE_FROM_GRENADE,
    CLASS_FROM_MELEE,
    FONT_GRENADE,
    FONT_MELEE,
    AMMO_RESERVES,
    FLINCH_RESIST,
    DAMAGE_RESIST,
    CHARGED_UP,
    EMERGENCY_REINFORCEMENT,
    FONT_HEALTH,
    SCAVENGER,
    HOLSTER,
    WEAPON_SURGE,
    ORB_HEAL,
    ORB_REGEN,
    ORB_GRENADE,
    ORB_MELEE,
    ORB_CLASS,
    ORB_ALL_ABILITIES,
    STACKS_ON_STACKS,
    FONT_WEAPONS,
    FONT_CLASS,
    MOVEMENT,
    CLASS_TO_GRENADE,
    CLASS_TO_MELEE,
    CLASS_TO_ALL,
    REAPER,
    POWERFUL_ATTRACTION,
    TIME_DILATION,
    UTILITY_KICKSTART,
    FINISHER_SPECIAL_AMMO,
    FINISHER_GRENADE,
    FINISHER_HEAL,
    FINISHER_OVERSHIELD,
    FINISHER_ARMOR_CHARGE
}

enum class ArmorModStackRule { DIMINISHING, NO_DUPLICATES }

data class ArmorModDefinition(
    val id: ResourceLocation,
    val displayName: String,
    val description: String,
    val kind: ArmorModKind,
    val allowedSlots: Set<DestinyArmorSlot>,
    val energyCost: Int,
    val effect: ArmorModEffect,
    val magnitude: Float = 0.0f,
    val stat: StatType? = null,
    val statBonus: Int = 0,
    val stackRule: ArmorModStackRule = ArmorModStackRule.DIMINISHING
) {
    val bonusStats: DestinyStats
        get() {
            val values = IntArray(StatType.entries.size)
            stat?.let { values[it.ordinal] = statBonus }
            return DestinyStats(values[0], values[1], values[2], values[3], values[4], values[5])
        }
}

object ArmorModRegistry {
    private const val MOD_ID = "destiny2-mod"
    private val definitions = linkedMapOf<ResourceLocation, ArmorModDefinition>()
    private val allSlots = DestinyArmorSlot.entries.toSet()

    init {
        registerStatPair("weapons", "武器", StatType.WEAPONS)
        registerStatPair("health", "生命", StatType.HEALTH)
        registerStatPair("class", "职业", StatType.CLASS)
        registerStatPair("grenade", "手雷", StatType.GRENADE)
        registerStatPair("super", "超能", StatType.SUPER)
        registerStatPair("melee", "近战", StatType.MELEE)

        // Helmet: orb generation, ammo discovery, targeting and super generation.
        elemental("siphon", "虹吸", DestinyArmorSlot.HELMET, 3, ArmorModEffect.ORB_ON_WEAPON_KILL, "连续使用%s伤害武器击杀会生成能量球。")
        slot("harmonic_siphon", "谐振虹吸", DestinyArmorSlot.HELMET, 2, ArmorModEffect.ORB_ON_WEAPON_KILL, "与当前超能元素一致的武器连续击杀会生成能量球。")
        slot("special_ammo_finder", "特殊弹药搜寻", DestinyArmorSlot.HELMET, 3, ArmorModEffect.AMMO_FINDER, "使用武器击杀会积累进度并额外生成特殊弹药。")
        slot("heavy_ammo_finder", "重型弹药搜寻", DestinyArmorSlot.HELMET, 3, ArmorModEffect.AMMO_FINDER, "使用武器击杀会积累进度并额外生成重型弹药。")
        slot("special_ammo_scout", "特殊弹药侦察", DestinyArmorSlot.HELMET, 1, ArmorModEffect.AMMO_SCOUT, "弹药搜寻触发时也为附近队友生成特殊弹药。")
        slot("heavy_ammo_scout", "重型弹药侦察", DestinyArmorSlot.HELMET, 1, ArmorModEffect.AMMO_SCOUT, "弹药搜寻触发时也为附近队友生成重型弹药。")
        slot("ashes_to_assets", "点石成金", DestinyArmorSlot.HELMET, 3, ArmorModEffect.SUPER_FROM_GRENADE, "手雷击杀提供额外超能能量。")
        slot("hands_on", "亲力亲为", DestinyArmorSlot.HELMET, 3, ArmorModEffect.SUPER_FROM_MELEE, "近战击杀提供额外超能能量。")
        slot("dynamo", "充沛动力", DestinyArmorSlot.HELMET, 3, ArmorModEffect.SUPER_FROM_CLASS, "在敌人附近使用职业技能会获得超能能量。")
        slot("power_preservation", "能量守恒", DestinyArmorSlot.HELMET, 3, ArmorModEffect.SUPER_ORB_POWER, "超能击杀生成的能量球更强。")
        elemental("targeting", "瞄准校准", DestinyArmorSlot.HELMET, 3, ArmorModEffect.TARGETING, "提高%s伤害武器的精度与瞄准稳定性。")

        // Boots replace D2 gauntlets. Names and behavior are foot/movement oriented by design.
        elemental("stride_loader", "步伐装填", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.RELOAD, "站稳或移动时提高%s伤害武器的装填速度。")
        elemental("quickstep", "迅捷步法", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.READY_SPEED, "移动后提高%s伤害武器的切换与举枪速度。")
        slot("grenade_kickstart", "手雷助跑", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.GRENADE_KICKSTART, "消耗全部手雷能量后，奔跑会返还手雷能量；护甲充能可强化返还。")
        slot("melee_kickstart", "近战助跑", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.MELEE_KICKSTART, "消耗全部近战能量后，奔跑会返还近战能量；护甲充能可强化返还。")
        slot("firepower", "爆破足迹", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.ORB_ON_GRENADE_KILL, "手雷击杀会在目标位置生成能量球。")
        slot("heavy_handed", "强袭足迹", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.ORB_ON_MELEE_KILL, "充能近战击杀会在目标位置生成能量球。")
        slot("momentum_transfer", "动量传递", DestinyArmorSlot.BOOTS, 2, ArmorModEffect.GRENADE_FROM_MELEE, "近战造成伤害后，持续移动会缩短手雷冷却。")
        slot("bolstering_detonation", "爆破推进", DestinyArmorSlot.BOOTS, 2, ArmorModEffect.CLASS_FROM_GRENADE, "手雷造成伤害后提高职业技能恢复。")
        slot("impact_induction", "冲击推进", DestinyArmorSlot.BOOTS, 2, ArmorModEffect.MELEE_FROM_GRENADE, "手雷造成伤害后提高近战技能恢复。")
        slot("focusing_strike", "专注踏击", DestinyArmorSlot.BOOTS, 2, ArmorModEffect.CLASS_FROM_MELEE, "近战造成伤害后提高职业技能恢复。")
        slot("font_of_focus", "专注之泉", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.FONT_GRENADE, "拥有护甲充能时提高手雷属性，并使充能随时间衰减。")
        slot("font_of_vigor", "活力之泉", DestinyArmorSlot.BOOTS, 3, ArmorModEffect.FONT_MELEE, "拥有护甲充能时提高近战属性，并使充能随时间衰减。")

        // Chest.
        elemental("reserves", "弹药储备", DestinyArmorSlot.CHEST, 3, ArmorModEffect.AMMO_RESERVES, "增加%s伤害武器可携带的特殊与重型弹药。")
        elemental("unflinching", "稳定抗扰", DestinyArmorSlot.CHEST, 3, ArmorModEffect.FLINCH_RESIST, "受到伤害时降低%s伤害武器的准星扰动。")
        listOf("arc" to "电弧", "solar" to "烈日", "void" to "虚空", "stasis" to "冰影", "strand" to "缚丝").forEach { (id, name) ->
            slot("${id}_resistance", "${name}抗性", DestinyArmorSlot.CHEST, 2, ArmorModEffect.DAMAGE_RESIST, "降低受到的${name}伤害。", 0.15f)
        }
        slot("concussive_dampener", "震荡阻尼", DestinyArmorSlot.CHEST, 3, ArmorModEffect.DAMAGE_RESIST, "降低爆炸与范围伤害。", 0.15f)
        slot("melee_damage_resistance", "近战伤害抗性", DestinyArmorSlot.CHEST, 3, ArmorModEffect.DAMAGE_RESIST, "降低近距离敌人造成的伤害。", 0.15f)
        slot("sniper_damage_resistance", "狙击伤害抗性", DestinyArmorSlot.CHEST, 3, ArmorModEffect.DAMAGE_RESIST, "降低远距离敌人造成的伤害。", 0.15f)
        slot("charged_up", "充能完毕", DestinyArmorSlot.CHEST, 3, ArmorModEffect.CHARGED_UP, "护甲充能上限提高一层。")
        slot("emergency_reinforcement", "紧急增援", DestinyArmorSlot.CHEST, 3, ArmorModEffect.EMERGENCY_REINFORCEMENT, "护盾破裂时消耗护甲充能并获得临时伤害抗性。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("font_of_endurance", "耐久之泉", DestinyArmorSlot.CHEST, 3, ArmorModEffect.FONT_HEALTH, "拥有护甲充能时提高生命属性，并使充能随时间衰减。")

        // Legs.
        elemental("scavenger", "弹药回收", DestinyArmorSlot.LEGS, 3, ArmorModEffect.SCAVENGER, "拾取弹药时为%s伤害武器获得额外弹药。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        elemental("holster", "自动装填枪套", DestinyArmorSlot.LEGS, 3, ArmorModEffect.HOLSTER, "逐步装填收起的%s伤害武器。")
        elemental("surge", "武器激涌", DestinyArmorSlot.LEGS, 3, ArmorModEffect.WEAPON_SURGE, "拥有护甲充能时提高%s伤害武器的伤害，并使充能随时间衰减。")
        slot("recuperation", "疗愈", DestinyArmorSlot.LEGS, 1, ArmorModEffect.ORB_HEAL, "拾取能量球立即恢复生命与护盾。")
        slot("better_already", "恢复如初", DestinyArmorSlot.LEGS, 1, ArmorModEffect.ORB_REGEN, "拾取能量球立即启动生命护盾恢复。")
        slot("innervation", "神经支配", DestinyArmorSlot.LEGS, 1, ArmorModEffect.ORB_GRENADE, "拾取能量球缩短手雷冷却。")
        slot("invigoration", "鼓舞", DestinyArmorSlot.LEGS, 1, ArmorModEffect.ORB_MELEE, "拾取能量球缩短近战冷却。")
        slot("insulation", "绝缘", DestinyArmorSlot.LEGS, 1, ArmorModEffect.ORB_CLASS, "拾取能量球缩短职业技能冷却。")
        slot("absolution", "赦免", DestinyArmorSlot.LEGS, 3, ArmorModEffect.ORB_ALL_ABILITIES, "拾取能量球缩短所有技能冷却。")
        slot("stacks_on_stacks", "层层不息", DestinyArmorSlot.LEGS, 4, ArmorModEffect.STACKS_ON_STACKS, "拾取能量球时额外获得一层护甲充能。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("font_of_agility", "敏捷之泉", DestinyArmorSlot.LEGS, 3, ArmorModEffect.FONT_WEAPONS, "拥有护甲充能时提高武器属性，并使充能随时间衰减。")
        slot("font_of_restoration", "恢复之泉", DestinyArmorSlot.LEGS, 3, ArmorModEffect.FONT_CLASS, "拥有护甲充能时提高职业属性，并使充能随时间衰减。")
        slot("enhanced_athletics", "强化运动", DestinyArmorSlot.LEGS, 3, ArmorModEffect.MOVEMENT, "提高基础移动速度与跳跃高度。")

        // Class item.
        slot("bomber", "投弹手", DestinyArmorSlot.CLASS_ITEM, 1, ArmorModEffect.CLASS_TO_GRENADE, "使用职业技能时缩短手雷冷却。")
        slot("outreach", "延伸", DestinyArmorSlot.CLASS_ITEM, 1, ArmorModEffect.CLASS_TO_MELEE, "使用职业技能时缩短近战冷却。")
        slot("distribution", "分配", DestinyArmorSlot.CLASS_ITEM, 3, ArmorModEffect.CLASS_TO_ALL, "在敌人附近使用职业技能时缩短所有技能冷却。")
        slot("reaper", "收割者", DestinyArmorSlot.CLASS_ITEM, 3, ArmorModEffect.REAPER, "使用职业技能后，下一次武器击杀生成能量球。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("powerful_attraction", "强力吸引", DestinyArmorSlot.CLASS_ITEM, 2, ArmorModEffect.POWERFUL_ATTRACTION, "使用职业技能时拾取附近的能量球。")
        slot("time_dilation", "时间膨胀", DestinyArmorSlot.CLASS_ITEM, 3, ArmorModEffect.TIME_DILATION, "延长会使护甲充能随时间衰减的模组持续时间。")
        slot("utility_kickstart", "职业启动", DestinyArmorSlot.CLASS_ITEM, 3, ArmorModEffect.UTILITY_KICKSTART, "职业能量耗尽时返还能量；护甲充能可强化返还。")
        slot("special_finisher", "特殊终结技", DestinyArmorSlot.CLASS_ITEM, 1, ArmorModEffect.FINISHER_SPECIAL_AMMO, "终结敌人时消耗三层护甲充能并生成特殊弹药。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("explosive_finisher", "爆破终结技", DestinyArmorSlot.CLASS_ITEM, 1, ArmorModEffect.FINISHER_GRENADE, "终结敌人时消耗护甲充能并恢复手雷能量。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("healthy_finisher", "疗愈终结技", DestinyArmorSlot.CLASS_ITEM, 1, ArmorModEffect.FINISHER_HEAL, "终结敌人时消耗护甲充能并恢复生命与护盾。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("bulwark_finisher", "壁垒终结技", DestinyArmorSlot.CLASS_ITEM, 1, ArmorModEffect.FINISHER_OVERSHIELD, "终结敌人时消耗护甲充能并获得临时护盾。", stackRule = ArmorModStackRule.NO_DUPLICATES)
        slot("empowered_finish", "充能终结", DestinyArmorSlot.CLASS_ITEM, 3, ArmorModEffect.FINISHER_ARMOR_CHARGE, "没有护甲充能时完成终结技会获得一层护甲充能。", stackRule = ArmorModStackRule.NO_DUPLICATES)
    }

    fun get(id: ResourceLocation?): ArmorModDefinition? = id?.let(definitions::get)
    fun all(): List<ArmorModDefinition> = definitions.values.toList()
    fun available(slot: DestinyArmorSlot, socketIndex: Int): List<ArmorModDefinition> = definitions.values.filter {
        if (socketIndex == 0) it.kind == ArmorModKind.STAT else it.kind == ArmorModKind.SLOT && slot in it.allowedSlots
    }

    private fun registerStatPair(path: String, displayName: String, stat: StatType) {
        stat(path, "小型${displayName}模组", stat, 5, 1)
        stat("major_$path", "${displayName}模组", stat, 10, 3)
    }

    private fun stat(path: String, name: String, stat: StatType, bonus: Int, cost: Int) {
        put(path, name, "装备后使${name.removePrefix("小型").removeSuffix("模组")}属性 +$bonus。", ArmorModKind.STAT, allSlots, cost, ArmorModEffect.STAT, stat = stat, statBonus = bonus)
    }

    private fun slot(path: String, name: String, slot: DestinyArmorSlot, cost: Int, effect: ArmorModEffect, description: String, magnitude: Float = 0.0f, stackRule: ArmorModStackRule = ArmorModStackRule.DIMINISHING) {
        put(path, name, description, ArmorModKind.SLOT, setOf(slot), cost, effect, magnitude, stackRule = stackRule)
    }

    private fun elemental(path: String, name: String, slot: DestinyArmorSlot, cost: Int, effect: ArmorModEffect, description: String, stackRule: ArmorModStackRule = ArmorModStackRule.DIMINISHING) {
        listOf("kinetic" to "动能", "arc" to "电弧", "solar" to "烈日", "void" to "虚空", "stasis" to "冰影", "strand" to "缚丝").forEach { (id, display) ->
            slot("${id}_$path", "$display$name", slot, cost, effect, description.format(display), stackRule = stackRule)
        }
        slot("harmonic_$path", "谐振$name", slot, (cost - 1).coerceAtLeast(1), effect, description.format("当前超能同调"), stackRule = stackRule)
    }

    private fun put(path: String, name: String, description: String, kind: ArmorModKind, slots: Set<DestinyArmorSlot>, cost: Int, effect: ArmorModEffect, magnitude: Float = 0.0f, stat: StatType? = null, statBonus: Int = 0, stackRule: ArmorModStackRule = ArmorModStackRule.DIMINISHING) {
        val id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "armor_mod/$path")
        definitions[id] = ArmorModDefinition(id, name, description, kind, slots, cost, effect, magnitude, stat, statBonus, stackRule)
    }
}
