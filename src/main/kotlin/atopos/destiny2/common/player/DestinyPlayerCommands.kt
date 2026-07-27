package atopos.destiny2.common.player

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRolls
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object DestinyPlayerCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("destinydata")
                    .executes { context ->
                        val player = context.source.playerOrException
                        val data = PlayerDestinyDataApi.get(player)
                        context.source.sendSuccess({ Component.literal(formatData(player, data)) }, false)
                        Command.SINGLE_SUCCESS
                    }
            )

            dispatcher.register(
                Commands.literal("destinycooldown")
                    .then(
                        Commands.literal("reset")
                            .executes { context ->
                                val player = context.source.playerOrException
                                val data = PlayerDestinyDataApi.get(player)
                                data.cooldowns = AbilityCooldowns()
                                data.combatState.superEnergy = 100.0f
                                DestinyNetworking.syncCooldowns(player)
                                DestinyNetworking.syncStatState(player)
                                context.source.sendSuccess(
                                    { Component.literal("全部技能冷却已重置，超能已充满。") },
                                    false
                                )
                                Command.SINGLE_SUCCESS
                            }
                    )
            )

            dispatcher.register(
                Commands.literal("destinyclass")
                    .executes { context ->
                        val data = PlayerDestinyDataApi.get(context.source.playerOrException)
                        val profile = DestinyClassRegistry.profileFor(data.destinyClass)
                        context.source.sendSuccess(
                            { Component.literal("当前职业: ${profile.title} (${profile.type.id}) - ${profile.description}") },
                            false
                        )
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.literal("list")
                            .executes { context ->
                                val classes = DestinyClassRegistry.all().joinToString("；") {
                                    "${it.title}=${it.type.id}"
                                }
                                context.source.sendSuccess({ Component.literal("可用职业: $classes") }, false)
                                Command.SINGLE_SUCCESS
                            }
                    )
                    .then(
                        Commands.literal("set")
                            .then(
                                Commands.argument("class", StringArgumentType.word())
                                    .executes { context ->
                                        val requestedClass = StringArgumentType.getString(context, "class")
                                        val destinyClass = DestinyClassType.findById(requestedClass)
                                        if (destinyClass == null) {
                                            context.source.sendFailure(
                                                Component.literal("未知职业: $requestedClass。可用: hunter, titan, warlock")
                                            )
                                            0
                                        } else {
                                            val player = context.source.playerOrException
                                            val data = PlayerDestinyDataApi.get(player)
                                            data.setClass(destinyClass)
                                            DestinyNetworking.syncPlayerData(player)
                                            val profile = DestinyClassRegistry.profileFor(destinyClass)
                                            context.source.sendSuccess(
                                                {
                                                    Component.literal(
                                                        "已切换为${profile.title}，默认子职业: ${data.subclass.displayName}。${formatStats(data.stats)}"
                                                    )
                                                },
                                                true
                                            )
                                            Command.SINGLE_SUCCESS
                                        }
                                    }
                            )
                    )
            )

            dispatcher.register(
                Commands.literal("destinysubclass")
                    .executes { context ->
                        val data = PlayerDestinyDataApi.get(context.source.playerOrException)
                        val profile = DestinySubclassRegistry.profileFor(data.subclass)
                        context.source.sendSuccess(
                            { Component.literal("当前子职业: ${profile.title} (${profile.type.id}) - ${profile.description}") },
                            false
                        )
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.literal("list")
                            .executes { context ->
                                val data = PlayerDestinyDataApi.get(context.source.playerOrException)
                                val subclasses = DestinySubclassRegistry.availableFor(data.destinyClass).joinToString("；") {
                                    "${it.title}=${it.type.id}"
                                }
                                context.source.sendSuccess(
                                    { Component.literal("${data.destinyClass.displayName}可用子职业: $subclasses") },
                                    false
                                )
                                Command.SINGLE_SUCCESS
                            }
                    )
                    .then(
                        Commands.literal("set")
                            .then(
                                Commands.argument("subclass", StringArgumentType.word())
                                    .executes { context ->
                                        val requestedSubclass = StringArgumentType.getString(context, "subclass")
                                        val subclass = DestinySubclassType.findById(requestedSubclass)
                                        if (subclass == null) {
                                            context.source.sendFailure(
                                                Component.literal("未知子职业: $requestedSubclass。可用: solar_warlock, void_hunter, arc_titan")
                                            )
                                            0
                                        } else {
                                            val player = context.source.playerOrException
                                            val data = PlayerDestinyDataApi.get(player)
                                            if (!data.setSubclass(subclass)) {
                                                context.source.sendFailure(
                                                    Component.literal(
                                                        "${subclass.displayName} 属于 ${subclass.requiredClass.displayName}，当前职业 ${data.destinyClass.displayName} 不能使用。"
                                                    )
                                                )
                                                0
                                            } else {
                                                DestinyNetworking.syncPlayerData(player)
                                                val profile = DestinySubclassRegistry.profileFor(subclass)
                                                context.source.sendSuccess(
                                                    { Component.literal("已切换为${profile.title}。元素: ${profile.element}。技能冷却已重置。") },
                                                    true
                                                )
                                                Command.SINGLE_SUCCESS
                                            }
                                        }
                                    }
                            )
                    )
            )

            dispatcher.register(
                Commands.literal("destinyaspect")
                    .executes { context ->
                        val player = context.source.playerOrException
                        val data = PlayerDestinyDataApi.get(player)
                        val equipped = data.subclassConfig.selectedAspects.joinToString(", ").ifBlank { "无" }
                        context.source.sendSuccess({ Component.literal("已装备星象: $equipped。用 /destinyaspect list 查看，用 /destinyaspect equip <id> 装备。") }, false)
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.literal("list")
                            .executes { context ->
                                val player = context.source.playerOrException
                                val options = DestinySubclassConfigRegistry.definitionFor(PlayerDestinyDataApi.get(player).subclass).aspectOptions
                                val list = options.joinToString(" | ") { "${shortId(it.id)}=${it.title}" }
                                context.source.sendSuccess({ Component.literal("可用星象: $list") }, false)
                                Command.SINGLE_SUCCESS
                            }
                    )
                    .then(
                        Commands.literal("equip")
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes { context ->
                                    val player = context.source.playerOrException
                                    val data = PlayerDestinyDataApi.get(player)
                                    val option = DestinySubclassConfigRegistry.definitionFor(data.subclass).aspectOptions
                                        .firstOrNull { it.id == normalizeConfigId(StringArgumentType.getString(context, "id")) }
                                    if (option == null) {
                                        context.source.sendFailure(Component.literal("未知或不属于当前子职业的星象。用 /destinyaspect list 查看。"))
                                        return@executes 0
                                    }
                                    if (!data.subclassConfig.selectedAspects.contains(option.id) &&
                                        !DestinySubclassConfigRegistry.toggleAspect(data.subclass, data.subclassConfig, option.id)
                                    ) {
                                        context.source.sendFailure(Component.literal("星象栏已满（最多 2 个）。"))
                                        return@executes 0
                                    }
                                    data.subclassConfig = DestinySubclassConfigRegistry.normalize(data.subclass, data.subclassConfig)
                                    DestinyNetworking.syncPlayerData(player)
                                    context.source.sendSuccess({ Component.literal("已装备星象：${option.title}") }, true)
                                    Command.SINGLE_SUCCESS
                                }
                            )
                    )
                    .then(
                        Commands.literal("remove")
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes { context ->
                                    val player = context.source.playerOrException
                                    val data = PlayerDestinyDataApi.get(player)
                                    val id = normalizeConfigId(StringArgumentType.getString(context, "id"))
                                    if (!data.subclassConfig.selectedAspects.remove(id)) {
                                        context.source.sendFailure(Component.literal("该星象未装备。"))
                                        return@executes 0
                                    }
                                    data.subclassConfig = DestinySubclassConfigRegistry.normalize(data.subclass, data.subclassConfig)
                                    DestinyNetworking.syncPlayerData(player)
                                    context.source.sendSuccess({ Component.literal("已移除星象。") }, true)
                                    Command.SINGLE_SUCCESS
                                }
                            )
                    )
            )

            dispatcher.register(
                Commands.literal("destinyfragment")
                    .executes { context ->
                        val player = context.source.playerOrException
                        val data = PlayerDestinyDataApi.get(player)
                        val equipped = data.subclassConfig.selectedFragments.joinToString(", ").ifBlank { "无" }
                        val capacity = DestinySubclassConfigRegistry.fragmentCapacity(data.subclass, data.subclassConfig)
                        context.source.sendSuccess({ Component.literal("已装备碎片: $equipped（$capacity 槽）。用 /destinyfragment list 查看。") }, false)
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.literal("list")
                            .executes { context ->
                                val player = context.source.playerOrException
                                val options = DestinySubclassConfigRegistry.definitionFor(PlayerDestinyDataApi.get(player).subclass).fragmentOptions
                                val list = options.joinToString(" | ") { "${shortId(it.id)}=${it.title}" }
                                context.source.sendSuccess({ Component.literal("可用碎片: $list") }, false)
                                Command.SINGLE_SUCCESS
                            }
                    )
                    .then(
                        Commands.literal("equip")
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes { context ->
                                    val player = context.source.playerOrException
                                    val data = PlayerDestinyDataApi.get(player)
                                    val option = DestinySubclassConfigRegistry.definitionFor(data.subclass).fragmentOptions
                                        .firstOrNull { it.id == normalizeConfigId(StringArgumentType.getString(context, "id")) }
                                    if (option == null) {
                                        context.source.sendFailure(Component.literal("未知或不属于当前子职业的碎片。用 /destinyfragment list 查看。"))
                                        return@executes 0
                                    }
                                    if (!data.subclassConfig.selectedFragments.contains(option.id) &&
                                        !DestinySubclassConfigRegistry.toggleFragment(data.subclass, data.subclassConfig, option.id)
                                    ) {
                                        context.source.sendFailure(Component.literal("碎片槽不足；先装备提供碎片槽的星象。"))
                                        return@executes 0
                                    }
                                    data.subclassConfig = DestinySubclassConfigRegistry.normalize(data.subclass, data.subclassConfig)
                                    DestinyNetworking.syncPlayerData(player)
                                    context.source.sendSuccess({ Component.literal("已装备碎片：${option.title}") }, true)
                                    Command.SINGLE_SUCCESS
                                }
                            )
                    )
                    .then(
                        Commands.literal("remove")
                            .then(Commands.argument("id", StringArgumentType.word())
                                .executes { context ->
                                    val player = context.source.playerOrException
                                    val id = normalizeConfigId(StringArgumentType.getString(context, "id"))
                                    val config = PlayerDestinyDataApi.get(player).subclassConfig
                                    if (!config.selectedFragments.remove(id)) {
                                        context.source.sendFailure(Component.literal("该碎片未装备。"))
                                        return@executes 0
                                    }
                                    DestinyNetworking.syncPlayerData(player)
                                    context.source.sendSuccess({ Component.literal("已移除碎片。") }, true)
                                    Command.SINGLE_SUCCESS
                                }
                            )
                    )
            )

            dispatcher.register(
                Commands.literal("destinypower")
                    .executes { context ->
                        val player = context.source.playerOrException
                        val data = PlayerDestinyDataApi.get(player)
                        val power = GuardianPowerRuntime.refresh(player)
                        context.source.sendSuccess(
                            {
                                Component.literal(
                                    "${data.journeyStage.displayName} | 装备光等 ${power.current} | 最高可用 ${power.highestAvailable} | 活动推荐 ${power.activityRecommended}" +
                                        if (power.suppressed) " | 光等压制 -${power.suppressionPercent}% 伤害 / +${power.suppressionPercent}% 承伤"
                                        else " | 未受光等压制"
                                )
                            },
                            false
                        )
                        Command.SINGLE_SUCCESS
                    }
                    .then(
                        Commands.literal("stage")
                            .requires { it.hasPermission(2) }
                            .then(
                                Commands.argument("id", StringArgumentType.word())
                                    .executes { context ->
                                        val requested = StringArgumentType.getString(context, "id")
                                        val stage = GuardianJourneyStage.entries.firstOrNull { it.id == requested }
                                        if (stage == null) {
                                            context.source.sendFailure(
                                                Component.literal("未知阶段。可用: ${GuardianJourneyStage.entries.joinToString(", ") { it.id }}")
                                            )
                                            return@executes 0
                                        }
                                        val player = context.source.playerOrException
                                        PlayerDestinyDataApi.get(player).journeyStage = stage
                                        val power = GuardianPowerRuntime.refresh(player)
                                        DestinyNetworking.syncNavigationState(player, power)
                                        context.source.sendSuccess(
                                            { Component.literal("阶段已设为 ${stage.displayName}，阶段奖励 ${stage.dropFloor}-${stage.rewardCap}，当前装备光等 ${power.current}。") },
                                            true
                                        )
                                        Command.SINGLE_SUCCESS
                                    }
                            )
                    )
            )

            dispatcher.register(
                Commands.literal("destinyroll")
                    .then(
                        Commands.literal("inspect")
                            .executes { context ->
                                val player = context.source.playerOrException
                                val stack = player.mainHandItem
                                val definition = GearRegistry.definitionFor(stack)
                                if (definition == null) {
                                    context.source.sendFailure(Component.literal("主手不是可 Roll 的命运武器。"))
                                    return@executes 0
                                }
                                GearRolls.ensureRoll(stack)
                                val roll = GearRolls.read(stack) ?: return@executes 0
                                val perks = roll.perkIds.mapNotNull(GearRegistry::perk)
                                val slots = perks.mapIndexed { index, perk ->
                                    val label = definition.rollColumnLabels.getOrElse(index) { "槽位 ${index + 1}" }
                                    "$label=${perk.displayName}"
                                }.joinToString("；")
                                val damage = ((GearRolls.damageMultiplier(stack) - 1.0f) * 100).toInt()
                                val attackSpeed = if (definition.frame.id.path == "vanilla_melee") {
                                    val cooldown = GearRolls.rollPerks(stack)
                                        .filter { it.scopes.contains(atopos.destiny2.common.gear.GearPerkScope.MELEE) }
                                        .fold(1.0f) { value, perk -> value * perk.cooldownMultiplier }
                                    ((1.0f / cooldown - 1.0f) * 100).toInt()
                                } else 0
                                context.source.sendSuccess(
                                    { Component.literal("Roll v${roll.version}: 光等 ${roll.power} | $slots | 伤害 ${if (damage >= 0) "+" else ""}$damage% | 攻速 ${if (attackSpeed >= 0) "+" else ""}$attackSpeed%") },
                                    false
                                )
                                Command.SINGLE_SUCCESS
                            }
                    )
                    .then(
                        Commands.literal("reroll")
                            .executes { context ->
                                val player = context.source.playerOrException
                                if (!GearRolls.reroll(player.mainHandItem)) {
                                    context.source.sendFailure(Component.literal("主手不是可 Roll 的命运武器。"))
                                    return@executes 0
                                }
                                context.source.sendSuccess({ Component.literal("已重铸主手武器 Roll。使用 /destinyroll inspect 查看实算属性。") }, true)
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
        }
    }

    private fun formatData(player: net.minecraft.server.level.ServerPlayer, data: PlayerDestinyData): String {
        val resolvedStats = DestinyStatsResolver.resolve(player)
        return "职业: ${data.destinyClass.displayName}, " +
            "子职业: ${data.subclass.displayName}, " +
            formatStats(resolvedStats) +
            "，战斗收益: ${formatBenefits(resolvedStats)}，" +
            "护盾 ${"%.1f".format(data.combatState.healthShield)}，超能 ${data.combatState.superEnergy.toInt()}%"
    }

    private fun shortId(id: String): String = id.substringAfter(':')

    private fun normalizeConfigId(id: String): String {
        return if (':' in id) id else "destiny2-mod:$id"
    }

    private fun formatStats(stats: DestinyStats): String {
        return "属性: 武器 ${stats.weapons} / 生命 ${stats.health} / " +
            "职业 ${stats.classAbility} / 手雷 ${stats.grenade} / " +
            "超能 ${stats.superStat} / 近战 ${stats.melee}"
    }

    private fun formatBenefits(stats: DestinyStats): String {
        val grenadeCooldown = DestinyStatFormulas.cooldownTicks(30 * 20, AbilitySlot.GRENADE, stats) / 20
        val meleeCooldown = DestinyStatFormulas.cooldownTicks(25 * 20, AbilitySlot.MELEE, stats) / 20
        val classCooldown = DestinyStatFormulas.cooldownTicks(45 * 20, AbilitySlot.CLASS_ABILITY, stats) / 20
        val shield = DestinyStatFormulas.healthShieldCapacity(stats)
        val superGain = (DestinyStatFormulas.superEnergyGainMultiplier(stats) * 100).toInt()
        return "护盾 ${"%.1f".format(shield)}，手雷 ${grenadeCooldown}s，近战 ${meleeCooldown}s，" +
            "职业 ${classCooldown}s，超能获取 ${superGain}%"
    }
}
