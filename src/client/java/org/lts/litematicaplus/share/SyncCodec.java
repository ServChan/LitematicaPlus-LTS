package org.lts.litematicaplus.share;

import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public final class SyncCodec {
    public static final String NAMESPACE = "syncmatica";
    public static final int MAX_STRING = 32767;
    public static final int MAX_REGIONS = 4096;

    public record Frame(SyncPacket type, FriendlyByteBuf body) {
    }

    private SyncCodec() {
    }

    public static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static byte[] frame(SyncPacket type, FriendlyByteBuf body) {
        FriendlyByteBuf out = buffer();
        out.writeUtf(type.id());
        if (body != null) {
            out.writeBytes(body, body.readerIndex(), body.readableBytes());
        }
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        return bytes;
    }

    public static Frame parse(byte[] raw) {
        FriendlyByteBuf in = new FriendlyByteBuf(Unpooled.wrappedBuffer(raw));
        SyncPacket type = SyncPacket.byId(in.readUtf(MAX_STRING));
        return type == null ? null : new Frame(type, in);
    }

    public static UUID hash(byte[] data) {
        try {
            return UUID.nameUUIDFromBytes(MessageDigest.getInstance("MD5").digest(data));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }

    public static UUID hash(MessageDigest digest) {
        return UUID.nameUUIDFromBytes(digest.digest());
    }

    public static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 unavailable", exception);
        }
    }

    public static void writeMetadata(FriendlyByteBuf buf, SharedPlacement placement, Set<SyncFeature> features) {
        buf.writeUUID(placement.id());
        buf.writeUtf(placement.fileName());
        buf.writeUUID(placement.hash());
        if (features.contains(SyncFeature.DISPLAY_NAME)) {
            buf.writeUtf(placement.displayName());
        }
        if (features.contains(SyncFeature.CORE_EX)) {
            writePlayer(buf, placement.owner());
            writePlayer(buf, placement.lastModifiedBy());
        }
        if (features.contains(SyncFeature.VERSION)) {
            buf.writeVarInt(placement.litematicVersion());
            buf.writeVarInt(placement.dataVersion());
        }
        writePosition(buf, placement, features);
    }

    public static SharedPlacement readMetadata(FriendlyByteBuf buf, Set<SyncFeature> features) {
        UUID id = buf.readUUID();
        String fileName = buf.readUtf(MAX_STRING);
        UUID hash = buf.readUUID();
        String displayName = features.contains(SyncFeature.DISPLAY_NAME) ? buf.readUtf(MAX_STRING) : fileName;
        SharedPlacement placement = new SharedPlacement(id, hash, fileName, displayName);
        if (features.contains(SyncFeature.CORE_EX)) {
            placement.setOwner(readPlayer(buf));
            placement.setLastModifiedBy(readPlayer(buf));
        }
        if (features.contains(SyncFeature.VERSION)) {
            int litematicVersion = buf.readVarInt();
            placement.setVersions(litematicVersion, buf.readVarInt());
        }
        readPosition(buf, placement, features);
        return placement;
    }

    public static void writePosition(FriendlyByteBuf buf, SharedPlacement placement, Set<SyncFeature> features) {
        buf.writeBlockPos(new BlockPos(placement.x(), placement.y(), placement.z()));
        buf.writeUtf(placement.dimension());
        buf.writeInt(placement.rotation());
        buf.writeInt(placement.mirror());
        if (features.contains(SyncFeature.CORE_EX)) {
            buf.writeInt(placement.regions().size());
            for (SharedPlacement.RegionShift region : placement.regions()) {
                buf.writeUtf(region.name());
                buf.writeBlockPos(new BlockPos(region.x(), region.y(), region.z()));
                buf.writeInt(region.rotation());
                buf.writeInt(region.mirror());
            }
        }
    }

    public static void readPosition(FriendlyByteBuf buf, SharedPlacement placement, Set<SyncFeature> features) {
        BlockPos pos = buf.readBlockPos();
        String dimension = buf.readUtf(MAX_STRING);
        int rotation = buf.readInt();
        int mirror = buf.readInt();
        placement.place(pos.getX(), pos.getY(), pos.getZ(), dimension, rotation, mirror);
        List<SharedPlacement.RegionShift> regions = new ArrayList<>();
        if (features.contains(SyncFeature.CORE_EX)) {
            int count = buf.readInt();
            if (count < 0 || count > MAX_REGIONS) {
                throw new IllegalArgumentException("sub-region count out of range: " + count);
            }
            for (int i = 0; i < count; i++) {
                String name = buf.readUtf(MAX_STRING);
                BlockPos regionPos = buf.readBlockPos();
                int regionRotation = buf.readInt();
                int regionMirror = buf.readInt();
                if (regionRotation < 0 || regionRotation > 3 || regionMirror < 0 || regionMirror > 2) {
                    throw new IllegalArgumentException("sub-region rotation/mirror out of range");
                }
                regions.add(new SharedPlacement.RegionShift(name, regionPos.getX(), regionPos.getY(), regionPos.getZ(),
                        regionRotation, regionMirror));
            }
        }
        placement.setRegions(regions);
    }

    public static void writePlayer(FriendlyByteBuf buf, SharedPlacement.Player player) {
        buf.writeUUID(player.uuid());
        buf.writeUtf(player.name());
    }

    public static SharedPlacement.Player readPlayer(FriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();
        return new SharedPlacement.Player(uuid, buf.readUtf(MAX_STRING));
    }
}
