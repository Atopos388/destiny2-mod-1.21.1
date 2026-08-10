package atopos.destiny2.common.player

import net.minecraft.resources.ResourceLocation

/**
 * Current Solar and Arc buildcraft catalog.
 *
 * Names, descriptions, icons, and Aspect slot counts are sourced from the
 * zh-chs Bungie Destiny manifest pulled on 2026-08-07. Runtime behavior is
 * implemented separately so the UI catalog cannot be mistaken for gameplay.
 */
object SolarArcSubclassCatalog {
    private const val MOD_ID = "destiny2-mod"
    private val SOLAR_ASPECT_IDS = setOf(
        "aspect_heat_rises",
        "aspect_touch_of_flame",
        "aspect_icarus_dash",
        "aspect_hellion"
    )

    val solarWarlockAspects = listOf(
        aspect(
            "aspect_heat_rises",
            "炙热升腾",
            "滑翔时可使用武器、近战和手雷。按住手雷键消耗手雷，获得炙热升腾并爆发治愈附近盟友；空中最后一击延长持续时间并回复近战能量。",
            2
        ),
        aspect(
            "aspect_touch_of_flame",
            "火焰之触",
            "强化治愈、烈日、烈焰和融合手雷。烈日手雷持续更久，并周期性向周围发射熔岩团块。",
            2
        ),
        aspect(
            "aspect_icarus_dash",
            "伊卡洛斯突进",
            "在空中快速闪身；炙热升腾激活时拥有额外闪身。空中用超能或武器快速击败目标可治愈自己。",
            3
        ),
        aspect(
            "aspect_hellion",
            "地狱火",
            "启动职业技能召唤烈日迫击炮，向远处目标发射火焰射弹并施加灼烧；伤害随手雷属性强化增益调整。",
            2
        )
    )

    val solarFragments = listOf(
        fragment("fragment_ember_of_benevolence", "仁慈余烬", "对盟友施加恢复、治愈或焕光后，短时间提升手雷、近战和职业技能回复。-10 手雷"),
        fragment("fragment_ember_of_beams", "光线余烬", "烈日超能弹道拥有更强的目标捕获能力。+10 超能"),
        fragment("fragment_ember_of_resolve", "决心余烬", "烈日手雷最后一击可获得治愈。"),
        fragment("fragment_ember_of_eruption", "喷发余烬", "烈日点燃的作用范围提升。+10 近战"),
        fragment("fragment_ember_of_tempering", "回火余烬", "烈日武器最后一击让你和盟友短时间提高生命值，最多叠加三层；生效时提高武器空中效率，并可由烈日武器最后一击生成焰灵。-10 职业"),
        fragment("fragment_ember_of_wonder", "惊奇余烬", "通过烈日点燃快速消灭多个目标会生成能量球。+10 生命值"),
        fragment("fragment_ember_of_mercy", "慈悲余烬", "复活盟友时，你和附近盟友获得恢复；拾取焰灵也会赋予恢复。+10 生命值"),
        fragment("fragment_ember_of_solace", "抚慰余烬", "施加在自身的焕光和恢复拥有更长持续时间。"),
        fragment("fragment_ember_of_torches", "火炬余烬", "对战斗人员进行充能近战攻击会让你和附近友军获得焕光。-10 手雷"),
        fragment("fragment_ember_of_searing", "炽热余烬", "击败灼烧目标可获得近战能量并生成焰灵。+10 职业"),
        fragment("fragment_ember_of_char", "烧焦余烬", "烈日点燃会把灼烧传播给受影响的目标。+10 手雷"),
        fragment("fragment_ember_of_singeing", "焦燃余烬", "灼烧目标时，职业技能充能速度加快。"),
        fragment("fragment_ember_of_combustion", "燃烧余烬", "烈日超能最后一击会使目标点燃并生成焰灵。+10 近战"),
        fragment("fragment_ember_of_empyrean", "至高天余烬", "烈日武器或技能最后一击会延长自身恢复和焕光的持续时间。-10 生命值"),
        fragment("fragment_ember_of_blistering", "起泡余烬", "烈日点燃消灭目标可获得手雷能量。"),
        fragment("fragment_ember_of_ashes", "骨灰余烬", "对目标施加更多灼烧层数。")
    )

    val arcTitanAspects = listOf(
        aspect(
            "aspect_juggernaut",
            "无畏护甲",
            "职业技能能量已满并短时间冲刺后获得正面护盾；护盾打破时耗尽职业技能能量，受击时获得电光充能。增幅状态显著强化护盾。",
            2,
            "aspect_juggernaut"
        ),
        aspect(
            "aspect_touch_of_thunder",
            "暴雷之触",
            "强化闪光、脉冲、闪电和风暴手雷。脉冲手雷会周期性生成离子轨迹并随持续时间提高伤害。",
            2,
            "aspect_touch_of_thunder"
        ),
        aspect(
            "aspect_knockout",
            "重击",
            "重伤目标或打破护盾后，短时间赋予近战电弧能量并提高范围和伤害；近战最后一击使你增幅并恢复生命。",
            2,
            "aspect_knockout"
        ),
        aspect(
            "aspect_storms_keep",
            "风暴要塞",
            "施放职业技能，为你和附近盟友提供电光充能。位于屏障后方时持续获得层数，满层后武器伤害也可释放电光充能。",
            2,
            "aspect_storms_keep"
        )
    )

    val arcFragments = listOf(
        fragment("fragment_spark_of_focus", "专注火花", "冲刺后短时间提高职业技能回复。-10 职业，-10 武器，-10 生命值", "arc_titan"),
        fragment("fragment_spark_of_volts", "伏特火花", "终结技使你进入增幅状态，并提供一层电光充能。+10 职业", "arc_titan"),
        fragment("fragment_spark_of_beacons", "信标火花", "增幅时，电弧特殊或重型弹药武器最后一击会造成致盲爆炸。", "arc_titan"),
        fragment("fragment_spark_of_recharge", "充能火花", "身受重伤时，近战和手雷能量回复速度加快。", "arc_titan"),
        fragment("fragment_spark_of_brilliance", "光辉火花", "用精准伤害击败被致盲的目标会造成致盲爆炸。+10 超能", "arc_titan"),
        fragment("fragment_spark_of_momentum", "动量火花", "滑行经过弹药盒会填装当前武器，并提供少量电光充能。", "arc_titan"),
        fragment("fragment_spark_of_feedback", "反馈火花", "承受近战伤害后，短时间增加造成的近战伤害。+10 生命值", "arc_titan"),
        fragment("fragment_spark_of_amplitude", "增幅火花", "增幅状态下快速击败目标会生成能量球。", "arc_titan"),
        fragment("fragment_spark_of_haste", "急速火花", "冲刺时生命值属性大幅提升。", "arc_titan"),
        fragment("fragment_spark_of_resistance", "抗性火花", "被战斗人员包围时提高伤害抗性。+10 近战", "arc_titan"),
        fragment("fragment_spark_of_discharge", "放电火花", "电弧武器最后一击有几率生成离子轨迹；收集离子轨迹会提供一层电光充能。-10 近战", "arc_titan"),
        fragment("fragment_spark_of_instinct", "直觉火花", "身受重伤时受到伤害会爆发电弧能量，伤害并震颤附近目标。", "arc_titan"),
        fragment("fragment_spark_of_ions", "离子火花", "击败被震颤或带有电光充能的目标会生成离子轨迹。", "arc_titan"),
        fragment("fragment_spark_of_magnitude", "量级火花", "延长闪电、脉冲和风暴手雷的持续时间。", "arc_titan"),
        fragment("fragment_spark_of_shock", "震颤火花", "电弧手雷会震颤目标。-10 手雷", "arc_titan"),
        fragment("fragment_spark_of_frequency", "频率火花", "近战命中后短时间提高当前武器的填装速度和稳定性；增幅时效果增强，并从所有来源获得更多电光充能。", "arc_titan")
    )

    private fun aspect(
        id: String,
        title: String,
        description: String,
        fragmentSlots: Int,
        iconId: String = id
    ): DestinyConfigOption {
        val folder = if (id in SOLAR_ASPECT_IDS) "solar_warlock" else "arc_titan"
        return DestinyConfigOption(
            id = "$MOD_ID:$id",
            title = title,
            description = description,
            fragmentSlots = fragmentSlots,
            icon = ResourceLocation.parse("$MOD_ID:textures/gui/subclass/$folder/$iconId.png")
        )
    }

    private fun fragment(
        id: String,
        title: String,
        description: String,
        folder: String = "solar_warlock"
    ) = DestinyConfigOption(
        id = "$MOD_ID:$id",
        title = title,
        description = description,
        icon = ResourceLocation.parse("$MOD_ID:textures/gui/subclass/$folder/$id.png")
    )

}
