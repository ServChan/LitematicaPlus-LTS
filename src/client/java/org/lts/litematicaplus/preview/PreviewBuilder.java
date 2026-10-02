package org.lts.litematicaplus.preview;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import org.joml.Vector3fc;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicRegion;

public final class PreviewBuilder {
    static final long MODEL_BLOCK_LIMIT = 250_000L;
    static final int MODEL_QUAD_LIMIT = 700_000;
    static final long VOXEL_TARGET = 400_000L;
    private static final int GRASS = 0x91BD59;
    private static final int FOLIAGE = 0x77AB2F;
    private static final int DRY_FOLIAGE = 0xA08A5A;
    private static final int WATER = 0x3F76E4;
    private static final Map<Identifier, PreviewModel.Texture> TEXTURES = new ConcurrentHashMap<>();

    private PreviewBuilder() {
    }

    static void clearTextures() {
        TEXTURES.clear();
    }

    public static PreviewModel build(Schematic schematic, BlockStateModelSet models, BlockColors colors,
            boolean detailed, BooleanSupplier cancelled) {
        PreviewWorld world = new PreviewWorld(schematic);
        long blocks = world.nonAir();
        if (detailed && models != null && colors != null && blocks <= MODEL_BLOCK_LIMIT) {
            PreviewModel model = buildModels(world, models, colors, cancelled);
            if (model != null) {
                return model;
            }
        }
        return buildVoxels(world, blocks, detailed && blocks > MODEL_BLOCK_LIMIT, cancelled);
    }

    private static PreviewModel buildModels(PreviewWorld world, BlockStateModelSet models, BlockColors colors,
            BooleanSupplier cancelled) {
        PreviewModel.Builder builder = new PreviewModel.Builder();
        ModelBlockRenderer renderer = new ModelBlockRenderer(true, true, colors);
        float[] xyz = new float[12];
        float[] uv = new float[8];
        int[] argb = new int[4];
        boolean[] overflow = new boolean[1];
        BlockQuadOutput output = (x, y, z, quad, instance) -> {
            if (builder.quads() >= MODEL_QUAD_LIMIT) {
                overflow[0] = true;
                return;
            }
            addQuad(builder, x, y, z, quad, instance, xyz, uv, argb);
        };
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int visited = 0;
        BlockModelLighter.enableCaching();
        try {
            for (PreviewWorld.Region region : world.regions()) {
                SchematicRegion source = region.source();
                for (int y = 0; y < source.absY(); y++) {
                    for (int z = 0; z < source.absZ(); z++) {
                        for (int x = 0; x < source.absX(); x++) {
                            if ((visited++ & 4095) == 0) {
                                checkCancelled(cancelled);
                                if (overflow[0]) {
                                    return null;
                                }
                            }
                            BlockState state = world.local(region, x, y, z);
                            if (state.isAir()) {
                                continue;
                            }
                            int worldX = region.minX() + x;
                            int worldY = region.minY() + y;
                            int worldZ = region.minZ() + z;
                            cursor.set(worldX, worldY, worldZ);
                            if (world.buried(cursor, state)) {
                                continue;
                            }
                            FluidState fluid = state.getFluidState();
                            if (!fluid.isEmpty()) {
                                addFluid(builder, world, cursor, fluid);
                            }
                            RenderShape shape = state.getRenderShape();
                            if (shape == RenderShape.MODEL) {
                                try {
                                    renderer.tesselateBlock(output, worldX, worldY, worldZ, world, cursor, state,
                                            models.get(state), state.getSeed(cursor));
                                } catch (RuntimeException exception) {
                                    addCube(builder, world, cursor, state, 1.0F);
                                }
                            } else if (state.hasBlockEntity() && fluid.isEmpty()) {
                                addCube(builder, world, cursor, state, 1.0F);
                            }
                        }
                    }
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }
        return overflow[0] ? null : builder.build();
    }

    private static PreviewModel buildVoxels(PreviewWorld world, long blocks, boolean simplified,
            BooleanSupplier cancelled) {
        long volume = Math.max(1L, world.volume());
        int stride = 1;
        if (blocks > VOXEL_TARGET) {
            stride = (int) Math.max(1L, Math.ceil(Math.cbrt((double) volume / (VOXEL_TARGET * 4.0))));
        }
        PreviewModel.Builder builder = new PreviewModel.Builder().simplified(simplified || stride > 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int visited = 0;
        for (PreviewWorld.Region region : world.regions()) {
            SchematicRegion source = region.source();
            for (int y = 0; y < source.absY(); y += stride) {
                for (int z = 0; z < source.absZ(); z += stride) {
                    for (int x = 0; x < source.absX(); x += stride) {
                        if ((visited++ & 4095) == 0) {
                            checkCancelled(cancelled);
                        }
                        BlockState state = world.local(region, x, y, z);
                        if (state.isAir()) {
                            continue;
                        }
                        int worldX = region.minX() + x;
                        int worldY = region.minY() + y;
                        int worldZ = region.minZ() + z;
                        int rgb = color(state);
                        boolean fluid = !state.getFluidState().isEmpty() && !state.canOcclude();
                        for (int face = 0; face < 6; face++) {
                            Direction direction = FACES[face];
                            cursor.set(worldX + direction.getStepX() * stride, worldY + direction.getStepY() * stride,
                                    worldZ + direction.getStepZ() * stride);
                            BlockState neighbour = world.getBlockState(cursor);
                            if (!neighbour.isAir() && (neighbour.canOcclude() || fluid && !neighbour.getFluidState().isEmpty())) {
                                continue;
                            }
                            builder.cubeFace(face, worldX, worldY, worldZ, stride, rgb, -1, fluid, fluid ? 0xC0 : 0xFF);
                        }
                    }
                }
            }
        }
        return builder.build();
    }

    private static final Direction[] FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST};

    private static void addQuad(PreviewModel.Builder builder, float x, float y, float z, BakedQuad quad,
            QuadInstance instance, float[] xyz, float[] uv, int[] argb) {
        TextureAtlasSprite sprite = quad.materialInfo().sprite();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        for (int i = 0; i < 4; i++) {
            Vector3fc position = quad.position(i);
            xyz[i * 3] = position.x() + x;
            xyz[i * 3 + 1] = position.y() + y;
            xyz[i * 3 + 2] = position.z() + z;
            long packed = quad.packedUV(i);
            uv[i * 2] = normalize(UVPair.unpackU(packed), u0, u1);
            uv[i * 2 + 1] = normalize(UVPair.unpackV(packed), v0, v1);
            argb[i] = instance.getColor(i);
        }
        PreviewModel.Texture texture = texture(sprite);
        builder.quad(xyz, uv, argb, builder.texture(texture), quad.materialInfo().layer().translucent());
    }

    private static float normalize(float value, float low, float high) {
        float span = high - low;
        return span == 0.0F ? 0.0F : (value - low) / span;
    }

    private static void addFluid(PreviewModel.Builder builder, PreviewWorld world, BlockPos pos, FluidState fluid) {
        boolean lava = fluid.is(FluidTags.LAVA);
        PreviewModel.Texture texture = load(Identifier.withDefaultNamespace(lava ? "textures/block/lava_still.png"
                : "textures/block/water_still.png"), 16, 16);
        int index = builder.texture(texture);
        int rgb = lava ? 0xFFFFFF : WATER & 0xFFFFFF;
        BlockPos.MutableBlockPos neighbourPos = new BlockPos.MutableBlockPos();
        for (int face = 0; face < 6; face++) {
            Direction direction = FACES[face];
            neighbourPos.setWithOffset(pos, direction);
            BlockState neighbour = world.getBlockState(neighbourPos);
            if (!neighbour.getFluidState().isEmpty() || neighbour.canOcclude()) {
                continue;
            }
            builder.cubeFace(face, pos.getX(), pos.getY(), pos.getZ(), 1.0F, rgb, index, !lava, lava ? 0xFF : 0xB8);
        }
    }

    private static void addCube(PreviewModel.Builder builder, PreviewWorld world, BlockPos pos, BlockState state,
            float size) {
        int rgb = color(state);
        BlockPos.MutableBlockPos neighbourPos = new BlockPos.MutableBlockPos();
        for (int face = 0; face < 6; face++) {
            neighbourPos.setWithOffset(pos, FACES[face]);
            if (world.getBlockState(neighbourPos).canOcclude()) {
                continue;
            }
            builder.cubeFace(face, pos.getX(), pos.getY(), pos.getZ(), size, rgb, -1, false, 0xFF);
        }
    }

    static int color(BlockState state) {
        try {
            MapColor mapColor = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (mapColor != null && mapColor != MapColor.NONE && mapColor.col != 0) {
                return mapColor.col & 0xFFFFFF;
            }
        } catch (RuntimeException ignored) {
        }
        return 0x8A8A8A;
    }

    private static PreviewModel.Texture texture(TextureAtlasSprite sprite) {
        Identifier name = sprite.contents().name();
        Identifier file = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
        return load(file, sprite.contents().width(), sprite.contents().height());
    }

    private static PreviewModel.Texture load(Identifier file, int frameWidth, int frameHeight) {
        return TEXTURES.computeIfAbsent(file, id -> read(id, frameWidth, frameHeight));
    }

    private static PreviewModel.Texture read(Identifier id, int frameWidth, int frameHeight) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return PreviewModel.Texture.NONE;
            }
            try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                int width = Math.min(image.getWidth(), Math.max(1, frameWidth));
                int height = Math.min(image.getHeight(), Math.max(1, frameHeight));
                int[] pixels = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        pixels[y * width + x] = image.getPixel(x, y);
                    }
                }
                return new PreviewModel.Texture(width, height, pixels);
            }
        } catch (Exception exception) {
            LitematicaPlusClient.LOGGER.debug("Could not read preview texture {}", id, exception);
            return PreviewModel.Texture.NONE;
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) {
            throw new CancellationException();
        }
    }

    static final class PreviewWorld implements BlockAndTintGetter {
        record Region(SchematicRegion source, int minX, int minY, int minZ, boolean[] air) {
        }

        private final List<Region> regions = new ArrayList<>();
        private final BlockState air = Blocks.AIR.defaultBlockState();

        PreviewWorld(Schematic schematic) {
            for (SchematicRegion region : schematic.regions().values()) {
                BlockState[] palette = region.palette();
                boolean[] isAir = new boolean[palette.length];
                for (int i = 0; i < palette.length; i++) {
                    isAir[i] = palette[i].isAir() || palette[i].is(Blocks.STRUCTURE_VOID);
                }
                this.regions.add(new Region(region, region.minX(), region.minY(), region.minZ(), isAir));
            }
        }

        List<Region> regions() {
            return this.regions;
        }

        long volume() {
            long total = 0L;
            for (Region region : this.regions) {
                total += region.source().volume();
            }
            return total;
        }

        long nonAir() {
            long total = 0L;
            for (Region region : this.regions) {
                total += region.source().countNonAir();
            }
            return total;
        }

        BlockState local(Region region, int x, int y, int z) {
            int id = region.source().paletteIndex(x, y, z);
            if (id < 0 || id >= region.air().length || region.air()[id]) {
                return this.air;
            }
            return region.source().palette()[id];
        }

        boolean buried(BlockPos pos, BlockState state) {
            if (!state.canOcclude()) {
                return false;
            }
            BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
            for (Direction direction : FACES) {
                neighbour.setWithOffset(pos, direction);
                if (!getBlockState(neighbour).canOcclude()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            for (Region region : this.regions) {
                SchematicRegion source = region.source();
                int lx = x - region.minX();
                int ly = y - region.minY();
                int lz = z - region.minZ();
                if (lx >= 0 && ly >= 0 && lz >= 0 && lx < source.absX() && ly < source.absY() && lz < source.absZ()) {
                    return local(region, lx, ly, lz);
                }
            }
            return this.air;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            BlockState state = getBlockState(pos);
            return state == this.air ? Fluids.EMPTY.defaultFluidState() : state.getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return LevelLightEngine.EMPTY;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            if (resolver == BiomeColors.GRASS_COLOR_RESOLVER) {
                return GRASS;
            }
            if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) {
                return FOLIAGE;
            }
            if (resolver == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
                return DRY_FOLIAGE;
            }
            if (resolver == BiomeColors.WATER_COLOR_RESOLVER) {
                return WATER;
            }
            return -1;
        }

        @Override
        public int getHeight() {
            return 4096;
        }

        @Override
        public int getMinY() {
            return -2048;
        }
    }
}
