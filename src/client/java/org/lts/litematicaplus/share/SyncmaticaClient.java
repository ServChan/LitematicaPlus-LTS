package org.lts.litematicaplus.share;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.LitematicaPlusClient;
import org.lts.litematicaplus.LitematicaPlusConfig;
import org.lts.litematicaplus.compat.Addon;
import org.lts.litematicaplus.compat.Addons;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.BlockStateNbt;
import org.lts.litematicaplus.schematic.LitematicCodec;
import org.lts.litematicaplus.schematic.Schematic;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.util.AtomicFilePersistence;
import org.lts.litematicaplus.util.LocalizedException;
import org.lts.litematicaplus.util.Notify;
import org.lts.litematicaplus.util.Workers;

public final class SyncmaticaClient implements SyncSession.Listener {
    public enum Status {
        LOADED,
        DOWNLOADING,
        CACHED,
        REMOTE,
        OTHER_DIMENSION
    }

    public static final String CACHE_FOLDER = "syncmatics";
    private static final SyncmaticaClient INSTANCE = new SyncmaticaClient();

    private final SyncSession session = new SyncSession(this::send, this);
    private final Map<UUID, UUID> uploads = new HashMap<>();
    private final Map<UUID, byte[]> uploadData = new HashMap<>();
    private final Set<UUID> preparing = new HashSet<>();
    private final Set<UUID> loadAfterDownload = new HashSet<>();
    private final Set<UUID> cached = new HashSet<>();
    private boolean registered;
    private boolean conflict;
    private int revision;
    private int generation;

    private SyncmaticaClient() {
    }

    public static SyncmaticaClient get() {
        return INSTANCE;
    }

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded("syncmatica")) {
            INSTANCE.conflict = true;
            LitematicaPlusClient.LOGGER.warn("Syncmatica is installed; shared schematics stay with it");
            return;
        }
        PayloadTypeRegistry.clientboundPlay().register(SyncmaticaPayload.TYPE, SyncmaticaPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SyncmaticaPayload.TYPE, SyncmaticaPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(SyncmaticaPayload.TYPE,
                (payload, context) -> INSTANCE.receive(payload.data()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> INSTANCE.reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(INSTANCE::reset));
        INSTANCE.registered = true;
    }

    public boolean registered() {
        return this.registered;
    }

    public boolean conflict() {
        return this.conflict;
    }

    public boolean available() {
        return this.session.ready();
    }

    public SyncSession.Phase phase() {
        return this.session.phase();
    }

    public String serverVersion() {
        return this.session.serverVersion();
    }

    public Set<SyncFeature> serverFeatures() {
        return this.session.serverFeatures();
    }

    public int revision() {
        return this.revision;
    }

    public List<SharedPlacement> placements() {
        return new ArrayList<>(this.session.placements());
    }

    public SharedPlacement placement(UUID id) {
        return this.session.placement(id);
    }

    public int downloadedBytes(SharedPlacement placement) {
        return this.session.downloaded(placement.id());
    }

    private void receive(byte[] data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> receive(data));
            return;
        }
        if (LitematicaPlusConfig.current().syncmaticaEnabled) {
            this.session.receive(data);
        }
    }

    private void send(byte[] data) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SyncmaticaPayload(data));
        }
    }

    private void reset() {
        this.session.close();
        this.uploads.clear();
        this.uploadData.clear();
        this.preparing.clear();
        this.loadAfterDownload.clear();
        this.generation++;
        this.revision++;
    }

    public void tick() {
        if (!this.registered) {
            return;
        }
        if (!LitematicaPlusConfig.current().syncmaticaEnabled && this.session.phase() != SyncSession.Phase.IDLE
                && this.session.phase() != SyncSession.Phase.CLOSED) {
            reset();
            return;
        }
        this.session.tick();
    }

    public static Path cacheDirectory() {
        return PlacementManager.schematicsDirectory().resolve(CACHE_FOLDER);
    }

    public static Path cacheFile(UUID hash) {
        return cacheDirectory().resolve(hash + SchematicIo.LITEMATIC_EXTENSION);
    }

    public static String currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    public SchematicPlacement linked(UUID id) {
        if (id == null) {
            return null;
        }
        for (SchematicPlacement placement : PlacementManager.get().placements()) {
            if (id.equals(placement.serverId())) {
                return placement;
            }
        }
        return null;
    }

    public SharedPlacement sharedFor(SchematicPlacement placement) {
        return placement == null ? null : this.session.placement(placement.serverId());
    }

    public Status status(SharedPlacement placement) {
        if (linked(placement.id()) != null) {
            return Status.LOADED;
        }
        if (this.session.isDownloading(placement.id())) {
            return Status.DOWNLOADING;
        }
        if (!placement.dimension().equals(currentDimension())) {
            return Status.OTHER_DIMENSION;
        }
        return this.cached.contains(placement.hash()) ? Status.CACHED : Status.REMOTE;
    }

    public boolean isSharing(SchematicPlacement placement) {
        return placement != null && (this.preparing.contains(placement.id()) || this.uploads.containsValue(placement.id()));
    }

    public boolean isModifying(SchematicPlacement placement) {
        return placement != null && placement.serverId() != null && this.session.isModifying(placement.serverId());
    }

    public boolean isModifyPending(SchematicPlacement placement) {
        return placement != null && placement.serverId() != null && this.session.isModifyPending(placement.serverId());
    }

    public boolean canShare(SchematicPlacement placement) {
        return available() && placement != null && placement.serverId() == null
                && placement.loadState() == SchematicPlacement.LoadState.READY && placement.schematic() != null
                && !isSharing(placement) && Minecraft.getInstance().level != null;
    }

    public void share(SchematicPlacement placement) {
        if (!canShare(placement)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Schematic schematic = placement.schematic();
        Path path = PlacementManager.resolve(placement.file());
        boolean litematic = path.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(SchematicIo.LITEMATIC_EXTENSION);
        String fileName = litematic ? path.getFileName().toString()
                : SchematicIo.baseName(path) + SchematicIo.LITEMATIC_EXTENSION;
        int litematicVersion = litematic ? schematic.metadata().schematicVersion : LitematicCodec.SCHEMATIC_VERSION;
        int dataVersion = litematic ? schematic.metadata().dataVersion : BlockStateNbt.currentDataVersion();
        SharedPlacement.Player owner = new SharedPlacement.Player(minecraft.getUser().getProfileId(),
                minecraft.getUser().getName());
        String dimension = currentDimension();
        int session = this.generation;
        UUID localId = placement.id();
        this.preparing.add(localId);
        this.revision++;
        CompletableFuture.supplyAsync(() -> {
            try {
                return litematic ? Files.readAllBytes(path) : encode(schematic);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, Workers.io()).whenComplete((data, failure) -> minecraft.execute(() -> {
            this.preparing.remove(localId);
            this.revision++;
            if (session != this.generation) {
                return;
            }
            SchematicPlacement current = PlacementManager.get().byId(localId);
            if (failure != null || data == null) {
                Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.share_prepare_failed",
                        failure == null ? new IllegalStateException("no data") : failure, placement.name());
                Notify.chat(Component.translatable("litematicaplus.share.share_failed_local", placement.name(),
                        Notify.describe(failure)).withStyle(ChatFormatting.RED));
                return;
            }
            if (current == null || current.serverId() != null) {
                return;
            }
            if (data.length > SyncSession.MAX_FILE) {
                Notify.chat(Component.translatable("litematicaplus.share.too_large", current.name(),
                        SyncSession.MAX_FILE >> 20).withStyle(ChatFormatting.RED));
                return;
            }
            SharedPlacement shared = new SharedPlacement(UUID.randomUUID(), SyncCodec.hash(data), fileName,
                    current.name());
            shared.setOwner(owner);
            shared.setLastModifiedBy(owner);
            shared.setVersions(litematicVersion, dataVersion);
            shared.place(current.x(), current.y(), current.z(), dimension, current.rotation().ordinal(),
                    current.mirror().ordinal());
            shared.setRegions(PlacementManager.sharedShifts(current));
            if (this.session.share(shared, data)) {
                this.uploads.put(shared.id(), localId);
                this.uploadData.put(shared.id(), data);
                Notify.actionBar("litematicaplus.share.sharing", current.name());
            }
        }));
    }

    private static byte[] encode(Schematic schematic) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(LitematicCodec.write(schematic), out);
        return out.toByteArray();
    }

    public void load(SharedPlacement shared) {
        if (!available()) {
            return;
        }
        SchematicPlacement existing = linked(shared.id());
        if (existing != null) {
            PlacementManager.get().select(existing);
            return;
        }
        if (!shared.dimension().equals(currentDimension())) {
            Notify.chat(Component.translatable("litematicaplus.share.other_dimension", shared.title(),
                    shared.dimension()).withStyle(ChatFormatting.RED));
            return;
        }
        if (this.session.isDownloading(shared.id())) {
            return;
        }
        Path file = cacheFile(shared.hash());
        Minecraft minecraft = Minecraft.getInstance();
        int session = this.generation;
        CompletableFuture.supplyAsync(() -> Files.isRegularFile(file), Workers.io())
                .whenComplete((present, failure) -> minecraft.execute(() -> {
                    if (session != this.generation || linked(shared.id()) != null
                            || this.session.placement(shared.id()) == null) {
                        return;
                    }
                    if (Boolean.TRUE.equals(present)) {
                        this.cached.add(shared.hash());
                        place(shared);
                    } else {
                        this.cached.remove(shared.hash());
                        if (this.session.download(shared.id())) {
                            this.loadAfterDownload.add(shared.id());
                        }
                    }
                    this.revision++;
                }));
    }

    public void unload(SharedPlacement shared) {
        SchematicPlacement local = linked(shared.id());
        if (local != null) {
            PlacementManager.get().remove(local);
        }
    }

    public void removeFromServer(SharedPlacement shared) {
        this.session.remove(shared.id());
    }

    private void place(SharedPlacement shared) {
        PlacementManager.get().createShared(cacheFile(shared.hash()), shared.title(),
                new BlockPos(shared.x(), shared.y(), shared.z()), Rotation.values()[shared.rotation()],
                Mirror.values()[shared.mirror()], shared.id(), shared.regions());
        this.revision++;
    }

    public boolean allowLockChange(SchematicPlacement placement, boolean locked) {
        UUID id = placement.serverId();
        if (id == null || !available() || this.session.placement(id) == null) {
            return true;
        }
        if (!locked) {
            if (this.session.isModifying(id)) {
                return true;
            }
            if (!this.session.canModify()) {
                Notify.chat(Component.translatable("litematicaplus.share.modify_unsupported")
                        .withStyle(ChatFormatting.RED));
            } else if (this.session.requestModify(id)) {
                Notify.actionBar("litematicaplus.share.modify_requested", placement.name());
            }
            this.revision++;
            return false;
        }
        if (this.session.finishModify(id, placement.x(), placement.y(), placement.z(), placement.rotation().ordinal(),
                placement.mirror().ordinal(), PlacementManager.sharedShifts(placement))) {
            Notify.actionBar("litematicaplus.share.modify_sent", placement.name());
            this.revision++;
        }
        return true;
    }

    public void forget(SchematicPlacement placement) {
        UUID id = placement.serverId();
        if (id != null) {
            this.session.abandonModify(id);
            this.revision++;
        }
    }

    public void reconcile() {
        if (!available()) {
            return;
        }
        PlacementManager manager = PlacementManager.get();
        for (SchematicPlacement local : new ArrayList<>(manager.placements())) {
            UUID id = local.serverId();
            if (id == null) {
                continue;
            }
            SharedPlacement shared = this.session.placement(id);
            if (shared == null) {
                manager.link(local, null);
                Notify.chat(Component.translatable("litematicaplus.share.removed", local.name()));
            } else if (!this.session.isModifying(id) && !this.session.isModifyPending(id)) {
                apply(local, shared);
            }
        }
        this.revision++;
    }

    private static void apply(SchematicPlacement local, SharedPlacement shared) {
        PlacementManager.get().applyShared(local, shared.x(), shared.y(), shared.z(),
                Rotation.values()[shared.rotation()], Mirror.values()[shared.mirror()], shared.regions());
    }

    private void scanCache() {
        Minecraft minecraft = Minecraft.getInstance();
        int session = this.generation;
        Path directory = cacheDirectory();
        CompletableFuture.supplyAsync(() -> {
            Set<UUID> found = new HashSet<>();
            if (!Files.isDirectory(directory)) {
                return found;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SchematicIo.LITEMATIC_EXTENSION)) {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    try {
                        found.add(UUID.fromString(name.substring(0, name.length() - SchematicIo.LITEMATIC_EXTENSION.length())));
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                }
            } catch (IOException exception) {
                Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.cache_unreadable", exception);
            }
            return found;
        }, Workers.io()).thenAccept(found -> minecraft.execute(() -> {
            if (session == this.generation) {
                this.cached.clear();
                this.cached.addAll(found);
                this.revision++;
            }
        }));
    }

    private void store(SharedPlacement shared, byte[] data, Runnable then) {
        Minecraft minecraft = Minecraft.getInstance();
        Path file = cacheFile(shared.hash());
        int session = this.generation;
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.isRegularFile(file) && Files.size(file) == data.length) {
                    return;
                }
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".part");
                try {
                    Files.write(temporary, data);
                    AtomicFilePersistence.replace(temporary, file);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                throw new CompletionException(new LocalizedException("litematicaplus.share.save_failed",
                        "Could not save " + file, exception));
            }
        }, Workers.io()).whenComplete((ignored, failure) -> minecraft.execute(() -> {
            if (failure != null) {
                Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.save_failed", failure, shared.title());
                Notify.chat(Component.translatable("litematicaplus.share.save_failed", shared.title(),
                        Notify.describe(failure)).withStyle(ChatFormatting.RED));
                return;
            }
            this.cached.add(shared.hash());
            this.revision++;
            if (session == this.generation && then != null) {
                then.run();
            }
        }));
    }

    @Override
    public void ready() {
        this.revision++;
        scanCache();
        reconcile();
        Notify.chat(Component.translatable("litematicaplus.share.connected", this.session.placements().size())
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void rejected(String serverVersion) {
        Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.rejected", "", serverVersion);
        this.revision++;
    }

    @Override
    public void added(SharedPlacement placement) {
        this.revision++;
    }

    @Override
    public void removed(SharedPlacement placement) {
        this.loadAfterDownload.remove(placement.id());
        SchematicPlacement local = linked(placement.id());
        if (local != null) {
            PlacementManager.get().link(local, null);
            Notify.chat(Component.translatable("litematicaplus.share.removed", local.name()));
        }
        this.revision++;
    }

    @Override
    public void modified(SharedPlacement placement) {
        SchematicPlacement local = linked(placement.id());
        if (local != null && !this.session.isModifying(placement.id())) {
            apply(local, placement);
        }
        this.revision++;
    }

    @Override
    public void shared(SharedPlacement placement) {
        UUID localId = this.uploads.remove(placement.id());
        byte[] data = this.uploadData.remove(placement.id());
        SchematicPlacement local = PlacementManager.get().byId(localId);
        if (local != null && local.serverId() == null) {
            PlacementManager.get().link(local, placement.id());
            Notify.chat(Component.translatable("litematicaplus.share.shared", local.name())
                    .withStyle(ChatFormatting.GREEN));
        }
        if (data != null) {
            store(placement, data, null);
        }
        this.revision++;
    }

    @Override
    public void shareFailed(SharedPlacement placement) {
        Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.share_failed", "", placement.title());
        this.uploads.remove(placement.id());
        this.uploadData.remove(placement.id());
        Notify.chat(Component.translatable("litematicaplus.share.share_failed", placement.title())
                .withStyle(ChatFormatting.RED));
        this.revision++;
    }

    @Override
    public void downloaded(SharedPlacement placement, byte[] data) {
        boolean load = this.loadAfterDownload.remove(placement.id());
        store(placement, data, () -> {
            if (load && linked(placement.id()) == null && this.session.placement(placement.id()) != null) {
                place(placement);
            }
        });
        this.revision++;
    }

    @Override
    public void downloadFailed(SharedPlacement placement) {
        if (this.loadAfterDownload.remove(placement.id())) {
            Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.download_failed", "", placement.title());
            Notify.chat(Component.translatable("litematicaplus.share.download_failed", placement.title())
                    .withStyle(ChatFormatting.RED));
        }
        this.revision++;
    }

    @Override
    public void modifyAccepted(UUID id) {
        SchematicPlacement local = linked(id);
        if (local == null) {
            this.session.abandonModify(id);
        } else {
            PlacementManager.get().forceLocked(local, false);
            Notify.actionBar("litematicaplus.share.modify_accepted", local.name());
        }
        this.revision++;
    }

    @Override
    public void modifyDenied(UUID id) {
        SchematicPlacement local = linked(id);
        if (local != null) {
            PlacementManager.get().forceLocked(local, true);
            SharedPlacement shared = this.session.placement(id);
            if (shared != null) {
                apply(local, shared);
            }
        }
        Notify.chat(Component.translatable("litematicaplus.share.modify_denied").withStyle(ChatFormatting.RED));
        this.revision++;
    }

    @Override
    public void message(String type, String key) {
        String local = "litematicaplus.share.server." + key.replace("syncmatica.", "");
        Component text = Language.getInstance().has(local) ? Component.translatable(local) : Component.literal(key);
        ChatFormatting color = switch (type) {
            case "ERROR" -> ChatFormatting.RED;
            case "WARNING" -> ChatFormatting.YELLOW;
            case "SUCCESS" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
        Notify.chat(Component.translatable("litematicaplus.share.server_message", text).withStyle(color));
        this.revision++;
    }

    @Override
    public void error(SyncPacket packet, RuntimeException failure) {
        Addons.report(Addon.SYNCMATICA, "litematicaplus.addon.problem.bad_packet", failure,
                packet == null ? "?" : packet.id());
    }
}
