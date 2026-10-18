package com.alrex.parcool;

/**
 * Client-only Forge initialisation.
 *
 * <p>Forge 1.20.1's {@code @Mod} annotation has no {@code dist} member (that is a NeoForge 1.20.2+
 * addition), so this cannot be a second {@code @Mod} class the way the 1.21.1 tree's NeoForge module
 * does it. It is reached through {@code DistExecutor.unsafeRunWhenOn} from {@link ParCoolForge},
 * which keeps this class - and every client-only type it references - from being loaded on a
 * dedicated server.
 */
public final class ParCoolForgeClient {

    private ParCoolForgeClient() {
    }

    public static void init() {
        ParCool.initClient();
    }
}
