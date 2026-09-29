package com.alrex.parcool.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * ParCool's own render types.
 *
 * <h2>Why two pipelines are built instead of reusing {@code RenderTypes.leash()}</h2>
 * 1.21.10 already moved culling and the vertex format out of the render type and into a
 * {@link RenderPipeline}, and it deleted {@code RenderStateShard#CULL} / {@code #NO_CULL} and the
 * {@code RENDERTYPE_LEASH_SHADER} shard together with them - {@code RenderStateShard} now only
 * carries texture / lightmap / overlay / layering / texturing / output state. So the 1.21.1 recipe
 * ({@code RenderType.create(name, format, mode, size, …)} plus a cull shard) no longer exists at all
 * and the rope has to own its pipeline.
 *
 * <p>Vanilla's leash pipeline draws {@code POSITION_COLOR_LIGHTMAP} in {@code TRIANGLE_STRIP} with
 * culling off, while the zipline rope emits {@code QUADS} and has a culled and an unculled variant
 * (the {@code Enable3DRenderingForZipline} config switch), so neither vanilla pipeline fits. The
 * shaders are the same {@code core/rendertype_leash} pair the 1.21.1 {@code RENDERTYPE_LEASH_SHADER}
 * shard selected, and the lightmap is still requested through {@code LightmapStateShard}, so the rope
 * looks exactly like it did on 1.21.1.
 *
 * <p>{@code RenderPipelines#register} and {@code RenderType#create} are not accessible from outside
 * {@code net.minecraft.client.renderer}, so the two are widened through {@code parcool.accesswidener}
 * (Fabric) and {@code accesstransformer.cfg} (NeoForge); the snippet constant
 * {@code RenderPipelines#MATRICES_FOG_SNIPPET} is private and is widened with them.
 *
 * <h2>Why {@link #register()} exists</h2>
 * {@code ShaderManager#apply} precompiles every pipeline in {@code RenderPipelines#getStaticPipelines()}
 * during the resource reload and aborts the game on the first one that fails, so the two pipelines
 * have to be in the map <em>before</em> that reload runs. They are registered from the class
 * initialiser, which only runs when this class is first touched - and the rope renderer, which is
 * the only other user, is not created until the first frame, long after the reload. Hence
 * {@link #register()}, which the client entry point calls during mod init.
 */
public class RenderTypes {
    public static final RenderType ZIPLINE_3D;
    public static final RenderType ZIPLINE_2D;

    static {
        ZIPLINE_2D = RenderType.create("zipline2d", 256,
                registerPipeline("parcool/zipline_no_cull", false),
                RenderType.CompositeState.builder()
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        );
        ZIPLINE_3D = RenderType.create("zipline3d", 256,
                registerPipeline("parcool/zipline", true),
                RenderType.CompositeState.builder()
                        .setTextureState(RenderStateShard.NO_TEXTURE)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .createCompositeState(false)
        );
    }

    /**
     * Forces this class's initialiser to run.
     *
     * <p>Without it the two pipelines are registered on the first frame instead of during mod init,
     * i.e. after {@code ShaderManager} has already snapshotted and precompiled the vanilla pipelines,
     * and the rope render then has no compiled program for {@code parcool/zipline}.
     */
    public static void register() {
        // Reading a constant is enough - the class initialiser above does the work.
        if (ZIPLINE_3D == null || ZIPLINE_2D == null) {
            throw new IllegalStateException("ParCool render types were not created");
        }
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
