package com.alrex.parcool.client.renderer.entity;

import com.alrex.parcool.client.renderer.RenderTypes;
import com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity;
import com.alrex.parcool.common.zipline.Zipline;
import com.alrex.parcool.config.ParCoolConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The rope between two zipline hooks.
 *
 * <h2>What changed in 1.21.3</h2>
 * 1.21.2 split the entity renderer in two: {@code EntityRenderer} no longer receives the entity
 * while drawing, it extracts a reusable {@link EntityRenderState} from it and then renders only that.
 * So the rope data is copied into {@link RopeRenderState} in {@link #extractRenderState} and the
 * geometry is emitted from {@link #render}. The light levels are the reason the split has to be
 * handled at all: they are read off the entity and the level, and neither is reachable from the
 * render state, so they are sampled during extraction.
 *
 * <p>1.21.3 is <b>not</b> far enough along for the 1.21.11 shape of that rework: there is no
 * {@code SubmitNodeCollector} yet and {@code render} still takes the {@link MultiBufferSource}, so
 * the buffer is requested here rather than handed to a custom-geometry submission. The vertex maths
 * itself is untouched.
 *
 * <p>{@code getBoundingBoxForCulling} left {@code Entity} in the same rework and now lives on the
 * renderer; it reads {@link ZiplineRopeEntity#getCullingBoundingBox()}.
 */
public class ZiplineRopeRenderer extends EntityRenderer<ZiplineRopeEntity, ZiplineRopeRenderer.RopeRenderState> {

    public ZiplineRopeRenderer(EntityRendererProvider.Context p_i46179_1_) {
        super(p_i46179_1_);
    }

    @Override
    public RopeRenderState createRenderState() {
        return new RopeRenderState();
    }

    @Override
    public void extractRenderState(ZiplineRopeEntity entity, RopeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        BlockPos start = entity.getStartPos();
        BlockPos end = entity.getEndPos();
        state.startPos = start;
        state.endPos = end;
        state.color = entity.getColor();
        state.zipline = entity.getZipline();
        state.render3d = ParCoolConfig.Client.Booleans.Enable3DRenderingForZipline.get();
        // The light has to be sampled while the entity is still around: render() only sees the render
        // state, so the four levels the vertex maths needs are resolved here, exactly the values the
        // 1.21.1 port read inside render().
        state.startBlockLight = getBlockLightLevel(entity, start);
        state.endBlockLight = getBlockLightLevel(entity, end);
        state.startSkyLight = entity.level().getBrightness(LightLayer.SKY, start);
        state.endSkyLight = entity.level().getBrightness(LightLayer.SKY, end);
    }

    @Override
    public boolean shouldRender(ZiplineRopeEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z);
    }

    /**
     * 1.21.1's {@code Entity#getBoundingBoxForCulling()} override: the rope's own box reaches from hook
     * to hook, which is far bigger than its 0.1 x 0.1 entity box, so without it the rope is culled as
     * soon as the midpoint leaves the frustum.
     */
    @Nonnull
    @Override
    protected AABB getBoundingBoxForCulling(@Nonnull ZiplineRopeEntity entity) {
        AABB culling = entity.getCullingBoundingBox();
        return culling == null ? super.getBoundingBoxForCulling(entity) : culling;
    }

    @Override
    public void render(RopeRenderState state, PoseStack matrixStack, MultiBufferSource multiBufferSource, int light) {
        renderRope(state, matrixStack, multiBufferSource);
    }

    private void renderRope(RopeRenderState state, PoseStack matrixStack, MultiBufferSource multiBufferSource) {
        BlockPos start = state.startPos;
        BlockPos end = state.endPos;
        // The null test has to run before getZipline() is dereferenced: a rope entity whose start/end
        // have not been delivered yet has a null zipline and used to NPE inside the world render, which
        // kills the frame.
        Zipline zipline = state.zipline;
        if (zipline == null || (start == BlockPos.ZERO && end == BlockPos.ZERO)) return;

        int color = state.color;
        float r = ((0xFF0000 & color) >> 16) / 255f;
        float g = ((0x00FF00 & color) >> 8) / 255f;
        float b = (0x0000FF & color) / 255f;

        Vec3 entityPos = new Vec3(state.x, state.y, state.z);
        Vec3 startPos = zipline.getStartPos();
        Vec3 startPosOffset = startPos.subtract(entityPos);
        Vec3 endOffsetFromStart = zipline.getOffsetToEndFromStart();

        boolean render3d = state.render3d;

        matrixStack.pushPose();
        {
            matrixStack.translate(startPosOffset.x(), startPosOffset.y(), startPosOffset.z());
            var vertexConsumer = render3d ?
                    multiBufferSource.getBuffer(RenderTypes.ZIPLINE_3D) :
                    multiBufferSource.getBuffer(RenderTypes.ZIPLINE_2D);
            Matrix4f transformMatrix = matrixStack.last().pose();

            int startBlockLightLevel = state.startBlockLight;
            int endBlockLightLevel = state.endBlockLight;
            int startSkyBrightness = state.startSkyLight;
            int endSkyBrightness = state.endSkyLight;

            int divisionCount = Math.min((int) Math.ceil(endOffsetFromStart.length() / 0.6), 24);
            float invLengthSqrtXZ = (float) Mth.fastInvSqrt(endOffsetFromStart.x() * endOffsetFromStart.x() + endOffsetFromStart.z() * endOffsetFromStart.z());
            float unitLengthX = (float) (endOffsetFromStart.x() * invLengthSqrtXZ);
            float unitLengthZ = (float) (endOffsetFromStart.z() * invLengthSqrtXZ);
            for (int i = 0; i < divisionCount; i++) {
                float colorScale = i % 2 == 0 ? 1f : 0.8f;

                for (int j = 0; j < 2; j++) {
                    if (render3d) {
                        renderRopeSingleBlock3D(
                                transformMatrix, vertexConsumer,
                                zipline,
                                i, divisionCount,
                                unitLengthX, unitLengthZ,
                                startBlockLightLevel, endBlockLightLevel,
                                startSkyBrightness, endSkyBrightness,
                                r * colorScale, g * colorScale, b * colorScale//,
                                //j % 2 == 0
                        );
                    } else {
                        renderRopeSingleBlock2D(
                                transformMatrix, vertexConsumer,
                                zipline,
                                i, divisionCount,
                                unitLengthX, unitLengthZ,
                                startBlockLightLevel, endBlockLightLevel,
                                startSkyBrightness, endSkyBrightness,
                                r * colorScale, g * colorScale, b * colorScale,
                                j % 2 == 0
                        );
                    }
                }
            }
        }
        matrixStack.popPose();
    }

    private void renderRopeSingleBlock2D(
            Matrix4f transformMatrix,
            VertexConsumer vertexConsumer,
            Zipline zipline,
            int currentCount, int maxCount,
            float unitLengthX,
            float unitLengthZ,
            int startBlockLightLevel, int endBlockLightLevel,
            int startSkyBrightness, int endSkyBrightness,
            float r, float g, float b,
            boolean tiltType
    ) {
        for (int i = 0; i < 2; i++) {
            float phase = (float) (currentCount + i) / maxCount;

            int lightLevel = LightTexture.pack((int) Mth.lerp(phase, startBlockLightLevel, endBlockLightLevel), (int) Mth.lerp(phase, startSkyBrightness, endSkyBrightness));
            Vec3 midPointD = zipline.getMidPointOffsetFromStart(phase);
            Vector3f midPoint = new Vector3f((float) midPointD.x(), (float) midPointD.y(), (float) midPointD.z());

            final float width = 0.075f;
            float tilt = zipline.getSlope(phase);
            float tiltInv = Mth.invSqrt(tilt * tilt + 1);
            float yOffset = width * tiltInv / 1.41421356f /*sqrt(2)*/;
            float xBaseOffset = unitLengthX * width * tilt * tiltInv / 1.41421356f;
            float zBaseOffset = unitLengthZ * width * tilt * tiltInv / 1.41421356f;
            float sign = tiltType ? 1 : -1;
            float xOffset = sign * unitLengthZ * width / 1.41421356f;
            float zOffset = sign * -unitLengthX * width / 1.41421356f;

            if (i == 0) {
                vertexConsumer
                        .addVertex(transformMatrix,
                                (midPoint.x() + xBaseOffset + xOffset),
                                (midPoint.y() - yOffset),
                                (midPoint.z() + zBaseOffset + zOffset)
                        )
                        .setColor(r, g, b, 1f)
                        .setLight(lightLevel);
                vertexConsumer
                        .addVertex(transformMatrix,
                                (midPoint.x() - xBaseOffset - xOffset),
                                (midPoint.y() + yOffset),
                                (midPoint.z() - zBaseOffset - zOffset)
                        )
                        .setColor(r, g, b, 1f)
                        .setLight(lightLevel);
            } else {
                vertexConsumer
                        .addVertex(transformMatrix,
                                (midPoint.x() - xBaseOffset - xOffset),
                                (midPoint.y() + yOffset),
                                (midPoint.z() - zBaseOffset - zOffset)
                        )
                        .setColor(r, g, b, 1f)
                        .setLight(lightLevel);
                vertexConsumer
                        .addVertex(transformMatrix,
                                (midPoint.x() + xBaseOffset + xOffset),
                                (midPoint.y() - yOffset),
                                (midPoint.z() + zBaseOffset + zOffset)
                        )
                        .setColor(r, g, b, 1f)
                        .setLight(lightLevel);
            }
        }
    }

    private void renderRopeSingleBlock3D(
            Matrix4f transformMatrix,
            VertexConsumer vertexConsumer,
            Zipline zipline,
            int currentCount, int maxCount,
            float unitLengthX,
            float unitLengthZ,
            int startBlockLightLevel, int endBlockLightLevel,
            int startSkyBrightness, int endSkyBrightness,
            float r, float g, float b
    ) {
        Vector3f[] vertexList = new Vector3f[8];
        int[] lightLevelList = new int[2];
        for (int i = 0; i < 2; i++) {
            float phase = (float) (currentCount + i) / maxCount;

            lightLevelList[i] = LightTexture.pack((int) Mth.lerp(phase, startBlockLightLevel, endBlockLightLevel), (int) Mth.lerp(phase, startSkyBrightness, endSkyBrightness));
            Vec3 midPointD = zipline.getMidPointOffsetFromStart(phase);
            Vector3f midPoint = new Vector3f((float) midPointD.x(), (float) midPointD.y(), (float) midPointD.z());

            final float width = 0.075f;
            float tilt = zipline.getSlope(phase);
            float tiltInv = Mth.invSqrt(tilt * tilt + 1);
            float yOffset = width * tiltInv / 1.41421356f /*sqrt(2)*/;
            float xBaseOffset = unitLengthX * width * tilt * tiltInv / 1.41421356f;
            float zBaseOffset = unitLengthZ * width * tilt * tiltInv / 1.41421356f;
            float xOffset = unitLengthZ * width / 1.41421356f;
            float zOffset = -unitLengthX * width / 1.41421356f;
            vertexList[4 * i] = new Vector3f(
                    (midPoint.x() - xBaseOffset + xOffset),
                    midPoint.y() + yOffset,
                    (midPoint.z() - zBaseOffset + zOffset)
            );
            vertexList[4 * i + 1] = new Vector3f(
                    (midPoint.x() - xBaseOffset - xOffset),
                    midPoint.y() + yOffset,
                    (midPoint.z() - zBaseOffset - zOffset)
            );
            vertexList[4 * i + 2] = new Vector3f(
                    (midPoint.x() + xBaseOffset - xOffset),
                    midPoint.y() - yOffset,
                    (midPoint.z() + zBaseOffset - zOffset)
            );
            vertexList[4 * i + 3] = new Vector3f(
                    (midPoint.x() + xBaseOffset + xOffset),
                    midPoint.y() - yOffset,
                    (midPoint.z() + zBaseOffset + zOffset)
            );
        }
        for (int i = 0; i < 4; i++) {
            vertexConsumer.addVertex(transformMatrix, vertexList[i].x(), vertexList[i].y(), vertexList[i].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[(i + 1) % 4].x(), vertexList[(i + 1) % 4].y(), vertexList[(i + 1) % 4].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[4 + (i + 1) % 4].x(), vertexList[4 + (i + 1) % 4].y(), vertexList[4 + (i + 1) % 4].z()).setColor(r, g, b, 1f).setLight(lightLevelList[1]);
            vertexConsumer.addVertex(transformMatrix, vertexList[4 + i].x(), vertexList[4 + i].y(), vertexList[4 + i].z()).setColor(r, g, b, 1f).setLight(lightLevelList[1]);
        }
        if (currentCount == 0) {
            vertexConsumer.addVertex(transformMatrix, vertexList[3].x(), vertexList[3].y(), vertexList[3].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[2].x(), vertexList[2].y(), vertexList[2].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[1].x(), vertexList[1].y(), vertexList[1].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[0].x(), vertexList[0].y(), vertexList[0].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
        } else if (currentCount == maxCount - 1) {
            vertexConsumer.addVertex(transformMatrix, vertexList[4].x(), vertexList[4].y(), vertexList[4].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[5].x(), vertexList[5].y(), vertexList[5].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[6].x(), vertexList[6].y(), vertexList[6].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
            vertexConsumer.addVertex(transformMatrix, vertexList[7].x(), vertexList[7].y(), vertexList[7].z()).setColor(r, g, b, 1f).setLight(lightLevelList[0]);
        }
    }

    /**
     * The rope's per-frame data. Since the 1.21.2 rework an {@code EntityRenderer} is shared and the
     * render state is a single reused instance, so everything the geometry needs has to be copied out
     * of the entity during extraction rather than read from it while drawing.
     */
    public static class RopeRenderState extends EntityRenderState {
        @Nullable
        public BlockPos startPos;
        @Nullable
        public BlockPos endPos;
        public int color;
        public boolean render3d;
        public int startBlockLight;
        public int endBlockLight;
        public int startSkyLight;
        public int endSkyLight;
        @Nullable
        public Zipline zipline;
    }
}
