package org.lts.litematicaplus.verify;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.engine.GhostEngine;
import org.lts.litematicaplus.engine.PlacementRuntime;
import org.lts.litematicaplus.materials.ContainerMemory;
import org.lts.litematicaplus.world.PlacementVolume;

public final class BlockDataCheck {
    private static final int CHECKS_PER_TICK = 128;
    private static final int LIMIT = 1024;
    private static final Map<UUID, Scan> SCANS = new HashMap<>();
    private static List<Problem> visible = List.of();

    public enum Kind {
        SIGN,
        BANNER,
        POT,
        CONTAINER;

        public Component label() {
            return Component.translatable("litematicaplus.verifier.data." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public record Problem(BlockPos pos, Kind kind, BlockState expected) {
    }

    private static final class Scan {
        final PlacementVolume volume;
        final long[] keys;
        final Long2ObjectLinkedOpenHashMap<Problem> problems = new Long2ObjectLinkedOpenHashMap<>();
        int cursor;

        Scan(PlacementVolume volume) {
            this.volume = volume;
            this.keys = volume.blockEntities().keySet().toLongArray();
        }
    }

    private BlockDataCheck() {
    }

    public static List<Problem> visible() {
        return visible;
    }

    public static Problem at(BlockPos pos) {
        for (Scan scan : SCANS.values()) {
            Problem problem = scan.problems.get(pos.asLong());
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    public static List<Problem> problems(PlacementRuntime runtime) {
        Scan scan = runtime == null ? null : SCANS.get(runtime.placement().id());
        return scan == null ? List.of() : List.copyOf(scan.problems.values());
    }

    public static void tick(Minecraft minecraft) {
        LitematicaPlusConfig config = LitematicaPlusConfig.current();
        ClientLevel level = minecraft.level;
        if (!config.verifyBlockData || !config.renderEnabled || level == null || minecraft.player == null) {
            if (!SCANS.isEmpty()) {
                SCANS.clear();
                visible = List.of();
            }
            return;
        }
        GhostEngine engine = GhostEngine.get();
        boolean changed = SCANS.keySet().removeIf(id -> {
            PlacementRuntime runtime = engine.runtime(id);
            return runtime == null || runtime.volume() == null || !runtime.placement().enabled();
        });
        int active = 0;
        for (PlacementRuntime runtime : engine.runtimes()) {
            if (runtime.volume() != null && runtime.placement().enabled()) {
                active++;
            }
        }
        if (active == 0) {
            visible = List.of();
            return;
        }
        int budget = Math.max(1, CHECKS_PER_TICK / active);
        for (PlacementRuntime runtime : engine.runtimes()) {
            PlacementVolume volume = runtime.volume();
            if (volume == null || !runtime.placement().enabled()) {
                continue;
            }
            UUID id = runtime.placement().id();
            Scan scan = SCANS.get(id);
            if (scan == null || scan.volume != volume) {
                scan = new Scan(volume);
                SCANS.put(id, scan);
                changed = true;
            }
            changed |= advance(level, engine, scan, budget);
        }
        if (changed) {
            List<Problem> next = new ArrayList<>();
            for (Scan scan : SCANS.values()) {
                next.addAll(scan.problems.values());
            }
            visible = List.copyOf(next);
        }
    }

    private static boolean advance(ClientLevel level, GhostEngine engine, Scan scan, int budget) {
        boolean changed = false;
        int checks = Math.min(budget, scan.keys.length);
        for (int i = 0; i < checks; i++) {
            if (scan.cursor >= scan.keys.length) {
                scan.cursor = 0;
            }
            long key = scan.keys[scan.cursor++];
            int x = BlockPos.getX(key);
            int y = BlockPos.getY(key);
            int z = BlockPos.getZ(key);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            Kind kind = null;
            BlockPos pos = new BlockPos(x, y, z);
            if (engine.categoryAt(x, y, z) == MismatchType.CORRECT) {
                BlockEntity live = level.getBlockEntity(pos);
                kind = live == null ? null : compare(level, scan.volume, pos, scan.volume.blockEntities().get(key), live);
            }
            if (kind == null) {
                changed |= scan.problems.remove(key) != null;
            } else if (scan.problems.size() < LIMIT || scan.problems.containsKey(key)) {
                Problem old = scan.problems.get(key);
                if (old == null || old.kind() != kind) {
                    scan.problems.put(key, new Problem(pos, kind, scan.volume.stateAt(x, y, z)));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static Kind compare(ClientLevel level, PlacementVolume volume, BlockPos pos, CompoundTag tag, BlockEntity live) {
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        if (live instanceof SignBlockEntity sign) {
            return sameText(ops, tag, "front_text", sign.getText(SignTextSlot.FRONT))
                    && sameText(ops, tag, "back_text", sign.getText(SignTextSlot.BACK)) ? null : Kind.SIGN;
        }
        if (live instanceof BannerBlockEntity banner) {
            Tag patterns = tag.get("patterns");
            if (patterns == null) {
                return banner.getPatterns().layers().isEmpty() ? null : Kind.BANNER;
            }
            return BannerPatternLayers.CODEC.parse(ops, patterns).result()
                    .map(expected -> expected.equals(banner.getPatterns()) ? null : Kind.BANNER).orElse(null);
        }
        if (live instanceof DecoratedPotBlockEntity pot) {
            Tag sherds = tag.get(DecoratedPotBlockEntity.TAG_SHERDS);
            return sherds == null ? null : PotDecorations.CODEC.parse(ops, sherds).result()
                    .map(expected -> expected.equals(pot.getDecorations()) ? null : Kind.POT).orElse(null);
        }
        if (live instanceof Container) {
            return compareContainer(volume, pos, tag);
        }
        return null;
    }

    private static boolean sameText(RegistryOps<Tag> ops, CompoundTag tag, String key, SignText live) {
        return tag.getCompound(key).flatMap(side -> SignText.CODEC.parse(ops, side).result())
                .map(expected -> sameLines(expected, live)).orElse(true);
    }

    private static boolean sameLines(SignText expected, SignText live) {
        List<Component> wanted = expected.getMessages(false);
        List<Component> found = live.getMessages(false);
        for (int line = 0; line < Math.min(wanted.size(), found.size()); line++) {
            if (!wanted.get(line).getString().equals(found.get(line).getString())) {
                return false;
            }
        }
        return true;
    }

    private static Kind compareContainer(PlacementVolume volume, BlockPos pos, CompoundTag tag) {
        ContainerMemory.Stored stored = ContainerMemory.at(pos);
        if (stored == null) {
            return null;
        }
        Reference2LongOpenHashMap<Item> expected = new Reference2LongOpenHashMap<>();
        addItems(tag, expected);
        if (stored.pair() != null) {
            CompoundTag other = volume.blockEntities().get(stored.pair().asLong());
            if (other != null) {
                addItems(other, expected);
            }
        }
        return expected.isEmpty() || sameCounts(expected, stored.counts()) ? null : Kind.CONTAINER;
    }

    static void addItems(CompoundTag tag, Reference2LongMap<Item> totals) {
        ListTag items = tag.getListOrEmpty("Items");
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompoundOrEmpty(i);
            Identifier id = Identifier.tryParse(entry.getStringOr("id", ""));
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
            int count = entry.getIntOr("count", entry.getByteOr("Count", (byte) 0));
            if (item != Items.AIR && count > 0) {
                totals.put(item, totals.getLong(item) + count);
            }
        }
    }

    static boolean sameCounts(Reference2LongMap<Item> expected, Reference2LongMap<Item> found) {
        for (Reference2LongMap.Entry<Item> entry : expected.reference2LongEntrySet()) {
            if (found.getLong(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        for (Reference2LongMap.Entry<Item> entry : found.reference2LongEntrySet()) {
            if (entry.getLongValue() > 0L && expected.getLong(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }
}
