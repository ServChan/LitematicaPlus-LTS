package org.lts.litematicaplus.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lts.litematicaplus.gui.HubScreen;
import org.lts.litematicaplus.placement.PlacementManager;
import org.lts.litematicaplus.placement.SchematicPlacement;
import org.lts.litematicaplus.schematic.SchematicIo;
import org.lts.litematicaplus.share.SharedPlacement;
import org.lts.litematicaplus.share.SyncFeature;
import org.lts.litematicaplus.share.SyncmaticaClient;

final class SyncmaticaSuite {
    private static final int SECOND = 20;

    private SyncmaticaSuite() {
    }

    static void run(ClientGameTestContext context) {
        Path directory = Path.of(System.getProperty("litematicaplus.syncmaticaServer"));
        PurpurServer server = null;
        try {
            server = PurpurServer.start(directory, true);
            join(context, server);
            List<SyncFeature> missing = context.computeOnClient(client -> List.of(SyncFeature.CORE, SyncFeature.FEATURE,
                    SyncFeature.MODIFY, SyncFeature.MESSAGE, SyncFeature.CORE_EX, SyncFeature.DISPLAY_NAME).stream()
                    .filter(feature -> !SyncmaticaClient.get().serverFeatures().contains(feature)).toList());
            require(missing.isEmpty(), "server did not negotiate " + missing);
            require(context.computeOnClient(client -> SyncmaticaClient.get().placements().isEmpty()),
                    "fresh server should have no shared schematics");

            BlockPos origin = context.computeOnClient(client -> client.player.blockPosition().offset(3, 0, 3));
            Path file = context.computeOnClient(client -> {
                Path target = PlacementManager.schematicsDirectory().resolve("gametest/shared_house.litematic");
                SchematicIo.write(target, LitematicaPlusGameTest.house());
                return target;
            });
            byte[] original = Files.readAllBytes(file);
            SchematicPlacement placement = context.computeOnClient(client -> PlacementManager.get().create(file, origin));
            context.waitFor(client -> placement.loadState() == SchematicPlacement.LoadState.READY, 30 * SECOND);
            context.runOnClient(client -> SyncmaticaClient.get().share(placement));
            context.waitFor(client -> placement.serverId() != null, 60 * SECOND);
            UUID id = context.computeOnClient(client -> placement.serverId());
            SharedPlacement shared = context.computeOnClient(client -> SyncmaticaClient.get().placement(id));
            String player = context.computeOnClient(client -> client.getUser().getName());
            require(shared.x() == origin.getX() && shared.y() == origin.getY() && shared.z() == origin.getZ(),
                    "shared origin " + shared.x() + " " + shared.y() + " " + shared.z() + " != " + origin);
            require(player.equals(shared.owner().name()), "owner " + shared.owner().name() + " != " + player);
            require(context.computeOnClient(client -> placement.locked()), "shared placement should be locked");
            Path blob = server.pluginData().resolve("syncmatics/" + shared.hash() + SchematicIo.LITEMATIC_EXTENSION);
            require(Files.isRegularFile(blob), "server did not store " + blob);
            require(Arrays.equals(original, Files.readAllBytes(blob)), "server copy differs from the shared file");
            context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SERVER));
            context.waitTicks(10);
            context.takeScreenshot("syncmatica_01_shared");
            context.setScreen(() -> null);

            context.runOnClient(client -> PlacementManager.get().setLocked(placement, false));
            context.waitFor(client -> !placement.locked(), 10 * SECOND);
            BlockPos moved = origin.offset(5, 0, -2);
            context.runOnClient(client -> {
                PlacementManager.get().move(placement, moved.getX(), moved.getY(), moved.getZ());
                PlacementManager.get().rotate(placement, Rotation.CLOCKWISE_90);
                PlacementManager.get().mirror(placement, Mirror.LEFT_RIGHT);
                PlacementManager.get().setLocked(placement, true);
            });
            context.waitFor(client -> {
                SharedPlacement current = SyncmaticaClient.get().placement(id);
                return current != null && current.x() == moved.getX() && current.z() == moved.getZ()
                        && current.rotation() == Rotation.CLOCKWISE_90.ordinal()
                        && current.mirror() == Mirror.LEFT_RIGHT.ordinal();
            }, 10 * SECOND);
            require(context.computeOnClient(client -> placement.locked()), "placement should be locked after the edit");

            leave(context, server);
            server.close();
            server = PurpurServer.start(directory, false);
            join(context, server);
            SharedPlacement restored = context.computeOnClient(client -> SyncmaticaClient.get().placement(id));
            require(restored != null, "shared schematic did not survive a server restart");
            require(restored.x() == moved.getX() && restored.rotation() == Rotation.CLOCKWISE_90.ordinal(),
                    "server forgot the edit");
            Path cache = context.computeOnClient(client -> SyncmaticaClient.cacheFile(restored.hash()));
            Files.deleteIfExists(cache);
            context.runOnClient(client -> SyncmaticaClient.get().load(restored));
            context.waitFor(client -> {
                SchematicPlacement local = SyncmaticaClient.get().linked(id);
                return local != null && local.loadState() == SchematicPlacement.LoadState.READY;
            }, 60 * SECOND);
            require(Arrays.equals(original, Files.readAllBytes(cache)), "downloaded copy differs from the shared file");
            SchematicPlacement downloaded = context.computeOnClient(client -> SyncmaticaClient.get().linked(id));
            require(downloaded.x() == moved.getX() && downloaded.z() == moved.getZ()
                    && downloaded.rotation() == Rotation.CLOCKWISE_90 && downloaded.mirror() == Mirror.LEFT_RIGHT,
                    "downloaded placement is not where the server says");
            require(downloaded.locked(), "downloaded shared placement should be locked");
            context.setScreen(() -> new HubScreen(null, HubScreen.Tab.SERVER));
            context.waitTicks(10);
            context.takeScreenshot("syncmatica_02_downloaded");
            context.setScreen(() -> null);

            context.runOnClient(client -> SyncmaticaClient.get().removeFromServer(restored));
            context.waitFor(client -> SyncmaticaClient.get().placement(id) == null, 10 * SECOND);
            context.waitFor(client -> downloaded.serverId() == null, 5 * SECOND);
            require(context.computeOnClient(client -> PlacementManager.get().placements().contains(downloaded)),
                    "removing from the server should keep the local placement");
            context.waitTicks(SECOND);
            require(!Files.exists(blob), "server kept the removed file");

            context.waitTicks(5 * SECOND);
            require(context.computeOnClient(client -> client.getConnection() != null), "client lost the connection");
            leave(context, server);
            server.close();
            List<String> problems = server.lines(line -> line.contains("[SyncmaticaPaper]")
                    && (line.contains("WARN]") || line.contains("ERROR]")));
            require(problems.isEmpty(), "server reported problems:\n" + String.join("\n", problems));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    private static void join(ClientGameTestContext context, PurpurServer server) {
        String address = "127.0.0.1:" + server.port();
        context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client,
                ServerAddress.parseString(address), new ServerData("syncmatica", address, ServerData.Type.OTHER), false,
                null));
        context.waitFor(client -> client.player != null && client.level != null, 60 * SECOND);
        context.waitFor(client -> SyncmaticaClient.get().available(), 30 * SECOND);
    }

    private static void leave(ClientGameTestContext context, PurpurServer server) {
        int before = server.lines(line -> line.contains("lost connection")).size();
        context.runOnClient(client -> client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
        context.waitFor(client -> client.level == null, 10 * SECOND);
        context.waitFor(client -> server.lines(line -> line.contains("lost connection")).size() > before, 10 * SECOND);
        context.setScreen(TitleScreen::new);
    }

    private static void require(boolean condition, String message) {
        LitematicaPlusGameTest.require(condition, message);
    }
}
