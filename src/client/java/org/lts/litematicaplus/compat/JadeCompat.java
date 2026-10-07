package org.lts.litematicaplus.compat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.tool.GhostRaycast;
import org.lts.litematicaplus.verify.MismatchType;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IPluginConfig;

public final class JadeCompat implements IWailaPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(LitematicaPlusClient.MOD_ID, "schematic");
    private static final int MAX_STATE_DIFFS = 3;

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        Addons.markJadeRegistered();
        registration.registerBlockComponent(SchematicProvider.INSTANCE, Block.class);
        registration.addRayTraceCallback((hit, accessor, original) -> ghost(registration, accessor));
    }

    private static Accessor<?> ghost(IWailaClientRegistration registration, Accessor<?> accessor) {
        Minecraft minecraft = Minecraft.getInstance();
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        if (!config.renderEnabled || !config.renderGhostBlocks || minecraft.level == null || minecraft.player == null) {
            return accessor;
        }
        try {
            GhostRaycast.Hit hit = GhostRaycast.trace(minecraft, candidate -> candidate.type() == MismatchType.MISSING);
            if (hit == null || accessor instanceof BlockAccessor block && block.getPosition().equals(hit.pos())) {
                return accessor;
            }
            Vec3 eye = minecraft.player.getEyePosition();
            Direction side = Direction.getApproximateNearest(eye.subtract(Vec3.atCenterOf(hit.pos())));
            BlockPos pos = hit.pos();
            return registration.blockAccessor()
                    .level(minecraft.level)
                    .player(minecraft.player)
                    .hit(new BlockHitResult(Vec3.atCenterOf(pos), side, pos, false))
                    .blockState(hit.state())
                    .serverConnected(accessor != null && accessor.isServerConnected())
                    .showDetails(accessor != null && accessor.showDetails())
                    .build();
        } catch (RuntimeException | LinkageError exception) {
            return accessor;
        }
    }

    private enum SchematicProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockPos pos = accessor.getPosition();
            GhostEngine engine = GhostEngine.get();
            BlockState expected = engine.schematicStateAt(pos.getX(), pos.getY(), pos.getZ());
            if (expected == null) {
                return;
            }
            BlockState world = accessor.getLevel().getBlockState(pos);
            if (world != accessor.getBlockState()) {
                tooltip.add(Component.translatable("litematicaplus.jade.ghost").withStyle(ChatFormatting.GOLD), UID);
                return;
            }
            MismatchType type = engine.categoryAt(pos.getX(), pos.getY(), pos.getZ());
            Component line = switch (type) {
                case WRONG_BLOCK -> Component.translatable("litematicaplus.jade.wrong_block", expected.getBlock().getName())
                        .withStyle(ChatFormatting.RED);
                case WRONG_STATE -> Component.translatable("litematicaplus.jade.wrong_state", differences(expected, world))
                        .withStyle(ChatFormatting.GOLD);
                case EXTRA -> Component.translatable("litematicaplus.jade.extra").withStyle(ChatFormatting.RED);
                case CORRECT -> Component.translatable("litematicaplus.jade.correct").withStyle(ChatFormatting.GREEN);
                default -> null;
            };
            if (line != null) {
                tooltip.add(line, UID);
            }
        }

        private static String differences(BlockState expected, BlockState world) {
            List<String> parts = new ArrayList<>();
            for (Property<?> property : expected.getProperties()) {
                if (!world.hasProperty(property) || world.getValue(property).equals(expected.getValue(property))) {
                    continue;
                }
                parts.add(property.getName() + "=" + value(expected, property));
                if (parts.size() >= MAX_STATE_DIFFS) {
                    break;
                }
            }
            return String.join(", ", parts);
        }

        private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
            return property.getName(state.getValue(property));
        }
    }
}
