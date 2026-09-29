package com.alrex.parcool.client.renderer;

import com.alrex.parcool.client.renderer.entity.ZiplineRopeRenderer;
import com.alrex.parcool.common.entity.EntityTypes;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;

public class Renderers {
    public static void register() {
        // ShaderManager#apply precompiles RenderPipelines#getStaticPipelines() during the resource
        // reload, which happens long before the first frame creates ZiplineRopeRenderer. The two
        // zipline pipelines therefore have to be in the map before that reload runs, and touching
        // RenderTypes is what puts them there.
        RenderTypes.register();
        EntityRendererRegistry.register(EntityTypes.ZIPLINE_ROPE, ZiplineRopeRenderer::new);
    }
}
