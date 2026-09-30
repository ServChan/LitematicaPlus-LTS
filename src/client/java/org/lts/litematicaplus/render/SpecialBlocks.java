package org.lts.litematicaplus.render;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fc;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.mixin.BlockModelRenderStateAccessor;

public final class SpecialBlocks {
    public record Entry(SpecialModelRenderer<?> renderer, Matrix4fc transform) {
    }

    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private static final Entry NONE = new Entry(null, null);
    private static final Reference2ObjectOpenHashMap<BlockState, Entry> CACHE = new Reference2ObjectOpenHashMap<>();
    private static BlockModelResolver resolver;
    private static boolean warned;

    private SpecialBlocks() {
    }

    public static Entry get(BlockState state) {
        Entry cached = CACHE.get(state);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        Entry resolved = resolve(state);
        CACHE.put(state, resolved == null ? NONE : resolved);
        return resolved;
    }

    public static boolean isSpecial(BlockState state) {
        return get(state) != null;
    }

    public static void clear() {
        CACHE.clear();
        resolver = null;
    }

    private static Entry resolve(BlockState state) {
        if (state.isAir()) {
            return null;
        }
        try {
            if (resolver == null) {
                resolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());
            }
            BlockModelRenderState renderState = new BlockModelRenderState();
            resolver.update(renderState, state, CONTEXT);
            BlockModelRenderStateAccessor accessor = (BlockModelRenderStateAccessor) (Object) renderState;
            SpecialModelRenderer<?> renderer = accessor.litematicaplus$specialRenderer();
            if (renderer == null) {
                return null;
            }
            return new Entry(renderer, accessor.litematicaplus$specialRendererTransformation());
        } catch (RuntimeException | LinkageError exception) {
            if (!warned) {
                warned = true;
                LitematicaPlusClient.LOGGER.warn("Could not resolve special block models", exception);
            }
            return null;
        }
    }
}
