package atopos.destiny2.common.player

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

data class DestinyConfigOption(
    val id: String,
    val title: String,
    val description: String,
    val fragmentSlots: Int = 0,
    val icon: ResourceLocation? = null
)

data class DestinySubclassConfigDefinition(
    val subclass: DestinySubclassType,
    val abilityOptions: Map<AbilitySlot, List<DestinyConfigOption>>,
    val aspectOptions: List<DestinyConfigOption>,
    val fragmentOptions: List<DestinyConfigOption>,
    val background: ResourceLocation? = null
)

data class PlayerSubclassConfiguration(
    val selectedAbilities: MutableMap<AbilitySlot, String> = mutableMapOf(),
    val selectedAspects: MutableList<String> = mutableListOf(),
    val selectedFragments: MutableList<String> = mutableListOf()
) {
    fun copy(): PlayerSubclassConfiguration {
        return PlayerSubclassConfiguration(
            selectedAbilities = selectedAbilities.toMutableMap(),
            selectedAspects = selectedAspects.toMutableList(),
            selectedFragments = selectedFragments.toMutableList()
        )
    }

    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        val abilitiesTag = CompoundTag()
        selectedAbilities.forEach { (slot, id) ->
            abilitiesTag.putString(slot.key, id)
        }
        tag.put("abilities", abilitiesTag)
        tag.putString("aspects", selectedAspects.joinToString(","))
        tag.putString("fragments", selectedFragments.joinToString(","))
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): PlayerSubclassConfiguration {
            val config = PlayerSubclassConfiguration()
            if (tag.contains("abilities")) {
                val abilitiesTag = tag.getCompound("abilities")
                AbilitySlot.entries.forEach { slot ->
                    if (abilitiesTag.contains(slot.key)) {
                        config.selectedAbilities[slot] = abilitiesTag.getString(slot.key)
                    }
                }
            }
            config.selectedAspects.addAll(readCsv(tag.getString("aspects")))
            config.selectedFragments.addAll(readCsv(tag.getString("fragments")))
            return config
        }

        private fun readCsv(value: String): List<String> {
            return value.split(",").map(String::trim).filter(String::isNotBlank)
        }
    }
}

object DestinySubclassConfigRegistry {
    private const val MOD_ID = "destiny2-mod"
    const val MAX_EQUIPPED_ASPECTS = 2

    private val SOLAR_WARLOCK = DestinySubclassConfigDefinition(
        subclass = DestinySubclassType.SOLAR_WARLOCK,
        abilityOptions = mapOf(
            AbilitySlot.GRENADE to listOf(option("$MOD_ID:solar_warlock_solar_grenade", "烈日手雷", "持续灼烧区域", icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_grenade.png")),
            AbilitySlot.MELEE to listOf(option("$MOD_ID:solar_warlock_incinerator_snap", "焚烧响指", "扇形烈焰投射物", icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_melee.png")),
            AbilitySlot.CLASS_ABILITY to listOf(option("$MOD_ID:solar_warlock_healing_rift", "治疗裂隙", "在脚下生成恢复区域", icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_class.png")),
            AbilitySlot.SUPER to listOf(option("$MOD_ID:solar_warlock_well_of_radiance", "光焰之井", "创造强力团队增益区域", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/well_of_radiance.png"))
        ),
        aspectOptions = listOf(
            option("$MOD_ID:aspect_heat_rises", "炙热升腾", "按住手雷2秒将其消耗，治疗附近友方并获得15秒炙热升腾；双击跳跃后按住空格可升空并悬停", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/solar_warlock/aspect_heat_rises.png"),
            option("$MOD_ID:aspect_touch_of_flame", "火焰之触", "强化烈日手雷表现", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/solar_warlock/aspect_touch_of_flame.png")
        ),
        fragmentOptions = listOf(
            option("$MOD_ID:fragment_ember_of_torches", "火炬余烬", "近战命中后获得焕光", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/fragment_ember_of_torches.png"),
            option("$MOD_ID:fragment_ember_of_singeing", "焦燃余烬", "灼烧目标时加快职业技能恢复", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/fragment_ember_of_singeing.png"),
            option("$MOD_ID:fragment_ember_of_solace", "抚慰余烬", "延长恢复和焕光持续时间", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/fragment_ember_of_solace.png"),
            option("$MOD_ID:fragment_ember_of_ashes", "骨灰余烬", "施加更多灼烧层数", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/fragment_ember_of_ashes.png")
        ),
        background = ResourceLocation.parse("$MOD_ID:textures/gui/subclass/solar_warlock/dawnblade_background.png")
    )

    private val VOID_HUNTER = DestinySubclassConfigDefinition(
        subclass = DestinySubclassType.VOID_HUNTER,
        abilityOptions = mapOf(
            AbilitySlot.GRENADE to listOf(option("$MOD_ID:void_hunter_void_grenade", "涡流手雷", "生成持续牵引并伤害敌人的虚空涡流", icon = "$MOD_ID:textures/gui/subclass/void_hunter/ability_void_grenade.png")),
            AbilitySlot.MELEE to listOf(option("$MOD_ID:void_hunter_snare_bomb", "陷阱炸弹", "投出烟雾陷阱，使敌人虚弱并失去方向", icon = "$MOD_ID:textures/gui/subclass/void_hunter/ability_snare_bomb.png")),
            AbilitySlot.CLASS_ABILITY to listOf(option("$MOD_ID:void_hunter_gambler_dodge", "赌徒闪身", "闪身规避攻击并恢复近战能力", icon = "$MOD_ID:textures/gui/subclass/void_hunter/ability_gamblers_dodge.png")),
            AbilitySlot.SUPER to listOf(option("$MOD_ID:void_hunter_shadowshot", "暗影箭矢：狩猎陷阱", "发射虚空锚点，牵引、压制并虚弱敌人", icon = "$MOD_ID:textures/gui/subclass/void_hunter/ability_shadowshot.png"))
        ),
        aspectOptions = listOf(
            option("$MOD_ID:aspect_on_the_prowl", "伺机而动", "进入隐身会标记优先目标；击败目标生成削弱烟云并为队伍恢复技能能量", fragmentSlots = 3, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_on_the_prowl.png"),
            option("$MOD_ID:aspect_trappers_ambush", "捕猎者的伏击", "消耗近战能量快速下坠，制造烟云并使附近友军隐身", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_trappers_ambush.png"),
            option("$MOD_ID:aspect_stylish_executioner", "潇洒行刑者", "击败受虚空减益影响的目标后获得隐身与真视", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_stylish_executioner.png"),
            option("$MOD_ID:aspect_vanishing_step", "隐身步法", "闪身结束后进入虚空隐身", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_vanishing_step.png")
        ),
        fragmentOptions = listOf(
            option("$MOD_ID:fragment_echo_of_exchange", "交换回声", "近战技能造成最后一击时获得手雷能量", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_exchange.png"),
            option("$MOD_ID:fragment_echo_of_cessation", "休止回声", "终结技产生虚空伤害爆发并使附近目标不稳定", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_cessation.png"),
            option("$MOD_ID:fragment_echo_of_leeching", "吸吮回声", "近战造成最后一击后开始恢复生命", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_leeching.png"),
            option("$MOD_ID:fragment_echo_of_persistence", "坚韧回声", "延长施加在你身上的虚空增益持续时间", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_persistence.png"),
            option("$MOD_ID:fragment_echo_of_instability", "失稳回声", "手雷造成最后一击后，虚空武器获得不稳定弹药", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_instability.png"),
            option("$MOD_ID:fragment_echo_of_undermining", "弱化回声", "虚空手雷会使目标虚弱", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_undermining.png"),
            option("$MOD_ID:fragment_echo_of_dilation", "扩张回声", "潜行时移动更快并获得增强雷达", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_dilation.png"),
            option("$MOD_ID:fragment_echo_of_reprisal", "报复回声", "被敌人包围时造成最后一击会获得超能能量", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_reprisal.png"),
            option("$MOD_ID:fragment_echo_of_harvest", "收割回声", "击败虚弱目标会生成能量球与虚空裂口", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_harvest.png"),
            option("$MOD_ID:fragment_echo_of_obscurity", "朦胧回声", "执行终结技后进入隐身", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_obscurity.png"),
            option("$MOD_ID:fragment_echo_of_remnants", "残存回声", "延长持续型虚空手雷的效果时间", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_remnants.png"),
            option("$MOD_ID:fragment_echo_of_provision", "补能回声", "手雷造成伤害时获得近战能量", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_provision.png"),
            option("$MOD_ID:fragment_echo_of_vigilance", "警惕回声", "护盾耗尽时击败目标会获得虚空覆盖护盾", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_vigilance.png"),
            option("$MOD_ID:fragment_echo_of_domineering", "霸道回声", "压制目标后提升机动性并装填当前武器", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_domineering.png"),
            option("$MOD_ID:fragment_echo_of_starvation", "饥饿回声", "拾取虚空裂口或能量球后获得吞噬", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_starvation.png"),
            option("$MOD_ID:fragment_echo_of_expulsion", "驱逐回声", "虚空技能造成最后一击会使目标爆炸", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_expulsion.png")
        ),
        background = ResourceLocation.parse("$MOD_ID:textures/gui/subclass/void_hunter/nightstalker_background.png")
    )

    private val ARC_TITAN = DestinySubclassConfigDefinition(
        subclass = DestinySubclassType.ARC_TITAN,
        abilityOptions = AbilitySlot.entries.associateWith { slot ->
            listOf(option("$MOD_ID:arc_titan_${slot.key}", "电弧泰坦 ${slot.key}", "占位原型技能"))
        },
        aspectOptions = listOf(
            option("$MOD_ID:aspect_arc_titan_placeholder_a", "电弧星象 A", "占位星象", fragmentSlots = 2),
            option("$MOD_ID:aspect_arc_titan_placeholder_b", "电弧星象 B", "占位星象", fragmentSlots = 2)
        ),
        fragmentOptions = placeholderFragments("arc_titan"),
        background = ResourceLocation.parse("$MOD_ID:textures/gui/loadout/subclass_config_bg.png")
    )

    private val definitions = listOf(SOLAR_WARLOCK, VOID_HUNTER, ARC_TITAN).associateBy { it.subclass }

    fun definitionFor(subclass: DestinySubclassType): DestinySubclassConfigDefinition {
        return definitions[subclass] ?: SOLAR_WARLOCK
    }

    fun defaultFor(subclass: DestinySubclassType): PlayerSubclassConfiguration {
        val definition = definitionFor(subclass)
        val abilities = definition.abilityOptions.mapValues { (_, options) -> options.firstOrNull()?.id.orEmpty() }
            .filterValues(String::isNotBlank)
            .toMutableMap()
        val aspects = definition.aspectOptions.take(MAX_EQUIPPED_ASPECTS).map { it.id }.toMutableList()
        val capacity = aspects.sumOf { aspectId ->
            definition.aspectOptions.firstOrNull { it.id == aspectId }?.fragmentSlots ?: 0
        }
        val fragments = definition.fragmentOptions.take(capacity).map { it.id }.toMutableList()
        return PlayerSubclassConfiguration(abilities, aspects, fragments)
    }

    fun installMissingDefaults(
        subclass: DestinySubclassType,
        config: PlayerSubclassConfiguration
    ): PlayerSubclassConfiguration {
        val definition = definitionFor(subclass)
        val migrated = normalize(subclass, config)
        definition.aspectOptions.asSequence()
            .map(DestinyConfigOption::id)
            .filterNot(migrated.selectedAspects::contains)
            .take((MAX_EQUIPPED_ASPECTS - migrated.selectedAspects.size).coerceAtLeast(0))
            .forEach(migrated.selectedAspects::add)

        val openFragmentSlots = (fragmentCapacity(subclass, migrated) - migrated.selectedFragments.size).coerceAtLeast(0)
        definition.fragmentOptions.asSequence()
            .map(DestinyConfigOption::id)
            .filterNot(migrated.selectedFragments::contains)
            .take(openFragmentSlots)
            .forEach(migrated.selectedFragments::add)
        return migrated
    }

    fun normalize(subclass: DestinySubclassType, config: PlayerSubclassConfiguration): PlayerSubclassConfiguration {
        val definition = definitionFor(subclass)
        val normalized = config.copy()
        AbilitySlot.entries.forEach { slot ->
            val validIds = definition.abilityOptions[slot].orEmpty().map { it.id }.toSet()
            val selected = normalized.selectedAbilities[slot]
            if (selected !in validIds) {
                definition.abilityOptions[slot]?.firstOrNull()?.let { normalized.selectedAbilities[slot] = it.id }
            }
        }
        normalized.selectedAspects.removeAll { aspectId -> definition.aspectOptions.none { it.id == aspectId } }
        while (normalized.selectedAspects.size > MAX_EQUIPPED_ASPECTS) {
            normalized.selectedAspects.removeLast()
        }
        normalized.selectedFragments.removeAll { fragmentId -> definition.fragmentOptions.none { it.id == fragmentId } }
        while (normalized.selectedFragments.size > fragmentCapacity(subclass, normalized)) {
            normalized.selectedFragments.removeLast()
        }
        return normalized
    }

    fun setAbility(subclass: DestinySubclassType, config: PlayerSubclassConfiguration, slot: AbilitySlot, abilityId: String): Boolean {
        val definition = definitionFor(subclass)
        if (definition.abilityOptions[slot].orEmpty().none { it.id == abilityId }) {
            return false
        }
        config.selectedAbilities[slot] = abilityId
        return true
    }

    fun toggleAspect(subclass: DestinySubclassType, config: PlayerSubclassConfiguration, aspectId: String): Boolean {
        val definition = definitionFor(subclass)
        if (definition.aspectOptions.none { it.id == aspectId }) {
            return false
        }
        if (config.selectedAspects.remove(aspectId)) {
            trimFragmentsToCapacity(subclass, config)
            return true
        }
        if (config.selectedAspects.size >= MAX_EQUIPPED_ASPECTS) {
            return false
        }
        config.selectedAspects.add(aspectId)
        return true
    }

    fun toggleFragment(subclass: DestinySubclassType, config: PlayerSubclassConfiguration, fragmentId: String): Boolean {
        val definition = definitionFor(subclass)
        if (definition.fragmentOptions.none { it.id == fragmentId }) {
            return false
        }
        if (config.selectedFragments.remove(fragmentId)) {
            return true
        }
        if (config.selectedFragments.size >= fragmentCapacity(subclass, config)) {
            return false
        }
        config.selectedFragments.add(fragmentId)
        return true
    }

    fun fragmentCapacity(subclass: DestinySubclassType, config: PlayerSubclassConfiguration): Int {
        val definition = definitionFor(subclass)
        return config.selectedAspects.sumOf { aspectId ->
            definition.aspectOptions.firstOrNull { it.id == aspectId }?.fragmentSlots ?: 0
        }.coerceAtLeast(0)
    }

    /** Maximum number of fragment widgets this subclass can need with a legal Aspect loadout. */
    fun maximumFragmentCapacity(definition: DestinySubclassConfigDefinition): Int {
        return definition.aspectOptions.asSequence()
            .map(DestinyConfigOption::fragmentSlots)
            .filter { it > 0 }
            .sortedDescending()
            .take(MAX_EQUIPPED_ASPECTS)
            .sum()
    }

    private fun trimFragmentsToCapacity(subclass: DestinySubclassType, config: PlayerSubclassConfiguration) {
        while (config.selectedFragments.size > fragmentCapacity(subclass, config)) {
            config.selectedFragments.removeLast()
        }
    }

    private fun option(
        id: String,
        title: String,
        description: String,
        fragmentSlots: Int = 0,
        icon: String? = null
    ): DestinyConfigOption {
        return DestinyConfigOption(id, title, description, fragmentSlots, icon?.let(ResourceLocation::parse))
    }

    private fun placeholderFragments(prefix: String): List<DestinyConfigOption> {
        return listOf(
            option("$MOD_ID:fragment_${prefix}_placeholder_1", "碎片 1", "占位碎片"),
            option("$MOD_ID:fragment_${prefix}_placeholder_2", "碎片 2", "占位碎片"),
            option("$MOD_ID:fragment_${prefix}_placeholder_3", "碎片 3", "占位碎片"),
            option("$MOD_ID:fragment_${prefix}_placeholder_4", "碎片 4", "占位碎片")
        )
    }
}
