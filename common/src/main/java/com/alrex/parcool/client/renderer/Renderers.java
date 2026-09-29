package com.alrex.parcool.client.renderer;

import com.alrex.parcool.client.renderer.entity.ZiplineRopeRenderer;
import com.alrex.parcool.common.entity.EntityTypes;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;

public class Renderers {
    public static void register() {
        // Loads the RenderTypes class during mod init. Without this the two zipline pipelines are
        // only registered when the rope renderer first runs, i.e. after ShaderManager has snapshotted
        // and precompiled the vanilla pipelines during the resource reload - and the rope would then
        // have no compiled program. See RenderTypes#register.
        RenderTypes.register();
        EntityRendererRegistry.register(EntityTypes.ZIPLINE_ROPE, ZiplineRopeRenderer::new);
    }
}
