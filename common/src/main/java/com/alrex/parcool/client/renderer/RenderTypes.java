package com.alrex.parcool.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * ParCool's own render types.
 *
 * <h2>What 1.21.2 changed</h2>
 * 1.21.2 moved the entity renderers onto render states ({@code extractRenderState} +
 * {@code EntityRenderer#render(EntityRenderState, ...)}) but kept {@code RenderType} exactly as 1.21.1
 * had it: a {@link RenderStateShard} composite state around a vertex format. {@code RenderSetup} /
 * {@code RenderPipeline} only arrive in 1.21.11, so this is still the 1.21.1 form.
 *
 * <p>Two types rather than vanilla's leash pipeline, because the zipline rope emits {@code QUADS} and
 * has a culled and an unculled variant (the {@code Enable3DRenderingForZipline} config switch). The
 * shaders are the same {@code core/rendertype_leash} pair that {@code RENDERTYPE_LEASH_SHADER} selects,
 * and the lightmap is still requested through {@code RenderStateShard#LIGHTMAP}, so the rope looks
 * the same as it did in 1.21.1.
 */
public class RenderTypes {
    public static final RenderType ZIPLINE_3D;
    public static final RenderType ZIPLINE_2D;

    static {
        ZIPLINE_2D = RenderType.create(
                "zipline2d",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
                VertexFormat.Mode.QUADS, 256,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        );
        ZIPLINE_3D = RenderType.create(
                "zipline3d",
                DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
                VertexFormat.Mode.QUADS, 256,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setCullState(RenderStateShard.CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        );
    }
}
