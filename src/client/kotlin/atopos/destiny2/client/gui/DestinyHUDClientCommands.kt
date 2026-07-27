package atopos.destiny2.client.gui

import com.mojang.brigadier.Command
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.network.chat.Component

object DestinyHUDClientCommands {
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("destinyui")
                    .then(literal("aspects").executes { context ->
                        context.source.sendFeedback(Component.literal("正在打开星象配置…"))
                        DestinyAspectScreen.requestOpen()
                        Command.SINGLE_SUCCESS
                    })
                    .then(literal("editor").executes { context ->
                        context.source.sendFeedback(Component.literal("正在打开 LDLib 通用 UI 编辑器…"))
                        DestinyLDLibEditor.requestOpen()
                        Command.SINGLE_SUCCESS
                    })
            )

            dispatcher.register(
                literal("destinyhud")
                    .executes { context ->
                        context.source.sendFeedback(Component.literal("HUD: ${DestinyAbilityHUDTemplate.summary()}"))
                        Command.SINGLE_SUCCESS
                    }
                    .then(literal("edit").executes { context ->
                        DestinyLDLibEditor.requestOpen(DestinyLDLibEditor.Target.ABILITY_HUD)
                        context.source.sendFeedback(Component.literal("正在打开技能 HUD 模板编辑器…"))
                        Command.SINGLE_SUCCESS
                    })
                    .then(literal("reset").executes { context ->
                        DestinyAbilityHUDTemplate.reset()
                        context.source.sendFeedback(Component.literal("技能 HUD 已恢复为新的 LDLib2 默认模板。"))
                        Command.SINGLE_SUCCESS
                    })
            )

            dispatcher.register(
                literal("destinynav")
                    .executes { context ->
                        context.source.sendFeedback(Component.literal("导航页: ${DestinyNavigationTemplate.summary()}"))
                        Command.SINGLE_SUCCESS
                    }
                    .then(literal("edit").executes { context ->
                        DestinyLDLibEditor.requestOpen(DestinyLDLibEditor.Target.NAVIGATION)
                        context.source.sendFeedback(Component.literal("正在打开 LDLib2 导航页模板编辑器。"))
                        Command.SINGLE_SUCCESS
                    })
                    .then(literal("reset").executes { context ->
                        DestinyNavigationTemplate.reset()
                        context.source.sendFeedback(Component.literal("导航页已恢复为默认 LDLib2 模板。"))
                        Command.SINGLE_SUCCESS
                    })
            )
        }
    }
}
