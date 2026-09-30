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
 * <p>Two are needed because the rope is drawn in one of two shapes: {@link #ZIPLINE_3D} with culling
 * and {@link #ZIPLINE_2D} without, switched by the {@code Enable3DRenderingForZipline} config entry.
 * Neither vanilla type matches: {@code RenderType.leash()} is the closest (it is also
 * {@code POSITION_COLOR_LIGHTMAP} with a lightmap) but it renders as a {@code TRIANGLE_STRIP} with
 * culling disabled, while the rope emits {@code QUADS} and has to be culled in the 3D variant.
 *
 * <h2>1.21.6 is halfway between the 1.21.1 and the 1.21.11 shape</h2>
 * {@code RenderStateShard} is still here - {@code RenderSetup} only arrives in 1.21.9 - so the
 * texture and lightmap state still go through {@link RenderType.CompositeState}. But 1.21.5 already
 * moved culling, the vertex format and the shaders out of the shards and into a
 * {@link RenderPipeline}, so {@code RenderStateShard#CULL} / {@code #NO_CULL} /
 * {@code #RENDERTYPE_LEASH_SHADER} no longer exist and there is no
 * {@code RenderType.create(String, VertexFormat, Mode, int, boolean, boolean, CompositeState)}
 * overload any more. What is left is the package-private
 * {@code RenderType.create(String, int, RenderPipeline, CompositeState)}, which is why this class
 * only compiles with the two widened members declared in
 * {@code common/src/main/resources/parcool.accesswidener} (Fabric) and
 * {@code neoforge/src/main/resources/META-INF/accesstransformer.cfg} (NeoForge).
 *
 * <p>{@code RenderPipelines#register} is private, but the map it fills is public, so the two
 * pipelines are registered the same way vanilla's are.
 *
 * <h2>Do not hand-roll the snippet</h2>
 * An earlier version of this file carried a private copy of the three uniform declarations,
 * declared *below* the static initialiser block, on the stated assumption that
 * {@code RenderPipelines#MATRICES_FOG_SNIPPET} is private in 1.21.6. Both halves of that are
 * wrong. The field is public:
 *
 * <pre>
 * javap -p net.minecraft.client.renderer.RenderPipelines   # 1.21.6 mojmap jar
 *   public static final RenderPipeline$Snippet MATRICES_FOG_SNIPPET;
 * </pre>
 *
 * and Java runs static initialisers in declaration order, so the block that calls
 * {@code registerPipeline} executed while the copy was still {@code null}. The failure was not
 * at build time and not at load time - the class initialised fine, and the mod loaded. It
 * surfaced only when a rope was actually drawn:
 *
 * <pre>
 * [Render thread/ERROR] (Minecraft) Reported exception thrown!
 * net.minecraft.ReportedException: Rendering entity in world
 * Caused by: java.lang.ExceptionInInitializerError
 *   at ZiplineRopeRenderer.render(ZiplineRopeRenderer.java:121)
 * Caused by: java.lang.NullPointerException: Cannot read field "vertexShader" because "snippet" is null
 *   at RenderPipeline$Builder.withSnippet(RenderPipeline.java:338)
 *   at com.alrex.parcool.client.renderer.RenderTypes.registerPipeline(RenderTypes.java:75)
 *   at com.alrex.parcool.client.renderer.RenderTypes.&lt;clinit&gt;(RenderTypes.java:45)
 * </pre>
 *
 * and the client died with exit code 255. Using the public vanilla snippet, as the 1.21.5, 1.21.7,
 * 1.21.8, 1.21.9 and 1.21.10 ports do, removes the declaration-order hazard entirely instead of
 * merely reordering the fields around it.
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
        // Same shaders and sampler the 1.21.1 RENDERTYPE_LEASH_SHADER shard selected, so the rope
        // still renders exactly as it did there; only the draw mode and the culling differ.
        // The snippet is vanilla's public MATRICES_FOG_SNIPPET, the same one the other ports use.
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
