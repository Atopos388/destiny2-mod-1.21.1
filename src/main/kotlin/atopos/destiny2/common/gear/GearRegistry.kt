package atopos.destiny2.common.gear

import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.weapon.DestinyAmmoType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object GearRegistry {
    private val definitions = linkedMapOf<ResourceLocation, GearDefinition>()
    private val definitionsByItem = linkedMapOf<Item, GearDefinition>()
    private val perks = linkedMapOf<ResourceLocation, GearPerk>()

    val MICRO_MISSILE_BURST = WeaponFrameDefinition(
        id = id("micro_missile_burst"),
        displayName = "三连微型导弹框架",
        description = "一次开火发射 3 枚微型导弹。",
        burstCount = 3,
        burstIntervalTicks = 4,
        projectileSpeed = 1.5f,
        cooldownTicks = 20,
        magazineSize = 3,
        reloadTicks = 36
    )

    val FORGOTTEN_NAME_FRAME = WeaponFrameDefinition(
        id = id("forgotten_name_exotic"),
        displayName = "异域手炮框架",
        description = "被遗忘之名的专属异域框架。",
        projectileSpeed = 6.0f,
        cooldownTicks = 15,
        magazineSize = 8,
        reloadTicks = 32
    )

    val IZANAGIS_BURDEN_FRAME = WeaponFrameDefinition(
        id = id("izanagis_burden_exotic"),
        displayName = "异域狙击步枪框架",
        description = "伊邪那岐的重担专属框架：90 RPM，4 发弹匣。",
        projectileSpeed = 10.0f,
        cooldownTicks = 14,
        magazineSize = 4,
        reloadTicks = 64
    )

    private val VANILLA_MELEE = WeaponFrameDefinition(id("vanilla_melee"), "原版近战框架", "保留原版剑、斧攻击节奏。")
    private val VANILLA_BOW = WeaponFrameDefinition(id("vanilla_bow"), "原版弓框架", "保留原版弓射击节奏。")
    private val VANILLA_CROSSBOW = WeaponFrameDefinition(id("vanilla_crossbow"), "原版弩框架", "保留原版弩射击节奏。")
    private val ARMOR_STAT_SHELL = WeaponFrameDefinition(id("armor_stat_shell"), "护甲属性外壳", "提供 roll 显示与基础属性词条。")

    fun register() {
        if (definitions.isNotEmpty()) {
            return
        }

        val rifledBarrel = perk("rifled_barrel", "精密膛线", "导弹弹速提高 10%。", projectileSpeedMultiplier = 1.10f, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val compensator = perk("chambered_compensator", "膛室补偿器", "爆炸范围提高 5%。", explosionRadiusMultiplier = 1.05f, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val stableLauncher = perk("stable_launcher", "稳定发射器", "导弹散布降低 10%。", projectileInaccuracyMultiplier = 0.90f, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val precisionPayload = perk("precision_payload", "精准弹头", "直接命中固定伤害提高 12.5%，不放大范围爆炸。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.PRECISION_BARREL)
        val highExplosiveBarrel = perk("high_explosive_barrel", "高爆枪管", "爆炸击杀会向附近目标附加 30 层灼烧。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.HIGH_EXPLOSIVE_BARREL)
        val trackingPayload = perk("tracking_payload", "追踪弹头", "导弹飞行时自动追踪 12 格内最近敌人。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.STABLE_LAUNCHER)
        val extendedMagazine = perk("extended_magazine", "扩容弹匣", "弹匣开火次数 +1。", magazineSizeBonus = 1, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val fastMagazine = perk("fast_magazine", "快速弹匣", "装填时间 ×0.90。", reloadTimeMultiplier = 0.90f, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val impactMagazine = perk("impact_magazine", "冲击弹匣", "直接命中伤害提高 10%。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.IMPACT_CASING)
        val reserveMagazine = perk("reserve_magazine", "备弹弹匣", "弹匣开火次数 +1。", magazineSizeBonus = 1, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val vorpal = perk("vorpal_rounds", "斩首弹药", "特殊弹伤害提高 15%；重弹伤害提高 10%。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.VORPAL_WEAPON)
        val fieldPrep = perk("field_prep", "战场准备", "蹲伏时装填时间 ×0.85，并提高备弹效率。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.FIELD_PREP)
        val velocity = perk("volatile_launch", "高压发射", "弹丸速度提高 10%。", projectileSpeedMultiplier = 1.10f, scopes = setOf(GearPerkScope.MICRO_MISSILE))
        val refund = perk("ambitious_supply", "野心补给", "击杀后下次装填可溢出，最多为基础弹匣的 1.5 倍。", scopes = setOf(GearPerkScope.MICRO_MISSILE), effect = GearPerkEffect.AMBITIOUS_ASSASSIN)

        val incompleteArchive = perk(
            "incomplete_archive",
            "残缺档案",
            """
            武器会记录被你精准击中的敌人。
            每次精准命中：获得 1 层【残响】
            最大：6 层　持续：1.25 秒（精准命中刷新）
            【残响】每层：
            约 +4.17% 精准伤害
            +5 辅助瞄准
            满层时装填动画 ×0.85
            你的准星会逐渐锁定目标弱点。
            """.trimIndent(),
            scopes = setOf(GearPerkScope.FORGOTTEN_NAME)
        )
        val eraseTheName = perk(
            "erase_the_name",
            "抹除姓名",
            """
            当拥有 6 层【残响】：
            下一次精准击杀触发【无名】。
            目标死亡后留下一个【遗忘】，周围敌人被标记 8 秒。
            对被标记目标造成额外 15% 伤害。
            如果连续击杀，标记不会消失。
            最多传播 5 个目标。
            """.trimIndent(),
            scopes = setOf(GearPerkScope.FORGOTTEN_NAME)
        )
        val forgottenNameCatalyst = perk(
            "forgotten_name_catalyst",
            "遗忘催化原型",
            "催化效果尚未配置。当前条目用于验证催化剂的安装、保存与卸载流程。",
            scopes = setOf(GearPerkScope.FORGOTTEN_NAME)
        )
        val honedEdge = perk(
            "izanagi_honed_edge",
            "精磨利刃",
            "按 B 使用备用武器操作：消耗当前弹匣，将 2—4 发子弹压缩为一发具有额外射程和等比例伤害的强化弹。",
            scopes = setOf(GearPerkScope.IZANAGIS_BURDEN)
        )
        val noDistractions = perk(
            "izanagi_no_distractions",
            "心无旁骛",
            "持续瞄准片刻后降低受到攻击时的准星扰动。",
            scopes = setOf(GearPerkScope.IZANAGIS_BURDEN)
        )

        registerDefinition(
            item = DestinyItems.FORGOTTEN_NAME,
            rarity = GearRarity.EXOTIC,
            category = GearCategory.WEAPON,
            frame = FORGOTTEN_NAME_FRAME,
            baseDamage = 10.0f,
            ammoItem = DestinyItems.PRIMARY_AMMO,
            ammoType = DestinyAmmoType.PRIMARY,
            precisionMultiplier = 1.7f,
            fixedPerks = listOf(incompleteArchive, eraseTheName),
            rollColumnLabels = listOf("异域内在", "异域特性"),
            hasCatalystSlot = true,
            catalysts = listOf(forgottenNameCatalyst)
        )

        registerDefinition(
            item = DestinyItems.IZANAGIS_BURDEN,
            rarity = GearRarity.EXOTIC,
            category = GearCategory.WEAPON,
            frame = IZANAGIS_BURDEN_FRAME,
            baseDamage = 20.0f,
            ammoItem = DestinyItems.SPECIAL_AMMO,
            ammoType = DestinyAmmoType.SPECIAL,
            precisionMultiplier = 1.5f,
            fixedPerks = listOf(honedEdge, noDistractions),
            rollColumnLabels = listOf("异域内在", "固有特性")
        )

        registerDefinition(
            item = DestinyItems.PERFECT_RETROGRADE,
            rarity = GearRarity.LEGENDARY,
            category = GearCategory.WEAPON,
            frame = MICRO_MISSILE_BURST,
            baseDamage = 6.0f,
            explosionRadius = 1.5f,
            ammoItem = DestinyItems.HEAVY_AMMO,
            ammoType = DestinyAmmoType.HEAVY,
            perkColumns = listOf(
                listOf(rifledBarrel, compensator, stableLauncher),
                listOf(extendedMagazine, fastMagazine, impactMagazine),
                listOf(precisionPayload, highExplosiveBarrel, trackingPayload, vorpal),
                listOf(fieldPrep, refund, reserveMagazine)
            ),
            rollColumnLabels = listOf("枪管", "弹夹", "Perk 1", "Perk 2")
        )

        registerDefinition(
            item = DestinyItems.MICRO_MISSILE_TEST,
            rarity = GearRarity.LEGENDARY,
            category = GearCategory.WEAPON,
            frame = MICRO_MISSILE_BURST,
            baseDamage = 4.5f,
            explosionRadius = 1.25f,
            ammoItem = DestinyItems.SPECIAL_AMMO,
            ammoType = DestinyAmmoType.SPECIAL,
            perkColumns = listOf(
                listOf(rifledBarrel, compensator, stableLauncher),
                listOf(extendedMagazine, fastMagazine, impactMagazine),
                listOf(precisionPayload, highExplosiveBarrel, trackingPayload, vorpal),
                listOf(fieldPrep, refund, reserveMagazine)
            ),
            rollColumnLabels = listOf("枪管", "弹夹", "Perk 1", "Perk 2")
        )

        registerVanillaWeapons()
        registerVanillaArmor()
    }

    fun definitionFor(stack: ItemStack): GearDefinition? = definitionsByItem[stack.item]

    fun definition(id: ResourceLocation): GearDefinition? = definitions[id]

    fun allDefinitions(): List<GearDefinition> = definitions.values.toList()

    fun perk(id: ResourceLocation): GearPerk? = perks[id]

    fun allPerks(): List<GearPerk> = perks.values.toList()

    private fun registerVanillaWeapons() {
        val executionersEdge = perk(
            "executioners_edge",
            "处刑刃",
            "攻击低生命目标时触发处刑判定；击杀后短暂提高移动和攻击节奏。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT, GearPerkTrigger.MELEE_KILL),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.EXECUTIONERS_EDGE,
            durationTicks = 100,
            internalCooldownTicks = 60
        )
        val guardCounter = perk(
            "guard_counter",
            "守势反击",
            "受到伤害后的 2 秒内近战命中会反击并击退目标。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.GUARD_COUNTER,
            durationTicks = 40,
            internalCooldownTicks = 80
        )
        val emberCarve = perk(
            "ember_carve",
            "余烬刻痕",
            "连续命中同一目标 3 次后迸发火花，伤害附近敌人。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.EMBER_CARVE,
            maxStacks = 3,
            internalCooldownTicks = 40
        )
        val momentumCleave = perk(
            "momentum_cleave",
            "动量顺劈",
            "冲刺后的首次近战变为小范围横扫。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.MOMENTUM_CLEAVE,
            internalCooldownTicks = 100
        )
        val duelistPulse = perk(
            "duelist_pulse",
            "决斗脉冲",
            "周围只有一个敌人时，命中后短暂获得减伤。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.DUELIST_PULSE,
            durationTicks = 60,
            internalCooldownTicks = 80
        )
        val bloodlessFinish = perk(
            "bloodless_finish",
            "无血终结",
            "近战击杀后治疗少量生命。",
            triggers = setOf(GearPerkTrigger.MELEE_KILL),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.BLOODLESS_FINISH,
            internalCooldownTicks = 120
        )
        val chainStep = perk(
            "chain_step",
            "连步",
            "近战击杀后，下一段追击更轻快。",
            triggers = setOf(GearPerkTrigger.MELEE_KILL),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.CHAIN_STEP,
            durationTicks = 80
        )
        val heavyReversal = perk(
            "heavy_reversal",
            "重击逆转",
            "低血量近战命中会强击退目标，并短暂降低所受伤害。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.HEAVY_REVERSAL,
            durationTicks = 30,
            internalCooldownTicks = 300
        )
        val panicGuard = perk(
            "panic_guard",
            "应急护身",
            "低血量受到攻击后，下一次近战命中强力击退敌人。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.PANIC_GUARD,
            durationTicks = 60,
            internalCooldownTicks = 180
        )
        val boneRhythm = perk(
            "bone_rhythm",
            "裂骨节奏",
            "连续命中 3 个不同敌人后，在受击面释放金色冲击环与火花震荡。",
            triggers = setOf(GearPerkTrigger.MELEE_HIT),
            scopes = setOf(GearPerkScope.MELEE),
            effect = GearPerkEffect.BONE_RHYTHM,
            maxStacks = 3,
            internalCooldownTicks = 100
        )
        val fireworkFinisher = perk(
            "firework_finisher",
            "烟花终结",
            "武器击杀时播放小型烟花粒子，并轻微伤害附近敌人。",
            triggers = setOf(GearPerkTrigger.MELEE_KILL, GearPerkTrigger.BOW_KILL),
            scopes = setOf(GearPerkScope.MELEE, GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.FIREWORK_FINISHER,
            internalCooldownTicks = 60
        )
        val featherStep = perk(
            "feather_step",
            "羽步",
            "击杀后短暂降低坠落伤害并提高移动速度。",
            triggers = setOf(GearPerkTrigger.MELEE_KILL, GearPerkTrigger.BOW_KILL),
            scopes = setOf(GearPerkScope.MELEE, GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.FEATHER_STEP,
            durationTicks = 100,
            internalCooldownTicks = 80
        )

        val echoArrow = perk(
            "echo_arrow",
            "回声箭",
            "满蓄力命中后生成一枚低伤害追踪回声箭。",
            triggers = setOf(GearPerkTrigger.BOW_HIT, GearPerkTrigger.CHARGE_RELEASE),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.ECHO_ARROW,
            internalCooldownTicks = 80
        )
        val pinningShot = perk(
            "pinning_shot",
            "钉身射击",
            "命中非满血目标时短暂减速。",
            triggers = setOf(GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.PINNING_SHOT,
            durationTicks = 60,
            internalCooldownTicks = 40
        )
        val perfectDraw = perk(
            "perfect_draw",
            "完美拉弓",
            "满蓄力释放后，下一次远程命中更快更狠。",
            triggers = setOf(GearPerkTrigger.CHARGE_RELEASE, GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW),
            effect = GearPerkEffect.PERFECT_DRAW,
            durationTicks = 80,
            internalCooldownTicks = 80
        )
        val splitString = perk(
            "split_string",
            "裂弦",
            "击杀后释放 3 枚弱化裂片攻击附近敌人。",
            triggers = setOf(GearPerkTrigger.BOW_KILL),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.SPLIT_STRING,
            internalCooldownTicks = 100
        )
        val gravityArc = perk(
            "gravity_arc",
            "重力弧线",
            "箭矢飞行越久，命中冲击越强；近距离无收益。",
            triggers = setOf(GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.GRAVITY_ARC
        )
        val markedQuiver = perk(
            "marked_quiver",
            "标记箭袋",
            "首次命中标记目标，短时间内下一次命中触发额外伤害。",
            triggers = setOf(GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.MARKED_QUIVER,
            durationTicks = 100
        )
        val ricochetFletching = perk(
            "ricochet_fletching",
            "跳弹尾羽",
            "箭矢打到方块时有一次反弹机会，反弹后伤害降低。",
            triggers = setOf(GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.RICOCHET_FLETCHING,
            internalCooldownTicks = 40
        )
        val calmRelease = perk(
            "calm_release",
            "静心释放",
            "站立不动拉满弓后，获得短暂精准增益和粒子提示。",
            triggers = setOf(GearPerkTrigger.CHARGE_RELEASE, GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW),
            effect = GearPerkEffect.CALM_RELEASE,
            durationTicks = 60,
            internalCooldownTicks = 60
        )
        val luckyRebound = perk(
            "lucky_rebound",
            "幸运回响",
            "弓弩命中后低概率返还弹药。",
            triggers = setOf(GearPerkTrigger.BOW_HIT),
            scopes = setOf(GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.LUCKY_REBOUND,
            triggerChance = 0.16f
        )
        val solarPopcorn = perk(
            "solar_popcorn",
            "日炎爆米花",
            "连续击杀小怪后触发小范围火花连锁。",
            triggers = setOf(GearPerkTrigger.MELEE_KILL, GearPerkTrigger.BOW_KILL),
            scopes = setOf(GearPerkScope.MELEE, GearPerkScope.BOW, GearPerkScope.CROSSBOW),
            effect = GearPerkEffect.SOLAR_POPCORN,
            internalCooldownTicks = 120
        )
        val arrowSplit = perk("arrow_split", "箭裂", "箭矢命中后从目标位置 360° 分裂出 4 根箭矢，每根造成原伤害的 45%。", triggers = setOf(GearPerkTrigger.BOW_HIT), scopes = setOf(GearPerkScope.BOW), effect = GearPerkEffect.ARROW_SPLIT, internalCooldownTicks = 80)
        val arcFlash = perk("arc_flash", "闪击", "精准命中头部时获得 6 秒增幅。", triggers = setOf(GearPerkTrigger.BOW_HIT), scopes = setOf(GearPerkScope.BOW), effect = GearPerkEffect.ARC_FLASH, durationTicks = 120, internalCooldownTicks = 100)
        val precisionBombardment = perk("precision_bombardment", "精准投弹", "精准命中头部时高抛发射一枚微型导弹。", triggers = setOf(GearPerkTrigger.BOW_HIT), scopes = setOf(GearPerkScope.BOW), effect = GearPerkEffect.PRECISION_BOMBARDMENT, internalCooldownTicks = 100)
        val slaughterOverture = perk("slaughter_overture", "屠戮序曲", "在 2 秒内连续命中 3 次后恢复 5% 最大生命。", triggers = setOf(GearPerkTrigger.MELEE_HIT), scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.SLAUGHTER_OVERTURE, maxStacks = 3, internalCooldownTicks = 100)
        val extinctionProtocol = perk("extinction_protocol", "灭绝协议", "击杀后获得 6 秒增幅与焕光。", triggers = setOf(GearPerkTrigger.MELEE_KILL), scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.EXTINCTION_PROTOCOL, durationTicks = 120, internalCooldownTicks = 120)
        val huntingMark = perk("hunting_mark", "猎杀印记", "击杀后标记下一名命中的敌人；对标记目标的近战伤害提高 15%，持续 8 秒。", triggers = setOf(GearPerkTrigger.MELEE_KILL, GearPerkTrigger.MELEE_HIT), scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.HUNTING_MARK, durationTicks = 160, internalCooldownTicks = 40)

        val keenEdge = perk("keen_edge", "锋锐刃口", "近战伤害提高 10%。", damageMultiplier = 1.10f, scopes = setOf(GearPerkScope.MELEE))
        val lightEdge = perk("light_edge", "轻量刃口", "攻击速度提高 20%。", cooldownMultiplier = 0.83f, scopes = setOf(GearPerkScope.MELEE))
        val weightedEdge = perk("weighted_edge", "沉重刃口", "近战伤害提高 20%，攻击间隔 ×1.18。", damageMultiplier = 1.20f, cooldownMultiplier = 1.18f, scopes = setOf(GearPerkScope.MELEE))
        val honedEdge = perk("honed_edge", "蓄力斩", "满攻击强度近战伤害提高 25%。", scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.HONED_EDGE)
        val serratedEdge = perk("serrated_edge", "锯齿流血", "近战命中施加短暂中毒。", scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.SERRATED_EDGE, internalCooldownTicks = 40)
        val emberBlade = perk("ember_blade", "烈焰刃", "近战命中附加 20 层灼烧。", scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.EMBER_BLADE, durationTicks = 100, internalCooldownTicks = 20)
        val balancedGuard = perk("balanced_guard", "平衡护手", "近战攻击后恢复速度提高：攻击间隔缩短 25%。", cooldownMultiplier = 0.75f, scopes = setOf(GearPerkScope.MELEE))
        val heavyGuard = perk("heavy_guard", "重型护手", "攻击后短暂获得伤害抗性。", scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.HEAVY_GUARD, durationTicks = 40)
        val duelistGuard = perk("duelist_guard", "决斗护手", "命中后短暂提高击退抗性。", scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.DUELIST_GUARD, durationTicks = 40)
        val eagerEdge = perk("eager_edge", "急切刀锋", "未手持该武器 3 秒后切回进入就绪；挥动武器后向前冲刺，并在 3 秒内获得一次额外空中跳跃。", triggers = setOf(GearPerkTrigger.SWAP_IN), scopes = setOf(GearPerkScope.MELEE), effect = GearPerkEffect.EAGER_EDGE, durationTicks = 60)

        val meleeColumns = listOf(
            listOf(keenEdge, lightEdge, weightedEdge),
            listOf(balancedGuard, heavyGuard, duelistGuard),
            listOf(honedEdge, serratedEdge, emberBlade, slaughterOverture, extinctionProtocol, huntingMark, executionersEdge, emberCarve, boneRhythm, momentumCleave, duelistPulse, heavyReversal),
            listOf(guardCounter, bloodlessFinish, chainStep, panicGuard, fireworkFinisher, solarPopcorn, featherStep, eagerEdge)
        )
        val axeMeleeColumns = meleeColumns.mapIndexed { index, column ->
            if (index == 3) column.filter { it.effect != GearPerkEffect.EAGER_EDGE } else column
        }

        val swiftLimbs = perk("swift_limbs", "轻质弓臂", "箭矢弹速提高 25%。", projectileSpeedMultiplier = 1.25f, scopes = setOf(GearPerkScope.BOW))
        val powerLimbs = perk("power_limbs", "强化弓臂", "箭矢伤害提高 10%。", damageMultiplier = 1.10f, scopes = setOf(GearPerkScope.BOW))
        val steadyPartLimbs = perk("steady_part_limbs", "稳定弓臂", "箭矢散布降低 50%。", projectileInaccuracyMultiplier = 0.50f, scopes = setOf(GearPerkScope.BOW))
        val lightweightLimbs = perk("lightweight_limbs", "游侠步伐", "拉弓期间获得速度 II。", scopes = setOf(GearPerkScope.BOW), effect = GearPerkEffect.LIGHTWEIGHT_LIMBS)
        val reinforcedLimbs = perk("reinforced_limbs", "远距强化", "飞行超过 15 tick 的箭矢造成强力远距伤害。", scopes = setOf(GearPerkScope.BOW), effect = GearPerkEffect.REINFORCED_LIMBS)
        val steadyLimbs = perk("steady_limbs", "标记箭矢", "箭矢命中后使目标发光 5 秒。", scopes = setOf(GearPerkScope.BOW), effect = GearPerkEffect.STEADY_LIMBS)
        val fastString = perk("fast_string", "快速弓弦", "箭矢速度提高 25%。", projectileSpeedMultiplier = 1.25f, scopes = setOf(GearPerkScope.BOW))
        val tautString = perk("taut_string", "紧绷弓弦", "提高拉弓速度与精度，不增加伤害。", scopes = setOf(GearPerkScope.BOW))
        val stableString = perk("stable_string", "稳定弓弦", "箭矢散布降低 60%。", projectileInaccuracyMultiplier = 0.40f, scopes = setOf(GearPerkScope.BOW))
        val reinforcedArmsPart = perk("reinforced_arms_part", "强化弩臂", "弩箭伤害提高 10%。", damageMultiplier = 1.10f, scopes = setOf(GearPerkScope.CROSSBOW))
        val lightweightArmsPart = perk("lightweight_arms_part", "轻量弩臂", "弩箭弹速提高 25%。", projectileSpeedMultiplier = 1.25f, scopes = setOf(GearPerkScope.CROSSBOW))
        val stableArmsPart = perk("stable_arms_part", "稳定弩臂", "弩箭散布降低 60%。", projectileInaccuracyMultiplier = 0.40f, scopes = setOf(GearPerkScope.CROSSBOW))
        val reinforcedArms = perk("reinforced_arms", "震荡弩臂", "弩箭命中时强力击退目标。", scopes = setOf(GearPerkScope.CROSSBOW), effect = GearPerkEffect.REINFORCED_ARMS)
        val lightweightArms = perk("lightweight_arms", "机动弩臂", "手持弩时获得速度 I。", scopes = setOf(GearPerkScope.CROSSBOW), effect = GearPerkEffect.LIGHTWEIGHT_ARMS)
        val multishotArms = perk("multishot_arms", "削弱弩臂", "弩箭命中时使目标虚弱 4 秒。", scopes = setOf(GearPerkScope.CROSSBOW), effect = GearPerkEffect.MULTISHOT_ARMS)
        val quickString = perk("quick_string", "快速弦组", "弩箭速度提高 25%。", projectileSpeedMultiplier = 1.25f, scopes = setOf(GearPerkScope.CROSSBOW))
        val piercingString = perk("piercing_string", "穿甲弦组", "提供穿透与反屏障效果，不增加伤害。", scopes = setOf(GearPerkScope.CROSSBOW))
        val stabilizingString = perk("stabilizing_string", "稳定弦组", "弩箭散布降低 40%。", projectileInaccuracyMultiplier = 0.60f, scopes = setOf(GearPerkScope.CROSSBOW))

        val bowColumns = listOf(
            listOf(swiftLimbs, powerLimbs, steadyPartLimbs),
            listOf(fastString, tautString, stableString),
            listOf(lightweightLimbs, reinforcedLimbs, steadyLimbs, arrowSplit, arcFlash, precisionBombardment, splitString, ricochetFletching, luckyRebound),
            listOf(fireworkFinisher, solarPopcorn, featherStep)
        )
        val crossbowColumns = listOf(
            listOf(reinforcedArmsPart, lightweightArmsPart, stableArmsPart),
            listOf(quickString, piercingString, stabilizingString),
            listOf(reinforcedArms, lightweightArms, multishotArms, gravityArc, echoArrow, pinningShot),
            listOf(perfectDraw, markedQuiver, calmRelease, splitString, ricochetFletching, luckyRebound, fireworkFinisher, solarPopcorn, featherStep)
        )

        listOf(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.MACE, Items.TRIDENT
        ).forEach { item ->
            registerDefinition(
                item, GearRarity.LEGENDARY, GearCategory.WEAPON, VANILLA_MELEE,
                perkColumns = meleeColumns,
                rollColumnLabels = listOf("刃口", "护手", "Perk 1", "Perk 2")
            )
        }
        listOf(
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE
        ).forEach { item ->
            registerDefinition(
                item, GearRarity.LEGENDARY, GearCategory.WEAPON, VANILLA_MELEE,
                perkColumns = axeMeleeColumns,
                rollColumnLabels = listOf("刃口", "护手", "Perk 1", "Perk 2")
            )
        }

        registerDefinition(
            Items.BOW, GearRarity.LEGENDARY, GearCategory.WEAPON, VANILLA_BOW,
            perkColumns = bowColumns,
            rollColumnLabels = listOf("弓臂", "弓弦", "Perk 1", "Perk 2")
        )
        registerDefinition(
            Items.CROSSBOW, GearRarity.LEGENDARY, GearCategory.WEAPON, VANILLA_CROSSBOW,
            perkColumns = crossbowColumns,
            rollColumnLabels = listOf("弩臂", "弦组", "Perk 1", "Perk 2")
        )
    }

    private fun registerVanillaArmor() {
        listOf(
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
        ).forEach { item ->
            registerDefinition(item, GearRarity.LEGENDARY, GearCategory.ARMOR, ARMOR_STAT_SHELL)
        }
        listOf(
            DestinyItems.OVERLOAD_HELMET, DestinyItems.YANYANG_CHESTPLATE, DestinyItems.BAORAN_LEGGINGS,
            DestinyItems.HUNTER_CLOAK, DestinyItems.WARLOCK_BOND, DestinyItems.TITAN_MARK
        )
            .forEach { item -> registerDefinition(item, GearRarity.LEGENDARY, GearCategory.ARMOR, ARMOR_STAT_SHELL) }
    }

    private fun registerDefinition(
        item: Item,
        rarity: GearRarity,
        category: GearCategory,
        frame: WeaponFrameDefinition,
        baseDamage: Float = 0.0f,
        explosionRadius: Float = 0.0f,
        ammoItem: Item? = null,
        ammoType: DestinyAmmoType = DestinyAmmoType.PRIMARY,
        precisionMultiplier: Float = 1.0f,
        fixedPerks: List<GearPerk> = emptyList(),
        perkColumns: List<List<GearPerk>> = emptyList(),
        rollColumnLabels: List<String> = emptyList(),
        hasCatalystSlot: Boolean = false,
        catalysts: List<GearPerk> = emptyList()
    ) {
        val id = BuiltInRegistries.ITEM.getKey(item)
        val definition = GearDefinition(
            item, id, rarity, category, frame, baseDamage, explosionRadius, ammoItem,
            ammoType, precisionMultiplier, fixedPerks, perkColumns, rollColumnLabels, hasCatalystSlot, catalysts
        )
        definitions[id] = definition
        definitionsByItem[item] = definition
    }

    private fun perk(
        path: String,
        displayName: String,
        description: String,
        damageMultiplier: Float = 1.0f,
        projectileSpeedMultiplier: Float = 1.0f,
        projectileInaccuracyMultiplier: Float = 1.0f,
        explosionRadiusMultiplier: Float = 1.0f,
        cooldownMultiplier: Float = 1.0f,
        ammoRefundChance: Float = 0.0f,
        reloadTimeMultiplier: Float = 1.0f,
        magazineSizeBonus: Int = 0,
        triggers: Set<GearPerkTrigger> = emptySet(),
        scopes: Set<GearPerkScope> = emptySet(),
        effect: GearPerkEffect = GearPerkEffect.NONE,
        durationTicks: Int = 0,
        maxStacks: Int = 1,
        internalCooldownTicks: Int = 0,
        triggerChance: Float = 1.0f
    ): GearPerk {
        val id = id(path)
        return perks.getOrPut(id) {
            GearPerk(
                id = id,
                displayName = displayName,
                description = description,
                damageMultiplier = damageMultiplier,
                projectileSpeedMultiplier = projectileSpeedMultiplier,
                projectileInaccuracyMultiplier = projectileInaccuracyMultiplier,
                explosionRadiusMultiplier = explosionRadiusMultiplier,
                cooldownMultiplier = cooldownMultiplier,
                ammoRefundChance = ammoRefundChance,
                reloadTimeMultiplier = reloadTimeMultiplier,
                magazineSizeBonus = magazineSizeBonus,
                triggers = triggers,
                scopes = scopes,
                effect = effect,
                durationTicks = durationTicks,
                maxStacks = maxStacks,
                internalCooldownTicks = internalCooldownTicks,
                triggerChance = triggerChance
            )
        }
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
    }
}
