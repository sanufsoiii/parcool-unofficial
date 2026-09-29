package com.alrex.parcool.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * ParCool's own render types for the zipline rope.
 *
 * <h2>Why two pipelines are built instead of reusing {@code RenderType.leash()}</h2>
 * Vanilla's leash pipeline draws {@code POSITION_COLOR_LIGHTMAP} in {@code TRIANGLE_STRIP} with
 * culling off, while the zipline rope emits {@code QUADS} and has a culled and an unculled variant
 * (the {@code Enable3DRenderingForZipline} config switch), so neither vanilla pipeline fits. The
 * shaders are the same {@code core/rendertype_leash} pair the old {@code RENDERTYPE_LEASH_SHADER}
 * shard selected, and the lightmap is still requested through the composite state, so the rope looks
 * the same as it did on 1.21.1.
 *
 * <h2>What 1.21.5 changed</h2>
 * 1.21.4 still assembled the composite state out of {@code RenderStateShard} shards. 1.21.5 deleted
 * the shader and cull shards ({@code RenderStateShard$CullStateShard} and
 * {@code RENDERTYPE_LEASH_SHADER} are gone from the class) and moved the vertex format, the
 * primitive mode and culling into the {@link RenderPipeline} instead. The factory is now
 * {@code RenderType.create(String, int, RenderPipeline, CompositeState)} and the composite-state
 * builder's setters are {@code protected}. Four things are therefore widened in
 * {@code common/src/main/resources/parcool.accesswidener} for Fabric and in
 * {@code neoforge/src/main/resources/META-INF/accesstransformer.cfg} for NeoForge:
 * the factory, the two state setters, {@code RenderStateShard#NO_TEXTURE}/{@code LIGHTMAP}, the
 * {@code RenderPipelines#MATRICES_COLOR_FOG_SNIPPET} the pipeline is built from, and
 * {@code RenderPipelines#PIPELINES_BY_LOCATION}.
 *
 * <p>The snippet is widened rather than re-spelled here on purpose: it is the union of the matrix,
 * colour-modulator and fog uniform blocks, and a pipeline whose declared uniform set does not match
 * what {@code core/rendertype_leash} actually uses fails to compile at first draw - the one class of
 * failure in this file that no compiler in this build would catch.
 *
 * <p>Registering in {@code RenderPipelines#PIPELINES_BY_LOCATION} is not strictly required - the
 * {@code RenderType} holds its pipeline by reference and the GPU device compiles it on first use -
 * but it is what makes the two take part in {@code ShaderManager}'s precompile pass, so a broken
 * pipeline is reported as an error at a resource reload instead of on the first frame a rope is
 * drawn.
 */
public class RenderTypes {
    public static final RenderType ZIPLINE_3D;
    public static final RenderType ZIPLINE_2D;

    static {
        ZIPLINE_2D = RenderType.create(
                "zipline2d",
                256,
                registerPipeline("parcool/zipline_no_cull", false),
                RenderType.CompositeState.builder()
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        );
        ZIPLINE_3D = RenderType.create(
                "zipline3d",
                256,
                registerPipeline("parcool/zipline", true),
                RenderType.CompositeState.builder()
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        );
    }

    private static RenderPipeline registerPipeline(String location, boolean cull) {
        // The very snippet RenderPipelines.LEASH is built from, so the two custom pipelines declare
        // exactly the uniforms core/rendertype_leash reads.
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_FOG_SNIPPET)
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
