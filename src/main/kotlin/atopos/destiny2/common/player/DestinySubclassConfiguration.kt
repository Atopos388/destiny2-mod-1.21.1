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
    val movementOptions: List<DestinyConfigOption> = emptyList(),
    val background: ResourceLocation? = null
)

data class PlayerSubclassConfiguration(
    val selectedAbilities: MutableMap<AbilitySlot, String> = mutableMapOf(),
    val selectedAspects: MutableList<String> = mutableListOf(),
    val selectedFragments: MutableList<String> = mutableListOf(),
    var selectedMovementId: String = ""
) {
    fun copy(): PlayerSubclassConfiguration {
        return PlayerSubclassConfiguration(
            selectedAbilities = selectedAbilities.toMutableMap(),
            selectedAspects = selectedAspects.toMutableList(),
            selectedFragments = selectedFragments.toMutableList(),
            selectedMovementId = selectedMovementId
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
        tag.putString("movement", selectedMovementId)
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
            if (tag.contains("movement")) {
                config.selectedMovementId = tag.getString("movement")
            }
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
            AbilitySlot.GRENADE to listOf(
                option(
                    "$MOD_ID:solar_warlock_solar_grenade",
                    "烈日手雷",
                    "生成持续伤害并灼烧敌人的烈日区域。",
                    icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_grenade.png"
                ),
                option(
                    "$MOD_ID:solar_warlock_healing_grenade",
                    "治愈手雷",
                    "命中时治愈盟友，并生成可拾取后获得恢复的善意烈日光球。",
                    icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_grenade.png"
                ),
                option(
                    "$MOD_ID:solar_warlock_firebolt_grenade",
                    "火焰弹手雷",
                    "向附近目标释放造成烈日伤害和轻度灼烧的火焰弹。",
                    icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_grenade.png"
                ),
                option(
                    "$MOD_ID:solar_warlock_fusion_grenade",
                    "融合手雷",
                    "附着在目标或地形上，延迟爆炸并施加中度灼烧。",
                    icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_grenade.png"
                )
            ),
            AbilitySlot.MELEE to listOf(option("$MOD_ID:solar_warlock_incinerator_snap", "焚烧响指", "扇形烈焰投射物", icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_melee.png")),
            AbilitySlot.CLASS_ABILITY to listOf(option("$MOD_ID:solar_warlock_healing_rift", "治疗裂隙", "在脚下生成恢复区域", icon = "$MOD_ID:textures/gui/hud/solar_warlock_skill_class.png")),
            AbilitySlot.SUPER to listOf(
                option("$MOD_ID:solar_warlock_well_of_radiance", "光焰之井", "创造强力团队增益区域", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/well_of_radiance.png"),
                option("$MOD_ID:solar_warlock_daybreak", "破晓", "进入漫游超能，攻击发射爆炸烈日剑；光线余烬强化目标捕获。", icon = "$MOD_ID:textures/gui/subclass/solar_warlock/well_of_radiance.png")
            )
        ),
        aspectOptions = SolarArcSubclassCatalog.solarWarlockAspects,
        fragmentOptions = SolarArcSubclassCatalog.solarFragments,
        movementOptions = listOf(
            option(
                GuardianJumpRules.WARLOCK_VECTOR_GLIDE_ID,
                "矢量滑翔",
                "快速按两次跳跃并持续按住以启动滑翔；松开会取消，再次按住可缓降。",
                icon = "$MOD_ID:textures/gui/subclass/solar_warlock.png"
            )
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
            option("$MOD_ID:aspect_on_the_prowl", "伺机而动", "周期性将附近的一名战斗人员标记为你和你队伍的首要目标。进入隐形状态会立即标记一个目标。击败首要目标会生成具有削弱效果的烟雾云，你和盟友穿过它时会获得隐身状态。当首要目标被击败时，你和附近的盟友会在短时间内提升武器填装速度和稳定性，并获得手雷、近战和职业技能能量。", fragmentSlots = 3, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_on_the_prowl.png"),
            option("$MOD_ID:aspect_trappers_ambush", "捕猎者的伏击", "激活空中移动来消耗你的职业技能能量并朝地面俯冲，撞击目标时造成伤害。若你拥有任意虚空状态效果，撞击伤害将大幅提高。使用此技能伤害对手会治疗你。使用此技能击败战斗人员会提供吞食。当你的职业属性达到100或更高时，此技能伤害会根据职业属性而增加。", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_trappers_ambush.png"),
            option("$MOD_ID:aspect_stylish_executioner", "潇洒行刑者", "击败虚弱的、被压制的、或不稳定的目标可获得隐身和真视。在完成一次潇洒处刑后，你下一次的隐身近战攻击会使目标虚弱。", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_stylish_executioner.png"),
            option("$MOD_ID:aspect_vanishing_step", "隐身步法", "闪身可使你获得隐形效果。", fragmentSlots = 2, icon = "$MOD_ID:textures/gui/subclass/void_hunter/aspect_vanishing_step.png")
        ),
        fragmentOptions = listOf(
            option("$MOD_ID:fragment_echo_of_exchange", "交换回声", "近战最后一击将赋予手雷能量。+10 近战", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_exchange.png"),
            option("$MOD_ID:fragment_echo_of_cessation", "休止回声", "终结技最后一击会制造一次虚空伤害爆发，使附近战斗人员获得不稳定效果。击败不稳定目标会制造一个虚空裂口。", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_cessation.png"),
            option("$MOD_ID:fragment_echo_of_leeching", "吸吮回声", "近战最后一击会为你和你附近的友军带来生命恢复效果。+10 生命值", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_leeching.png"),
            option("$MOD_ID:fragment_echo_of_persistence", "坚韧回声", "你获得的虚空增益（隐身，覆盖护盾，吞食）的持续时间延长。-10 职业，-10 武器，-10 生命值", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_persistence.png"),
            option("$MOD_ID:fragment_echo_of_instability", "失稳回声", "使用手雷击败目标可给你的虚空武器装上不稳定弹药。+10 近战", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_instability.png"),
            option("$MOD_ID:fragment_echo_of_undermining", "弱化回声", "你的虚空手雷会削弱目标。-10 手雷", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_undermining.png"),
            option("$MOD_ID:fragment_echo_of_dilation", "扩张回声", "蹲下时，你的潜行速度更快并获得增强雷达分辨率。+10 武器，+10 超能", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_dilation.png"),
            option("$MOD_ID:fragment_echo_of_reprisal", "报复回声", "被战斗人员包围时的最后一击将赋予超能能量。", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_reprisal.png"),
            option("$MOD_ID:fragment_echo_of_harvest", "收割回声", "击败虚弱的目标会生成一个能量球以及一个虚空裂口。", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_harvest.png"),
            option("$MOD_ID:fragment_echo_of_obscurity", "朦胧回声", "终结技最后一击将赋予隐身。+10 职业", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_obscurity.png"),
            option("$MOD_ID:fragment_echo_of_remnants", "残存回声", "来自你的手雷的残留效果（涡流手雷，虚空墙壁，虚空尖刺，量子光束）的持续时间延长。", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_remnants.png"),
            option("$MOD_ID:fragment_echo_of_provision", "补能回声", "使用手雷对目标造成伤害可回复近战能量。+10 手雷", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_provision.png"),
            option("$MOD_ID:fragment_echo_of_vigilance", "警惕回声", "护盾耗尽时击败一个目标，就会获得一个临时的虚空覆盖护盾。", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_vigilance.png"),
            option("$MOD_ID:fragment_echo_of_domineering", "霸道回声", "压制目标后，你的武器属性会在短时间内提升，并用储存弹药填装当前装备的武器。击败被压制的目标会生成一个虚空裂口。+10 手雷", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_domineering.png"),
            option("$MOD_ID:fragment_echo_of_starvation", "饥饿回声", "拾取虚空裂口或能量球会赋予吞食。-10 职业", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_starvation.png"),
            option("$MOD_ID:fragment_echo_of_expulsion", "驱逐回声", "虚空技能最后一击将造成目标爆炸。+10 超能", icon = "$MOD_ID:textures/gui/subclass/void_hunter/fragment_echo_of_expulsion.png")
        ),
        movementOptions = listOf(
            option(
                GuardianJumpRules.HUNTER_TRIPLE_JUMP_ID,
                "三级跳",
                "在空中追加两次独立跳跃；每一段都能续高并适度修正方向。",
                icon = "$MOD_ID:textures/gui/subclass/void_hunter.png"
            )
        ),
        background = ResourceLocation.parse("$MOD_ID:textures/gui/subclass/void_hunter/nightstalker_background.png")
    )

    private val ARC_TITAN = DestinySubclassConfigDefinition(
        subclass = DestinySubclassType.ARC_TITAN,
        abilityOptions = mapOf(
            AbilitySlot.GRENADE to listOf(
                option(
                    "$MOD_ID:arc_titan_pulse_grenade",
                    "脉冲手雷",
                    "投出一枚周期性释放电弧脉冲的手雷。"
                ),
                option(
                    "$MOD_ID:arc_titan_flashbang_grenade",
                    "闪光手雷",
                    "弹跳后爆炸，对范围内战斗人员造成伤害并施加致盲。"
                ),
                option(
                    "$MOD_ID:arc_titan_lightning_grenade",
                    "闪电手雷",
                    "附着表面后连续释放电弧脉冲。"
                ),
                option(
                    "$MOD_ID:arc_titan_storm_grenade",
                    "风暴手雷",
                    "生成周期落雷的电弧风暴；暴雷之触使雷云追踪敌人。"
                )
            ),
            AbilitySlot.MELEE to listOf(
                option(
                    "$MOD_ID:arc_titan_thunderclap",
                    "雷霆一击",
                    "按住近战键原地蓄力，松开后向前释放电弧重击；蓄力越久，伤害与范围越高。"
                )
            ),
            AbilitySlot.CLASS_ABILITY to listOf(
                option(
                    "$MOD_ID:arc_titan_thruster",
                    "推进器",
                    "沿当前移动方向快速闪避；允许在空中使用。"
                ),
                option(
                    "$MOD_ID:arc_titan_barricade",
                    "屏障",
                    "在前方部署一面可阻挡敌方投射物的屏障；风暴要塞会强化屏障后的电光充能。"
                )
            ),
            AbilitySlot.SUPER to listOf(
                option(
                    "$MOD_ID:arc_titan_thundercrash",
                    "雷霆冲击",
                    "化作电弧导弹高速飞行，碰撞目标或地形时引发大范围爆炸。"
                )
            )
        ),
        aspectOptions = SolarArcSubclassCatalog.arcTitanAspects,
        fragmentOptions = SolarArcSubclassCatalog.arcFragments,
        movementOptions = listOf(
            option(
                GuardianJumpRules.TITAN_LIFT_ID,
                "升空",
                "在空中持续按住跳跃，以短促推力维持升空。",
                icon = "$MOD_ID:textures/gui/subclass/arc_titan.png"
            )
        ),
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
        return PlayerSubclassConfiguration(
            selectedAbilities = abilities,
            selectedAspects = aspects,
            selectedFragments = fragments,
            selectedMovementId = definition.movementOptions.firstOrNull()?.id.orEmpty()
        )
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
        if (definition.movementOptions.none { it.id == normalized.selectedMovementId }) {
            normalized.selectedMovementId = definition.movementOptions.firstOrNull()?.id.orEmpty()
        }
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

    fun setMovement(
        subclass: DestinySubclassType,
        config: PlayerSubclassConfiguration,
        movementId: String
    ): Boolean {
        val definition = definitionFor(subclass)
        if (definition.movementOptions.none { it.id == movementId }) {
            return false
        }
        config.selectedMovementId = movementId
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
