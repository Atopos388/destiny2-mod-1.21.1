package atopos.destiny2.common.entity

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ArcPulseGrenadeVisualContractTest {
    private val entitySource = Path.of(
        "src", "main", "kotlin", "atopos", "destiny2", "common", "entity", "ArcTitanEntities.kt"
    )
    private val rendererSource = Path.of(
        "src", "client", "kotlin", "atopos", "destiny2", "client", "renderer",
        "ArcPulseGrenadeWorldRenderer.kt"
    )
    private val clientEntrypoint = Path.of(
        "src", "client", "kotlin", "atopos", "destiny2", "Destiny2MODClient.kt"
    )
    private val shaderRoot = Path.of(
        "src", "main", "resources", "assets", "destiny2-mod", "shaders", "core"
    )

    @Test
    fun `each real damage pulse drives the client effect through an entity event`() {
        val source = Files.readString(entitySource)
        assertTrue(source.contains("level.broadcastEntityEvent(this, PULSE_EVENT)"))
        assertTrue(source.contains("override fun handleEntityEvent(status: Byte)"))
        assertTrue(source.contains("clientPulseVisualAge = 0"))
        assertEquals(15, ArcPulseGrenadeEntity.PULSE_INTERVAL_TICKS)
        assertEquals(11, ArcPulseGrenadeEntity.CLIENT_PULSE_VISUAL_TICKS)
        assertTrue(ArcPulseGrenadeEntity.FINISHING_TICKS >= 6)
    }

    @Test
    fun `renderer is registered and keeps pulse responsibilities separated`() {
        val renderer = Files.readString(rendererSource)
        val entrypoint = Files.readString(clientEntrypoint)
        assertTrue(entrypoint.contains("ArcPulseGrenadeWorldRenderer.register()"))
        assertTrue(renderer.contains("renderProjectile("))
        assertTrue(renderer.contains("renderAnchor("))
        assertTrue(renderer.contains("renderPulse("))
        assertTrue(renderer.contains("renderPulseFilaments("))
        assertTrue(renderer.contains("renderPulsePlasmaMasses("))
        assertTrue(renderer.contains("PULSE_PLASMA_TONGUES"))
        assertFalse(renderer.contains("PULSE_SHELL_MASSES"))
        assertTrue(renderer.contains("renderImpactStreaks("))
        assertTrue(renderer.contains("renderBallisticDebris("))
        assertTrue(renderer.contains("emitFieldBillboard("))
        assertFalse(renderer.contains("emitBillboardDiamond("))
        assertTrue(renderer.contains("DefaultVertexFormat.POSITION_TEX_COLOR"))
        assertTrue(renderer.contains("setAlphaBlend()"))
        assertTrue(renderer.contains("renderDisc("))
        assertTrue(renderer.contains("addHemisphere("))
        assertTrue(renderer.contains("pulseAge / 2.0f"))
        assertTrue(renderer.contains("Vector3f(radius, radius, radius)"))
        assertTrue(renderer.contains("hemisphere = true"))
        assertTrue(renderer.contains("redrawSlice = floor(pulseAge / 1.25f)"))
    }

    @Test
    fun `pulse grenade shaders expose every runtime uniform`() {
        assertShader(
            "arc_pulse_grenade_core",
            setOf("ModelViewMat", "ProjMat", "ColorModulator", "CameraPos", "Time", "Mode", "Opacity")
        )
        assertShader(
            "arc_pulse_grenade_field",
            setOf("ModelViewMat", "ProjMat", "ColorModulator", "Time", "Mode", "Progress", "Opacity")
        )
    }

    private fun assertShader(name: String, expectedUniforms: Set<String>) {
        val definitionPath = shaderRoot.resolve("$name.json")
        val vertexPath = shaderRoot.resolve("$name.vsh")
        val fragmentPath = shaderRoot.resolve("$name.fsh")
        assertTrue(Files.isRegularFile(definitionPath), "missing $definitionPath")
        assertTrue(Files.isRegularFile(vertexPath), "missing $vertexPath")
        assertTrue(Files.isRegularFile(fragmentPath), "missing $fragmentPath")

        val definition = Files.newBufferedReader(definitionPath).use(JsonParser::parseReader).asJsonObject
        val uniforms = definition.getAsJsonArray("uniforms")
            .map { it.asJsonObject.get("name").asString }
            .toSet()
        assertEquals(expectedUniforms, uniforms)
        assertEquals("destiny2-mod:$name", definition.get("vertex").asString)
        assertEquals("destiny2-mod:$name", definition.get("fragment").asString)
    }
}
