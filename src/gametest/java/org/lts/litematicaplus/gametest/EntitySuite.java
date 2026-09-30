package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.RegionBuilder;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.schematic.SchematicMetadata;
import org.lts.litematicaplus.tool.EntityAssist;

final class EntitySuite {
    private static final BlockPos BASE = new BlockPos(60, -60, 90);

    private EntitySuite() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        RegionBuilder builder = new RegionBuilder("Wall", 0, 0, 0, 5, 3, 3);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                builder.set(x, y, 0, Blocks.STONE.defaultBlockState());
            }
        }
        CompoundTag frame = entity("minecraft:item_frame", 1.5, 1.5, 1.03125);
        frame.putByte("Facing", (byte) Direction.SOUTH.get3DDataValue());
        frame.putByte("ItemRotation", (byte) 3);
        frame.put("Item", item("minecraft:diamond"));
        builder.entity(frame);
        CompoundTag painting = entity("minecraft:painting", 2.5, 1.5, 1.03125);
        painting.putByte("facing", (byte) Direction.SOUTH.get2DDataValue());
        painting.putString("variant", "minecraft:kebab");
        builder.entity(painting);
        CompoundTag glow = entity("minecraft:glow_item_frame", 3.5, 1.5, 1.03125);
        glow.putByte("Facing", (byte) Direction.SOUTH.get3DDataValue());
        builder.entity(glow);
        CompoundTag stand = entity("minecraft:armor_stand", 4.5, 0.0, 2.5);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(90.0F));
        rotation.add(FloatTag.valueOf(0.0F));
        stand.put("Rotation", rotation);
        CompoundTag equipment = new CompoundTag();
        equipment.put("head", item("minecraft:iron_helmet"));
        stand.put("equipment", equipment);
        builder.entity(stand);
        CompoundTag pig = entity("minecraft:pig", 0.5, 0.0, 2.5);
        builder.entity(pig);
        SchematicMetadata metadata = new SchematicMetadata();
        metadata.name = "Wall";
        metadata.totalBlocks = 15L;
        Schematic schematic = new Schematic(metadata, Map.of("Wall", builder.build()));
        Path file = context.computeOnClient(client -> {
            Path target = PlacementManager.schematicsDirectory().resolve("gametest/wall.litematic");
            try {
                SchematicIo.write(target, schematic);
                LitematicaPlusGameTest.require(SchematicIo.read(target).regions().get("Wall").entities().size() == 5,
                        "entities should survive a save and load");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return target;
        });
        world.getServer().runCommand("fill 60 -60 90 64 -58 90 minecraft:stone");
        world.getServer().runCommand("clear @p");
        world.getServer().runCommand("tp @p 62.5 -60 93.5 180 0");
        world.getConnection().waitForClientboundPackets();
        world.getConnection().waitForChunksRender();
        SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, BASE));
        LitematicaPlusGameTest.settle(context, placement);
        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = true;
            config.entityPlacement = true;
            LitematicaPlusConfig.save(config);
        });
        context.waitTicks(12);
        PlacementRuntime runtime = context.computeOnClient(client -> GhostEngine.get().runtime(placement.id()));
        List<EntityAssist.Ghost> ghosts = context.computeOnClient(client -> List.copyOf(EntityAssist.ghosts(runtime)));
        LitematicaPlusGameTest.require(ghosts.size() == 4, "four schematic entities are supported, got " + ghosts.size());
        for (EntityAssist.Ghost ghost : ghosts) {
            LitematicaPlusGameTest.require(ghost.status() == EntityAssist.Status.MISSING,
                    ghost.kind() + " should be missing, is " + ghost.status());
        }
        EntityAssist.Ghost frameGhost = ghosts.get(0);
        LitematicaPlusGameTest.require(frameGhost.pos().equals(new BlockPos(61, -59, 91)),
                "the frame marker should sit in front of the wall, got " + frameGhost.pos());
        context.takeScreenshot("11_entity_markers");

        TestUi.aim(context, world, 62.5, -60.0, 93.5, 61.5, -58.5, 91.05);
        TestUi.use(context, world, 16);
        LitematicaPlusGameTest.require(count(world, ItemFrame.class, new BlockPos(61, -59, 91)) == 1,
                "the first use should hang the item frame");
        TestUi.use(context, world, 16);
        String framed = world.getServer().computeOnServer(server -> server.overworld()
                .getEntitiesOfClass(ItemFrame.class, new AABB(new BlockPos(61, -59, 91))).stream().findFirst()
                .map(entity -> BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString()).orElse("<none>"));
        LitematicaPlusGameTest.require(framed.equals("minecraft:diamond"), "the second use should put the diamond in, got " + framed);
        TestUi.use(context, world, 30);
        int turned = world.getServer().computeOnServer(server -> server.overworld()
                .getEntitiesOfClass(ItemFrame.class, new AABB(new BlockPos(61, -59, 91))).stream().findFirst()
                .map(ItemFrame::getRotation).orElse(-1));
        LitematicaPlusGameTest.require(turned == 3, "the third use should turn the item three steps, got " + turned);

        TestUi.aim(context, world, 62.5, -60.0, 93.5, 62.5, -58.5, 91.05);
        TestUi.use(context, world, 16);
        String variant = world.getServer().computeOnServer(server -> server.overworld()
                .getEntitiesOfClass(Painting.class, new AABB(new BlockPos(62, -59, 91))).stream().findFirst()
                .map(entity -> entity.getVariant().getRegisteredName()).orElse("<none>"));
        LitematicaPlusGameTest.require(variant.equals("minecraft:kebab"), "the painting should be the schematic one, got " + variant);

        TestUi.aim(context, world, 62.5, -60.0, 93.5, 63.5, -58.5, 91.05);
        TestUi.use(context, world, 16);
        LitematicaPlusGameTest.require(count(world, GlowItemFrame.class, new BlockPos(63, -59, 91)) == 1,
                "the glow item frame should hang next to the painting");

        TestUi.aim(context, world, 62.5, -60.0, 93.5, 64.5, -59.2, 92.5);
        TestUi.use(context, world, 16);
        float yaw = world.getServer().computeOnServer(server -> server.overworld()
                .getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(64, -60, 92))).stream().findFirst()
                .map(ArmorStand::getYRot).orElse(Float.NaN));
        LitematicaPlusGameTest.require(Math.abs(yaw - 90.0F) < 0.5F, "the armor stand should face as in the schematic, yaw " + yaw);
        TestUi.use(context, world, 16);
        String helmet = world.getServer().computeOnServer(server -> server.overworld()
                .getEntitiesOfClass(ArmorStand.class, new AABB(new BlockPos(64, -60, 92))).stream().findFirst()
                .map(entity -> BuiltInRegistries.ITEM.getKey(entity.getItemBySlot(EquipmentSlot.HEAD).getItem()).toString())
                .orElse("<none>"));
        LitematicaPlusGameTest.require(helmet.equals("minecraft:iron_helmet"), "the armor stand should get its helmet, got " + helmet);
        context.waitTicks(12);
        for (EntityAssist.Ghost ghost : context.computeOnClient(client -> List.copyOf(EntityAssist.ghosts(runtime)))) {
            LitematicaPlusGameTest.require(ghost.status() == EntityAssist.Status.DONE,
                    ghost.kind() + " should be done, is " + ghost.status());
        }
        world.getServer().runCommand("tp @p 62.5 -60 95.5 180 5");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(10);
        context.takeScreenshot("11b_entities_placed");

        context.runOnClient(client -> PlacementManager.get().rotate(placement, Rotation.CLOCKWISE_90));
        context.waitTicks(5);
        LitematicaPlusGameTest.settle(context, placement);
        context.waitTicks(12);
        PlacementRuntime turnedRuntime = context.computeOnClient(client -> GhostEngine.get().runtime(placement.id()));
        List<EntityAssist.Ghost> rotated = context.computeOnClient(client -> List.copyOf(EntityAssist.ghosts(turnedRuntime)));
        LitematicaPlusGameTest.require(rotated.size() == 4, "rotated placement should keep four entities, got " + rotated.size());
        LitematicaPlusGameTest.require(rotated.get(0).pos().equals(new BlockPos(59, -59, 91)),
                "a clockwise turn should move the frame to 59 -59 91, got " + rotated.get(0).pos());
        LitematicaPlusGameTest.require(rotated.get(0).entity().getDirection() == Direction.WEST,
                "a clockwise turn should make the frame face west, got " + rotated.get(0).entity().getDirection());

        context.runOnClient(client -> {
            LitematicaPlusConfig config = LitematicaPlusConfig.currentCopy();
            config.easyPlaceEnabled = false;
            LitematicaPlusConfig.save(config);
            PlacementManager.get().remove(placement);
        });
        world.getServer().runCommand("kill @e[type=!minecraft:player,x=55,y=-62,z=85,dx=14,dy=8,dz=12]");
        world.getServer().runCommand("fill 56 -60 86 68 -56 96 minecraft:air");
        world.getServer().runCommand("clear @p");
        world.getConnection().waitForClientboundPackets();
        context.waitTicks(5);
    }

    private static <T extends net.minecraft.world.entity.Entity> int count(TestSingleplayerContext world, Class<T> type,
            BlockPos pos) {
        return world.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(type, new AABB(pos)).size());
    }

    private static CompoundTag entity(String id, double x, double y, double z) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(x));
        position.add(DoubleTag.valueOf(y));
        position.add(DoubleTag.valueOf(z));
        tag.put("Pos", position);
        return tag;
    }

    private static CompoundTag item(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", 1);
        return tag;
    }
}
