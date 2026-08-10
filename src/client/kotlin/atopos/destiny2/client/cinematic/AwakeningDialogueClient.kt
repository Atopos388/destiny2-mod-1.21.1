package atopos.destiny2.client.cinematic

import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import kotlin.math.roundToInt

/** Plays dialogue, subtitles, and Ghost foley at the authored animation keyframes. */
object AwakeningDialogueClient : HudRenderCallback {
    private data class SubtitleSegment(
        val subtitleKey: String,
        val startTick: Int,
        val endTick: Int
    )

    private data class Cue(
        val sound: SoundEvent,
        val startTick: Int,
        val durationTicks: Int,
        val source: SoundSource,
        val volume: Float = 1.0f,
        val subtitles: List<SubtitleSegment> = emptyList()
    )

    private class TimelineSoundInstance(
        sound: SoundEvent,
        source: SoundSource,
        authoredVolume: Float
    ) : AbstractTickableSoundInstance(sound, source, RandomSource.create()) {
        init {
            volume = authoredVolume
            pitch = 1.0f
            looping = false
            delay = 0
            attenuation = SoundInstance.Attenuation.NONE
            relative = true
        }

        override fun tick() = Unit
    }

    private var active = false
    private var timelineTicks = 0
    private val startedCues = linkedSetOf<Int>()
    private val playingSounds = mutableListOf<TimelineSoundInstance>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> stop() }
    }

    fun startTimeline() {
        stop()
        active = true
        timelineTicks = 0
        playDueCues()
    }

    fun stop() {
        val soundManager = Minecraft.getInstance().soundManager
        playingSounds.forEach(soundManager::stop)
        playingSounds.clear()
        startedCues.clear()
        active = false
        timelineTicks = 0
    }

    fun isActive(): Boolean = active

    private fun tick(client: Minecraft) {
        if (!active) return
        if (client.player == null || client.level == null) {
            stop()
            return
        }
        if (client.isPaused) return

        timelineTicks++
        playDueCues()
    }

    private fun playDueCues() {
        val soundManager = Minecraft.getInstance().soundManager
        CUES.forEachIndexed { index, cue ->
            if (index in startedCues || timelineTicks < cue.startTick) return@forEachIndexed
            startedCues += index
            TimelineSoundInstance(cue.sound, cue.source, cue.volume).also {
                playingSounds += it
                soundManager.play(it)
            }
        }
    }

    fun render(graphics: GuiGraphics, tickDelta: DeltaTracker) {
        if (!active) return
        val client = Minecraft.getInstance()
        val font = client.font
        val partialTick = if (client.isPaused) {
            0.0f
        } else {
            tickDelta.getGameTimeDeltaPartialTick(false).coerceIn(0.0f, 1.0f)
        }
        val age = timelineTicks + partialTick
        val cue = CUES
            .asSequence()
            .filter { it.subtitles.isNotEmpty() && age >= it.startTick && age < it.startTick + it.durationTicks }
            .maxByOrNull(Cue::startTick)
            ?: return
        val localAge = age - cue.startTick
        val subtitle = cue.subtitles.firstOrNull {
            localAge >= it.startTick && localAge < it.endTick
        } ?: return
        val subtitleAge = localAge - subtitle.startTick
        val remaining = subtitle.endTick - localAge
        val fade = minOf(
            (subtitleAge / FADE_IN_TICKS).coerceIn(0.0f, 1.0f),
            (remaining / FADE_OUT_TICKS).coerceIn(0.0f, 1.0f)
        )
        val alpha = (255.0f * smoothStep(fade)).roundToInt().coerceIn(0, 255)
        if (alpha <= 0) return

        val screenWidth = graphics.guiWidth()
        val line = Component.translatable(subtitle.subtitleKey)
        val speakerY = TOP_MARGIN
        val textY = speakerY + font.lineHeight + 4
        val panelWidth = maxOf(font.width(line), SPEAKER_RULE_WIDTH) + PANEL_PADDING * 2
        val left = (screenWidth - panelWidth) / 2
        val bottom = textY + font.lineHeight + PANEL_PADDING
        val panelAlpha = (alpha * 0.52f).roundToInt().coerceIn(0, 255)

        graphics.fill(left, speakerY - PANEL_PADDING, left + panelWidth, bottom, panelAlpha shl 24)
        val accentAlpha = (alpha * 0.85f).roundToInt().coerceIn(0, 255)
        graphics.fill(
            screenWidth / 2 - SPEAKER_RULE_WIDTH / 2,
            textY - 3,
            screenWidth / 2 + SPEAKER_RULE_WIDTH / 2,
            textY - 2,
            (accentAlpha shl 24) or ACCENT_RGB
        )
        graphics.drawCenteredString(
            font,
            Component.translatable(SPEAKER_KEY),
            screenWidth / 2,
            speakerY,
            (alpha shl 24) or ACCENT_RGB
        )
        graphics.drawString(
            font,
            line,
            (screenWidth - font.width(line)) / 2,
            textY,
            (alpha shl 24) or TEXT_RGB,
            true
        )
    }

    override fun onHudRender(graphics: GuiGraphics, tickDelta: DeltaTracker) = render(graphics, tickDelta)

    private fun smoothStep(value: Float): Float = value * value * (3.0f - 2.0f * value)

    private val CUES = listOf(
        Cue(
            DestinySounds.AWAKENING_EYES_UP,
            startTick = 0,
            durationTicks = 87,
            source = SoundSource.VOICE,
            subtitles = listOf(
                SubtitleSegment("dialogue.destiny2-mod.awakening.eyes_up.1", 0, 24),
                SubtitleSegment("dialogue.destiny2-mod.awakening.eyes_up.2", 24, 46),
                SubtitleSegment("dialogue.destiny2-mod.awakening.eyes_up.3", 46, 87)
            )
        ),
        Cue(
            DestinySounds.GHOST_AWAKENING_MOVE_IN,
            startTick = 107,
            durationTicks = 30,
            source = SoundSource.PLAYERS,
            volume = 0.8f
        ),
        Cue(
            DestinySounds.AWAKENING_SEARCHED,
            startTick = 152,
            durationTicks = 62,
            source = SoundSource.VOICE,
            subtitles = listOf(SubtitleSegment("dialogue.destiny2-mod.awakening.searched.1", 0, 62))
        ),
        Cue(
            DestinySounds.AWAKENING_GHOST,
            startTick = 213,
            durationTicks = 222,
            source = SoundSource.VOICE,
            subtitles = listOf(
                SubtitleSegment("dialogue.destiny2-mod.awakening.ghost.1", 0, 34),
                SubtitleSegment("dialogue.destiny2-mod.awakening.ghost.2", 34, 78),
                SubtitleSegment("dialogue.destiny2-mod.awakening.ghost.3", 78, 105),
                SubtitleSegment("dialogue.destiny2-mod.awakening.ghost.4", 105, 158),
                SubtitleSegment("dialogue.destiny2-mod.awakening.ghost.5", 158, 222)
            )
        ),
        Cue(
            DestinySounds.AWAKENING_WAIT,
            startTick = 400,
            durationTicks = 29,
            source = SoundSource.VOICE,
            subtitles = listOf(SubtitleSegment("dialogue.destiny2-mod.awakening.wait.1", 0, 29))
        ),
        Cue(
            DestinySounds.AWAKENING_MOVE,
            startTick = 436,
            durationTicks = 75,
            source = SoundSource.VOICE,
            subtitles = listOf(
                SubtitleSegment("dialogue.destiny2-mod.awakening.move.1", 0, 41),
                SubtitleSegment("dialogue.destiny2-mod.awakening.move.2", 41, 75)
            )
        ),
        Cue(
            DestinySounds.GHOST_AWAKENING_MOVE_OUT,
            startTick = 506,
            durationTicks = 28,
            source = SoundSource.PLAYERS,
            volume = 0.8f
        )
    )

    private const val SPEAKER_KEY = "dialogue.destiny2-mod.speaker.ghost"
    private const val TOP_MARGIN = 18
    private const val PANEL_PADDING = 7
    private const val SPEAKER_RULE_WIDTH = 34
    private const val FADE_IN_TICKS = 4.0f
    private const val FADE_OUT_TICKS = 6.0f
    private const val ACCENT_RGB = 0xD9B978
    private const val TEXT_RGB = 0xF2F4F5
}
