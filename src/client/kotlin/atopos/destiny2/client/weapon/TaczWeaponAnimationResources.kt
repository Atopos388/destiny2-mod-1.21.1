package atopos.destiny2.client.weapon

import atopos.destiny2.common.weapon.TaczWeaponAnimationBridge
import atopos.destiny2.common.weapon.TaczWeaponAnimationContract
import com.google.gson.JsonParser
import com.mojang.brigadier.Command
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** Audits every gun discovered by the generic TaCZ gun-pack loader. */
object TaczWeaponAnimationResources : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("DestinyTaCZAnimation")
    private val reports = ConcurrentHashMap<ResourceLocation, Report>()

    data class Specification(
        val weaponId: ResourceLocation,
        val animationResource: ResourceLocation,
        val modelResource: ResourceLocation,
        val usesStandardPositioning: Boolean
    )

    data class Report(
        val specification: Specification,
        val animations: Set<String>,
        val bones: Set<String>,
        val placeholders: Set<String>,
        val loadErrors: List<String>
    ) {
        val missingEssentialAnimations: Set<String>
            get() = TaczWeaponAnimationContract.essentialAnimations - animations
        val missingEssentialBones: Set<String>
            get() = requiredBones - bones
        private val requiredBones: Set<String>
            get() = if (specification.usesStandardPositioning) {
                setOf(
                    TaczWeaponAnimationContract.ROOT_BONE,
                    TaczGunPackResources.IDLE_VIEW,
                    TaczGunPackResources.IRON_VIEW
                )
            } else {
                TaczWeaponAnimationContract.essentialBones
            }
        val missingWorkflowBones: Set<String>
            get() = TaczWeaponAnimationContract.workflowBones - bones
        val ready: Boolean
            get() = loadErrors.isEmpty() && missingEssentialAnimations.isEmpty() &&
                missingEssentialBones.isEmpty() && placeholders.isEmpty()

        fun summary(): String = buildString {
            append(specification.weaponId).append(": ")
            if (ready) {
                append("TaCZ 动画工作流完整")
                return@buildString
            }
            if (loadErrors.isNotEmpty()) append("读取错误=").append(loadErrors.joinToString(" | ")).append("; ")
            if (missingEssentialAnimations.isNotEmpty()) {
                append("缺少必需动画=").append(missingEssentialAnimations.joinToString(", ")).append("; ")
            }
            if (placeholders.isNotEmpty()) append("占位动画=").append(placeholders.joinToString(", ")).append("; ")
            if (missingEssentialBones.isNotEmpty()) {
                append("缺少必需骨骼=").append(missingEssentialBones.joinToString(", ")).append("; ")
            }
            if (missingWorkflowBones.isNotEmpty()) {
                append("缺少功能骨骼=").append(missingWorkflowBones.joinToString(", "))
            }
        }
    }

    fun register() {
        TaczWeaponAnimationBridge.animationResolver = ::resolveAnimation
        TaczWeaponAnimationBridge.locomotionResolver = ::locomotionAnimation
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("destinyanimation")
                    .then(
                        literal("check").executes { context ->
                            val current = reports.values.sortedBy { it.specification.weaponId.toString() }
                            if (current.isEmpty()) {
                                context.source.sendFeedback(Component.literal("尚未加载武器动画资源"))
                            } else {
                                current.forEach { context.source.sendFeedback(Component.literal(it.summary())) }
                            }
                            Command.SINGLE_SUCCESS
                        }
                    )
            )
        }
    }

    override fun getFabricId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "tacz_weapon_animation_contracts")

    override fun onResourceManagerReload(manager: ResourceManager) {
        val specifications = TaczGunPackResources.definitions().map {
            Specification(it.id, it.animation, it.model, it.usesStandardPositioning)
        }
        val loaded = specifications.associate { it.weaponId to audit(manager, it) }
        reports.clear()
        reports.putAll(loaded)
        loaded.values.forEach { report ->
            if (report.ready) logger.info("{}", report.summary()) else logger.warn("{}", report.summary())
        }
    }

    fun report(weaponId: ResourceLocation): Report? = reports[weaponId]

    private fun audit(manager: ResourceManager, spec: Specification): Report {
        val errors = mutableListOf<String>()
        val animations = linkedSetOf<String>()
        val placeholders = linkedSetOf<String>()
        val bones = linkedSetOf<String>()

        runCatching {
            val resource = manager.getResource(spec.animationResource).orElseThrow()
            val root = resource.openAsReader().use(JsonParser::parseReader).asJsonObject
            root.getAsJsonObject("animations")?.entrySet()?.forEach { (name, value) ->
                animations += name
                if (value.isJsonObject && value.asJsonObject.get("destiny2_placeholder")?.asBoolean == true) {
                    placeholders += name
                }
            }
        }.onFailure { errors += "动画文件 ${spec.animationResource}: ${it.message}" }

        runCatching {
            val resource = manager.getResource(spec.modelResource).orElseThrow()
            val root = resource.openAsReader().use(JsonParser::parseReader).asJsonObject
            root.getAsJsonArray("minecraft:geometry")?.forEach { geometry ->
                geometry.asJsonObject.getAsJsonArray("bones")?.forEach { bone ->
                    bone.asJsonObject.get("name")?.asString?.let(bones::add)
                }
            }
        }.onFailure { errors += "模型文件 ${spec.modelResource}: ${it.message}" }

        return Report(spec, animations, bones, placeholders, errors)
    }

    private fun resolveAnimation(weaponId: ResourceLocation, requested: String, fallback: String): String {
        val report = reports[weaponId]
        // Empty compatibility slots are deliberately present in the merged file,
        // but selecting one resets every animated hand/weapon bone to bind pose.
        val available = report?.animations.orEmpty() - report?.placeholders.orEmpty()
        return when {
            requested in available -> requested
            fallback in available -> fallback
            TaczWeaponAnimationContract.STATIC_IDLE in available -> TaczWeaponAnimationContract.STATIC_IDLE
            else -> requested
        }
    }

    private fun locomotionAnimation(weaponId: ResourceLocation): String {
        val player = Minecraft.getInstance().player
            ?: return TaczWeaponAnimationContract.STATIC_IDLE
        val options = Minecraft.getInstance().options
        val requested = when {
            player.isSprinting && !player.onGround() -> TaczWeaponAnimationContract.RUN_HOLD
            player.isSprinting -> TaczWeaponAnimationContract.RUN
            DestinyWeaponAimClient.progress(1.0f) > 0.5f && options.keyUp.isDown ->
                TaczWeaponAnimationContract.WALK_AIMING
            options.keyUp.isDown -> TaczWeaponAnimationContract.WALK_FORWARD
            options.keyDown.isDown -> TaczWeaponAnimationContract.WALK_BACKWARD
            options.keyLeft.isDown || options.keyRight.isDown -> TaczWeaponAnimationContract.WALK_SIDEWAY
            else -> TaczWeaponAnimationContract.STATIC_IDLE
        }
        return resolveAnimation(weaponId, requested, TaczWeaponAnimationContract.STATIC_IDLE)
    }
}
