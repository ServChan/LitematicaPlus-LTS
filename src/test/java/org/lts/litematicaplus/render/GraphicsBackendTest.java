package org.lts.litematicaplus.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class GraphicsBackendTest {
    @Test
    void openGlBackendNameIsOpenGlWithoutProbingTheContext() {
        assertEquals(GraphicsBackend.Kind.OPENGL, GraphicsBackend.classify("OpenGL", () -> fail("probed GL")));
    }

    @Test
    void vulkanBackendNameIsVulkanWithoutProbingTheContext() {
        assertEquals(GraphicsBackend.Kind.VULKAN, GraphicsBackend.classify("Vulkan", () -> fail("probed GL")));
        assertEquals(GraphicsBackend.Kind.VULKAN, GraphicsBackend.classify("VULKAN 1.3", () -> fail("probed GL")));
    }

    @Test
    void unknownBackendFallsBackToTheCurrentGlContext() {
        assertEquals(GraphicsBackend.Kind.OPENGL, GraphicsBackend.classify("Metal", () -> true));
        assertEquals(GraphicsBackend.Kind.OTHER, GraphicsBackend.classify("Metal", () -> false));
        assertEquals(GraphicsBackend.Kind.OTHER, GraphicsBackend.classify(null, () -> false));
    }
}
