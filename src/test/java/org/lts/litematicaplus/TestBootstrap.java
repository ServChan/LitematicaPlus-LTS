package org.lts.litematicaplus;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class TestBootstrap {
    private static boolean bootstrapped;

    private TestBootstrap() {
    }

    public static synchronized void ensure() {
        if (bootstrapped) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bootstrapped = true;
    }
}
