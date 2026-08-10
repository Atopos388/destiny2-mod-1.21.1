package atopos.destiny2.client.cinematic

import atopos.destiny2.common.sound.DestinySounds
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.logging.LogUtils
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.ForkJoinPool
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Resource-backed cinematic playback without a native video decoder.
 *
 * Frames are prepared at build-authoring time. During playback only the current
 * and next texture stay registered, keeping the decoded GPU footprint bounded.
 */
object CinematicVideoClient {
    private class CinematicAudioInstance : AbstractTickableSoundInstance(
        DestinySounds.GUARDIAN_AWAKENING,
        SoundSource.MASTER,
        RandomSource.create()
    ) {
        private var ageTicks = 0

        init {
            volume = 0.0f
            pitch = 1.0f
            looping = false
            delay = 0
            attenuation = SoundInstance.Attenuation.NONE
            relative = true
        }

        override fun tick() {
            ageTicks++
            val progress = (ageTicks / AUDIO_FADE_IN_TICKS).coerceIn(0.0f, 1.0f)
            volume = sin(progress * HALF_PI).toFloat()
        }

        override fun canStartSilent(): Boolean = true
    }

    private data class DecodedFrame(
        val index: Int,
        val image: NativeImage
    )

    data class Definition(
        val framePathFormat: String,
        val frameCount: Int,
        val framesPerSecond: Int,
        val sourceWidth: Int,
        val sourceHeight: Int
    ) {
        val durationTicks: Int
            get() = INTRO_BLACK_HOLD_TICKS +
                ((frameCount * TICKS_PER_SECOND) + framesPerSecond - 1) / framesPerSecond
    }

    private var definition: Definition? = null
    private var currentFrame = -1
    private var currentTexture: ResourceLocation? = null
    private var pendingFrame = -1
    private var pendingReady: DecodedFrame? = null
    private var playbackGeneration = 0
    private var audio: CinematicAudioInstance? = null

    fun start(definition: Definition): Boolean {
        stop()
        if (definition.frameCount <= 0 || definition.framesPerSecond <= 0) return false

        val client = Minecraft.getInstance()
        val first = frameLocation(definition, 0)
        val firstImage = readAndDecode(client, first) ?: return false

        this.definition = definition
        currentFrame = 0
        currentTexture = first
        client.textureManager.register(first, DynamicTexture(firstImage))
        scheduleDecode(1)
        audio = CinematicAudioInstance().also(client.soundManager::play)
        return true
    }

    fun stop() {
        val client = Minecraft.getInstance()
        playbackGeneration++
        currentTexture?.let(client.textureManager::release)
        pendingReady?.image?.close()
        audio?.let(client.soundManager::stop)
        definition = null
        currentFrame = -1
        currentTexture = null
        pendingFrame = -1
        pendingReady = null
        audio = null
    }

    fun render(graphics: GuiGraphics, deltaTracker: DeltaTracker, elapsedTicks: Int): Boolean {
        val video = definition ?: return false
        val partialTick = if (Minecraft.getInstance().isPaused) {
            0.0f
        } else {
            deltaTracker.getGameTimeDeltaPartialTick(false).coerceIn(0.0f, 1.0f)
        }
        val videoElapsedTicks = (elapsedTicks - INTRO_BLACK_HOLD_TICKS).coerceAtLeast(0)
        val frame = floor(
            ((videoElapsedTicks + partialTick) * video.framesPerSecond) / TICKS_PER_SECOND.toDouble()
        ).toInt().coerceIn(0, video.frameCount - 1)
        selectFrame(frame)

        val texture = currentTexture ?: return false
        val screenWidth = graphics.guiWidth()
        val screenHeight = graphics.guiHeight()
        graphics.fill(0, 0, screenWidth, screenHeight, BLACK)

        val scale = min(
            screenWidth.toDouble() / video.sourceWidth,
            screenHeight.toDouble() / video.sourceHeight
        )
        val drawWidth = (video.sourceWidth * scale).toInt().coerceAtLeast(1)
        val drawHeight = (video.sourceHeight * scale).toInt().coerceAtLeast(1)
        val left = (screenWidth - drawWidth) / 2
        val top = (screenHeight - drawHeight) / 2
        graphics.blit(
            texture,
            left,
            top,
            drawWidth,
            drawHeight,
            0.0f,
            0.0f,
            video.sourceWidth,
            video.sourceHeight,
            video.sourceWidth,
            video.sourceHeight
        )
        val cinematicHeight = (screenWidth / CINEMATIC_ASPECT_RATIO)
            .roundToInt()
            .coerceAtMost(screenHeight)
        val letterboxHeight = ((screenHeight - cinematicHeight) / 2).coerceAtLeast(0)
        if (letterboxHeight > 0) {
            graphics.fill(0, 0, screenWidth, letterboxHeight, BLACK)
            graphics.fill(0, screenHeight - letterboxHeight, screenWidth, screenHeight, BLACK)
        }
        renderIntroFade(graphics, screenWidth, screenHeight, elapsedTicks, partialTick)
        return true
    }

    private fun renderIntroFade(
        graphics: GuiGraphics,
        screenWidth: Int,
        screenHeight: Int,
        elapsedTicks: Int,
        partialTick: Float
    ) {
        val fadeTicks = elapsedTicks + partialTick - INTRO_BLACK_HOLD_TICKS
        val progress = (fadeTicks / INTRO_FADE_IN_TICKS).coerceIn(0.0f, 1.0f)
        val smoothProgress = progress * progress * progress *
            (progress * (progress * 6.0f - 15.0f) + 10.0f)
        val alpha = ((1.0f - smoothProgress) * 255.0f).roundToInt().coerceIn(0, 255)
        if (alpha > 0) {
            graphics.fill(0, 0, screenWidth, screenHeight, alpha shl 24)
        }
    }

    private fun selectFrame(frame: Int) {
        if (frame <= currentFrame) return
        val video = definition ?: return
        val decoded = pendingReady ?: return
        if (decoded.index > frame) return

        val client = Minecraft.getInstance()
        currentTexture?.let(client.textureManager::release)
        val nextTexture = frameLocation(video, decoded.index)
        client.textureManager.register(nextTexture, DynamicTexture(decoded.image))
        currentFrame = decoded.index
        currentTexture = nextTexture
        pendingFrame = -1
        pendingReady = null

        val nextFrame = if (frame > currentFrame + 1) frame else currentFrame + 1
        scheduleDecode(nextFrame)
    }

    private fun scheduleDecode(frame: Int) {
        val video = definition ?: return
        if (frame !in 0 until video.frameCount) return
        if (pendingFrame == frame) return

        val client = Minecraft.getInstance()
        val location = frameLocation(video, frame)
        val resource = client.resourceManager.getResource(location).orElse(null)
        if (resource == null) {
            LOGGER.warn("Missing cinematic frame {}", location)
            return
        }
        val bytes = try {
            resource.open().use { it.readAllBytes() }
        } catch (exception: Exception) {
            LOGGER.warn("Failed to read cinematic frame {}", location, exception)
            return
        }

        val generation = playbackGeneration
        pendingFrame = frame
        ForkJoinPool.commonPool().execute {
            val image = decodeJpeg(bytes, location)
            client.execute {
                if (
                    image != null &&
                    generation == playbackGeneration &&
                    definition === video &&
                    pendingFrame == frame
                ) {
                    pendingReady = DecodedFrame(frame, image)
                } else {
                    image?.close()
                    if (generation == playbackGeneration && pendingFrame == frame) {
                        pendingFrame = -1
                    }
                }
            }
        }
    }

    private fun readAndDecode(
        client: Minecraft,
        location: ResourceLocation
    ): NativeImage? {
        val resource = client.resourceManager.getResource(location).orElse(null)
        if (resource == null) {
            LOGGER.warn("Missing cinematic frame {}", location)
            return null
        }
        return try {
            val bytes = resource.open().use { it.readAllBytes() }
            decodeJpeg(bytes, location)
        } catch (exception: Exception) {
            LOGGER.warn("Failed to read cinematic frame {}", location, exception)
            null
        }
    }

    private fun decodeJpeg(bytes: ByteArray, location: ResourceLocation): NativeImage? {
        val buffered = try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (exception: Exception) {
            LOGGER.warn("Failed to decode JPEG cinematic frame {}", location, exception)
            return null
        } ?: run {
            LOGGER.warn("Unsupported cinematic frame image {}", location)
            return null
        }

        val width = buffered.width
        val height = buffered.height
        val pixels = buffered.getRGB(0, 0, width, height, null, 0, width)
        val nativeImage = NativeImage(width, height, false)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val argb = pixels[row + x]
                val abgr = (argb and -0x1000000) or
                    ((argb and 0x00ff0000) ushr 16) or
                    (argb and 0x0000ff00) or
                    ((argb and 0x000000ff) shl 16)
                nativeImage.setPixelRGBA(x, y, abgr)
            }
        }
        return nativeImage
    }

    private fun frameLocation(definition: Definition, frame: Int): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            String.format(Locale.ROOT, definition.framePathFormat, frame + 1)
        )

    private const val TICKS_PER_SECOND = 20
    private const val INTRO_BLACK_HOLD_TICKS = 4
    private const val INTRO_FADE_IN_TICKS = 14.0f
    private const val AUDIO_FADE_IN_TICKS = 6.0f
    private const val HALF_PI = Math.PI / 2.0
    private const val CINEMATIC_ASPECT_RATIO = 2.39
    private const val BLACK = -0x1000000
    private val LOGGER = LogUtils.getLogger()
}
