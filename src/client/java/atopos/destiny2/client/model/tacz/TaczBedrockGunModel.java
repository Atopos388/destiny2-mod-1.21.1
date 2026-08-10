package atopos.destiny2.client.model.tacz;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal TaCZ-compatible Bedrock gun model runtime.
 *
 * <p>This deliberately follows TaCZ 1.21.1's BedrockModel/BedrockPart
 * coordinate rules instead of SimpleBedrockModel's unrelated model space.
 * It only contains the gun-pack responsibilities needed by this mod:
 * Bedrock geometry, authored positioning bones, animation transforms and
 * first-person hand locators.</p>
 */
public final class TaczBedrockGunModel {
    public static final String LEFT_HAND_POS = "lefthand_pos";
    public static final String RIGHT_HAND_POS = "righthand_pos";
    public static final String CONSTRAINT = "constraint";

    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();

    private @Nullable HandPose leftHandPose;
    private @Nullable HandPose rightHandPose;
    private final Vector3f constraintTranslationFreedom = new Vector3f();
    private final Vector3f constraintRotationFreedom = new Vector3f();

    public TaczBedrockGunModel(byte[] bytes) {
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8
        )) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Bedrock geometry", exception);
        }

        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new IllegalArgumentException("Missing minecraft:geometry");
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = intValue(description, "texture_width", 16);
        int textureHeight = intValue(description, "texture_height", 16);
        JsonArray rawBones = geometry.getAsJsonArray("bones");
        if (rawBones == null) {
            throw new IllegalArgumentException("Missing geometry bones");
        }

        // TaCZ creates all parts first because parent links and relative pivots
        // are resolved in a second pass.
        for (JsonElement element : rawBones) {
            JsonObject raw = element.getAsJsonObject();
            String name = stringValue(raw, "name", null);
            if (name == null || name.isBlank()) {
                continue;
            }
            Bone bone = new Bone(name);
            bone.parentName = stringValue(raw, "parent", null);
            bone.rawPivot = vector(raw.get("pivot"), 0.0F, 0.0F, 0.0F);
            bone.rawRotation = vector(raw.get("rotation"), 0.0F, 0.0F, 0.0F);
            bones.putIfAbsent(name, bone);
        }

        for (JsonElement element : rawBones) {
            JsonObject raw = element.getAsJsonObject();
            Bone bone = bones.get(stringValue(raw, "name", ""));
            if (bone == null) {
                continue;
            }
            Bone parent = bone.parentName == null ? null : bones.get(bone.parentName);
            bone.parent = parent;
            if (parent == null) {
                bone.x = bone.rawPivot.x;
                bone.y = 24.0F - bone.rawPivot.y;
                bone.z = bone.rawPivot.z;
                roots.add(bone);
            } else {
                bone.x = bone.rawPivot.x - parent.rawPivot.x;
                bone.y = parent.rawPivot.y - bone.rawPivot.y;
                bone.z = bone.rawPivot.z - parent.rawPivot.z;
                parent.children.add(bone);
            }
            bone.xRot = radians(bone.rawRotation.x);
            bone.yRot = radians(bone.rawRotation.y);
            bone.zRot = radians(bone.rawRotation.z);

            JsonArray cubes = raw.getAsJsonArray("cubes");
            if (cubes != null) {
                for (JsonElement cubeElement : cubes) {
                    loadCube(
                            bone,
                            cubeElement.getAsJsonObject(),
                            textureWidth,
                            textureHeight
                    );
                }
            }
            JsonObject polyMesh = raw.getAsJsonObject("poly_mesh");
            if (polyMesh != null) {
                loadPolyMesh(bone, polyMesh, textureWidth, textureHeight);
            }
        }
    }

    public boolean hasBone(String name) {
        return bones.containsKey(name);
    }

    public void resetAnimation() {
        constraintTranslationFreedom.zero();
        constraintRotationFreedom.zero();
        for (Bone bone : bones.values()) {
            bone.offsetX = 0.0F;
            bone.offsetY = 0.0F;
            bone.offsetZ = 0.0F;
            bone.animationRotation.identity();
            bone.scaleX = 1.0F;
            bone.scaleY = 1.0F;
            bone.scaleZ = 1.0F;
        }
    }

    /**
     * Applies TaCZ's Bedrock animation listener conventions.
     * Position is relative Bedrock pixels; Y is inverted and all components
     * become model blocks. Rotation is a relative Z-Y-X quaternion.
     */
    public void applyAnimation(
            String boneName,
            @Nullable Vector3f position,
            @Nullable Vector3f rotationRadians,
            @Nullable Vector3f scale
    ) {
        if (CONSTRAINT.equals(boneName)) {
            if (position != null) {
                constraintTranslationFreedom.set(
                        freedom(position.x), freedom(position.y), freedom(position.z)
                );
            }
            if (rotationRadians != null) {
                constraintRotationFreedom.set(
                        freedom((float) Math.toDegrees(rotationRadians.x)),
                        freedom((float) Math.toDegrees(rotationRadians.y)),
                        freedom((float) Math.toDegrees(rotationRadians.z))
                );
            }
            return;
        }
        Bone bone = bones.get(boneName);
        if (bone == null) {
            return;
        }
        if (position != null) {
            bone.offsetX = position.x / 16.0F;
            bone.offsetY = -position.y / 16.0F;
            bone.offsetZ = position.z / 16.0F;
        }
        if (rotationRadians != null) {
            bone.animationRotation.rotationZYX(
                    rotationRadians.z,
                    rotationRadians.y,
                    rotationRadians.x
            );
        }
        if (scale != null) {
            bone.scaleX = scale.x;
            bone.scaleY = scale.y;
            bone.scaleZ = scale.z;
        }
    }

    /**
     * Applies a TaCZ blending track. Shooting tracks use this path so rapid
     * shots can overlap instead of restarting and replacing the current pose.
     */
    public void blendAnimation(
            String boneName,
            @Nullable Vector3f position,
            @Nullable Vector3f rotationRadians,
            @Nullable Vector3f scale
    ) {
        if (CONSTRAINT.equals(boneName)) {
            if (position != null) {
                constraintTranslationFreedom.max(new Vector3f(
                        freedom(position.x), freedom(position.y), freedom(position.z)
                ));
            }
            if (rotationRadians != null) {
                constraintRotationFreedom.max(new Vector3f(
                        freedom((float) Math.toDegrees(rotationRadians.x)),
                        freedom((float) Math.toDegrees(rotationRadians.y)),
                        freedom((float) Math.toDegrees(rotationRadians.z))
                ));
            }
            return;
        }
        Bone bone = bones.get(boneName);
        if (bone == null) {
            return;
        }
        if (position != null) {
            bone.offsetX += position.x / 16.0F;
            bone.offsetY -= position.y / 16.0F;
            bone.offsetZ += position.z / 16.0F;
        }
        if (rotationRadians != null) {
            Vector3f currentZYX = bone.animationRotation.getEulerAnglesZYX(new Vector3f());
            bone.animationRotation.rotationZYX(
                    currentZYX.z + rotationRadians.z,
                    currentZYX.y + rotationRadians.y,
                    currentZYX.x + rotationRadians.x
            );
        }
        if (scale != null) {
            bone.scaleX *= scale.x;
            bone.scaleY *= scale.y;
            bone.scaleZ *= scale.z;
        }
    }

    /**
     * Exact counterpart of TaCZ FirstPersonRenderGunEvent's positioning-node
     * inverse. Animation offsets intentionally do not participate.
     */
    public @Nullable Matrix4f positioningInverse(String boneName) {
        Bone target = bones.get(boneName);
        if (target == null) {
            return null;
        }
        List<Bone> path = new ArrayList<>();
        for (Bone cursor = target; cursor != null; cursor = cursor.parent) {
            path.add(0, cursor);
        }
        Matrix4f result = new Matrix4f().identity();
        for (int index = path.size() - 1; index >= 0; index--) {
            Bone bone = path.get(index);
            result.rotateX(-bone.xRot);
            result.rotateY(-bone.yRot);
            result.rotateZ(-bone.zRot);
            if (bone.parent != null) {
                result.translate(-bone.x / 16.0F, -bone.y / 16.0F, -bone.z / 16.0F);
            } else {
                result.translate(
                        -bone.x / 16.0F,
                        1.5F - bone.y / 16.0F,
                        -bone.z / 16.0F
                );
            }
        }
        return result;
    }

    /**
     * Applies TaCZ's inverse-animation constraint (ICA) around the authored
     * {@code constraint} point. The constraint animation channels are
     * freedoms in [0, 1], not ordinary bone transforms: 0 locks an axis and
     * 1 leaves the animation untouched.
     */
    public void applyAnimationConstraint(PoseStack poseStack, float weight) {
        Bone constraint = bones.get(CONSTRAINT);
        if (constraint == null || weight <= 0.0F) {
            return;
        }

        List<Bone> path = new ArrayList<>();
        for (Bone cursor = constraint; cursor != null; cursor = cursor.parent) {
            path.add(0, cursor);
        }

        Matrix4f animatedMatrix = new Matrix4f().identity();
        Matrix4f originMatrix = new Matrix4f().identity();
        for (Bone bone : path) {
            boolean isConstraint = bone == constraint;
            if (!isConstraint) {
                animatedMatrix.translate(bone.offsetX, bone.offsetY, bone.offsetZ);
            }
            translatePathBone(animatedMatrix, bone);
            rotateBindPose(animatedMatrix, bone);
            if (!isConstraint) {
                animatedMatrix.rotate(bone.animationRotation);
            }

            translatePathBone(originMatrix, bone);
            rotateBindPose(originMatrix, bone);
        }

        Vector3f originTranslation = originMatrix.getTranslation(new Vector3f());
        Vector3f animatedTranslation = animatedMatrix.getTranslation(new Vector3f());
        Vector3f inverseTranslation = originTranslation.sub(animatedTranslation, new Vector3f());
        inverseTranslation.mulDirection(poseStack.last().pose());
        inverseTranslation.mul(
                constraintTranslationFreedom.x - 1.0F,
                constraintTranslationFreedom.y - 1.0F,
                1.0F - constraintTranslationFreedom.z
        );

        // Do not subtract absolute Euler angles here. A constraint chain near
        // a 90-degree bind rotation can represent two almost identical poses
        // with Euler values a half-turn apart, which flips the muzzle toward
        // the camera at full ADS. Resolve the shortest relative quaternion
        // first, then expose its small per-axis delta for TaCZ ICA weighting.
        Quaternionf originRotation = originMatrix.getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf animatedRotation = animatedMatrix.getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf relativeRotation = new Quaternionf(originRotation)
                .conjugate()
                .mul(animatedRotation)
                .normalize();
        if (relativeRotation.w < 0.0F) {
            relativeRotation.set(
                    -relativeRotation.x,
                    -relativeRotation.y,
                    -relativeRotation.z,
                    -relativeRotation.w
            );
        }
        Vector3f inverseRotation = relativeRotation.getEulerAnglesZYX(new Vector3f());
        inverseRotation.mul(
                constraintRotationFreedom.x - 1.0F,
                constraintRotationFreedom.y - 1.0F,
                constraintRotationFreedom.z - 1.0F
        );

        poseStack.translate(animatedTranslation.x, animatedTranslation.y + 1.5F, animatedTranslation.z);
        poseStack.mulPose(Axis.XP.rotation(inverseRotation.x * weight));
        poseStack.mulPose(Axis.YP.rotation(inverseRotation.y * weight));
        poseStack.mulPose(Axis.ZP.rotation(inverseRotation.z * weight));
        poseStack.translate(-animatedTranslation.x, -animatedTranslation.y - 1.5F, -animatedTranslation.z);

        Matrix4f pose = poseStack.last().pose();
        pose.m30(pose.m30() - inverseTranslation.x * weight);
        pose.m31(pose.m31() - inverseTranslation.y * weight);
        pose.m32(pose.m32() + inverseTranslation.z * weight);
    }

    private static void translatePathBone(Matrix4f matrix, Bone bone) {
        if (bone.parent != null) {
            matrix.translate(bone.x / 16.0F, bone.y / 16.0F, bone.z / 16.0F);
        } else {
            matrix.translate(bone.x / 16.0F, bone.y / 16.0F - 1.5F, bone.z / 16.0F);
        }
    }

    private static void rotateBindPose(Matrix4f matrix, Bone bone) {
        matrix.rotateZ(bone.zRot);
        matrix.rotateY(bone.yRot);
        matrix.rotateX(bone.xRot);
    }

    private static float freedom(float value) {
        return Math.min(1.0F, Math.abs(value));
    }

    public void render(
            PoseStack poseStack,
            ItemDisplayContext context,
            VertexConsumer consumer,
            int light,
            int overlay
    ) {
        leftHandPose = null;
        rightHandPose = null;
        for (Bone root : roots) {
            renderBone(root, poseStack, context, consumer, light, overlay);
        }
    }

    public @Nullable HandPose getLeftHandPose() {
        return leftHandPose;
    }

    public @Nullable HandPose getRightHandPose() {
        return rightHandPose;
    }

    private void renderBone(
            Bone bone,
            PoseStack poseStack,
            ItemDisplayContext context,
            VertexConsumer consumer,
            int light,
            int overlay
    ) {
        poseStack.pushPose();
        bone.transform(poseStack);

        // TaCZ replaces these guide-cube nodes with functional arm renderers.
        // They are never emitted as gun geometry in any view.
        if (LEFT_HAND_POS.equals(bone.name) || RIGHT_HAND_POS.equals(bone.name)) {
            if (context.firstPerson()) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                HandPose handPose = new HandPose(
                        new Matrix4f(poseStack.last().pose()),
                        new Matrix3f(poseStack.last().normal())
                );
                if (LEFT_HAND_POS.equals(bone.name)) {
                    leftHandPose = handPose;
                } else {
                    rightHandPose = handPose;
                }
            }
            poseStack.popPose();
            return;
        }

        for (Cube cube : bone.cubes) {
            cube.render(poseStack.last(), consumer, light, overlay);
        }
        for (Bone child : bone.children) {
            renderBone(child, poseStack, context, consumer, light, overlay);
        }
        poseStack.popPose();
    }

    private void loadCube(Bone bone, JsonObject raw, int textureWidth, int textureHeight) {
        Vector3f origin = vector(raw.get("origin"), 0.0F, 0.0F, 0.0F);
        Vector3f size = vector(raw.get("size"), 0.0F, 0.0F, 0.0F);
        float inflate = floatValue(raw, "inflate", 0.0F);
        boolean mirror = booleanValue(raw, "mirror", false);
        JsonElement rotationElement = raw.get("rotation");

        if (rotationElement == null || rotationElement.isJsonNull()) {
            float x = origin.x - bone.rawPivot.x;
            float y = bone.rawPivot.y - origin.y - size.y;
            float z = origin.z - bone.rawPivot.z;
            bone.cubes.add(createCube(raw.get("uv"), x, y, z, size, inflate, mirror, textureWidth, textureHeight));
            return;
        }

        Vector3f pivot = vector(raw.get("pivot"), bone.rawPivot.x, bone.rawPivot.y, bone.rawPivot.z);
        Vector3f rotation = vector(rotationElement, 0.0F, 0.0F, 0.0F);
        Bone cubePart = new Bone(null);
        cubePart.x = pivot.x - bone.rawPivot.x;
        cubePart.y = bone.rawPivot.y - pivot.y;
        cubePart.z = pivot.z - bone.rawPivot.z;
        cubePart.xRot = radians(rotation.x);
        cubePart.yRot = radians(rotation.y);
        cubePart.zRot = radians(rotation.z);
        cubePart.parent = bone;
        float x = origin.x - pivot.x;
        float y = pivot.y - origin.y - size.y;
        float z = origin.z - pivot.z;
        cubePart.cubes.add(createCube(raw.get("uv"), x, y, z, size, inflate, mirror, textureWidth, textureHeight));
        bone.children.add(cubePart);
    }

    /**
     * Loads Blockbench's Bedrock {@code poly_mesh} extension into the same
     * renderable used by cube geometry. Positions remain in Bedrock pixels so
     * bind pivots, animation transforms and authored cube models share one
     * coordinate space.
     */
    private static void loadPolyMesh(
            Bone bone,
            JsonObject raw,
            int textureWidth,
            int textureHeight
    ) {
        JsonArray rawPositions = raw.getAsJsonArray("positions");
        JsonArray rawUvs = raw.getAsJsonArray("uvs");
        JsonArray rawNormals = raw.getAsJsonArray("normals");
        JsonArray rawPolygons = raw.getAsJsonArray("polys");
        if (rawPositions == null || rawPolygons == null || rawPositions.isEmpty()) {
            return;
        }

        List<Vector3f> positions = new ArrayList<>(rawPositions.size());
        for (JsonElement element : rawPositions) {
            Vector3f source = vector(element, 0.0F, 0.0F, 0.0F);
            positions.add(new Vector3f(
                    source.x - bone.rawPivot.x,
                    bone.rawPivot.y - source.y,
                    source.z - bone.rawPivot.z
            ));
        }

        List<float[]> uvs = new ArrayList<>();
        boolean normalizedUvs = booleanValue(raw, "normalized_uvs", true);
        if (rawUvs != null) {
            for (JsonElement element : rawUvs) {
                JsonArray uv = element.isJsonArray() ? element.getAsJsonArray() : null;
                float u = uv != null && !uv.isEmpty() ? numeric(uv.get(0), 0.0F) : 0.0F;
                float v = uv != null && uv.size() > 1 ? numeric(uv.get(1), 0.0F) : 0.0F;
                if (normalizedUvs) {
                    // Blockbench's OBJ-converted poly_mesh stores normalized
                    // V from the lower edge. Minecraft textures address V
                    // from the upper edge, so preserve U and flip only V.
                    v = 1.0F - v;
                } else {
                    u /= Math.max(1, textureWidth);
                    v = 1.0F - v / Math.max(1, textureHeight);
                }
                uvs.add(new float[]{u, v});
            }
        }

        List<Vector3f> normals = new ArrayList<>();
        if (rawNormals != null) {
            for (JsonElement element : rawNormals) {
                Vector3f source = vector(element, 0.0F, 1.0F, 0.0F);
                Vector3f converted = new Vector3f(source.x, -source.y, source.z);
                normals.add(converted.lengthSquared() > 1.0E-8F
                        ? converted.normalize()
                        : new Vector3f(0.0F, 1.0F, 0.0F));
            }
        }

        List<Polygon> polygons = new ArrayList<>(rawPolygons.size());
        for (JsonElement polygonElement : rawPolygons) {
            if (!polygonElement.isJsonArray()) {
                continue;
            }
            JsonArray points = polygonElement.getAsJsonArray();
            int pointCount = points.size();
            if (pointCount > 3 && sameMeshPoint(points.get(0), points.get(pointCount - 1))) {
                pointCount--;
            }
            if (pointCount < 3) {
                continue;
            }

            Vertex[] vertices = new Vertex[pointCount];
            boolean valid = true;
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                JsonElement pointElement = points.get(pointIndex);
                if (!pointElement.isJsonArray()) {
                    valid = false;
                    break;
                }
                JsonArray indices = pointElement.getAsJsonArray();
                int positionIndex = meshIndex(indices, 0);
                if (positionIndex < 0 || positionIndex >= positions.size()) {
                    valid = false;
                    break;
                }
                Vector3f position = positions.get(positionIndex);
                Vertex vertex = new Vertex(position.x, position.y, position.z);

                int uvIndex = meshIndex(indices, 2);
                if (uvIndex < 0 || uvIndex >= uvs.size()) {
                    uvIndex = meshIndex(indices, 1);
                }
                if (uvIndex >= 0 && uvIndex < uvs.size()) {
                    float[] uv = uvs.get(uvIndex);
                    vertex.u = uv[0];
                    vertex.v = uv[1];
                }

                int normalIndex = meshIndex(indices, 1);
                if (normalIndex >= 0 && normalIndex < normals.size()) {
                    vertex.normal = new Vector3f(normals.get(normalIndex));
                }
                vertices[pointIndex] = vertex;
            }
            if (!valid) {
                continue;
            }

            // The entity cutout render type consumes quads. Fan-triangulate
            // arbitrary polygons and repeat the third point for each triangle.
            for (int index = 1; index < vertices.length - 1; index++) {
                Vertex first = vertices[0];
                Vertex second = vertices[index];
                Vertex third = vertices[index + 1];
                Vector3f normal = faceNormal(first.position, second.position, third.position);
                polygons.add(new Polygon(new Vertex[]{first, second, third, third}, normal));
            }
        }
        if (!polygons.isEmpty()) {
            bone.cubes.add(new Cube(polygons));
        }
    }

    private static boolean sameMeshPoint(JsonElement first, JsonElement second) {
        return first != null && second != null && first.equals(second);
    }

    private static int meshIndex(JsonArray indices, int index) {
        if (index < 0 || index >= indices.size()) {
            return -1;
        }
        try {
            return indices.get(index).getAsInt();
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static Vector3f faceNormal(Vector3f first, Vector3f second, Vector3f third) {
        Vector3f normal = new Vector3f(second).sub(first).cross(new Vector3f(third).sub(first));
        return normal.lengthSquared() > 1.0E-8F
                ? normal.normalize()
                : new Vector3f(0.0F, 1.0F, 0.0F);
    }

    private static Cube createCube(
            @Nullable JsonElement uvElement,
            float x,
            float y,
            float z,
            Vector3f size,
            float inflate,
            boolean mirror,
            int textureWidth,
            int textureHeight
    ) {
        if (uvElement != null && uvElement.isJsonObject()) {
            return Cube.perFace(
                    x, y, z,
                    size.x, size.y, size.z,
                    inflate,
                    textureWidth,
                    textureHeight,
                    uvElement.getAsJsonObject()
            );
        }
        Vector3f uv = vector(uvElement, 0.0F, 0.0F, 0.0F);
        return Cube.box(
                uv.x, uv.y,
                x, y, z,
                size.x, size.y, size.z,
                inflate,
                mirror,
                textureWidth,
                textureHeight
        );
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static Vector3f vector(
            @Nullable JsonElement element,
            float defaultX,
            float defaultY,
            float defaultZ
    ) {
        if (element == null || !element.isJsonArray()) {
            return new Vector3f(defaultX, defaultY, defaultZ);
        }
        JsonArray array = element.getAsJsonArray();
        return new Vector3f(
                array.size() > 0 ? numeric(array.get(0), defaultX) : defaultX,
                array.size() > 1 ? numeric(array.get(1), defaultY) : defaultY,
                array.size() > 2 ? numeric(array.get(2), defaultZ) : defaultZ
        );
    }

    private static float numeric(JsonElement element, float fallback) {
        try {
            return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                    ? element.getAsFloat()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static @Nullable String stringValue(
            @Nullable JsonObject object,
            String key,
            @Nullable String fallback
    ) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(@Nullable JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        return numeric(object.get(key), fallback);
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public record HandPose(Matrix4f pose, Matrix3f normal) {
    }

    private static final class Bone {
        private final @Nullable String name;
        private @Nullable String parentName;
        private @Nullable Bone parent;
        private Vector3f rawPivot = new Vector3f();
        private Vector3f rawRotation = new Vector3f();
        private final List<Bone> children = new ArrayList<>();
        private final List<Cube> cubes = new ArrayList<>();
        private float x;
        private float y;
        private float z;
        private float xRot;
        private float yRot;
        private float zRot;
        private float offsetX;
        private float offsetY;
        private float offsetZ;
        private final Quaternionf animationRotation = new Quaternionf();
        private float scaleX = 1.0F;
        private float scaleY = 1.0F;
        private float scaleZ = 1.0F;

        private Bone(@Nullable String name) {
            this.name = name;
        }

        private void transform(PoseStack poseStack) {
            poseStack.translate(offsetX, offsetY, offsetZ);
            poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
            if (zRot != 0.0F) {
                poseStack.mulPose(Axis.ZP.rotation(zRot));
            }
            if (yRot != 0.0F) {
                poseStack.mulPose(Axis.YP.rotation(yRot));
            }
            if (xRot != 0.0F) {
                poseStack.mulPose(Axis.XP.rotation(xRot));
            }
            poseStack.mulPose(animationRotation);
            poseStack.scale(scaleX, scaleY, scaleZ);
        }
    }

    private static final class Cube {
        private final List<Polygon> polygons;

        private Cube(List<Polygon> polygons) {
            this.polygons = polygons;
        }

        private static Cube box(
                float textureX,
                float textureY,
                float x,
                float y,
                float z,
                float width,
                float height,
                float depth,
                float inflate,
                boolean mirror,
                float textureWidth,
                float textureHeight
        ) {
            float xEnd = x + width;
            float yEnd = y + height;
            float zEnd = z + depth;
            x -= inflate;
            y -= inflate;
            z -= inflate;
            xEnd += inflate;
            yEnd += inflate;
            zEnd += inflate;
            if (mirror) {
                float swap = xEnd;
                xEnd = x;
                x = swap;
            }

            Vertex v1 = new Vertex(x, y, z);
            Vertex v2 = new Vertex(xEnd, y, z);
            Vertex v3 = new Vertex(xEnd, yEnd, z);
            Vertex v4 = new Vertex(x, yEnd, z);
            Vertex v5 = new Vertex(x, y, zEnd);
            Vertex v6 = new Vertex(xEnd, y, zEnd);
            Vertex v7 = new Vertex(xEnd, yEnd, zEnd);
            Vertex v8 = new Vertex(x, yEnd, zEnd);

            int dx = (int) width;
            int dy = (int) height;
            int dz = (int) depth;
            float p1 = textureX + dz;
            float p2 = textureX + dz + dx;
            float p3 = textureX + dz + dx + dx;
            float p4 = textureX + dz + dx + dz;
            float p5 = textureX + dz + dx + dz + dx;
            float p6 = textureY + dz;
            float p7 = textureY + dz + dy;
            float p8 = textureY;
            float p9 = textureX;

            List<Polygon> polygons = new ArrayList<>(6);
            polygons.add(new Polygon(new Vertex[]{v6, v5, v1, v2}, p1, p8, p2, p6, textureWidth, textureHeight, mirror, Direction.DOWN));
            polygons.add(new Polygon(new Vertex[]{v3, v4, v8, v7}, p2, p6, p3, p8, textureWidth, textureHeight, mirror, Direction.UP));
            polygons.add(new Polygon(new Vertex[]{v1, v5, v8, v4}, p9, p6, p1, p7, textureWidth, textureHeight, mirror, Direction.WEST));
            polygons.add(new Polygon(new Vertex[]{v2, v1, v4, v3}, p1, p6, p2, p7, textureWidth, textureHeight, mirror, Direction.NORTH));
            polygons.add(new Polygon(new Vertex[]{v6, v2, v3, v7}, p2, p6, p4, p7, textureWidth, textureHeight, mirror, Direction.EAST));
            polygons.add(new Polygon(new Vertex[]{v5, v6, v7, v8}, p4, p6, p5, p7, textureWidth, textureHeight, mirror, Direction.SOUTH));
            return new Cube(polygons);
        }

        private static Cube perFace(
                float x,
                float y,
                float z,
                float width,
                float height,
                float depth,
                float inflate,
                float textureWidth,
                float textureHeight,
                JsonObject faces
        ) {
            float xEnd = x + width;
            float yEnd = y + height;
            float zEnd = z + depth;
            x -= inflate;
            y -= inflate;
            z -= inflate;
            xEnd += inflate;
            yEnd += inflate;
            zEnd += inflate;

            Vertex v1 = new Vertex(x, y, z);
            Vertex v2 = new Vertex(xEnd, y, z);
            Vertex v3 = new Vertex(xEnd, yEnd, z);
            Vertex v4 = new Vertex(x, yEnd, z);
            Vertex v5 = new Vertex(x, y, zEnd);
            Vertex v6 = new Vertex(xEnd, y, zEnd);
            Vertex v7 = new Vertex(xEnd, yEnd, zEnd);
            Vertex v8 = new Vertex(x, yEnd, zEnd);

            List<Polygon> polygons = new ArrayList<>(6);
            // TaCZ remaps Bedrock face names onto Java's cube directions.
            // EAST/WEST and UP/DOWN are intentionally exchanged here.
            addFace(polygons, faces, "up", new Vertex[]{v6, v5, v1, v2}, Direction.DOWN, textureWidth, textureHeight);
            addFace(polygons, faces, "down", new Vertex[]{v3, v4, v8, v7}, Direction.UP, textureWidth, textureHeight);
            addFace(polygons, faces, "east", new Vertex[]{v1, v5, v8, v4}, Direction.WEST, textureWidth, textureHeight);
            addFace(polygons, faces, "north", new Vertex[]{v2, v1, v4, v3}, Direction.NORTH, textureWidth, textureHeight);
            addFace(polygons, faces, "west", new Vertex[]{v6, v2, v3, v7}, Direction.EAST, textureWidth, textureHeight);
            addFace(polygons, faces, "south", new Vertex[]{v5, v6, v7, v8}, Direction.SOUTH, textureWidth, textureHeight);
            return new Cube(polygons);
        }

        private static void addFace(
                List<Polygon> output,
                JsonObject faces,
                String name,
                Vertex[] vertices,
                Direction direction,
                float textureWidth,
                float textureHeight
        ) {
            JsonObject face = faces.getAsJsonObject(name);
            if (face == null || !face.has("uv")) {
                return;
            }
            Vector3f uv = vector(face.get("uv"), 0.0F, 0.0F, 0.0F);
            Vector3f uvSize = vector(face.get("uv_size"), 0.0F, 0.0F, 0.0F);
            output.add(new Polygon(
                    vertices,
                    uv.x,
                    uv.y,
                    uv.x + uvSize.x,
                    uv.y + uvSize.y,
                    textureWidth,
                    textureHeight,
                    false,
                    direction
            ));
        }

        private void render(
                PoseStack.Pose pose,
                VertexConsumer consumer,
                int light,
                int overlay
        ) {
            Matrix4f matrix = pose.pose();
            Matrix3f normalMatrix = pose.normal();
            int color = FastColor.ARGB32.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F);
            for (Polygon polygon : polygons) {
                for (Vertex vertex : polygon.vertices) {
                    Vector3f normal = new Vector3f(
                            vertex.normal != null ? vertex.normal : polygon.normal
                    ).mul(normalMatrix).normalize();
                    Vector4f position = new Vector4f(
                            vertex.position.x / 16.0F,
                            vertex.position.y / 16.0F,
                            vertex.position.z / 16.0F,
                            1.0F
                    ).mul(matrix);
                    consumer.addVertex(
                            position.x,
                            position.y,
                            position.z,
                            color,
                            vertex.u,
                            vertex.v,
                            overlay,
                            light,
                            normal.x,
                            normal.y,
                            normal.z
                    );
                }
            }
        }
    }

    private static final class Vertex {
        private final Vector3f position;
        private @Nullable Vector3f normal;
        private float u;
        private float v;

        private Vertex(float x, float y, float z) {
            position = new Vector3f(x, y, z);
        }

        private Vertex copy() {
            Vertex copy = new Vertex(position.x, position.y, position.z);
            copy.u = u;
            copy.v = v;
            copy.normal = normal == null ? null : new Vector3f(normal);
            return copy;
        }
    }

    private static final class Polygon {
        private final Vertex[] vertices;
        private final Vector3f normal;

        private Polygon(Vertex[] source, Vector3f normal) {
            vertices = new Vertex[source.length];
            for (int index = 0; index < source.length; index++) {
                vertices[index] = source[index].copy();
            }
            this.normal = new Vector3f(normal);
        }

        private Polygon(
                Vertex[] source,
                float u1,
                float v1,
                float u2,
                float v2,
                float textureWidth,
                float textureHeight,
                boolean mirror,
                Direction direction
        ) {
            vertices = new Vertex[source.length];
            for (int index = 0; index < source.length; index++) {
                vertices[index] = source[index].copy();
            }
            remap(vertices[0], u2 / textureWidth, v1 / textureHeight);
            remap(vertices[1], u1 / textureWidth, v1 / textureHeight);
            remap(vertices[2], u1 / textureWidth, v2 / textureHeight);
            remap(vertices[3], u2 / textureWidth, v2 / textureHeight);
            if (mirror) {
                for (int index = 0; index < vertices.length / 2; index++) {
                    Vertex swap = vertices[index];
                    vertices[index] = vertices[vertices.length - 1 - index];
                    vertices[vertices.length - 1 - index] = swap;
                }
            }
            normal = new Vector3f(direction.step());
            if (mirror) {
                normal.mul(-1.0F, 1.0F, 1.0F);
            }
        }

        private static void remap(Vertex vertex, float u, float v) {
            vertex.u = u;
            vertex.v = v;
        }
    }
}
