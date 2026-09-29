package com.alrex.parcool.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * ParCool's own render types.
 *
 * <h2>Why two pipelines are built instead of reusing {@code RenderTypes.leash()}</h2>
 * 1.21.11 dropped {@code RenderStateShard} and reduced {@code RenderType} to a named
 * {@link RenderSetup} around a {@link RenderPipeline}, i.e. culling and the vertex format moved into
 * the pipeline. Vanilla's leash pipeline draws {@code POSITION_COLOR_LIGHTMAP} in
 * {@code TRIANGLE_STRIP} with culling off, while the zipline rope emits {@code QUADS} and has a
 * culled and an unculled variant (the {@code Enable3DRenderingForZipline} config switch), so
 * neither vanilla pipeline fits. The shaders are the same
 * {@code core/rendertype_leash} pair the 1.21.1 {@code RENDERTYPE_LEASH_SHADER} shard selected, and
 * the lightmap is still requested through {@code useLightmap()}, so the rope looks the same.
 *
 * <p>{@code RenderPipelines#register} is private, but the map it fills is public, so the two
 * pipelines are registered the same way vanilla's are.
 */
public class RenderTypes {
    public static final RenderType ZIPLINE_3D;
    public static final RenderType ZIPLINE_2D;

    static {
        ZIPLINE_2D = RenderType.create("zipline2d",
                RenderSetup.builder(registerPipeline("parcool/zipline_no_cull", false))
                        .useLightmap()
                        .createRenderSetup());
        ZIPLINE_3D = RenderType.create("zipline3d",
                RenderSetup.builder(registerPipeline("parcool/zipline", true))
                        .useLightmap()
                        .createRenderSetup());
    }

    private static RenderPipeline registerPipeline(String location, boolean cull) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(location)
                .withVertexShader("core/rendertype_leash")
                .withFragmentShader("core/rendertype_leash")
                .withSampler("Sampler2")
                .withCull(cull)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS)
                .build();
        RenderPipelines.PIPELINES_BY_LOCATION.put(pipeline.getLocation(), pipeline);
        return pipeline;
    }
}
