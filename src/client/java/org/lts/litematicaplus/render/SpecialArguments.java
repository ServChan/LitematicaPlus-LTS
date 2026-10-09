package org.lts.litematicaplus.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.world.PlacementVolume;

public final class SpecialArguments {
    private static final Object NONE = new Object();
    private static final int RESOLVES_PER_FRAME = 48;
    private static int budget = RESOLVES_PER_FRAME;
    private static final Map<PlacementVolume, Long2ObjectOpenHashMap<Object>> CACHE = new WeakHashMap<>();
    private static boolean warned;

    private SpecialArguments() {
    }

    public static Object get(PlacementVolume volume, int x, int y, int z, BlockState state,
            SpecialModelRenderer<?> renderer) {
        long key = BlockPos.asLong(x, y, z);
        Long2ObjectOpenHashMap<Object> arguments = CACHE.computeIfAbsent(volume, ignored -> new Long2ObjectOpenHashMap<>());
        Object cached = arguments.get(key);
        if (cached == null) {
            if (!volume.blockEntities().containsKey(key)) {
                arguments.put(key, NONE);
                return null;
            }
            if (budget <= 0) {
                return null;
            }
            budget--;
            cached = resolve(volume, key, state, renderer);
            arguments.put(key, cached == null ? NONE : cached);
        }
        return cached == NONE ? null : cached;
    }

    public static void beginFrame() {
        budget = RESOLVES_PER_FRAME;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static Object resolve(PlacementVolume volume, long key, BlockState state, SpecialModelRenderer<?> renderer) {
        CompoundTag tag = volume.blockEntities().get(key);
        Minecraft minecraft = Minecraft.getInstance();
        if (tag == null || minecraft.level == null || state.getBlock().asItem() == Items.AIR) {
            return null;
        }
        try {
            CompoundTag fixed = BlockStateNbt.fixBlockEntity(tag.copy(), volume.dataVersion());
            BlockEntity blockEntity = BlockEntity.loadStatic(BlockPos.of(key), state, fixed,
                    minecraft.level.registryAccess());
            if (blockEntity == null) {
                return null;
            }
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            stack.applyComponents(blockEntity.collectComponents());
            return renderer.extractArgument(stack);
        } catch (RuntimeException | LinkageError exception) {
            if (!warned) {
                warned = true;
                LitematicaPlusClient.LOGGER.warn("Could not read block entity data for a schematic ghost", exception);
            }
            return null;
        }
    }
}
