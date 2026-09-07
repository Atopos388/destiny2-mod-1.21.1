package atopos.destiny2.common.physics;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class FallenSoldierAssetTest {
    private final Path assets=Path.of("src/main/resources/assets/destiny2-mod");
    @Test void geometryAndAllAnimationTargetsResolve() throws Exception {
        var geo=JsonParser.parseString(Files.readString(assets.resolve("geo/fallen_soldier.geo.json")))
            .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        var names=new HashSet<String>();int cubes=0;
        for(var element:geo.getAsJsonArray("bones")){
            var b=element.getAsJsonObject();assertTrue(names.add(b.get("name").getAsString()));
            if(b.has("cubes"))cubes+=b.getAsJsonArray("cubes").size();
        }
        assertEquals(29,names.size());assertEquals(421,cubes);assertTrue(names.contains("cloth_shawl"));
        for(var element:geo.getAsJsonArray("bones")){
            var b=element.getAsJsonObject();if(b.has("parent"))assertTrue(names.contains(b.get("parent").getAsString()));
        }
        var animations=JsonParser.parseString(Files.readString(assets.resolve("animations/fallen_soldier.animation.json")))
            .getAsJsonObject().getAsJsonObject("animations");assertEquals(7,animations.size());
        for(var animation:animations.entrySet()){
            var bones=animation.getValue().getAsJsonObject().getAsJsonObject("bones");
            for(String name:bones.keySet())assertTrue(names.contains(name));
            assertFalse(bones.has("tabard_front"));assertFalse(bones.has("tabard_back"));
        }
        assertEquals("hold_on_last_frame",animations.getAsJsonObject("animation.fallen.death").get("loop").getAsString());
        assertTrue(Files.size(assets.resolve("textures/entity/fallen_soldier.png"))>1000);
    }
    @Test void independentRegistrationAndClientOnlyPhysics() throws Exception {
        var registry=Files.readString(Path.of("src/main/kotlin/atopos/destiny2/common/entity/DestinyEntities.kt"));
        assertTrue(registry.contains("\"fallen_soldier\""));assertTrue(registry.contains("\"fallen_captain\""));
        var entity=Files.readString(Path.of("src/main/kotlin/atopos/destiny2/common/entity/FallenSoldierEntity.kt"));
        assertFalse(entity.contains("client."));assertFalse(entity.contains("ClothGrid"));
        var renderer=Files.readString(Path.of("src/client/kotlin/atopos/destiny2/client/renderer/FallenSoldierRenderer.kt"));
        assertTrue(renderer.contains("FallenClothRenderer"));assertTrue(renderer.contains("cloth.end()"));
    }
}
