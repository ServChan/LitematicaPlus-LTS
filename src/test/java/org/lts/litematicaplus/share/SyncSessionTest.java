package org.lts.litematicaplus.share;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class SyncSessionTest {
    private static final EnumSet<SyncFeature> SERVER_FEATURES = EnumSet.allOf(SyncFeature.class);

    private static final class Recorder implements SyncSession.Listener {
        final List<String> events = new ArrayList<>();
        byte[] downloaded;
        SharedPlacement last;

        @Override
        public void ready() {
            this.events.add("ready");
        }

        @Override
        public void rejected(String serverVersion) {
            this.events.add("rejected " + serverVersion);
        }

        @Override
        public void added(SharedPlacement placement) {
            this.last = placement;
            this.events.add("added " + placement.title());
        }

        @Override
        public void removed(SharedPlacement placement) {
            this.events.add("removed " + placement.title());
        }

        @Override
        public void modified(SharedPlacement placement) {
            this.last = placement;
            this.events.add("modified " + placement.title());
        }

        @Override
        public void shared(SharedPlacement placement) {
            this.last = placement;
            this.events.add("shared " + placement.title());
        }

        @Override
        public void shareFailed(SharedPlacement placement) {
            this.events.add("shareFailed " + placement.title());
        }

        @Override
        public void downloaded(SharedPlacement placement, byte[] data) {
            this.downloaded = data;
            this.events.add("downloaded " + placement.title());
        }

        @Override
        public void downloadFailed(SharedPlacement placement) {
            this.events.add("downloadFailed " + placement.title());
        }

        @Override
        public void modifyAccepted(UUID id) {
            this.events.add("modifyAccepted");
        }

        @Override
        public void modifyDenied(UUID id) {
            this.events.add("modifyDenied");
        }

        @Override
        public void message(String type, String key) {
            this.events.add("message " + type + " " + key);
        }

        @Override
        public void error(SyncPacket packet, RuntimeException failure) {
            throw failure;
        }
    }

    private final List<byte[]> outbox = new ArrayList<>();
    private final Recorder recorder = new Recorder();
    private final SyncSession session = new SyncSession(this.outbox::add, this.recorder);

    private SyncCodec.Frame next(SyncPacket expected) {
        assertFalse(this.outbox.isEmpty(), "expected " + expected + " but nothing was sent");
        SyncCodec.Frame frame = SyncCodec.parse(this.outbox.removeFirst());
        assertNotNull(frame);
        assertEquals(expected, frame.type());
        return frame;
    }

    private void server(SyncPacket type, FriendlyByteBuf body) {
        this.session.receive(SyncCodec.frame(type, body));
    }

    private static FriendlyByteBuf id(UUID id) {
        FriendlyByteBuf buf = SyncCodec.buffer();
        buf.writeUUID(id);
        return buf;
    }

    private static SharedPlacement sample(String name, byte[] data) {
        SharedPlacement placement = new SharedPlacement(UUID.randomUUID(), SyncCodec.hash(data), name + ".litematic", name);
        placement.setOwner(new SharedPlacement.Player(UUID.randomUUID(), "Builder"));
        placement.setLastModifiedBy(placement.owner());
        placement.setVersions(7, 4790);
        placement.place(-120, -60, 3051, "minecraft:overworld", 1, 2);
        return placement;
    }

    private void handshake(SharedPlacement... existing) {
        FriendlyByteBuf version = SyncCodec.buffer();
        version.writeUtf("0.3.20");
        server(SyncPacket.REGISTER_VERSION, version);
        next(SyncPacket.FEATURE_REQUEST);
        FriendlyByteBuf features = SyncCodec.buffer();
        features.writeUtf(SyncFeature.join(SERVER_FEATURES));
        server(SyncPacket.FEATURE, features);
        assertEquals(SyncSession.PROTOCOL_VERSION, next(SyncPacket.REGISTER_VERSION).body().readUtf());
        server(SyncPacket.FEATURE_REQUEST, null);
        EnumSet<SyncFeature> client = SyncFeature.parse(next(SyncPacket.FEATURE).body().readUtf());
        assertEquals(EnumSet.allOf(SyncFeature.class), client);
        FriendlyByteBuf confirm = SyncCodec.buffer();
        confirm.writeInt(existing.length);
        for (SharedPlacement placement : existing) {
            SyncCodec.writeMetadata(confirm, placement, client);
        }
        server(SyncPacket.CONFIRM_USER, confirm);
        assertTrue(this.session.ready());
    }

    @Test
    void handshakeFollowsThePaperPluginOrder() {
        SharedPlacement house = sample("house", new byte[] {1, 2, 3});
        house.setRegions(List.of(new SharedPlacement.RegionShift("roof", 1, 2, -3, 3, 1)));
        handshake(house);
        assertEquals(List.of("ready"), this.recorder.events);
        SharedPlacement received = this.session.placement(house.id());
        assertEquals("house", received.title());
        assertEquals(house.hash(), received.hash());
        assertEquals("Builder", received.owner().name());
        assertEquals(-120, received.x());
        assertEquals(-60, received.y());
        assertEquals(3051, received.z());
        assertEquals(1, received.rotation());
        assertEquals(2, received.mirror());
        assertEquals(4790, received.dataVersion());
        assertEquals(house.regions(), received.regions());
        assertTrue(this.outbox.isEmpty());
    }

    @Test
    void legacyServerSkipsFeatureNegotiation() {
        assertTrue(SyncSession.legacy("0.1.7"));
        assertFalse(SyncSession.legacy("0.10.1"));
        assertFalse(SyncSession.legacy("0.3.20"));
        FriendlyByteBuf version = SyncCodec.buffer();
        version.writeUtf("0.1.4");
        server(SyncPacket.REGISTER_VERSION, version);
        next(SyncPacket.REGISTER_VERSION);
        assertEquals(EnumSet.of(SyncFeature.CORE), this.session.serverFeatures());
    }

    @Test
    void tooOldServerIsRejected() {
        FriendlyByteBuf version = SyncCodec.buffer();
        version.writeUtf(SyncSession.INCOMPATIBLE_VERSION);
        server(SyncPacket.REGISTER_VERSION, version);
        assertEquals(List.of("rejected 0.0.1"), this.recorder.events);
        assertTrue(this.outbox.isEmpty());
    }

    @Test
    void sharingStreamsTheFileInAcknowledgedChunks() {
        handshake();
        byte[] data = new byte[SyncSession.CHUNK * 2 + 7232];
        new Random(4).nextBytes(data);
        SharedPlacement placement = sample("tower", data);
        assertTrue(this.session.share(placement, data));
        SharedPlacement announced = SyncCodec.readMetadata(next(SyncPacket.REGISTER_METADATA).body(), SERVER_FEATURES);
        assertEquals(placement.id(), announced.id());
        assertEquals(placement.hash(), announced.hash());
        assertEquals("tower.litematic", announced.fileName());
        server(SyncPacket.REQUEST_LITEMATIC, id(placement.id()));
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        List<Integer> sizes = new ArrayList<>();
        while (true) {
            SyncCodec.Frame frame = SyncCodec.parse(this.outbox.removeFirst());
            assertEquals(placement.id(), frame.body().readUUID());
            if (frame.type() == SyncPacket.FINISHED_LITEMATIC) {
                break;
            }
            assertEquals(SyncPacket.SEND_LITEMATIC, frame.type());
            int size = frame.body().readInt();
            sizes.add(size);
            byte[] chunk = new byte[size];
            frame.body().readBytes(chunk);
            received.writeBytes(chunk);
            server(SyncPacket.RECEIVED_LITEMATIC, id(placement.id()));
        }
        assertEquals(List.of(SyncSession.CHUNK, SyncSession.CHUNK, 7232), sizes);
        assertArrayEquals(data, received.toByteArray());
        assertEquals(placement.hash(), SyncCodec.hash(received.toByteArray()));
        FriendlyByteBuf broadcast = SyncCodec.buffer();
        SyncCodec.writeMetadata(broadcast, announced, EnumSet.allOf(SyncFeature.class));
        server(SyncPacket.REGISTER_METADATA, broadcast);
        assertEquals(List.of("ready", "shared tower"), this.recorder.events);
        assertFalse(this.session.isSharing(placement.id()));
    }

    @Test
    void cancelledShareIsReported() {
        handshake();
        byte[] data = {9, 9, 9};
        SharedPlacement placement = sample("wall", data);
        assertTrue(this.session.share(placement, data));
        next(SyncPacket.REGISTER_METADATA);
        server(SyncPacket.CANCEL_SHARE, id(placement.id()));
        assertEquals(List.of("ready", "shareFailed wall"), this.recorder.events);
    }

    @Test
    void downloadVerifiesTheHash() {
        byte[] data = new byte[SyncSession.CHUNK + 100];
        new Random(7).nextBytes(data);
        SharedPlacement placement = sample("farm", data);
        handshake(placement);
        assertTrue(this.session.download(placement.id()));
        assertEquals(placement.id(), next(SyncPacket.REQUEST_LITEMATIC).body().readUUID());
        for (int offset = 0; offset < data.length; offset += SyncSession.CHUNK) {
            int size = Math.min(SyncSession.CHUNK, data.length - offset);
            FriendlyByteBuf chunk = id(placement.id());
            chunk.writeInt(size);
            chunk.writeBytes(data, offset, size);
            server(SyncPacket.SEND_LITEMATIC, chunk);
            assertEquals(placement.id(), next(SyncPacket.RECEIVED_LITEMATIC).body().readUUID());
        }
        server(SyncPacket.FINISHED_LITEMATIC, id(placement.id()));
        assertArrayEquals(data, this.recorder.downloaded);
        assertEquals(List.of("ready", "downloaded farm"), this.recorder.events);
    }

    @Test
    void corruptDownloadFails() {
        SharedPlacement placement = sample("bridge", new byte[] {1, 2, 3, 4});
        handshake(placement);
        assertTrue(this.session.download(placement.id()));
        next(SyncPacket.REQUEST_LITEMATIC);
        FriendlyByteBuf chunk = id(placement.id());
        chunk.writeInt(4);
        chunk.writeBytes(new byte[] {1, 2, 3, 5});
        server(SyncPacket.SEND_LITEMATIC, chunk);
        next(SyncPacket.RECEIVED_LITEMATIC);
        server(SyncPacket.FINISHED_LITEMATIC, id(placement.id()));
        assertEquals(List.of("ready", "downloadFailed bridge"), this.recorder.events);
    }

    @Test
    void silentDownloadTimesOut() {
        SharedPlacement placement = sample("arch", new byte[] {1});
        handshake(placement);
        assertTrue(this.session.download(placement.id()));
        next(SyncPacket.REQUEST_LITEMATIC);
        for (int i = 0; i <= SyncSession.TRANSFER_TIMEOUT_TICKS; i++) {
            this.session.tick();
        }
        next(SyncPacket.CANCEL_LITEMATIC);
        assertEquals(List.of("ready", "downloadFailed arch"), this.recorder.events);
    }

    @Test
    void modifyRoundTrip() {
        SharedPlacement placement = sample("castle", new byte[] {5});
        handshake(placement);
        assertTrue(this.session.requestModify(placement.id()));
        next(SyncPacket.MODIFY_REQUEST);
        server(SyncPacket.MODIFY_REQUEST_ACCEPT, id(placement.id()));
        assertTrue(this.session.isModifying(placement.id()));
        assertTrue(this.session.finishModify(placement.id(), 10, 70, -20, 3, 0));
        FriendlyByteBuf finish = next(SyncPacket.MODIFY_FINISH).body();
        assertEquals(placement.id(), finish.readUUID());
        SharedPlacement moved = new SharedPlacement(placement.id(), placement.hash(), "", "");
        SyncCodec.readPosition(finish, moved, SERVER_FEATURES);
        assertEquals(10, moved.x());
        assertEquals(70, moved.y());
        assertEquals(-20, moved.z());
        assertEquals(3, moved.rotation());
        assertEquals("minecraft:overworld", moved.dimension());
        FriendlyByteBuf broadcast = id(placement.id());
        SyncCodec.writePosition(broadcast, moved, EnumSet.allOf(SyncFeature.class));
        SyncCodec.writePlayer(broadcast, new SharedPlacement.Player(UUID.randomUUID(), "Editor"));
        server(SyncPacket.MODIFY, broadcast);
        assertEquals("Editor", this.recorder.last.lastModifiedBy().name());
        assertEquals(List.of("ready", "modifyAccepted", "modified castle"), this.recorder.events);
    }

    @Test
    void deniedOrAbandonedModifyLeavesNoLock() {
        SharedPlacement placement = sample("gate", new byte[] {6});
        handshake(placement);
        assertTrue(this.session.requestModify(placement.id()));
        next(SyncPacket.MODIFY_REQUEST);
        server(SyncPacket.MODIFY_REQUEST_DENY, id(placement.id()));
        assertFalse(this.session.isModifying(placement.id()));
        assertTrue(this.session.requestModify(placement.id()));
        next(SyncPacket.MODIFY_REQUEST);
        this.session.abandonModify(placement.id());
        server(SyncPacket.MODIFY_REQUEST_ACCEPT, id(placement.id()));
        assertEquals(placement.id(), next(SyncPacket.MODIFY_FINISH).body().readUUID());
        assertFalse(this.session.isModifying(placement.id()));
        assertEquals(List.of("ready", "modifyDenied"), this.recorder.events);
    }

    @Test
    void removalAndMessagesReachTheListener() {
        SharedPlacement placement = sample("statue", new byte[] {8});
        handshake(placement);
        assertTrue(this.session.remove(placement.id()));
        next(SyncPacket.REMOVE_SYNCMATIC);
        server(SyncPacket.REMOVE_SYNCMATIC, id(placement.id()));
        FriendlyByteBuf message = SyncCodec.buffer();
        message.writeUtf("ERROR");
        message.writeUtf("syncmatica.error.cancelled_transmit_exceed_quota");
        server(SyncPacket.MESSAGE, message);
        assertEquals(List.of("ready", "removed statue", "message ERROR syncmatica.error.cancelled_transmit_exceed_quota"),
                this.recorder.events);
        assertTrue(this.session.placements().isEmpty());
    }

    @Test
    void blockPositionsMatchThePluginPacking() {
        int[][] samples = {{-120, -60, 3051}, {29999999, 319, -29999999}, {0, -64, 0}, {-1, 2047, -1}};
        for (int[] pos : samples) {
            FriendlyByteBuf buf = SyncCodec.buffer();
            buf.writeBlockPos(new BlockPos(pos[0], pos[1], pos[2]));
            long expected = ((long) (pos[0] & 0x3FFFFFF) << 38) | ((long) (pos[2] & 0x3FFFFFF) << 12)
                    | ((long) (pos[1] & 0xFFF));
            assertEquals(expected, buf.readLong());
        }
    }

    @Test
    void framesCarryTheTypeIdentifier() {
        FriendlyByteBuf buf = SyncCodec.buffer();
        buf.writeUtf("syncmatica:mesage");
        buf.writeInt(42);
        byte[] expected = new byte[buf.readableBytes()];
        buf.readBytes(expected);
        FriendlyByteBuf body = SyncCodec.buffer();
        body.writeInt(42);
        assertArrayEquals(expected, SyncCodec.frame(SyncPacket.MESSAGE, body));
    }
}
