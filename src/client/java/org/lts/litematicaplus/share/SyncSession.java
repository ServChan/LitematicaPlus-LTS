package org.lts.litematicaplus.share;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;

public final class SyncSession {
    public static final String PROTOCOL_VERSION = "0.3.20";
    public static final String INCOMPATIBLE_VERSION = "0.0.1";
    public static final int CHUNK = 16384;
    public static final int MAX_CHUNK = 32768;
    public static final int MAX_FILE = 64 << 20;
    public static final int MAX_PLACEMENTS = 100000;
    public static final int TRANSFER_TIMEOUT_TICKS = 30 * 20;
    public static final int SHARE_TIMEOUT_TICKS = 60 * 20;
    public static final Set<SyncFeature> FEATURES = Collections.unmodifiableSet(EnumSet.allOf(SyncFeature.class));

    public enum Phase {
        IDLE,
        FEATURES,
        VERSION,
        READY,
        CLOSED
    }

    public interface Listener {
        void ready();

        void rejected(String serverVersion);

        void added(SharedPlacement placement);

        void removed(SharedPlacement placement);

        void modified(SharedPlacement placement);

        void shared(SharedPlacement placement);

        void shareFailed(SharedPlacement placement);

        void downloaded(SharedPlacement placement, byte[] data);

        void downloadFailed(SharedPlacement placement);

        void modifyAccepted(UUID id);

        void modifyDenied(UUID id);

        void message(String type, String key);

        void error(SyncPacket packet, RuntimeException failure);
    }

    private static final class Upload {
        final SharedPlacement placement;
        final byte[] data;
        int offset;
        boolean requested;
        int lastActivity;

        Upload(SharedPlacement placement, byte[] data, int now) {
            this.placement = placement;
            this.data = data;
            this.lastActivity = now;
        }
    }

    private static final class Download {
        final SharedPlacement placement;
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        final MessageDigest digest = SyncCodec.md5();
        int lastActivity;

        Download(SharedPlacement placement, int now) {
            this.placement = placement;
            this.lastActivity = now;
        }
    }

    private final Consumer<byte[]> sender;
    private final Listener listener;
    private Phase phase = Phase.IDLE;
    private String serverVersion = "";
    private EnumSet<SyncFeature> serverFeatures = EnumSet.noneOf(SyncFeature.class);
    private final Map<UUID, SharedPlacement> placements = new LinkedHashMap<>();
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private final Map<UUID, Download> downloads = new HashMap<>();
    private final Map<UUID, Integer> modifyRequests = new HashMap<>();
    private final Set<UUID> abandoned = new HashSet<>();
    private final Set<UUID> modifying = new HashSet<>();
    private int ticks;

    public SyncSession(Consumer<byte[]> sender, Listener listener) {
        this.sender = sender;
        this.listener = listener;
    }

    public Phase phase() {
        return this.phase;
    }

    public boolean ready() {
        return this.phase == Phase.READY;
    }

    public String serverVersion() {
        return this.serverVersion;
    }

    public Set<SyncFeature> serverFeatures() {
        return Collections.unmodifiableSet(this.serverFeatures);
    }

    public Collection<SharedPlacement> placements() {
        return Collections.unmodifiableCollection(this.placements.values());
    }

    public SharedPlacement placement(UUID id) {
        return id == null ? null : this.placements.get(id);
    }

    public boolean canModify() {
        return this.serverFeatures.contains(SyncFeature.MODIFY);
    }

    public boolean isModifying(UUID id) {
        return this.modifying.contains(id);
    }

    public boolean isModifyPending(UUID id) {
        return this.modifyRequests.containsKey(id);
    }

    public boolean isSharing(UUID id) {
        return this.uploads.containsKey(id);
    }

    public boolean isDownloading(UUID id) {
        return this.downloads.containsKey(id);
    }

    public int downloaded(UUID id) {
        Download download = this.downloads.get(id);
        return download == null ? -1 : download.data.size();
    }

    public void receive(byte[] raw) {
        SyncCodec.Frame frame;
        try {
            frame = SyncCodec.parse(raw);
        } catch (RuntimeException failure) {
            this.listener.error(null, failure);
            return;
        }
        if (frame == null) {
            return;
        }
        try {
            handle(frame.type(), frame.body());
        } catch (RuntimeException failure) {
            this.listener.error(frame.type(), failure);
        }
    }

    private void handle(SyncPacket type, FriendlyByteBuf body) {
        switch (type) {
            case REGISTER_VERSION -> start(body.readUtf(SyncCodec.MAX_STRING));
            case FEATURE_REQUEST -> {
                if (this.phase != Phase.CLOSED) {
                    FriendlyByteBuf buf = SyncCodec.buffer();
                    buf.writeUtf(SyncFeature.join(FEATURES));
                    send(SyncPacket.FEATURE, buf);
                }
            }
            case FEATURE -> {
                if (this.phase == Phase.FEATURES) {
                    this.serverFeatures = SyncFeature.parse(body.readUtf(SyncCodec.MAX_STRING));
                    sendVersion();
                }
            }
            case CONFIRM_USER -> {
                if (this.phase == Phase.VERSION) {
                    confirm(body);
                }
            }
            default -> {
                if (this.phase == Phase.READY) {
                    handleReady(type, body);
                }
            }
        }
    }

    private void start(String version) {
        reset();
        this.serverVersion = version;
        if (INCOMPATIBLE_VERSION.equals(version)) {
            this.phase = Phase.CLOSED;
            this.listener.rejected(version);
            return;
        }
        if (legacy(version)) {
            this.serverFeatures = EnumSet.of(SyncFeature.CORE);
            sendVersion();
            return;
        }
        this.phase = Phase.FEATURES;
        send(SyncPacket.FEATURE_REQUEST, null);
    }

    static boolean legacy(String version) {
        if (version == null || !version.matches("^\\d+(\\.\\d+){2,4}$")) {
            return false;
        }
        String current = version;
        int minimum = current.indexOf('.');
        while (current.length() > minimum) {
            if (current.equals("0.1")) {
                return true;
            }
            current = current.substring(0, current.lastIndexOf('.'));
        }
        return false;
    }

    private void sendVersion() {
        this.phase = Phase.VERSION;
        FriendlyByteBuf buf = SyncCodec.buffer();
        buf.writeUtf(PROTOCOL_VERSION);
        send(SyncPacket.REGISTER_VERSION, buf);
    }

    private void confirm(FriendlyByteBuf body) {
        int count = body.readInt();
        if (count < 0 || count > MAX_PLACEMENTS) {
            throw new IllegalArgumentException("placement count out of range: " + count);
        }
        List<SharedPlacement> received = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            received.add(SyncCodec.readMetadata(body, FEATURES));
        }
        for (SharedPlacement placement : received) {
            this.placements.put(placement.id(), placement);
        }
        this.phase = Phase.READY;
        this.listener.ready();
    }

    private void handleReady(SyncPacket type, FriendlyByteBuf body) {
        switch (type) {
            case REGISTER_METADATA -> {
                SharedPlacement placement = SyncCodec.readMetadata(body, FEATURES);
                Upload upload = this.uploads.remove(placement.id());
                this.placements.put(placement.id(), placement);
                if (upload != null) {
                    this.listener.shared(placement);
                } else {
                    this.listener.added(placement);
                }
            }
            case REMOVE_SYNCMATIC -> {
                UUID id = body.readUUID();
                this.modifying.remove(id);
                this.modifyRequests.remove(id);
                this.abandoned.remove(id);
                Download download = this.downloads.remove(id);
                SharedPlacement removed = this.placements.remove(id);
                if (download != null) {
                    this.listener.downloadFailed(download.placement);
                }
                if (removed != null) {
                    this.listener.removed(removed);
                }
            }
            case MODIFY -> {
                SharedPlacement placement = this.placements.get(body.readUUID());
                if (placement != null) {
                    SyncCodec.readPosition(body, placement, FEATURES);
                    placement.setLastModifiedBy(SyncCodec.readPlayer(body));
                    this.listener.modified(placement);
                }
            }
            case MESSAGE -> {
                String kind = body.readUtf(SyncCodec.MAX_STRING);
                this.listener.message(kind, body.readUtf(SyncCodec.MAX_STRING));
            }
            case CANCEL_SHARE -> {
                Upload upload = this.uploads.remove(body.readUUID());
                if (upload != null) {
                    this.listener.shareFailed(upload.placement);
                }
            }
            case REQUEST_LITEMATIC -> {
                Upload upload = this.uploads.get(body.readUUID());
                if (upload != null) {
                    upload.requested = true;
                    upload.offset = 0;
                    sendChunk(upload);
                }
            }
            case RECEIVED_LITEMATIC -> {
                Upload upload = this.uploads.get(body.readUUID());
                if (upload != null && upload.requested && upload.offset <= upload.data.length) {
                    sendChunk(upload);
                }
            }
            case CANCEL_LITEMATIC -> {
                UUID id = body.readUUID();
                Upload upload = this.uploads.remove(id);
                if (upload != null) {
                    this.listener.shareFailed(upload.placement);
                }
                Download download = this.downloads.remove(id);
                if (download != null) {
                    this.listener.downloadFailed(download.placement);
                }
            }
            case SEND_LITEMATIC -> receiveChunk(body);
            case FINISHED_LITEMATIC -> {
                Download download = this.downloads.remove(body.readUUID());
                if (download != null) {
                    if (SyncCodec.hash(download.digest).equals(download.placement.hash())) {
                        this.listener.downloaded(download.placement, download.data.toByteArray());
                    } else {
                        this.listener.downloadFailed(download.placement);
                    }
                }
            }
            case MODIFY_REQUEST_ACCEPT -> {
                UUID id = body.readUUID();
                if (this.modifyRequests.remove(id) != null) {
                    if (this.abandoned.remove(id)) {
                        sendFinish(this.placements.get(id));
                    } else if (this.placements.containsKey(id)) {
                        this.modifying.add(id);
                        this.listener.modifyAccepted(id);
                    }
                }
            }
            case MODIFY_REQUEST_DENY -> {
                UUID id = body.readUUID();
                boolean pending = this.modifyRequests.remove(id) != null;
                boolean active = this.modifying.remove(id);
                this.abandoned.remove(id);
                if (pending || active) {
                    this.listener.modifyDenied(id);
                }
            }
            default -> {
            }
        }
    }

    private void receiveChunk(FriendlyByteBuf body) {
        UUID id = body.readUUID();
        Download download = this.downloads.get(id);
        if (download == null) {
            return;
        }
        int size = body.readInt();
        if (size < 0 || size > MAX_CHUNK || size > body.readableBytes()
                || download.data.size() + (long) size > MAX_FILE) {
            this.downloads.remove(id);
            sendId(SyncPacket.CANCEL_LITEMATIC, id);
            this.listener.downloadFailed(download.placement);
            return;
        }
        byte[] chunk = new byte[size];
        body.readBytes(chunk);
        download.data.write(chunk, 0, size);
        download.digest.update(chunk);
        download.lastActivity = this.ticks;
        sendId(SyncPacket.RECEIVED_LITEMATIC, id);
    }

    private void sendChunk(Upload upload) {
        upload.lastActivity = this.ticks;
        FriendlyByteBuf buf = SyncCodec.buffer();
        buf.writeUUID(upload.placement.id());
        int remaining = upload.data.length - upload.offset;
        if (remaining <= 0) {
            upload.offset = upload.data.length + 1;
            send(SyncPacket.FINISHED_LITEMATIC, buf);
            return;
        }
        int length = Math.min(CHUNK, remaining);
        buf.writeInt(length);
        buf.writeBytes(upload.data, upload.offset, length);
        upload.offset += length;
        send(SyncPacket.SEND_LITEMATIC, buf);
    }

    public boolean share(SharedPlacement placement, byte[] data) {
        if (!ready() || data == null || data.length == 0 || data.length > MAX_FILE
                || this.uploads.containsKey(placement.id()) || this.placements.containsKey(placement.id())) {
            return false;
        }
        this.uploads.put(placement.id(), new Upload(placement, data, this.ticks));
        FriendlyByteBuf buf = SyncCodec.buffer();
        SyncCodec.writeMetadata(buf, placement, writeFeatures());
        send(SyncPacket.REGISTER_METADATA, buf);
        return true;
    }

    public boolean download(UUID id) {
        SharedPlacement placement = this.placements.get(id);
        if (!ready() || placement == null || this.downloads.containsKey(id)) {
            return false;
        }
        this.downloads.put(id, new Download(placement, this.ticks));
        sendId(SyncPacket.REQUEST_LITEMATIC, id);
        return true;
    }

    public boolean requestModify(UUID id) {
        if (!ready() || !canModify() || !this.placements.containsKey(id) || this.modifying.contains(id)
                || this.modifyRequests.containsKey(id)) {
            return false;
        }
        this.abandoned.remove(id);
        this.modifyRequests.put(id, this.ticks);
        sendId(SyncPacket.MODIFY_REQUEST, id);
        return true;
    }

    public boolean finishModify(UUID id, int x, int y, int z, int rotation, int mirror) {
        SharedPlacement placement = this.placements.get(id);
        if (placement == null || !this.modifying.remove(id)) {
            return false;
        }
        placement.place(x, y, z, placement.dimension(), rotation, mirror);
        sendFinish(placement);
        return true;
    }

    public void abandonModify(UUID id) {
        if (this.modifying.remove(id)) {
            sendFinish(this.placements.get(id));
        } else if (this.modifyRequests.containsKey(id)) {
            this.abandoned.add(id);
        }
    }

    private void sendFinish(SharedPlacement placement) {
        if (placement == null) {
            return;
        }
        FriendlyByteBuf buf = SyncCodec.buffer();
        buf.writeUUID(placement.id());
        SyncCodec.writePosition(buf, placement, writeFeatures());
        send(SyncPacket.MODIFY_FINISH, buf);
    }

    public boolean remove(UUID id) {
        if (!ready() || !this.placements.containsKey(id)) {
            return false;
        }
        sendId(SyncPacket.REMOVE_SYNCMATIC, id);
        return true;
    }

    public void tick() {
        this.ticks++;
        if (this.phase != Phase.READY) {
            return;
        }
        Iterator<Map.Entry<UUID, Download>> downloadIterator = this.downloads.entrySet().iterator();
        while (downloadIterator.hasNext()) {
            Map.Entry<UUID, Download> entry = downloadIterator.next();
            if (this.ticks - entry.getValue().lastActivity > TRANSFER_TIMEOUT_TICKS) {
                downloadIterator.remove();
                sendId(SyncPacket.CANCEL_LITEMATIC, entry.getKey());
                this.listener.downloadFailed(entry.getValue().placement);
            }
        }
        Iterator<Map.Entry<UUID, Upload>> uploadIterator = this.uploads.entrySet().iterator();
        while (uploadIterator.hasNext()) {
            Map.Entry<UUID, Upload> entry = uploadIterator.next();
            Upload upload = entry.getValue();
            boolean streaming = upload.requested && upload.offset <= upload.data.length;
            int limit = streaming ? TRANSFER_TIMEOUT_TICKS : SHARE_TIMEOUT_TICKS;
            if (this.ticks - upload.lastActivity > limit) {
                uploadIterator.remove();
                if (streaming) {
                    sendId(SyncPacket.CANCEL_LITEMATIC, entry.getKey());
                }
                this.listener.shareFailed(upload.placement);
            }
        }
        Iterator<Map.Entry<UUID, Integer>> modifyIterator = this.modifyRequests.entrySet().iterator();
        while (modifyIterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = modifyIterator.next();
            if (this.ticks - entry.getValue() > TRANSFER_TIMEOUT_TICKS) {
                modifyIterator.remove();
                if (!this.abandoned.remove(entry.getKey())) {
                    this.listener.modifyDenied(entry.getKey());
                }
            }
        }
    }

    public void close() {
        reset();
        this.phase = Phase.CLOSED;
    }

    private void reset() {
        this.phase = Phase.IDLE;
        this.serverVersion = "";
        this.serverFeatures = EnumSet.noneOf(SyncFeature.class);
        this.placements.clear();
        this.uploads.clear();
        this.downloads.clear();
        this.modifyRequests.clear();
        this.abandoned.clear();
        this.modifying.clear();
    }

    private Set<SyncFeature> writeFeatures() {
        EnumSet<SyncFeature> features = EnumSet.copyOf(FEATURES);
        features.retainAll(this.serverFeatures);
        return features;
    }

    private void sendId(SyncPacket type, UUID id) {
        FriendlyByteBuf buf = SyncCodec.buffer();
        buf.writeUUID(id);
        send(type, buf);
    }

    private void send(SyncPacket type, FriendlyByteBuf body) {
        this.sender.accept(SyncCodec.frame(type, body));
    }
}
