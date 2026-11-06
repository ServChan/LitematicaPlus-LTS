package org.lts.litematicaplus.mhud;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.tool.EntityAssist;
import org.lts.litematicaplus.world.PlacementVolume;

public final class SchematicEntities {
    private SchematicEntities() {
    }

    public static boolean active() {
        return MaterialHud.enabled() && MaterialHudConfig.current().countEntities;
    }

    public static void add(PlacementRuntime runtime, Reference2LongOpenHashMap<Item> total,
            Reference2LongOpenHashMap<Item> missing) {
        PlacementVolume volume = runtime == null ? null : runtime.volume();
        if (volume == null) {
            return;
        }
        Reference2LongOpenHashMap<Item> wanted = new Reference2LongOpenHashMap<>();
        for (PlacementVolume.EntitySeed seed : volume.entities()) {
            CompoundTag tag = seed.tag();
            String id = tag.getStringOr("id", "");
            Item item = placeable(id) ? itemFor(id) : null;
            if (item == null) {
                continue;
            }
            wanted.addTo(item, 1L);
            Item inside = tag.getCompound("Item").map(compound -> itemFor(compound.getStringOr("id", ""))).orElse(null);
            if (inside != null) {
                wanted.addTo(inside, 1L);
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        Reference2LongOpenHashMap<Item> done = new Reference2LongOpenHashMap<>();
        for (EntityAssist.Ghost ghost : EntityAssist.ghosts(runtime)) {
            if (ghost.status() == EntityAssist.Status.DONE) {
                Item item = itemFor(EntityType.getKey(ghost.entity().getType()).toString());
                if (item != null) {
                    done.addTo(item, 1L);
                }
            }
        }
        for (Reference2LongOpenHashMap.Entry<Item> entry : wanted.reference2LongEntrySet()) {
            long count = entry.getLongValue();
            total.addTo(entry.getKey(), count);
            missing.addTo(entry.getKey(), Math.max(0L, count - done.getLong(entry.getKey())));
        }
    }

    static boolean placeable(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (path) {
            case "item_frame", "glow_item_frame", "painting", "armor_stand", "end_crystal", "minecart", "ItemFrame",
                 "Painting", "ArmorStand", "EnderCrystal", "MinecartRideable" -> true;
            default -> path.endsWith("_minecart") || path.endsWith("_boat") || path.endsWith("_raft");
        };
    }

    static Item itemFor(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String clean = id.contains(":") ? id : "minecraft:" + id;
        clean = switch (clean) {
            case "minecraft:ItemFrame" -> "minecraft:item_frame";
            case "minecraft:Painting" -> "minecraft:painting";
            case "minecraft:ArmorStand" -> "minecraft:armor_stand";
            case "minecraft:EnderCrystal" -> "minecraft:end_crystal";
            case "minecraft:MinecartRideable", "minecraft:minecart" -> "minecraft:minecart";
            default -> clean;
        };
        Identifier key = Identifier.tryParse(clean);
        if (key == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
        return item == Items.AIR ? null : item;
    }
}
